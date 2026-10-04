# Slate

One Android app (Kotlin + Compose, Material3) holding two quiz modules, **General** and **ICT**, ported from the web apps
`~/Projects/Self/Quiz/general-quiz` and `~/Projects/Self/Quiz/ict-quiz`. Personal, single-user app.

## Backends — two separate Supabase projects
| Module | Project ref | Notes |
|---|---|---|
| General | `dancporuhvfwieyyzhdd` | normalized `categories` + `questions`, `subtopics`, `written_*` |
| ICT | `skqarcuggnbjbhpxytmd` | `questions(module, category_slug, uid, payload jsonb)` |

- Each module has its own `SupabaseAuth` session (the same Google account is a different user id in each).
- Both databases are **owner-locked**: RLS on `user_progress` / `user_highlights` / `profiles` and the trash/admin RPCs only
  admit the owner (`ksnkkc@gmail.com`; uuids differ per project). Content tables are public-read.
- Publishable keys live in `backend/Backends.kt` (safe to ship; sent as `apikey` only). No networking library: raw
  `HttpURLConnection` + `org.json`, like Magpie.
- PostgREST truncates silently at 1000 rows: always page (`Postgrest.selectAll`) with a unique `order=`.
- Google sign-in needs `GOOGLE_WEB_CLIENT_ID_GENERAL` / `_ICT` in `local.properties` plus an Android OAuth client for `com.syed.slate`
  with the release-key SHA-1. **Android OAuth clients are globally unique per (package, SHA-1) across all GCP projects** — the one
  Android client lives in the `general-quiz` project (number 6944427758), so BOTH web client IDs in local.properties point at that
  project, and each Supabase project's Google provider must list that web client under **Authorized Client IDs**. The old
  `ict-quiz` project's web client (979538703978-…) is unused; trying to create the same Android client there fails with
  "package name and fingerprint are already in use".

## Identity
`core/Uid.kt` is a bit-exact port of `qid.js` (cyrb53). Progress and highlights are keyed by uid, so never change it;
`UidTest` checks it against uids stored in both databases. General uids are `q<base36>`, ICT uids `<module>:<base36>`.
In practice the app reads `uid` from the row; the hash is needed for content that is not in the database (Practice, Equation).

## Offline
`content/ContentRepository` keeps each module as a JSONL file in `filesDir`, replaced atomically on refresh. The UI reads only
from it. `progress/ProgressRepository` writes locally first and queues edits (`PendingQueue`, last-write-wins per column,
bulk upsert grouped by column set, backoff 4/12/30/60/300 s). Flag rules in `FlagRules` mirror the web apps.

## Themes
Three scopes in `ui/theme/Palette.kt`: `SHELL` (rust, the Slate home/settings), `GENERAL` (marigold, from general-quiz),
`ICT` (blue, from ict-quiz). Each destination in `SlateNav` is wrapped in its own `SlateTheme(scope)`. Set every
`surfaceContainer*` level or Material falls back to lavender.

