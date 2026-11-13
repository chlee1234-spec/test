package android.view;
import android.content.Context;
public abstract class ViewGroup extends View {
    public ViewGroup(Context context) { super(context); }
    public void addView(View child) {}
    public static class LayoutParams {
        public int width;
        public int height;
        public LayoutParams(int width, int height) {}
    }
}
