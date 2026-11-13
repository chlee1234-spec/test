package com.petforge.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

/** Draws one sprite-sheet frame (optionally mirrored) and forwards touches to the service. */
public class PetView extends View {
    private final PetService service;
    private final Bitmap sheet;
    private final int tilesX;
    private final int cellW;
    private final int cellH;
    private final Paint paint = new Paint();
    private final Rect src = new Rect();
    private final Rect dst = new Rect();
    private int frame;
    private boolean flipped;
    private float opacity = 1f;

    public PetView(Context context, PetService service, Bitmap sheet, int tilesX, int tilesY) {
        super(context);
        this.service = service;
        this.sheet = sheet;
        this.tilesX = tilesX;
        this.cellW = sheet.getWidth() / tilesX;
        this.cellH = sheet.getHeight() / tilesY;
        paint.setFilterBitmap(true);
    }

    public void show(int frame, boolean flipped, float opacity) {
        this.frame = frame;
        this.flipped = flipped;
        this.opacity = opacity;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int fx = frame % tilesX;
        int fy = frame / tilesX;
        src.set(fx * cellW, fy * cellH, fx * cellW + cellW, fy * cellH + cellH);
        dst.set(0, 0, getWidth(), getHeight());
        paint.setAlpha(Math.round(Math.max(0f, Math.min(1f, opacity)) * 255f));
        if (flipped) {
            canvas.save();
            canvas.scale(-1f, 1f, getWidth() / 2f, getHeight() / 2f);
            canvas.drawBitmap(sheet, src, dst, paint);
            canvas.restore();
        } else {
            canvas.drawBitmap(sheet, src, dst, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return service.onPetTouch(event.getAction(), event.getRawX(), event.getRawY());
    }
}
