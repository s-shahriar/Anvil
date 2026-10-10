// Pre-renders the web apps' formula pages (math formulas, equations) and PUBLISHES them to Supabase, so Slate reads one
// copy instead of a bundled one: an edit in a web project reaches Slate with `node tools/prerender/build.mjs`, no APK.
//
//   node tools/prerender/build.mjs [--dry]
//
// Needs the two web projects checked out next to Slate (GENERAL_QUIZ / ICT_QUIZ override the paths), Node, Google Chrome,
// and each web project's SUPABASE_SERVICE_ROLE_KEY in its .env.local. Rows go to `content_blobs` (kind `web`):
//   general / web/math               { sections, html }
//   ict     / web/equation_<topic>   { id, groups, equations, html }
// Static support files (KaTeX css + fonts, the page stylesheets and scripts) are code, not content: they are still written to
// app/src/main/assets/web and ship with the app.
//
// What it does:
//  1. bundles the web components with esbuild (stubbing their app-only imports) and renders them to static markup, with
//     cover mode forced ON so every coverable element carries its "covered" class (a controller script turns it on/off);
//  2. lets headless Chrome run the web's own uid logic over the markup, so each Important-able card gets data-uid;
//  3. uploads the markup, and copies KaTeX's CSS/fonts and the page stylesheets.
import { createRequire } from 'node:module'
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

const HOME = os.homedir()
const GQ = process.env.GENERAL_QUIZ || `${HOME}/Projects/Self/Quiz/general-quiz`
const IQ = process.env.ICT_QUIZ || `${HOME}/Projects/Self/Quiz/ict-quiz`
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../..')
const OUT = `${ROOT}/app/src/main/assets`
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'slate-prerender-'))
const esbuild = createRequire(`${IQ}/package.json`)('esbuild')

fs.mkdirSync(`${OUT}/web`, { recursive: true })

const DRY = process.argv.includes('--dry')
/** Rows to publish, per project. */
const rows = { general: [], ict: [] }

/** Bundle JSX (entry source given as text) against one web project's node_modules, then import the result. */
async function run(entry, project, stubs) {
  const stubPlugin = {
    name: 'stubs',
    setup(b) {
      for (const [re, contents] of stubs) {
        b.onResolve({ filter: re }, (a) => ({ path: a.path, namespace: 'stub' }))
        b.onLoad({ filter: re, namespace: 'stub' }, () => ({ contents, loader: 'jsx', resolveDir: project }))
      }
    },
  }
  const result = await esbuild.build({
    stdin: { contents: entry, resolveDir: project, loader: 'jsx', sourcefile: 'entry.jsx' },
    bundle: true, platform: 'node', format: 'cjs', write: false, jsx: 'automatic', logLevel: 'error',
    nodePaths: [`${project}/node_modules`],
    loader: { '.js': 'jsx', '.css': 'empty' },
    define: { 'import.meta.env': '{}', 'process.env.NODE_ENV': '"production"' },
    plugins: [stubPlugin],
  })
  // CommonJS, not ESM: React's server renderer needs Node built-ins that an ES bundle cannot require().
  const file = path.join(TMP, `bundle-${Math.random().toString(36).slice(2)}.cjs`)
  fs.writeFileSync(file, result.outputFiles[0].text)
  const m = await import(pathToFileURL(file).href)
  return m.default ?? m
}

/** Runs [script] over [html] in headless Chrome and returns the resulting body markup. */
function inChrome(html, script) {
  const page = path.join(TMP, `page-${Math.random().toString(36).slice(2)}.html`)
  fs.writeFileSync(page, `<!doctype html><meta charset="utf-8"><body><div id="root">${html}</div><script>${script}\n` +
    `document.getElementById('root').insertAdjacentHTML('afterbegin','<!--S-->');document.getElementById('root').insertAdjacentHTML('beforeend','<!--E-->');</script></body>`)
  const dom = execFileSync('google-chrome', ['--headless=new', '--no-sandbox', '--disable-gpu', '--virtual-time-budget=3000', '--dump-dom', pathToFileURL(page).href],
    { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 })
  // The markers also appear as plain text inside the helper <script> that follows the content, so take the first end
  // marker after the start marker, not the last one in the document.
  const a = dom.indexOf('<!--S-->'), b = a < 0 ? -1 : dom.indexOf('<!--E-->', a)
  if (a < 0 || b < 0) throw new Error('Chrome step produced no markup')
  return dom.slice(a + 8, b)
}

