# VipLedger

A local-first, offline personal ledger for Android. Ledgers, accounts, categories, tags, transactions, recurring entries, installments, and statistics stay on the device. Cloud calls are limited to optional natural-language entry, and WebDAV stores encrypted backups only.

Still in development. The application version is defined only in `version.properties`.

## Documentation rule

Keep this README current as you work: **important updates and necessary content must be written into it as they happen**, not batched up for later. Whenever a change affects build steps, setup, features, or requirements, update the corresponding section in the same change.

## Features

**Assets and savings (P2):** the five tabs are Home / Calendar / Assets / Statistics / Me.
Assets groups funds, credit, prepaid, receivable/payable, loan and investment accounts. Totals
use the same included, unarchived account set: positive balances are assets and negative balances
are debt. Account detail exposes its entries, edit/archive, transfers, and balance calibration
(an adjustment entry, excluded from income/expense). The app records a verifiable balance baseline
on upgrade or first asset observation; historical trend charts are reserved for P7.

Savings goals bind to a non-debt account. Deposits and withdrawals create one real transfer each;
progress derives from the linked entries and never posts a second balance change. Reaching the target
marks it achieved; goals can be edited, archived and restored. Withdrawals and linked-entry edits,
deletion or restoration cannot make saved progress negative. Linked transfers retain their savings
direction/account. Home's settings icon controls card visibility and order, with a reset action;
the default cards are Budget / Today / Savings / Seven days. Goals, transfer links, layout and asset
baselines are included in format-4 backups, which remain compatible with format 1–3 snapshots.
Ledger renames and edits to linked entries update rows in place, preserving their child records.
Transaction-and-tag writes and savings transfers roll back together on failure.

The home dashboard now combines monthly spending, assets, remaining budget, today's income and
expense, a seven-day trend, pending drafts/payment reviews, and recent entries grouped by date.
Tap a recent entry to edit it directly. Card order and visibility are customizable per ledger.

**Monthly budgets:** tap the Budget card on Home to set a total or first/second-level expense
category limit, including zero limits. Limits carry forward, unspent money does not. Historical
month edits preserve subsequent limits. Refunds reduce spending in the month received. Each
budget can notify once at 80% and 100% per month; in-app progress is available without notification
permission. Stop a budget through its edit dialog. Budgets and alert history are included in backups.

**Calendar:** switch months, select a date to inspect its entries, and use “Record on this day”
to open the existing editor with that date. Today and monthly data refresh while the page remains
open, including timezone changes (within 30 seconds). Calendar income/expense, home, budgets,
and statistics share the same confirmed-entry/refund rules.

**Reimbursement and lending (P3):** an existing confirmed expense can join a reimbursement project
(single or multi-item) with counterparty, remark and termination. Receipts post one linked refund per
item, inherit the original category and stats exclusion, and share the original-expense cap with
ordinary refunds, so a received amount reduces the month it arrives in while the outstanding claim
stays visible and never becomes a receivable asset. Lending records disburse principal as a transfer
to an auto-created receivable/payable account, post interest as income (lent out) or expense
(borrowed), support partial settlement down to zero (auto-settled) and due-date reminders. Project
plus item links, receipt plus refund, and principal transfer plus account link each commit in one
transaction and roll back together. Format-5 backups include the five new tables and stay compatible
with formats 1–4.

**Installment, mortgage and investment (P4):** an installment plan posts each repayment as separate
legs: principal is a transfer from a cash account into the plan's debt account, while interest and
fees are expenses, so spending counts only interest and fees. A plan is either an existing debt (no
new principal) or a new disbursement that posts its principal transfer once. Periods support
per-period settlement with partial repayment (fee → interest → principal), settle-in-full, partial
prepayment that keeps the remaining period count and rewrites only unpaid periods, and rate changes
effective from a chosen unpaid period; custom plans enter principal, interest and fee per period.
Legacy paid periods without split links are shown only as a difference and rewritten solely on
explicit user correction, never silently. Investments are amount ledgers: adding or redeeming
principal is a transfer (excluded from income/expense), realized gains are income and losses or fees
are expenses, and the list shows invested principal, cumulative investment and realized result
without market data or automatic valuation. Format-6 backups include the installment links and the
two investment tables and stay compatible with formats 1–5.

