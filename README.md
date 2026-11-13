# PetForge

Character image → desktop pet for Windows (`.exe`) and Android (`.apk`).
Animation model and XML format come from [desktopPet](https://github.com/Adrianotiger/desktopPet);
this project re-implements the engine (Go for Windows, Java for Android) so new characters can be built
automatically. See `CLAUDE.md` for the workflow and `prompts/spritesheet_prompt.md` for the image prompt.

    python3 make.py --sheet sheet.png --name "Mochi"
