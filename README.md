# LEAF — offline document reader for Android

**Leaf** is a fully offline Android reader for **PDF, DOCX, XLSX, PPTX, TXT and EPUB** documents.
It is built with **Kotlin + Jetpack Compose + Material 3**, targets **Android 6.0 (API 23)** and
works entirely on-device: **no account, no cloud, no sign-in, no network permission, no analytics**.

---

## What 2.1.0 fixes

Feedback from an Android 6 phone led to four concrete fixes:

1. **No permission prompt any more.** Leaf now declares **no permissions at all** — the
   optional “scan the whole storage” request was removed. Everything happens through the
   system folder picker, which grants access without a runtime dialog.
2. **All files on the device are listed.** The new **Device** tab (bottom navigation, and
   “Device files” in the library menu) walks the folder you pick with the system picker and
   lists every PDF, DOCX, XLSX, PPTX, TXT and EPUB below it, with its size and type icon.
   On Android 10+ Leaf additionally lists media-store documents without any permission.
3. **Files open again.** Two bugs stopped every document from opening: rows swallowed the
   tap, and `file://` uris were handed to the content resolver. Both are fixed, and the
   reader now repaints correctly (the pages used to stay blank because the custom views
   never invalidated themselves).
4. **The app is tested on an emulator in CI.** Every push installs the APK on an Android 6
   (API 23) emulator, opens a Word document and a PDF, searches inside the PDF, visits the
   device browser and every tab, and commits the screenshots, the UI hierarchy and the
   logcat back into the repository (`.ci/smoke/`).

In addition the PDF pipeline was made memory aware (the page size adapts to the free heap,
the bitmap cache holds two pages and the text index is only built when you actually search
inside a file) and PDFBox now runs on a thread with a large stack, so searching inside a
PDF with standard fonts no longer overflows the stack.

---

## Install

Grab `leaf.apk` (release, signed) from the `dist/` folder of this repository or from the
GitHub Release page, then install it on the device (Android 6.0+). The app never asks for
storage permissions: you grant access to individual files through the system document picker
and Leaf keeps using exactly the URI you picked — your files are never copied around.

On first launch Leaf seeds a small **demo library** (a PDF, DOCX, XLSX, PPTX, TXT and EPUB) so
every feature can be explored immediately, even on a device with no documents.

## Highlights

| Area | What Leaf does |
| --- | --- |
| **Device files** | A dedicated tab that lists every PDF, Word, Excel, PowerPoint, text and EPUB file that already lives on the phone. *Choose folder* uses the system folder picker, so Leaf **never requests a storage permission** and never copies your files — it only remembers the uri you already granted. Files can be added to the library or opened straight away. |
| **Library** | Type icon, name, folder, tags, size and reading progress per row; folder chips with live counts; “Jump back in” hero card; “Continue reading” grid; long-press action sheet; empty state; two-pane list + detail on wide screens; optional grouping by file type with per-group counts, size, highlight and bookmark statistics. |
| **Recents** | Automatic logging of every open document, de-duplicated, split into *Today* / *Earlier*, 24-hour count badge on the nav bar, per-row remove with **Undo**, clear-today / clear-all with confirmation. |
| **Favorites** | Instant star toggle with haptics, its own independent sort order, same row actions as the library. |
| **Folders** | Custom folders, 10 colour swatches, nested folders, rename / recolour / move / delete (children and documents are re-parented, never lost), document and sub-folder counts, smart + user collections with duplicate-name rejection, move picker showing the folder tree. |
| **Tags** | 10 colour swatches, multi-tag documents, rename / recolour / delete with usage counts, AND filtering, tag manager sheet. |
| **Reader** | Exact per-document resume (page, in-page scroll, zoom, direction, reflow, theme, orientation), three progress indicators, hide page numbers, position saved on every jump, manual bookmarks + bookmarks sheet, four reading themes plus *Auto*, theme-aware highlights, vertical continuous list, horizontal snap pager, pinch / double-tap / toolbar zoom from 70 %–250 % with the percentage visible, drag panning, single-column text reflow, fullscreen with a dimmed footer that restores the chrome, serif & sans typefaces, auto-rotate with manual cycling and a per-document orientation lock. |
| **Search** | File-name, tag, folder and full-text search with AND filters, a result summary, in-file snippets with match counts and the first matching page, “open at page” that pre-fills the reader’s find bar, in-reader next/previous with “3 of 17”, search history (last 10, remove / re-run / clear) and quick searches in the blank state. Sorts by name, date added, last opened and file size with an explicit direction, an independent sort for favorites and a shared sort inside type groups. |
| **Annotation** | Five highlight colours, drag to select then choose a colour (the last used colour is pre-selected), tapping a highlight opens a context bar (recolour / remove / copy / done), 50-step undo & redo, destructive actions offer an Undo snackbar, a highlights summary panel with chip, page, quote and timestamp plus jump / delete / clear-all. Highlights survive zoom, theme and direction changes and app restarts — normalized geometry for PDF and paged Office files, text offsets for reflow, TXT, DOCX and EPUB. |
| **Multi-tab** | Browser-like tabs with a live count badge, an open-documents sheet showing icon, name, “Page 3 of 5 · 2 h ago” and an ACTIVE marker, close on every row (including the active one), instant switching with per-tab state, closing returns to the previous tab with Undo, close-all with confirmation, an “Open a new document…” pick mode with a banner, dashed rows and a + affordance, and a polite refusal when the configurable tab limit (1–10, default 6) is reached. |
| **Share & copy** | Share the whole document, share the current page as a PNG, share selected text, copy the file path, copy the current page text. |
| **File management** | Rename (extension preserved), delete with confirmation + Undo, move to / remove from a folder, add / remove tags, favourite toggle and a file-info panel — all reachable from the long-press row *and* from the reader overflow menu. |
| **Settings** | Light / dark / system theme, default reading theme, animations, haptics, language, default scroll direction, default reflow, default zoom, typeface, remember last page, show page numbers, auto-rotate, per-document orientation lock, maximum tabs, keep screen awake, clearing recents / search history / reading positions / highlights, resetting the demo library, and an About screen with version information and live library counts. |
| **Design** | Material 3 with dynamic colour seeded from **Leaf Green `#2B7A5B`**, an earthy palette, leaf and bookmark iconography, page-turn / leaf-sway motion, ripple and elevation, serif reading text with a sans-serif UI, content descriptions everywhere, ≥48 dp touch targets, TalkBack-friendly order, system font scaling, no colour-only meaning, bottom navigation below 600 dp and a navigation rail from 600 dp, responsive from 360 dp to 900 dp+, snackbars above the navigation bar and never over the reader toolbar. |

