# TransVerse

TransVerse is a privacy-first Android assistant for capturing, translating and acting on text from anywhere. It accepts typed text, shared text, images, speech, barcodes and an optional floating screen-capture workflow.

## Core workflow

1. Capture text from the Text, Image, Speech or Barcode entry point—or share selected text/an image into TransVerse.
2. Review and correct the captured text.
3. Detect its language and translate it with an on-device ML Kit model.
4. Copy, speak or share the result, or act on extracted URLs, email addresses and phone numbers.
5. Save the result locally, favourite it for quick access, or enable Incognito mode to avoid persistence.

## Highlights

- On-device OCR, language identification, translation and entity extraction
- English Smart Reply suggestions when supported by ML Kit
- Searchable, favourite-first local history backed by Room
- Share-sheet and Android Process Text integration
- Optional user-controlled floating capture bubble
- Light, dark and system themes
- Adjustable font size, reading speed and speech pitch
- No account and no application-owned cloud history

## Technology

- Kotlin and Android Views with an incremental feature-first architecture
- ViewModel, LiveData/Flow and a single shared analysis state holder
- Hilt dependency injection
- Room database with explicit migrations
- Google ML Kit and Play Services-delivered models
- Material 3, Navigation, View Binding and Data Binding

## Development

Requirements:

- Android Studio with JDK 17
- Android SDK 36
- An Android 8.0+ device or emulator (`minSdk 26`)

Common checks:

```shell
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew bundleRelease
```

CI runs all three checks for pushes and pull requests. Room exports versioned schemas to `app/schemas`; review and commit generated schema changes whenever the database version changes.

## Permissions

- Camera: image and barcode capture
- Microphone: speech recognition
- Notifications: foreground capture visibility
- Display over other apps: optional Quick Capture bubble
- Media projection: requested through Android’s system prompt for each screen-capture session

TransVerse does not request broad external-storage access. Exports use Android MediaStore or the system share sheet.

## Privacy

Captured content and history remain on the device unless the user explicitly shares them. Some ML Kit models require an initial download through Google Play services. Incognito mode prevents new analysis results from being saved.

## Contributing and licence

See [CONTRIBUTING.md](CONTRIBUTING.md). TransVerse is available under the [MIT License](LICENSE).
