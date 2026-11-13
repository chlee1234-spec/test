package android.provider;
import android.content.Context;
public final class Settings {
    public static final String ACTION_MANAGE_OVERLAY_PERMISSION = "android.settings.action.MANAGE_OVERLAY_PERMISSION";
    public static boolean canDrawOverlays(Context context) { return false; }
}
