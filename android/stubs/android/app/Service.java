package android.app;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.IBinder;
public abstract class Service extends ContextWrapper {
    public static final int START_STICKY = 1;
    public Service() { super(null); }
    public abstract IBinder onBind(Intent intent);
    public void onCreate() {}
    public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    public void onDestroy() {}
    public final void startForeground(int id, Notification notification) {}
    public final void stopSelf() {}
}