// ── Math formulas (general-quiz) ─────────────────────────────────────────────
async function math() {
  const sec = `${GQ}/src/components/utility/math-formulas/sections`
  const names = fs.readdirSync(sec).filter((f) => f.endsWith('.jsx')).map((f) => f.replace('.jsx', ''))
  // The page renders them in SECTIONS order; map section ids to components by the order they are imported there.
  const page = fs.readFileSync(`${GQ}/src/components/utility/MathFormulas.jsx`, 'utf8')
  const order = [...page.matchAll(/<(\w+Section) \/>/g)].map((m) => m[1])
  const entry = `
    import { renderToStaticMarkup } from 'react-dom/server'
    import { CoverProvider } from '${GQ}/src/components/utility/math-formulas/MathFormulaHelpers.jsx'
    import { SECTIONS } from '${GQ}/src/data/utility/mathFormulasData.js'
    ${names.map((n) => `import ${n} from '${sec}/${n}.jsx'`).join('\n')}
    const all = { ${names.join(', ')} }
    const order = ${JSON.stringify(order)}
    export const sections = SECTIONS
    export const html = renderToStaticMarkup(<CoverProvider value={true}>{order.map(n => { const C = all[n]; return <C key={n} /> })}</CoverProvider>)
  `
  const mod = await run(entry, GQ, [
    [/ImportantContext\.jsx$/, `export const useImportantContext = () => ({ value: new Set(), add() {}, remove() {}, toggle() {} })`],
  ])
  // The web derives each card's identity in the browser (section id + card title -> hash); do the same, with the same code.
  const qid = fs.readFileSync(`${GQ}/src/lib/qid.js`, 'utf8').replace(/export /g, '')
  const body = inChrome(mod.html, `${qid}
    document.querySelectorAll('.mf-card').forEach(function (card) {
      if (!card.querySelector(':scope > .mf-imp-btn')) return;
      var sectionId = (card.closest('.mf-section') || {}).id || '';
      var t = card.querySelector('.mf-card-title');
      card.setAttribute('data-uid', mathUidOfText(sectionId + '::' + ((t && t.textContent) || card.textContent || '')));
    });`)
  rows.general.push({ kind: 'web', key: 'math', sort_order: 0, payload: { sections: mod.sections, html: body } })
  const cards = (body.match(/data-uid="/g) || []).length
  console.log(`math: ${mod.sections.length} sections, ${cards} markable cards, ${(body.length / 1024).toFixed(0)} KB`)
}

// ── Equations (ict-quiz) ─────────────────────────────────────────────────────
async function equations() {
  const entry = `
    import { renderToStaticMarkup } from 'react-dom/server'
    import { CoverProvider, EqRow, Mem, Symbols } from '${IQ}/src/components/equation/EquationHelpers.jsx'
    import { DIAGRAMS } from '${IQ}/src/components/equation/diagrams/index.js'
    import cn from '${IQ}/src/data/equation/computer_network.js'
    import os from '${IQ}/src/data/equation/operating_system.js'
    import tc from '${IQ}/src/data/equation/telecommunication.js'
    import { uidFor } from '${IQ}/src/lib/qid.js'
    const hl = () => undefined
    const topics = { computer_network: cn, operating_system: os, telecommunication: tc }
    function Group({ g, i, topic }) {
      const Diagram = DIAGRAMS[g.diagram]
      return (
        <section className="eq-group" id={'eq-' + g.id}>
          <header className="eq-group-head">
            <span className="eq-group-num">{i + 1}</span>
            <div><h2 className="eq-group-title">{g.title}</h2>{g.sub && <p className="eq-group-sub">{g.sub}</p>}</div>
          </header>
          <div className="eq-card" data-hl-root={uidFor('equation', topic + '/' + g.id)}>
            {Diagram && (<figure className="eq-diagram"><div className="eq-diagram-scroll"><Diagram /></div>
              {g.caption && <figcaption data-hl-block="caption">{g.caption}</figcaption>}</figure>)}
            {g.symbols?.length > 0 && <Symbols items={g.symbols} hl={hl} />}
            <div className="eq-list">{g.equations.map(eq => <EqRow key={eq.name} eq={eq} hl={hl} />)}</div>
            {g.mnemonic && <Mem><span data-hl-block="mnemonic">{g.mnemonic}</span></Mem>}
          </div>
        </section>
      )
    }
    export const out = Object.fromEntries(Object.entries(topics).map(([id, data]) => [id, {
      groups: data.groups.map(g => ({ id: g.id, title: g.title, equations: g.equations.length })),
      html: renderToStaticMarkup(<CoverProvider value={true}>{data.groups.map((g, i) => <Group key={g.id} g={g} i={i} topic={id} />)}</CoverProvider>),
    }]))
  `
  const mod = await run(entry, IQ, [
    [/shared\/HighlightableText\.jsx$/, `export default function HighlightableText({ as: As = 'span', className, block, text, highlights, ...rest }) { return <As className={className} data-hl-block={block} {...rest}>{text}</As> }`],
    [/lib\/highlightSync\.js$/, `export const DEFAULT_COLOR = 'mint'`],
  ])
  Object.entries(mod.out).forEach(([id, t], i) => {
    rows.ict.push({
      kind: 'web', key: `equation_${id}`, sort_order: i,
      payload: { id, groups: t.groups, equations: t.groups.reduce((n, g) => n + g.equations, 0), html: t.html },
    })
    console.log(`equation ${id}: ${t.groups.length} groups, ${(t.html.length / 1024).toFixed(0)} KB`)
  })
}

/** Reads KEY=value pairs from a web project's env files. */
function envOf(project) {
  const env = {}
  for (const f of ['.env', '.env.local']) {
    if (!fs.existsSync(`${project}/${f}`)) continue
    for (const line of fs.readFileSync(`${project}/${f}`, 'utf8').split('\n')) {
      const m = line.match(/^\s*([\w.]+)\s*=\s*(.*)\s*$/)
      if (m && !line.trimStart().startsWith('#')) env[m[1]] = m[2].trim().replace(/^["']|["']$/g, '')
    }
  }
  return env
}

/** Upserts a project's rows into its own Supabase project (service role key from that project's env). */
async function publish(name, project) {
  const list = rows[name]
  for (const r of list) console.log(`  ${name}/${r.kind}/${r.key}: ${(JSON.stringify(r.payload).length / 1024).toFixed(0)} KB`)
  if (DRY) return console.log(`${name}: dry run, nothing uploaded`)
  const env = envOf(project)
  if (!env.VITE_SUPABASE_URL || !env.SUPABASE_SERVICE_ROLE_KEY) throw new Error(`${project}: missing VITE_SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY`)
  const { createClient } = createRequire(`${project}/package.json`)('@supabase/supabase-js')
  const db = createClient(env.VITE_SUPABASE_URL, env.SUPABASE_SERVICE_ROLE_KEY)
  for (const r of list) { // one at a time: the math page alone is ~600 KB
    const { error } = await db.from('content_blobs').upsert({ ...r, updated_at: new Date().toISOString() }, { onConflict: 'kind,key' })
    if (error) throw new Error(`${name}/${r.key}: ${error.message}`)
  }
  console.log(`${name}: published ${list.length} rows`)
}

// ── Highlights inside the equation WebView: the web's own anchoring + painting code, plus glue ──
function highlightScript() {
  const strip = (t) => t.replace(/^export /gm, '').replace(/^import .*$/gm, '')
  const anchor = strip(fs.readFileSync(`${GQ}/src/lib/textAnchor.js`, 'utf8'))
  const dom = strip(fs.readFileSync(`${GQ}/src/lib/highlightDom.js`, 'utf8')).replace(/DEFAULT_COLOR/g, "'mint'")
  const glue = fs.readFileSync(new URL('./highlight-glue.js', import.meta.url), 'utf8')
  fs.writeFileSync(`${OUT}/web/highlight.js`, `/* Generated by tools/prerender/build.mjs from the web apps' textAnchor.js and highlightDom.js. */\n(function(){\n${anchor}\n${dom}\n${glue}\n})();\n`)
  console.log('highlight.js written')
}

// ── KaTeX css + fonts (woff2 only: every WebView that runs this app supports it) ──
function katex() {
  const dist = `${GQ}/node_modules/katex/dist`
  fs.mkdirSync(`${OUT}/web/katex/fonts`, { recursive: true })
  let css = fs.readFileSync(`${dist}/katex.min.css`, 'utf8')
  css = css.replace(/,url\([^)]*\.woff\) format\("woff"\)/g, '').replace(/,url\([^)]*\.ttf\) format\("truetype"\)/g, '')
  fs.writeFileSync(`${OUT}/web/katex/katex.min.css`, css)
  for (const f of fs.readdirSync(`${dist}/fonts`).filter((f) => f.endsWith('.woff2'))) fs.copyFileSync(`${dist}/fonts/${f}`, `${OUT}/web/katex/fonts/${f}`)
  for (const [name, file] of [['equation', `${IQ}/src/components/equation/equation.css`], ['mathformulas', `${GQ}/src/components/utility/MathFormulas.css`]]) {
    // Slate's copies are adapted (e.g. equation diagrams fill the card), so an existing one is never overwritten.
    if (!fs.existsSync(`${OUT}/web/${name}.css`)) fs.copyFileSync(file, `${OUT}/web/${name}.css`)
  }
  console.log('katex css + fonts copied')
}

try {
  await math(); await equations(); katex(); highlightScript()
  await publish('general', GQ); await publish('ict', IQ)
} finally { fs.rmSync(TMP, { recursive: true, force: true }) }
