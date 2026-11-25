# Architecture

TransVerse uses a pragmatic feature-first Android architecture with a Jetpack Compose UI.

## UI

A single `MainActivity` hosts Compose edge to edge. `ui/app/TransVerseApp` is the shell: Translate, History and Settings are top-level destinations shown in a bottom navigation bar on phones and a navigation rail from 600dp; saved scans, the barcode scanner and the help/privacy/licence pages are pushed on top. Shared text, images, Process Text and the floating-capture results reach the Translate screen as `IncomingContent` through `MainViewModel`.

The theme (`ui/theme`) is Material 3 Expressive: brand or wallpaper colours, the expressive motion scheme, rounded shapes, the Figtree typeface, and a user-controlled reading size that applies only to transcripts and translations (`readingTextStyle()`). `AppearanceStore` is the observable source of truth for appearance settings.

Each feature follows the same shape: a stateful screen composable gets its `@HiltViewModel` via `hiltViewModel()` and delegates to a stateless `…Content(state, callbacks)` that has previews. ViewModels expose immutable `StateFlow` UI state.

## Data flow

The Translate screen owns capture (typing, camera/gallery with OCR, speech, incoming content) and hands text to `scannerAI/AnalysisPipeline`, which runs:

`identifying → downloading model → translating → analysing → complete/error`

Completed results are saved through `ScanRepository` unless Incognito mode is on. Room is the single source of truth for saved history. Capture files (images, speech audio) live in app-private storage and are managed by `CaptureFileStore`: they are deleted with their history entry, and orphans (e.g. Incognito captures) are removed at app start.

## Boundaries

- `ui/app`, `ui/theme`: app shell, navigation and design system
- `ui/translate`, `ui/history`, `ui/settings`, `ui/barcode`: feature screens and their ViewModels
- `scannerAI/`: reusable analysis orchestration and product rules
- `database/`: Room entities, DAO, migrations, repository and capture-file storage
- `data/`: typed access to user settings (`Preference`, `AppearanceStore`)
- `floatingWindow/`: the floating bubble and screen-capture foreground services, media projection
- `ui/overlay`: Compose UI in overlay windows (`OverlayWindow` hosts Compose from a Service): bubble, fan menu, capture editor and in-place translation card
- `ui/crop`: the shared crop editor (handles, zoom, loupe, tap-to-select OCR text blocks, translate-on-image), used by the capture overlay and the in-app crop dialog
- `di/`: application-scoped dependency construction

## Design decisions

- ML Kit clients are owned by the pipeline/ViewModels that use them and closed when they are cleared.
- Smart Reply and entity enhancements are only requested for English target text because of current model limitations.
- History is written only after optional enhancements complete, avoiding partially saved records.
- Share-sheet and Process Text are primary cross-app integrations; the overlay is optional because it requires sensitive Android permissions.
- Floating capture freezes the screen first: one frame is grabbed, then the user selects on that still image, so nothing changes underneath. The translation appears in place over the current app. An optional capture session keeps the projection open (surface detached between captures) to skip the per-capture consent.
- The barcode scanner uses CameraX with ML Kit's `MlKitAnalyzer`; scanned codes are not saved to history.
