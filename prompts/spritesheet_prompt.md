# ChatGPT 生圖 prompt(角色 sprite sheet)

使用方式:在 ChatGPT 附上**角色參考圖**,貼上下面整段(英文效果最穩),把 `[CHARACTER]` 換成角色描述。
拿到圖後原樣(PNG,不要截圖、不要壓縮)交給 Claude 即可。

```
Using the attached image as the character reference, create ONE sprite sheet image.

Canvas: square, 1024x1024 px. A strict 4 columns x 4 rows grid of 16 equal square cells
(256x256 px each). No grid lines, no borders, no labels, no text, no shadows on the ground.
Background: one flat, uniform, pure magenta (#FF00FF) everywhere, including between cells.
Do not use magenta, pink or purple on the character.

Character: [CHARACTER] — exactly the same design, colours, proportions, art style and
apparent size in all 16 cells. Full body, side view, fully inside its cell with a small
margin, feet resting at the same height in every cell. Clean outline, no motion blur.

Cells, left to right, top to bottom (all facing RIGHT unless stated):
Row 1 (cells 1-4):   walk cycle, 4 frames: right foot forward, passing, left foot forward, passing.
Row 2 (cells 5-8):   idle, standing still, 4 frames: small breathing / blink / tail or ear movement.
Row 3 (cells 9-12):  cells 9-10 sleeping (curled up or lying, eyes closed, tiny breathing change);
                     cells 11-12 happy: little jump / arms up, 2 different poses.
Row 4 (cells 13-16): cells 13-14 being picked up: hanging in the air, legs dangling, 2 slightly
                     different frames, facing the viewer; cells 15-16 falling: arms/legs spread,
                     2 slightly different frames.
```

## 常見問題 / 重生建議
- 背景不是純洋紅、或角色身上有洋紅色 → 重生並加上 "the background must be exactly #FF00FF".
- 某格角色大小或位置差很多 → 只需說「重畫第 N 格,其餘保持不變」,但 ChatGPT 常會整張重畫,直接整張重生較穩。
- 格線/文字出現 → 重生並強調 "no grid lines, no text".
- 腳不在同一水平線不要緊,Claude 會自動對齊底線並統一縮放。
