# Anvil

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
- Google sign-in needs `GOOGLE_WEB_CLIENT_ID_GENERAL` / `_ICT` in `local.properties` (the web client IDs from each Supabase
  project's Google provider) plus an Android OAuth client for `com.syed.anvil` with the release-key SHA-1.

## Identity
`core/Uid.kt` is a bit-exact port of `qid.js` (cyrb53). Progress and highlights are keyed by uid, so never change it;
`UidTest` checks it against uids stored in both databases. General uids are `q<base36>`, ICT uids `<module>:<base36>`.
In practice the app reads `uid` from the row; the hash is needed for content that is not in the database (Practice, Equation).

## Offline
`content/ContentRepository` keeps each module as a JSONL file in `filesDir`, replaced atomically on refresh. The UI reads only
from it. `progress/ProgressRepository` writes locally first and queues edits (`PendingQueue`, last-write-wins per column,
bulk upsert grouped by column set, backoff 4/12/30/60/300 s). Flag rules in `FlagRules` mirror the web apps.

## Themes
Three scopes in `ui/theme/Palette.kt`: `SHELL` (rust, the Anvil home/settings), `GENERAL` (marigold, from general-quiz),
`ICT` (blue, from ict-quiz). Each destination in `AnvilNav` is wrapped in its own `AnvilTheme(scope)`. Set every
`surfaceContainer*` level or Material falls back to lavender.

## Build
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`
- `./gradlew :app:assembleRelease`
- Debug and release are signed with the same key (`anvil-release.jks`, via gitignored `keystore.properties`), so they install
  over each other. **Back up the keystore: losing it means no more in-place updates.** Never commit it.

## GitHub release rules (the in-app updater depends on these)
- Repo `s-shahriar/Anvil` must stay **public** (the updater reads `releases.atom` without credentials).
- Asset must be named `Anvil-vX.Y.Z.apk`; optionally add `Anvil-vX.Y.Z.apk.sha256` (`sha256sum` output) and the app verifies it.
- `versionName` in `app/build.gradle.kts` must equal the tag without the `v`; bump `versionCode` by 1 each release.
- Release: `./gradlew :app:assembleRelease && cp app/build/outputs/apk/release/app-release.apk Anvil-vX.Y.Z.apk &&
  sha256sum Anvil-vX.Y.Z.apk > Anvil-vX.Y.Z.apk.sha256 && gh release create vX.Y.Z --repo s-shahriar/Anvil
  --title "Anvil vX.Y.Z" --notes "..." Anvil-vX.Y.Z.apk Anvil-vX.Y.Z.apk.sha256`

## Roadmap
1. ✅ Foundation: project, three themes, two backends, uid hash, offline content + progress queue, updater.
2. ✅ Quiz core (works for General and ICT MCQ): topic mode select + pool chooser, MCQ quiz, study (paged, filters, search),
   exam (setup + run, two-tap stop), Nailed/Important lists, group search, notes, HTML rendering with offline images.
   Still to do for General: LiveMCQ sub-topic switcher, `written_categories/cards` data page, Utility pages (math formulas
   with KaTeX, financial terms), left/right-hand layout toggle, recycle bin.
3. ✅ ICT Written/Extra/Viva reader (`ui/reader`, `ReaderScreen`): accordion cards, segments + sub-segments, hanging-indent
   questions, all answer blocks (code with highlighting, image, summary, points, ASCII diagram, table, mistakes, mnemonic,
   extended), search into answers with deep-link, flags/notes, resumable offline picture cache.
4. Practice (Linux/SQL drill), Equation (KaTeX + diagrams, bundled), highlights (offset anchoring, keep web compatible).
5. Mobile extras (reminders, streaks, spaced repetition, timed exams).

## Open items (check later)
- **Picture questions are not verified on screen.** All 129 images referenced by General are cached on the device (20 MB), but
  nobody has looked at one rendered. Where to look: General > LiveMCQ > মানসিক দক্ষতা (`lm_mental_ability`) > Study, page 4,
  first card ("Group the given figures into three classes…"); also the quiz screen and an image inside an explanation.
  Check size/aspect, dark mode, and the "Image not available offline" placeholder with the cache cleared.
- Not yet looked at by hand: Extra and Viva readers, the Nailed/Important lists for long-form cards, Settings "Pictures: x of y",
  dark theme on the new screens, tablets / large font sizes.
- Left/right-hand toggle, recycle bin, LiveMCQ sub-topic switcher, General written-data page, Utility pages: not built yet.

## Testing notes
- Unit tests cover uid hashing, flag rules/queue, HTML parsing, pools/search, updater helpers (27 tests).
- Smoke-tested on the `Medium_Phone_API_36.1` emulator: download, offline relaunch in airplane mode, quiz, exam.
  Not yet exercised: Google sign-in and server sync (need the Google client IDs), image rendering, study/saved screens by hand.
