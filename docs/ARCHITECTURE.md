# Architecture

TransVerse uses a pragmatic feature-first Android architecture.

## Data flow

User events originate in a Fragment and are passed to a feature ViewModel. Text, image and audio ViewModels share `AnalysisViewModel`, which owns the analysis state machine:

`idle → identifying → downloading model → translating → analysing → complete/error`

The ViewModel publishes read-only result state to the UI and persists completed results through `ScanRepository`. Room is the single source of truth for saved history. Incognito mode short-circuits persistence but does not change the on-device analysis pipeline.

## Boundaries

- `ui/`: screens, Android interaction and rendering
- `scannerAI/`: reusable analysis orchestration and product rules
- `database/`: Room entities, DAO, migrations and repository
- `data/Preference`: typed access to user settings
- `floatingWindow/`: foreground-service and media-projection integration
- `di/`: application-scoped dependency construction

## Design decisions

- ML Kit clients live with the analysis state holder and are closed in `onCleared`.
- Smart Reply and entity enhancements are only requested for English target text because of current model limitations.
- History is written only after optional enhancements complete, avoiding partially saved records.
- Share-sheet and Process Text are primary cross-app integrations; the overlay is optional because it requires sensitive Android permissions.
- UI migration is incremental. Existing scanner/media code remains View-based while redesigned screens use Material 3 components and the same state boundaries.
