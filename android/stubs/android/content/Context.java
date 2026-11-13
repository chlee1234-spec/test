package android.content;
import android.content.res.AssetManager;
import android.content.res.Resources;
public abstract class Context {
    public static final String NOTIFICATION_SERVICE = "notification";
    public static final String WINDOW_SERVICE = "window";
    public Context() {}
    public Object getSystemService(String name) { return null; }
    public AssetManager getAssets() { return null; }
    public Resources getResources() { return null; }
    public String getPackageName() { return null; }
    public void startActivity(Intent intent) {}
    public ComponentName startService(Intent intent) { return null; }
    public ComponentName startForegroundService(Intent intent) { return null; }
    public boolean stopService(Intent intent) { return false; }
}
