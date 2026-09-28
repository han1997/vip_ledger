# VipLedger

A local-first, offline personal ledger for Android. Ledgers, accounts, categories, tags, transactions, recurring entries, installments, and statistics stay on the device. Cloud calls are limited to optional natural-language entry, and WebDAV stores encrypted backups only.

Version `0.1.0`, still in development.

## Documentation rule

Keep this README current as you work: **important updates and necessary content must be written into it as they happen**, not batched up for later. Whenever a change affects build steps, setup, features, or requirements, update the corresponding section in the same change.

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
.\gradlew.bat test assembleDebug assembleRelease
.\gradlew.bat lintDebug
```

When asked to "build" the project, produce both variants: `assembleDebug assembleRelease`.

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Signed release APK: `app/build/outputs/apk/release/app-release.apk`

### Release signing

`assembleRelease` is signed with a project release keystore, not the debug keystore. Signing is driven by an untracked root-level `keystore.properties`; when that file is missing the release build stays unsigned instead of failing, so fresh checkouts and CI keep working.

`keystore.properties` (root, gitignored) requires four keys:

```properties
storeFile=keystore/vipledger-release.jks
storePassword=<store password>
keyAlias=vipledger
keyPassword=<key password>
```

Generate the keystore if it does not exist (RSA 2048, alias `vipledger`, 10000-day validity):

```powershell
keytool -genkeypair -v -keystore keystore/vipledger-release.jks -storetype JKS `
  -alias vipledger -keyalg RSA -keysize 2048 -validity 10000 `
  -storepass <password> -keypass <password> `
  -dname "CN=VipLedger, OU=Release, O=VipLedger, L=Unknown, ST=Unknown, C=CN"
```

Verify the signed APK:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```

> **Back up the keystore.** `keystore/` and `keystore.properties` are gitignored and never committed. Keep a copy of both in a password manager or secure backup: losing the keystore means future releases are signed with a different key and cannot upgrade an installed app in place.

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
