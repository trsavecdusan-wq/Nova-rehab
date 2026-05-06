# Changelog

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
