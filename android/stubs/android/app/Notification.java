package android.app;
import android.content.Context;
import android.graphics.drawable.Icon;
public class Notification {
    public static class Builder {
        public Builder(Context context, String channelId) {}
        public Builder setContentTitle(CharSequence title) { return this; }
        public Builder setContentText(CharSequence text) { return this; }
        public Builder setSmallIcon(Icon icon) { return this; }
        public Builder setContentIntent(PendingIntent intent) { return this; }
        public Builder setOngoing(boolean ongoing) { return this; }
        public Notification build() { return null; }
    }
}