## Architecture

```
app/src/main/java/com/leaf/reader/
├── MainActivity.kt          single activity, edge-to-edge, Compose entry point
├── LeafApp.kt               Application, PDFBox init, demo library seeding
├── AppContainer.kt          manual dependency injection
├── core/                    SAF helpers (FileUtils) and formatters (Formatters)
├── data/
│   ├── db/                  Room entities, DAOs and database
│   ├── prefs/               DataStore: settings, navigation, tabs, search history
│   ├── DocumentRepository   single source of truth over the DAOs
│   └── DemoLibrary          generates a PDF/DOCX/XLSX/PPTX/EPUB/TXT demo set
├── engine/                  parsers: PDF (PdfRenderer + PDFBox), DOCX/XLSX/PPTX
│                            (ZipInputStream + XmlPullParser), EPUB, TXT and the
│                            paginating text engine
├── model/                   domain models and enums
├── reader/                  reader UI, PDF & text renderers, highlights, bookmarks
└── ui/                      library, recents, favorites, search, settings, about,
                             group screens, shared sheets and the app shell
```

* **Room** stores documents, folders, tags + cross references, collections, recents,
  bookmarks, highlights, cached text and per-document reader state.
* **DataStore** stores settings, the selected destination, the open tabs and the search history.
* **PDF** pages are rendered with the platform `PdfRenderer`; text and glyph geometry for
  in-file search and normalized highlights come from PDFBox-Android.
* **Office and EPUB** documents are parsed in-app with hand-written ZIP/XML parsers, so no
  conversion service or network call is ever needed.

## Building

The project is built with **GitHub Actions** (`.github/workflows/build-apk.yml`):

1. JDK 17 + Android SDK 35 + build-tools 35.0.0 are installed on the runner.
2. `assembleDebug assembleRelease` produces both APKs; the release build falls back to a
   CI-generated debug keystore when no `leaf-release.jks` is committed.
3. Pushing a `v*` tag copies the APKs into `dist/` and publishes a GitHub Release.

```bash
# build locally (requires JDK 17 + Android SDK 35)
./gradlew assembleDebug assembleRelease

# or trigger the hosted build
git tag v2.0.0 && git push origin v2.0.0
```

## Continuous testing

Every push also runs the app on an **Android 6 (API 23) emulator** inside GitHub Actions
(`reactivecircus/android-emulator-runner`): the APK is installed, the library, the reader, an
in-document search and the device browser are driven with `adb`, and the screenshots, the UI
hierarchy, the logcat and any native crash tombstone are committed back to the branch under
`.ci/smoke/` so a regression can be seen immediately.

## Privacy

Leaf declares **no permissions at all** — no `INTERNET`, no storage. The only system dialogs you
ever see are Android's own file-picker grants, which are how the platform hands an app access to a
file without copying it. It has no analytics, no crash reporting and no
network code at all. The only storage access is the access *you* grant through the system
document picker; Leaf stores document URIs, never copies of your files.
