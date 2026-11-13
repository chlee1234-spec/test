package com.petforge.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Launcher entry: asks for the overlay permission once, then starts the pet and closes. */
public class MainActivity extends Activity implements View.OnClickListener {
    private boolean stopping;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = getIntent();
        if (i != null && i.getBooleanExtra("stop", false)) {
            stopping = true;
            stopService(new Intent(this, PetService.class));
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (stopping) return;
        if (Settings.canDrawOverlays(this)) {
            startForegroundService(new Intent(this, PetService.class));
            finish();
        } else {
            showPermissionScreen();
        }
    }

    private void showPermissionScreen() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 96, 48, 48);
        TextView text = new TextView(this);
        text.setText("To show the pet on top of other apps, allow \"Display over other apps\" / "
                + "請允許「顯示在其他應用程式上層」。");
        box.addView(text);
        Button b = new Button(this);
        b.setText("Open settings / 開啟設定");
        b.setOnClickListener(this);
        box.addView(b);
        setContentView(box);
    }

    @Override
    public void onClick(View v) {
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())));
    }
}
