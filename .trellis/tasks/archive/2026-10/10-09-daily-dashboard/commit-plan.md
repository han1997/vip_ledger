# Commit plan

Application implementation and verification are complete. Await the single confirmation required by .trellis/workflow.md section 3.4.

## 1. docs: record feature completion roadmap and phased tasks

- docs/PLAN.md
- .trellis/tasks/10-09-assets-goals/
- .trellis/tasks/10-09-capture-acceptance/
- .trellis/tasks/10-09-completion-acceptance/
- .trellis/tasks/10-09-daily-dashboard/
- .trellis/tasks/10-09-feature-completion/
- .trellis/tasks/10-09-finance-plans/
- .trellis/tasks/10-09-reimbursement-lending/
- .trellis/tasks/10-09-reports-evolution/
- .trellis/tasks/10-09-voice-image-entry/
- docs/FEATURE-ROADMAP.md

## 2. feat: add monthly budgets, calendar and home dashboard

- .trellis/spec/android/build-and-env.md
- .trellis/spec/android/import-export.md
- .trellis/spec/android/index.md
- .trellis/spec/android/ui.md
- CHANGELOG.md
- README.md
- app/src/main/kotlin/com/han1997/vipledger/MainActivity.kt
- app/src/main/kotlin/com/han1997/vipledger/VipLedgerApplication.kt
- app/src/main/kotlin/com/han1997/vipledger/ui/VipLedgerApp.kt
- core/data/src/main/kotlin/com/han1997/vipledger/core/data/backup/SnapshotArchive.kt
- core/data/src/main/kotlin/com/han1997/vipledger/core/data/backup/SnapshotJsonCodec.kt
- core/data/src/test/kotlin/com/han1997/vipledger/core/data/backup/SnapshotArchiveTest.kt
- core/database/src/main/kotlin/com/han1997/vipledger/core/database/LedgerDatabase.kt
- core/database/src/main/kotlin/com/han1997/vipledger/core/database/Migrations.kt
- core/database/src/test/kotlin/com/han1997/vipledger/core/database/LedgerDatabaseTest.kt
- core/database/src/test/kotlin/com/han1997/vipledger/core/database/MigrationTest.kt
- core/designsystem/src/main/kotlin/com/han1997/vipledger/core/designsystem/component/chart/BarChart.kt
- core/designsystem/src/main/res/values/strings.xml
- core/domain/src/main/kotlin/com/han1997/vipledger/core/domain/model/HomeOverview.kt
- core/domain/src/main/kotlin/com/han1997/vipledger/core/domain/usecase/home/ObserveHomeOverviewUseCase.kt
- core/domain/src/test/kotlin/com/han1997/vipledger/core/domain/usecase/ObserveHomeOverviewUseCaseTest.kt
- docs/DATA-MODEL.md
- feature/ledger/build.gradle.kts
- feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/navigation/LedgerRoutes.kt
- feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/ui/home/HomeScreen.kt
- feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/ui/home/HomeUiState.kt
- feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/ui/home/HomeViewModel.kt
- feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/ui/txnedit/TxnEditScreen.kt
- feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/ui/txnedit/TxnEditViewModel.kt
- feature/ledger/src/main/res/values/strings.xml
- version.properties
- .trellis/spec/android/budget-calendar.md
- app/src/main/kotlin/com/han1997/vipledger/notify/BudgetMonitor.kt
- app/src/main/res/values/budget_strings.xml
- core/data/src/main/kotlin/com/han1997/vipledger/core/data/di/BudgetModule.kt
- core/data/src/main/kotlin/com/han1997/vipledger/core/data/repository/BudgetRepositoryImpl.kt
- core/data/src/test/kotlin/com/han1997/vipledger/core/data/backup/BudgetSnapshotTest.kt
- core/database/schemas/com.han1997.vipledger.core.database.LedgerDatabase/3.json
- core/database/src/main/kotlin/com/han1997/vipledger/core/database/dao/BudgetDao.kt
- core/database/src/main/kotlin/com/han1997/vipledger/core/database/entity/BudgetEntities.kt
- core/domain/src/main/kotlin/com/han1997/vipledger/core/domain/calculator/BudgetCalculator.kt
- core/domain/src/main/kotlin/com/han1997/vipledger/core/domain/model/Budget.kt
- core/domain/src/main/kotlin/com/han1997/vipledger/core/domain/repository/BudgetRepository.kt
- core/domain/src/main/kotlin/com/han1997/vipledger/core/domain/usecase/budget/
- core/domain/src/main/kotlin/com/han1997/vipledger/core/domain/usecase/home/ObserveCalendarUseCase.kt
- core/domain/src/main/kotlin/com/han1997/vipledger/core/domain/usecase/home/ObserveLocalDayUseCase.kt
- core/domain/src/main/kotlin/com/han1997/vipledger/core/domain/usecase/home/ObservePendingAutoCountUseCase.kt
- core/domain/src/test/kotlin/com/han1997/vipledger/core/domain/usecase/BudgetUseCasesTest.kt
- feature/ledger/src/androidTest/
- feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/ui/budget/
- feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/ui/calendar/
- feature/ledger/src/main/kotlin/com/han1997/vipledger/feature/ledger/ui/common/MonthNavigation.kt
- feature/ledger/src/main/res/values/daily_strings.xml
- feature/ledger/src/test/kotlin/com/han1997/vipledger/feature/ledger/ui/calendar/

## Keep untracked user inputs out of commits

- Snipaste_2026-10-09_16-55-28.png
- Snipaste_2026-10-09_16-55-44.png
- Snipaste_2026-10-09_16-56-37.png
- iPhoneX-Mockup2.jpg
- 账单_1009122349.zip

After the two work commits: archive only 10-09-daily-dashboard and record the journal. Keep the parent and P2-P8 tasks open. No push.