## Build
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`
- `./gradlew :app:assembleRelease`
- Debug and release are signed with the same key (`slate-release.jks`, via gitignored `keystore.properties`), so they install
  over each other. **Back up the keystore: losing it means no more in-place updates.** Never commit it.

## GitHub release rules (the in-app updater depends on these)
- Repo `s-shahriar/Slate` must stay **public** (the updater reads `releases.atom` without credentials).
- Asset must be named `Slate-vX.Y.Z.apk`; optionally add `Slate-vX.Y.Z.apk.sha256` (`sha256sum` output) and the app verifies it.
- `versionName` in `app/build.gradle.kts` must equal the tag without the `v`; bump `versionCode` by 1 each release.
- Release: `./gradlew :app:assembleRelease && cp app/build/outputs/apk/release/app-release.apk Slate-vX.Y.Z.apk &&
  sha256sum Slate-vX.Y.Z.apk > Slate-vX.Y.Z.apk.sha256 && gh release create vX.Y.Z --repo s-shahriar/Slate
  --title "Slate vX.Y.Z" --notes "..." Slate-vX.Y.Z.apk Slate-vX.Y.Z.apk.sha256`
- Renamed 2026-10-04: Anvil → Slate (display name, package `com.syed.anvil`→`com.syed.slate`, repo `s-shahriar/Anvil`→`s-shahriar/Slate`,
  project folder `~/Projects/Self/Anvil`→`~/Projects/Self/Slate`, keystore file, asset prefix). The repo was renamed on GitHub, so old
  `s-shahriar/Anvil` links redirect. The pre-rename releases (v0.1.0, v0.1.1, titled "Anvil") were deleted along with their
  `Anvil-*.apk` assets — v0.1.2 is the only release; installs of the old package (`com.syed.anvil`) can't update to it (different app id).

## Roadmap
1. ✅ Foundation: project, three themes, two backends, uid hash, offline content + progress queue, updater.
2. ✅ Quiz core (works for General and ICT MCQ): topic mode select + pool chooser, MCQ quiz, study (paged, filters, search),
   exam (setup + run, two-tap stop), Nailed/Important lists, group search, notes, HTML rendering with offline images.
   Also done: LiveMCQ sub-topic switcher in Study, Written » Data page (cached offline, cache `version` upgrades old copies).
   Also done: Utility pages (math formulas page, Financial Terms). Still to do for General: left/right-hand toggle, recycle bin.
3. ✅ ICT Written/Extra/Viva reader (`ui/reader`, `ReaderScreen`): accordion cards, segments + sub-segments, hanging-indent
   questions, all answer blocks (code with highlighting, image, summary, points, ASCII diagram, table, mistakes, mnemonic,
   extended), search into answers with deep-link, flags/notes, resumable offline picture cache.
4. ✅ Practice (`practice/`, `ui/practice`, `PracticeScreens`): bundled `assets/practice/{linux,sql}.json`, Info/Commands/Practice tabs, SQL-forgiving
   answer matching (ported exactly; flag ids `practice__<cat>__<topic>__<cmd>` match the web), Important list across both. It is a virtual
   "Practice" group on the ICT module screen.
   Equation is done too (`ui/web`, `FormulaScreens`): a virtual "Equation" group on ICT, cover-and-recall. Still to do: highlights.
5. ✅ Highlights, recycle bin, left/right-hand layout. Next: mobile extras (reminders, streaks, spaced repetition, timed exams).

## Pre-rendered pages (math formulas, equations, financial terms)
The web apps build these from JSX + KaTeX; Slate ships them as static HTML in `app/src/main/assets/web` and shows each in a
script-light `WebView` (`ui/web/FormulaPage.kt`: no network, no file access, JS only for `controller.js`). Regenerate with
`node tools/prerender/build.mjs` (needs the two web projects next to Slate, Node and Google Chrome; output is committed, so building
the app needs none of it). The tool bundles the web components with esbuild (stubbing their app-only imports), renders them with cover mode
forced ON, lets headless Chrome run the web's own uid code to tag each math card with `data-uid` (`m<hash>`), and copies KaTeX CSS/fonts.
`controller.js` switches cover mode on/off, reveals one element per tap, and bridges stars to Slate. Theme tokens are injected from the
active palette. Re-run the tool whenever the web apps change those pages. Equation highlights will need the web's DOM anchoring ported
into the WebView (blocks are already tagged `data-hl-block`).

## Highlights, recycle bin, hand layout
- Highlights (`highlight/`, `ui/highlight/HText.kt`): same `user_highlights` rows and block keys as the web (`q`, `q.N`, `explanation`, `summary.N`, `points.N`,
  `table.rR.cC`, `eq`-page `sym:`/`eq:` keys...). `Anchor` is a port of `textAnchor.js`; selection uses a read-only text field, the colour bar replaces the
  system menu. HTML blocks are aligned to the browser's `textContent` offsets (`HtmlAlign`). Equation pages use the web's own anchoring JS, generated
  into `assets/web/highlight.js` by the prerender tool. Edits are local first and queued, like flags.
- Recycle bin (`trash/`): trash/restore/purge via the owner-only RPCs, queued offline (`TrashQueue`), hidden instantly; "not authorized" undoes locally.
- Left-hand layout: `LocalLeftHand` + `HandMirror`/`SlateTopBar` flip bars and action rows (Home hand icon or Settings).

## Releasing
`tools/release.sh` builds the signed APK, writes `Slate-vX.Y.Z.apk` + `.sha256`; `NOTES='...' tools/release.sh --publish` creates the GitHub release
(repo `s-shahriar/Slate` must be public). Bump `versionName`/`versionCode` first; notes come from the `NOTES` env var (no notes file).

## Web parity (mirrored from general-quiz / ict-quiz, 2026-10-04)
- **Sub-modules are Magpie-style cards** (`ModuleScreen`: `SubModuleLauncher`): opening General/ICT shows a 2-column grid of rounded
  cards (icon coin + name + blurb + question count); tapping one opens that section in place (hero row, then the section's
  topics; system Back / top-bar Back returns to the grid). General's Written card opens the Data page. The earlier pill
  switcher (and wrapped chips / dropdown variants) was replaced at the user's request; don't bring them back.
- **Home module cards**: two tall Magpie-style cards (230dp, 32dp radius, 68dp icon coin, title + tagline, offline status).
- **Reading text is Inter (bundled `res/font/inter_*.ttf`), NOT Jakarta** (`Type.kt`): zero tracking, bodyLarge 17/30, bodyMedium 15.7/29, bodySmall 13/21;
  Bangla falls back to system Noto Sans Bengali. Jakarta (the web's `--font-display`) reads cramped as body copy. Sizes/leading
  mirror the web's reading rules (question ~1.1rem/1.75, points ~0.98rem/1.85). HTML paragraph gap 10dp (`.rich p`).
  Written cards: 7dp glowing dots, sub-points as 2dp 40%-alpha left bars + 88% text, সংক্ষেপ card with topic-colour
  border + hairline separators. Equation diagrams fill the card (`equation.css`, no 560px floor/720px cap).
- **Top-bar actions are the web's trio** (`SlateTopBar`): theme toggle (`LocalToggleTheme`/`LocalDarkTheme`, flips
  LIGHT↔DARK), sync-queue button (`LocalOpenSyncQueue`), hand toggle. Provided at the `SlateNav` root.
- **Hand mirroring**: action clusters must be FULL-WIDTH rows so `HandMirror` (Rtl) relocates them to the mirrored
  edge — a wrap-content row only reorders in place. AutoMirrored icons (back arrow) flip themselves.
- **Sync queue sheet** (`ui/component/SyncQueue.kt`, port of SyncDrawer): top-bar cloud button or the waiting strip.
  Status line (syncing/retrying-in-Ns/offline/signed-out/all-synced·ago), Retry now (cancels backoff on all three
  queues), WAITING rows (question text + topic resolved from the cache, action, queued-ago, Undo for invertible flag
  patches and trash ops — purge and notes not undoable), synced receipt. Repos expose
  `queue`/`queueOps`/`phase`/`retryAt`/`savedAt`/`retryNow()`/`undoQueued()`; flag timestamps persist in
  `pending_ts_*.json`.
- **LiveMCQ Admin** (`ui/screen/AdminScreen.kt`, port of the web's /admin; owner-gated by `LivemcqAdmin.OWNER_UID`,
  entry card in the LiveMCQ section): three tabs — Manage (search/filter, move category, set sub-topic, delete via the
  admin_livemcq_* RPCs), Last import (the 10-minute grouping of `groupImports`), Import (livefav JSON picker,
  normalize + gap/range warnings, favorite_id de-dup, per-row category/sub-topic, bulk insert; uid via `Uid.general` =
  the web's qid.js so flags stay aligned). The tf-idf/kNN suggester is NOT ported — classification is manual + bulk.
- **Exam Random Mix** ("mix" pools every group), **Settings** shows Nailed/Important/Weak totals per module.
- **Per-card Topic edit** (`StudyCard.onLiveMcqEdit`, owner + LiveMCQ only): fetches the row by uid and applies
  admin_livemcq_set_category / _set_subtopic, then `content.refresh()`. **QuestionPeek** (`ReaderScreen`): while an
  open card's question scrolls off the top, a slim pinned bar names it; tap scrolls back.
- **Still missing**: the LiveMCQ category suggester (classifier), GK study-notes reader (GkStudyMode; needs bundled
  `data/gk/*.json` + a block renderer).

## Testing notes
- Unit tests cover uid hashing, flag rules/queue, HTML parsing, pools/search, updater helpers (27 tests).
- Smoke-tested on the `Medium_Phone_API_36.1` emulator: download, offline relaunch in airplane mode, quiz, exam.
- Also smoke-tested on **Waydroid** (the user's container; launch with the `wayphone` bash function for a real
  480x1000 @ 200dpi portrait window). Quirks: `waydroid app install` silently fails (Play Protect) — use
  `adb connect 192.168.240.112:5555 && adb install -r` instead; after a session restart adb says "offline" until
  you `adb disconnect` and reconnect; `uiautomator dump` reuses a stale `/sdcard/ui.xml` if you don't `rm -f` it
  between dumps (verify with `exec-out screencap -p` screenshots instead — strip stderr junk before the `\x89PNG`
  magic); a "Waydroid Updater" system dialog occasionally steals focus. Sign-in cannot work there (no Play
  Services for Credential Manager); everything else was verified on screen: home, General subtabs + wrapping
  section chips, pool chooser sheet (verbatim web labels), quiz, study with Q-numbers, Utility tab, math page,
  sync strip ("1 change waiting — sign in to sync"), process-death state restoration.

## Open items (check later)
- **Google sign-in, last step pending (2026-10-04)**: the Android OAuth client for `com.syed.slate` (SHA-1
  `0C:00:DF:05:8E:76:55:F1:1F:D0:71:79:20:4C:EA:06:99:93:FF:0D`) now exists in the `general-quiz` project, and both
  modules request sign-in against its web client. Remaining: in the **Supabase ICT dashboard** (Authentication →
  Sign In / Providers → Google), add the general web client (`6944427758-2vo7…`) to **Authorized Client IDs** —
  until then General signs in but ICT can't. After signing in for the first time: verify flags/highlights actually
  sync and the Important/Weak/Nailed pools fill from the server.
- Highlights: verified on a General question and on an Equation page; not yet by hand on the Written/Extra/Viva cards, ICT MCQ code blocks, math page (not highlightable), or sync after sign-in.
- Recycle bin: restore of a server-side trashed question needs a sign-in as the owner; not exercised end to end.
- Content refresh is now a **delta sync** (`ContentRepository.probe`/`delta`): a light `uid,sort_order`+placement
  probe (~300KB) is diffed against the cache and only changed/new rows are fetched (`uid=in.…` chunks of 200);
  removed uids are dropped; tiny metadata tables (General categories/subtopics/written_cards) are always
  refetched. No `updated_at` columns exist — uids are cyrb53 content hashes, so any edit changes the uid. A
  full re-download happens only when the cache is missing or its `version` is old. Opening a module NEVER probes the server
  (only a missing/outdated cache downloads); the Refresh button (module top bar / Settings) runs the delta check.
- Math page: 13 of the owner's 15 saved `m…` marks match computed card ids; 2 (`m1y7v8pus1j3`, `mz4jpvp31v0`) match no current card
  (probably retitled since). On the emulator the section chip row above the WebView did not paint until the first interaction;
  likely a software-GL artefact, but check on a real phone. First paint of a page takes seconds on the emulator.
- **Picture questions are not verified on screen.** All 129 images referenced by General are cached on the device (20 MB), but
  nobody has looked at one rendered. Where to look: General > LiveMCQ > মানসিক দক্ষতা (`lm_mental_ability`) > Study, page 4,
  first card ("Group the given figures into three classes…"); also the quiz screen and an image inside an explanation.
  Check size/aspect, dark mode, and the "Image not available offline" placeholder with the cache cleared.
- Not yet looked at by hand: Extra and Viva readers, the Nailed/Important lists for long-form cards, Settings "Pictures: x of y",
  dark theme on the new screens, tablets / large font sizes.
- Not built yet: mobile extras (reminders, streaks, spaced repetition, timed exams).
- Practice is verified for Linux and the SQL first topic only; SQL answers with multi-line `answers`, the Commands tab and the Important
  list screen have not been looked at by hand.

## Testing notes
- Unit tests cover uid hashing, flag rules/queue, HTML parsing, pools/search, updater helpers (27 tests).
- Smoke-tested on the `Medium_Phone_API_36.1` emulator: download, offline relaunch in airplane mode, quiz, exam.
  Not yet exercised: Google sign-in and server sync (need the Google client IDs), image rendering, study/saved screens by hand.