The complete agreed feature roadmap and later phases are in [docs/FEATURE-ROADMAP.md](docs/FEATURE-ROADMAP.md).
Category expansion now uses stable row containers; a Compose device test repeatedly expands,
switches and collapses parent categories to prevent a reproduced node-insertion crash.
Reimbursement, lending, installment plans and investment ledgers are now implemented; the P5 voice and image/share entry slices are implemented (optional attachment saving remains), while report improvements remain in later phases.

JD OCR handles the observed light-theme label variants (`订单蝙号`, `支付万式`, `©完成`) as well as the dark-theme variants. Verified JD order pages can retry after expansion or scrolling, with at least two seconds between screenshots and foreground/permission checks repeated before capture; an early incomplete screenshot no longer permanently blocks that page.

JD OCR reconstructs label/value rows from recognized line coordinates instead of relying on text-block order. Known JD OCR label errors (`支付时闻`, `台计`, and an icon before `完成`) are corrected only in JD OCR input; amounts and order-number digits are never guessed or rewritten. The store row with `进店` takes precedence over store names inside product images.

JD's verified `OrderDetailActivityTaro` order-detail page can use the optional on-device OCR fallback even when its accessibility tree exposes no text. Completed orders require an order number, payment method and payment-time label; actual totals are read from `实付款 … 合计`, excluding discounts and product prices. Historical order results remain pending for review.

Pinduoduo signed-for order details can create review candidates when actual payment, payment method, order number and order-time labels are present. Accessibility text such as `实付:,134元` is supported. These historical order pages always require time review; shipping or order placement time is not treated as payment time and balances are not changed automatically.

Alipay accessibility can expose a displayed negative amount as `支出20元`. Bill parsing supports this text form and the full counterparty labels `收款方全称` / `付款方全称`; screenshot appearance alone is not used as evidence of the accessibility text format.

Alipay bill-detail capture supports the merchant header, signed expense amount, payment time and payment method. Field labels must match completely (`付款方` must not match `付款方式`), and the payment-method action `查看扣款顺序` is excluded. Repeated events in one payment window share a session; revealing an order number can enrich the pending record when its amount and payment time match. Existing records without reliable shared identity are not automatically deleted or merged.

Capture status separates the latest processing attempt from the latest saved record. The permissions panel reports whether a capture updated a record, made no change, or failed, and whether an unambiguous amount was extracted. Reopening a known transaction does not necessarily create a new record.

Payment captures that need review now also produce a notification (when Android notification permission is granted); tap it to open the review queue. WeChat bill details support the signed debit header and Chinese payment dates. Historical bills remain pending for confirmation and do not change balances automatically.

- Multiple isolated ledgers
- Income, expense, refund, transfer, repayment, and balance adjustment
- Accounts, categories, and custom tags
- Recurring entries and installment plans
- Search, statistics, category share, and trends
- On-device classifier, plus optional OpenAI-compatible natural-language entry
- Voice entry with press/hold/release/cancel controls, editable transcription, on-device-first system recognition, and an independent online-transcription switch (off by default)
- Image entry via the system photo picker and image sharing: multi-image on-device Chinese OCR feeds the same draft review; originals are never saved
- CSV and JSON import/export, with encrypted WebDAV snapshots
- Offline payment capture with a separate review queue and account bindings

