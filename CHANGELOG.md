# Changelog

## 2026-05-07 - v1.0.12 (versionCode 12)

Changed modules:
- communication
- main-ui
- elease

Fixed / added:
- fixed communication page-size inconsistency so CommunicationCatalog now matches 4 / 9 / 16 / 25
- verified main screen top-zone layout remains [RED -] [RADIO 2x3] [PATIENT/SPEED/DATE] [GREEN +]
- verified patient/date module updates language flag, patient name, speed, day, date and time without km/h
- verified bottom labels stay one-line and compact with SPROSTI fallback

Known issues:
- final green build still depends on GitHub Actions after push
- this patch intentionally avoids changes outside main screen and paging

## 2026-05-07 - v1.0.11 (versionCode 11)

Changed modules:
- `main-ui`
- `communication`
- `paging`
- `release`

Fixed / added:
- fixed tablet main screen layout regression with stable top zones for radio and patient info
- moved on-screen volume buttons to standalone left/right positions around the top modules
- restored patient name, language flag, speed value, day, date and time without km/h label
- fixed bottom action button labels so they stay readable on one line
- fixed communication paging so enabled main icons are no longer cut off after the first page
- added paging diagnostics for total icons, enabled icons, hidden reasons, page size and page count

Known issues:
- final green build still depends on GitHub Actions after push
- this release intentionally avoids changes to settings, gallery, mirror, companion and video call flows
## 2026-05-07 - v1.0.10 (versionCode 10)

Changed modules:
- `main-ui`
- `gallery`
- `mirror`
- `settings`
- `release`

Fixed / added:
- fixed broken UTF-8 labels on the main screen bottom buttons
- corrected the patient/date module and removed the old km/h display
- corrected on-screen volume button placement around the patient/date area
- simplified patient gallery controls to arrow navigation and close-only flow
- moved the mirror close button away from the capture button
- replaced remaining settings dropdown use with fullscreen pickers and improved tablet-safe readability

Known issues:
- final green build still depends on GitHub Actions after push
- this release intentionally avoids changing TTS, communicator logic, companion logic and video call behavior

## 2026-05-06 - v1.0.9 (versionCode 9)

Changed modules:
- `settings`
- `icon-settings`
- `main-ui`
- `release`

Fixed / added:
- Settings UI stabilization
- replaced dropdowns with fullscreen pickers
- improved tablet-safe readability across settings screens
- fixed clipped and dark text in settings flows

Known issues:
- final green build still depends on GitHub Actions after push
- companion and tablet blocker fixes remain in separate stabilization commits
## 2026-05-06 - v1.0.8 (versionCode 8)

Changed modules:
- `companion`
- `settings`
- `main-ui`
- `release`

Fixed / added:
- fixed companion crash after contact selection with safe startup validation and reset flow
- added safe fallback for invalid saved contact config and text-based tablet config import foundation
- standardized settings submenu readability with a shared settings UI styler
- repositioned tablet on-screen volume controls to the top area around the speed module

Known issues:
- final green build still depends on GitHub Actions after push
- companion config import currently uses shared text foundation; QR/file onboarding can be layered later
## 2026-05-06 - v1.0.7 (versionCode 7)

Changed modules:
- `settings`
- `communication/ui`
- `speech`
- `release`

Fixed / added:
- added optional hardware volume button control modes for communication
- kept normal Android behavior as default
- added repeat, stop, speech-volume and page-navigation volume button modes
- kept volume buttons untouched unless explicit admin setting is enabled
- bumped release version metadata for the hardware control update

Known issues:
- final green build still depends on GitHub Actions after push
- some older settings labels in SettingsActivity still contain legacy encoded text and can be cleaned separately

## 2026-05-06 - v1.0.6 (versionCode 6)

Changed modules:
- `speech`
- `settings`
- `core/storage`
- `release`

Fixed / added:
- added hybrid OpenAI/local speech response modes with immediate fallback
- added reusable SpeechCacheManager and background preload of common phrases
- added advanced speech settings for provider, mode, style, model and cache cleanup
- added speech diagnostics for average delay, cache hit rate and last source
- bumped release version metadata for the speech responsiveness update

Known issues:
- final green build still depends on GitHub Actions after push
- some older settings labels in SettingsActivity still contain legacy encoded text and can be cleaned separately

## 2026-05-06 - v1.0.5 (versionCode 5)

Changed modules:
- `settings`
- `backup/export-import`
- `learning`
- `release`

Fixed / added:
- connected manual settings export/import actions in SettingsActivity
- added ZIP share/export flow and partial import preview flow
- added rollback-safe import with automatic backup before restore
- aligned app-private backup path with NovaRehabPaths
- bumped release version metadata for the export/import stabilization update

Known issues:
- final green build still depends on GitHub Actions after push
- some older settings labels in SettingsActivity still contain legacy encoded text and can be cleaned separately

## 2026-05-06 - v1.0.4 (versionCode 4)

Changed modules:
- `settings`
- `backup/export-import`
- `learning`
- `release`

Fixed / added:
- added manual ZIP export of NovaRehab personalized profile and settings
- added safe ZIP import with preview and partial restore options
- added statistics JSON export
- added rollback backup before import
- updated release version metadata for this stabilization update

Known issues:
- final green build still depends on GitHub Actions after push
- some older settings labels in SettingsActivity still contain legacy encoded text and can be cleaned separately
## 2026-05-06 - v1.0.3 (versionCode 3)

Changed modules:
- `music`
- `core/storage`
- `settings`
- `release`

Fixed bugs:
- Added safer USB music import into local `NovaRehab/music` storage.
- Added duplicate-safe music copy rules and clearer import progress.
- Refreshed local music playlist after import from USB.
- Bumped release version metadata for the next test build.

Known issues:
- Final green build confirmation still depends on GitHub Actions after push.
- USB access still depends on the device exposing the USB stick as readable storage.

## 2026-05-06 - v1.0.2 (versionCode 2)

Changed modules:
- `communication`
- `media_messaging`
- `core/storage`
- `settings`
- `companion`
- `update`

Fixed bugs:
- Stabilized Slovenian/Ukrainian communicator structure so language no longer changes main icon layout.
- Added safer JSON handling for BOM and explicit main icon lists.
- Switched NovaRehab file storage to app-specific external storage for gallery, camera, updates, backups and icon data.
- Stabilized gallery and mirror save flow so camera captures are stored and read from the same NovaRehab path.
- Improved icon visibility diagnostics and settings readability on dark backgrounds.
- Unified release metadata URL usage and bumped release version metadata.

Known issues:
- Final green build confirmation still depends on GitHub Actions after push.
- Some older update/backup code paths may still need future cleanup if new release workflows are added.



