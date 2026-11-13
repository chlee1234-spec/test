package com.petforge.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.petforge.core.PetEngine;

import java.io.InputStream;

/** Foreground service that shows the pet in a system overlay window. */
public class PetService extends Service implements Runnable {
    private static final String CHANNEL = "pet";

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private PetView view;
    private PetEngine eng;
    private Handler handler;
    private int ticks;
    private float dragDX;
    private float dragDY;

    @Override
    public void run() {
        step();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            start();
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void start() throws Exception {
        InputStream in = getAssets().open("animations.xml");
        PetEngine.Pet pet;
        try {
            pet = PetEngine.parseXml(in);
        } finally {
            in.close();
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;
        opts.inMutable = true;
        Bitmap sheet = BitmapFactory.decodeByteArray(pet.png, 0, pet.png.length, opts);
        // desktopPet sprite sheets use pure magenta as the transparency key.
        int[] px = new int[sheet.getWidth() * sheet.getHeight()];
        sheet.getPixels(px, 0, sheet.getWidth(), 0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < px.length; i++) {
            if ((px[i] & 0xFFFFFF) == 0xFF00FF) px[i] = 0;
        }
        sheet.setPixels(px, 0, sheet.getWidth(), 0, 0, sheet.getWidth(), sheet.getHeight());

        // Foreground notification first (must happen within a few seconds of startForegroundService).
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, pet.name, NotificationManager.IMPORTANCE_LOW));
        Intent stop = new Intent(this, MainActivity.class).putExtra("stop", true);
        PendingIntent pi = PendingIntent.getActivity(this, 0, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Bitmap icon = Bitmap.createBitmap(sheet, 0, 0, sheet.getWidth() / pet.tilesX, sheet.getHeight() / pet.tilesY);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle(pet.name)
                .setContentText("Tap to stop")
                .setSmallIcon(Icon.createWithBitmap(icon))
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(1, n);

        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        // 1 desktop pixel = 1 dp, so pets look the same size as on the desktop.
        eng = new PetEngine(pet, dm.density, System.nanoTime());
        eng.setEnv(dm.widthPixels, dm.heightPixels, dm.widthPixels, dm.heightPixels);

        int w = Math.round((float) eng.imageW());
        int h = Math.round((float) eng.imageH());
        lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        view = new PetView(this, this, sheet, pet.tilesX, pet.tilesY);
        eng.spawn();
        eng.tick();
        apply();
        wm.addView(view, lp);

        handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(this, eng.interval);
    }

    private void apply() {
        lp.x = Math.round((float) eng.x);
        lp.y = Math.round((float) (eng.y + eng.offsetY));
        view.show(eng.frame, eng.flipped, (float) eng.opacity);
    }

    private void step() {
        eng.tick();
        apply();
        wm.updateViewLayout(view, lp);
        if (++ticks % 30 == 0) {
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            eng.setEnv(dm.widthPixels, dm.heightPixels, dm.widthPixels, dm.heightPixels);
        }
        handler.postDelayed(this, eng.interval);
    }

    boolean onPetTouch(int action, float rawX, float rawY) {
        if (eng == null) return false;
        if (action == MotionEvent.ACTION_DOWN) {
            handler.removeCallbacks(this);
            eng.beginDrag();
            dragDX = rawX - (float) eng.x;
            dragDY = rawY - (float) eng.y;
            eng.tick();
            apply();
            wm.updateViewLayout(view, lp);
            handler.postDelayed(this, eng.interval);
            return true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            eng.dragTo(rawX - dragDX, rawY - dragDY);
            apply();
            wm.updateViewLayout(view, lp);
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            handler.removeCallbacks(this);
            eng.endDrag();
            step();
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        if (handler != null) handler.removeCallbacks(this);
        if (wm != null && view != null) {
            try {
                wm.removeView(view);
            } catch (RuntimeException ignored) {
                // view was never attached
            }
        }
        super.onDestroy();
    }
}
