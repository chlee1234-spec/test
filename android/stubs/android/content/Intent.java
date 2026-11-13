package android.content;
import android.net.Uri;
public class Intent {
    public Intent() {}
    public Intent(String action) {}
    public Intent(String action, Uri uri) {}
    public Intent(Context packageContext, Class<?> cls) {}
    public Intent putExtra(String name, boolean value) { return this; }
    public boolean getBooleanExtra(String name, boolean defaultValue) { return defaultValue; }
}
