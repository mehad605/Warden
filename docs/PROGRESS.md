# Warden Overhaul — Iteration Progress Log

| Iter | Sector | Criterion | Change | Metric Before -> After | Scores Before -> After | Keep/Revert | Commit |
|---|---|---|---|---|---|---|---|
| 0 | Phase 0-1 | Baselines & Safety Net | Tagged `baseline-v1`, created `overhaul/v2`, captured `ANALYSIS.md`, `BASELINE.md`, `SCORECARD.md`, `PARITY.md` | APK 7.51 MB / 95,072 DEX refs / 224 deps / 610 lint warnings / 0 unit tests | S1:0 S2:0 S3:0 S4:0 S5:0 S6:3 S7:2 S8:2 | KEEP | `4ad996c` |
| 1 | S1/S2/S3 | Unused deps, dead code & resources | Removed 9 unused deps (Room/KSP/ZXing/Shizuku/MPAndroidChart/TimeRangePicker/ConstraintLayout), 8 dead Kotlin files, 35 unused res/junk files, unwanted CAMERA/Shizuku/Room manifest entries | APK 7.51 MB -> 6.62 MB (-893 KB); DEX 95,072 -> 85,439; Deps 224 -> 205; 25/25 tests pass | S1:1 S2:1 S3:1 S4:0 S5:0 S6:3 S7:2 S8:4 | KEEP | `e088ad0` |
| 2 | S1/S2/S3 | Native libs, reflection & global singleton | Replaced `MultiProcessDataStoreFactory` + Gson settings codec with single-process `AtomicFile` + `org.json` `JsonSettingsRepository` behind `SettingsRepository` and lazy `AppContainer` | APK 6.62 MB -> 6.48 MB (-136 KB); Native `.so` 4 -> 0; Deps 205 -> 198; 26/26 tests pass | S1:2 S2:1 S3:2 S4:0 S5:1 S6:3 S7:2 S8:4 | KEEP | pending |