The recording screen keeps a single save action at the bottom and confirms before leaving with
unsaved edits. Times are second-precise: changing the date keeps the time of day, manual time entry
is second-level, Today and Yesterday are computed live, and an AI-recognized time wins over the
form's default. Categories render as a grid of full names, with the sub-category row inserted
directly under the tapped parent and only one parent expanded at a time; tags can be searched or
created inline, and a same-named tag is reused instead of duplicated. Transaction lists show
seconds so entries in the same minute keep a stable order.

## Automatic bookkeeping

Open **Me → Automatic bookkeeping**, choose a fixed ledger, bind payment methods to accounts,
and grant notification access or accessibility access in Android settings. Chinese OCR is optional
and bundled for offline use; screenshots and raw notifications are not saved.

Permissions, apps, and account bindings are grouped into expandable sections. A fixed ledger is
required before capture can be enabled. You can capture a payment first, then select its observed
payment method when creating an account binding; missing accounts are explained in the form.
Password and API-key fields mask their contents.

WeChat, Alipay, JD, Pinduoduo, and Taobao are configured capture targets. Each starts in review mode:
validate real payment samples before enabling automatic posting for that platform. Ambiguous amounts,
accounts, direction, refunds, and possible duplicates remain pending. Posted entries can be edited or
deleted through the record link while capture evidence remains available for deduplication.

The October 2026 device-test session ended at the user's request: selected Alipay and Pinduoduo flows passed, while JD OCR was provisionally accepted with remaining accuracy limitations. WeChat final field verification, Taobao, and the broader original acceptance matrix remain incomplete; this is not full five-platform certification.

Refunds increase the receiving account balance and reduce expense in the month received. Partial
refunds can reference an original expense. Database version 1 upgrades to version 2 without resetting
data; version 1 snapshots remain readable. Restoring a snapshot disables capture and clears platform
verification, so settings must be reviewed before capture is enabled again.

OPPO X8 Ultra / ColorOS 17 and real payment capture across all five platforms still require device
acceptance. Synthetic parser tests do not establish platform compatibility. Accessibility screenshots
require Android 11 or later; protected windows cannot be captured. Android restricted settings and
manufacturer background restrictions may require manual setup as described in the app.

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

- Debug APK: `app/build/outputs/apk/debug/fun.han1997.vipledger-v<versionName>-<versionCode>-debug.apk`
- Signed release APK: `app/build/outputs/apk/release/fun.han1997.vipledger-v<versionName>-<versionCode>-release.apk`

### Application versions

`version.properties` is the single source for `versionName` and `versionCode`.
For each delivered application source/resource/build change, bump once and record its summary:

```powershell
python -B tools/version.py bump --message "更新说明"
python -B tools/version.py bump --part minor --message "次版本更新说明" # use instead of patch when needed
python -B -m unittest discover -s tools/tests -v
```

The default is a patch bump; `--part major` also works. Every bump increments `versionCode`
and prepends an entry to `CHANGELOG.md`. The tool needs Python 3.8+ and no extra packages.
Repeated builds, fixes within the same delivery, and documentation/Trellis-only edits do not
bump again. Gradle only reads the version file. **Me** shows the installed version at the bottom,
including when no ledger exists.

The installed application ID is `fun.han1997.vipledger`; Kotlin packages and module namespaces
remain `com.han1997.vipledger`. The first installation with this ID starts with fresh data and
coexists with the old application, whose data stays intact. No automatic migration is performed.
Subsequent versions use the same release key and `adb -s <physical-device-serial> install -r $releaseApk`.
Verify the resulting package version after installation; do not uninstall or automate phone navigation.

APK names include the application ID, version name, version code, and build type. Unsigned releases
add `-unsigned` before `.apk`. Always select the current version, not a leftover APK from an earlier build.

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
$appVersion = ConvertFrom-StringData (Get-Content -Raw version.properties)
$releaseApk = "app/build/outputs/apk/release/fun.han1997.vipledger-v$($appVersion.versionName)-$($appVersion.versionCode)-release.apk"
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs $releaseApk
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
