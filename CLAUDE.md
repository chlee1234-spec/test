# PetForge — character image → Windows + Android desktop pet (GitHub Actions)

Built on the desktopPet model (https://github.com/Adrianotiger/desktopPet). The user gives ONE image
(4x4 sprite sheet made in ChatGPT with `prompts/spritesheet_prompt.md`) and gets the pet back with no other steps.
No third-party jars; builds run in GitHub Actions (user decision).

## When the user sends a sprite sheet
1. `python3 make.py --sheet <their.png> --name "<Display Name>"` (python3 + numpy/scipy/Pillow). Writes `pets/<id>/`
   (animations.xml in desktopPet schema, icon.png, preview.gif, sprite_preview.png).
2. Exit code 2 = QUALITY WARNINGS: tell the user which frames to redo (it names them) — do not push.
3. Look at `pets/<id>/preview.gif` / `sprite_preview.png`.
4. Commit and push `pets/<id>/` (git or the github connector). `.github/workflows/build.yml` triggers on `pets/**`,
   builds `<id>-windows.zip` (original C# app with the pet injected) and `<id>.apk` (Gradle), and publishes Release `pet-<id>`.
5. The sandbox proxy blocks the Actions API and release blobs. Verify via the github connector: `list_releases` /
   `get_release_by_tag pet-<id>`, check-runs on the commit, and on failure the log branches `buildlog-<id>-windows|android`
   (full build.log). Give the user the Release link (they download there); SendUserFile only if the blob is reachable.
6. Tell the user: Windows may show SmartScreen (unsigned); Android needs "install unknown apps" and the one-time
   "display over other apps" permission. The repo is public unless the user makes it private (art/releases are public).

## Layout
- `pets/<id>/` per-pet data. `petforge/sheet2pet.py` (chroma key, normalise) + `xmlgen.py` (emits animations.xml,
  flattened on magenta because the original app uses a magenta TransparencyKey).
- `android/` Gradle project; `app/src/main/java/com/petforge/core` engine (`PetEngine.parseXml`, also run on the desktop JVM by
  `android/test/EngineCheck.java <animations.xml>`), `.../app` overlay Service/View. Workflow injects assets + launcher icon.
- Windows: no code here; the workflow checks out Adrianotiger/desktopPet at the pinned `DESKTOPPET_REF` and swaps
  `src/Resources/animations.xml`. Settings live in `DesktopPet.config` beside the exe, so pets don't share state.

## Gotchas
- Fixed 16-frame sheet layout (see `xmlgen.py`/prompt): 0-3 walk right, 4-7 idle, 8-9 sleep, 10-11 happy, 12-13 drag, 14-15 fall.
- APK is signed with the per-build SDK debug key: a rebuilt APK can't update-in-place over an older one (uninstall first).
- Android/Windows runtime behaviour is unverified on real devices until the user reports it.
