# VipLedger

A local-first, offline personal ledger for Android. Ledgers, accounts, categories, tags, transactions, recurring entries, installments, and statistics stay on the device. Cloud calls are limited to optional natural-language entry, and WebDAV stores encrypted backups only.

Version `0.1.0`, still in development.

## Features

- Multiple isolated ledgers
- Income, expense, transfer, repayment, and balance adjustment
- Accounts, categories, and custom tags
- Recurring entries and installment plans
- Search, statistics, category share, and trends
- On-device classifier, plus optional OpenAI-compatible natural-language entry
- CSV and JSON import/export, with encrypted WebDAV snapshots

## Requirements

- JDK 17
- Android SDK: compileSdk/targetSdk 36, minSdk 26
- Android Studio or the Gradle wrapper

Put the SDK path in the untracked `local.properties`:

```properties
sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

## Build

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Modules

| Module | Responsibility |
|---|---|
| `:app` | App entry and navigation |
| `:core:*` | Model, domain, database, data, and design system |
| `:feature:*` | Ledger, statistics, AI, and backup UI |
| `:ml:classifier` | On-device linear classifier |

See `docs/PLAN.md`, `docs/ARCHITECTURE.md`, `docs/DATA-MODEL.md`, and `docs/DEV-NOTES.md`.

## License

Released under the [MIT License](LICENSE).
