# P2 commit plan

feat: add assets, savings goals and configurable home cards

- .trellis/spec/android/architecture.md
- .trellis/spec/android/assets-savings.md
- .trellis/spec/android/database.md
- .trellis/spec/android/import-export.md
- .trellis/spec/android/index.md
- .trellis/spec/android/ui.md
- .trellis/tasks/10-09-assets-goals/bug-analysis.md
- .trellis/tasks/10-09-assets-goals/commit-plan.md
- .trellis/tasks/10-09-assets-goals/prd.md
- .trellis/tasks/10-09-assets-goals/task.json
- CHANGELOG.md
- README.md
- app/src/main/kotlin/com/han1997/vipledger/ui/VipLedgerApp.kt
- core/data/src/main/kotlin/com/han1997/vipledger/core/data/backup/SnapshotArchive.kt
- core/data/src/main/kotlin/com/han1997/vipledger/core/data/backup/SnapshotJsonCodec.kt
- core/data/src/main/kotlin/com/han1997/vipledger/core/data/di/UseCaseModule.kt
- core/data/src/main/kotlin/com/han1997/vipledger/core/data/di/WealthModule.kt
- core/data/src/main/kotlin/com/han1997/vipledger/core/data/repository/TxnRepositoryImpl.kt
- core/data/src/main/kotlin/com/han1997/vipledger/core/data/repository/WealthRepositoryImpl.kt
- core/data/src/test/kotlin/com/han1997/vipledger/core/data/autobook/AutoBookRepositoryTest.kt
- core/data/src/test/kotlin/com/han1997/vipledger/core/data/backup/SnapshotArchiveTest.kt
- core/data/src/test/kotlin/com/han1997/vipledger/core/data/backup/WealthIntegrationTest.kt
- core/database/schemas/com.han1997.vipledger.core.database.LedgerDatabase/4.json
- core/database/src/main/kotlin/com/han1997/vipledger/core/database/LedgerDatabase.kt
- core/database/src/main/kotlin/com/han1997/vipledger/core/database/Migrations.kt
- core/database/src/main/kotlin/com/han1997/vipledger/core/database/dao/LedgerDao.kt
- core/database/src/main/kotlin/com/han1997/vipledger/core/database/dao/TxnDao.kt
- core/database/src/main/kotlin/com/han1997/vipledger/core/database/dao/WealthDao.kt
- core/database/src/main/kotlin/com/han1997/vipledger/core/database/entity/WealthEntities.kt
- core/database/src/test/kotlin/com/han1997/vipledger/core/database/LedgerDatabaseTest.kt
- core/database/src/test/kotlin/com/han1997/vipledger/core/database/MigrationTest.kt
- core/designsystem/src/main/kotlin/com/han1997/vipledger/core/designsystem/format/Formatters.kt
- core/designsystem/src/main/res/values/strings.xml
- core/domain/src/main/kotlin/com/han1997/vipledger/core/domain/calculator/BalanceCalculator.kt
- core/domain/src/main/kotlin/com/han1997/vipledger/core/domain/calculator/SavingsCalculator.kt
- core/domain/src/main/kotlin/com/han1997/vipledger/core/domain/model/Wealth.kt
- core/domain/src/main/kotlin/com/han1997/vipledger/core/domain/repository/WealthRepository.kt
- core/domain/src/main/kotlin/com/han1997/vipledger/core/domain/usecase/home/ObserveHomeOverviewUseCase.kt
- core/domain/src/main/kotlin/com/han1997/vipledger/core/domain/usecase/txn/DeleteTxnUseCase.kt
- core/domain/src/main/kotlin/com/han1997/vipledger/core/domain/usecase/txn/UpdateTxnUseCase.kt
- core/domain/src/main/kotlin/com/han1997/vipledger/core/domain/usecase/wealth/
- core/domain/src/test/kotlin/com/han1997/vipledger/core/domain/calculator/BalanceCacheTest.kt
- core/domain/src/test/kotlin/com/han1997/vipledger/core/domain/calculator/WealthCalculatorTest.kt
- core/model/src/main/kotlin/com/han1997/vipledger/core/model/LedgerEnums.kt
- docs/DATA-MODEL.md
- docs/FEATURE-ROADMAP.md
- feature/ledger/src/androidTest/kotlin/com/han1997/vipledger/feature/ledger/ui/wealth/
- feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/navigation/LedgerRoutes.kt
- feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/ui/home/HomeScreen.kt
- feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/ui/home/HomeUiState.kt
- feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/ui/home/HomeViewModel.kt
- feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/ui/manage/AccountManageViewModel.kt
- feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/ui/txnedit/TxnEditScreen.kt
- feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/ui/txnedit/TxnEditViewModel.kt
- feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/ui/wealth/
- feature/ledger/src/main/res/values/wealth_strings.xml
- feature/ledger/src/test/kotlin/com/han1997/vipledger/feature/ledger/ui/wealth/
- version.properties

## Untracked user inputs (excluded)

- Snipaste_2026-10-09_16-55-28.png
- Snipaste_2026-10-09_16-55-44.png
- Snipaste_2026-10-09_16-56-37.png
- iPhoneX-Mockup2.jpg
- 账单_1009122349.zip

After commit: archive only assets-goals and record the session. Parent roadmap and P3-P8 remain open.
