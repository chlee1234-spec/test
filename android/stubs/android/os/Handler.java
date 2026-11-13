package android.os;
public class Handler {
    public Handler(Looper looper) {}
    public final boolean postDelayed(Runnable r, long delayMillis) { return true; }
    public final void removeCallbacks(Runnable r) {}
}
