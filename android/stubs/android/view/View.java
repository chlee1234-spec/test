package android.view;
import android.content.Context;
import android.graphics.Canvas;
public class View {
    public interface OnClickListener { void onClick(View v); }
    public View(Context context) {}
    protected void onDraw(Canvas canvas) {}
    public boolean onTouchEvent(MotionEvent event) { return false; }
    public void invalidate() {}
    public int getWidth() { return 0; }
    public int getHeight() { return 0; }
    public void setPadding(int left, int top, int right, int bottom) {}
    public void setOnClickListener(OnClickListener l) {}
}
