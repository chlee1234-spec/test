# PetForge

Character image → desktop pet for Windows and Android, built by **GitHub Actions** on top of the original
[desktopPet](https://github.com/Adrianotiger/desktopPet) design (`animations.xml`, schema `https://esheep.petrucci.ch/`).

- **Windows**: the original C# desktopPet app is built unchanged from a pinned commit; only its default pet
  (`src/Resources/animations.xml`) is replaced by your pet. Delivered as `<id>-windows.zip` (the exe needs its DLLs).
- **Android**: `android/` is a standard Gradle project (official Android SDK/AGP, no third-party jars) whose
  Java engine reads the same `animations.xml`. Delivered as `<id>.apk`.
- No third-party jars or actions: only GitHub's own actions plus Microsoft/NuGet setup actions (as in the original repo).

Flow: `python3 make.py --sheet sheet.png --name "Name"` → `pets/<id>/` → push → Actions → Release `pet-<id>`.
