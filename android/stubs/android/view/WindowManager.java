package android.view;
public interface WindowManager {
    void addView(View view, ViewGroup.LayoutParams params);
    void updateViewLayout(View view, ViewGroup.LayoutParams params);
    void removeView(View view);
    Display getDefaultDisplay();
    class LayoutParams extends ViewGroup.LayoutParams {
        public static final int TYPE_APPLICATION_OVERLAY = 2038;
        public static final int FLAG_NOT_FOCUSABLE = 8;
        public static final int FLAG_NOT_TOUCH_MODAL = 32;
        public static final int FLAG_LAYOUT_IN_SCREEN = 256;
        public static final int FLAG_LAYOUT_NO_LIMITS = 512;
        public int x;
        public int y;
        public int gravity;
        public LayoutParams(int w, int h, int type, int flags, int format) { super(w, h); }
    }
}
