# PetForge — character image → Windows .exe + Android .apk

Personal tool built on the desktopPet animation model (https://github.com/Adrianotiger/desktopPet).
Goal: the user gives ONE character image (a 4x4 sprite sheet made in ChatGPT with
`prompts/spritesheet_prompt.md`) and gets `<id>.exe` + `<id>.apk` back with no other steps.

## When the user sends a sprite sheet
1. `python3 make.py --sheet <their.png> --name "<Display Name>"`  (needs python3 + numpy/scipy/Pillow,
   Go 1.24, JDK 17+, node/npm). It fetches `tools/bundletool.jar` itself (pinned SHA-256).
2. If it exits with QUALITY WARNINGS, tell the user which frames to redo (it names them) — do not ship.
3. Look at `out/<id>/preview.gif` / `sprite_preview.png` before delivering.
4. Deliver `out/<id>/<id>.exe` and `<id>.apk` with SendUserFile. Mention: Windows may show SmartScreen
   (unsigned exe); Android needs "install unknown apps" and the "display over other apps" permission once.
5. If the sandbox is new, the repo is cloned fresh; nothing else to set up.

For an existing desktopPet pet: `python3 make.py --xml Pets/<x>/animations.xml --name <X>`.

## Layout
- `engine/` Go animation engine (+tests) · `android/src/com/petforge/core/` Java port (+`android/test/EngineCheck`)
  Both read `pet.txt` (see FORMAT.md). Keep the two engines behaviourally identical.
- `windows/` Win32 layered window (stdlib only, cross-compiled; cannot be run in the sandbox).
- `android/src/com/petforge/app/` Service + overlay view; `android/stubs/` = hand-written android.jar stubs
  (signatures must match the real Android API exactly or the app crashes at runtime).
- `tools/` build_apk.py (javac → D8 → hand-written binary manifest → zip → apksig), axml.py, fetch_bundletool.py.
- `petforge/` sheet2pet.py (chroma key, normalise, animation set), xml2pet.py.

## Gotchas
- D8 in bundletool.jar crashes on anonymous inner classes → implement interfaces on the class itself.
- No launcher icon in the APK (no resources.arsc); the system default icon is used.
- Keystore lives in `keystore/` (gitignored). A new sandbox makes a new key, so an APK built later cannot
  update-in-place over an earlier one signed by another key (uninstall first). Different pets are separate apps.
- Windows/Android windows behaviour is unverified on real devices unless the user reported it.
