# Contributing

Thank you for improving TransVerse.

## Before opening a change

1. Keep capture, analysis and persistence responsibilities separate.
2. Put reusable analysis rules in `scannerAI` and data access in `database`.
3. Keep user-facing text in string resources and include accessibility descriptions for interactive images.
4. Add a Room migration and exported schema for every database change.
5. Add or update tests for changed product rules.

Run the quality checks before submitting:

```shell
./gradlew testDebugUnitTest lintDebug bundleRelease
```

Never commit signing keys, local SDK paths, generated APKs, user data or secrets.
