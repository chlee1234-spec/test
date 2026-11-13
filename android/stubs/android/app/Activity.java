package android.app;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.View;
public class Activity extends ContextThemeWrapper {
    public Activity() {}
    protected void onCreate(Bundle savedInstanceState) {}
    protected void onResume() {}
    public void setContentView(View view) {}
    public void finish() {}
    public Intent getIntent() { return null; }
}
