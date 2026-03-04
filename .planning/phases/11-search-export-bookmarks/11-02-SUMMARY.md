---
plan: 11-02
status: complete
completed: 2026-03-04
commits:
  - 2caa004 feat(11-02): FileProvider + ExportRepository + SettingsViewModel export intent
  - 58fd9d0 feat(11-02): Export tests — ExportRepositoryTest + SettingsViewModelTest export cases
---

## What Was Built

Full-account ZIP export via Android ShareSheet: FileProvider setup, ExportRepository downloading from `/api/export`, SettingsViewModel export intent, SettingsScreen UI button.

## Deliverables

### res/xml/file_paths.xml
- `<cache-path name="exports" path="." />` for FileProvider content URI generation

### AndroidManifest.xml
- `<provider>` declaration for `androidx.core.content.FileProvider` with `${applicationId}.fileprovider` authority

### ExportRepository.kt / ExportRepositoryImpl.kt
- `exportAll(): Result<File>` — GET `/api/export`, writes ByteArray to `cacheDir/outlinergod-export-{timestamp}.zip`
- Uses Ktor HttpClient following SyncRepositoryImpl pattern

### SettingsViewModel.kt
- `isExporting: Boolean = false` added to `SettingsUiState.Success`
- `SettingsSideEffect.ShareFile(filePath: String)` added to existing sealed class
- `exportAllData()` intent: sets isExporting true, calls exportRepository.exportAll(), posts ShareFile on success, ShowError on failure, resets isExporting in finally

### SettingsScreen.kt
- "Export All Data" OutlinedButton below logout button
- CircularProgressIndicator when isExporting is true
- ShareFile side effect handler: FileProvider.getUriForFile → Intent.ACTION_SEND with application/zip

## Tests (5 new)
- `ExportRepositoryTest`: 3 tests — Result.success/failure contract
- `SettingsViewModelTest` (updated): 2 new tests — exportAllData success (ShareFile side effect + state transitions), exportAllData failure (ShowError side effect + state transitions)

## Issues / Deviations

None. ShareFile is a variant of the existing `SettingsSideEffect` sealed class (not a separate class).
