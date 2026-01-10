package mgks.os.swv;

import mgks.os.swv.R; // BARIS INI WAJIB ADA UNTUK MEMPERBAIKI ERROR

/*
  Smart WebView v8
  https://github.com/mgks/Android-SmartWebView
*/

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class Firebase extends FirebaseMessagingService {

    private final String fcm_channel = SWVContext.asw_fcm_channel;

    @Override
    public void onNewToken(String s) {
        super.onNewToken(s);
        Log.d("Firebase", "onNewToken() called");
        if (!s.isEmpty()) {
            SWVContext.fcm_token = s;
            Log.d("TOKEN_REFRESHED", s);
        } else {
            Log.d("TOKEN_REFRESHED", "NULL >> FAILED");
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        if (message.getNotification() != null) {
            String title = message.getNotification().getTitle();
            String body = message.getNotification().getBody();
            String uri = message.getData().get("uri");
            String click_action = message.getNotification().getClickAction();

            if (uri == null) {
                uri = SWVContext.ASWV_URL;
            }
            if (click_action == null) {
                click_action = "OPEN_URI";
            }

            Log.d("FCM_MESSAGE", "Title: " + title + ", Body: " + body + ", URI: " + uri + ", Click Action: " + click_action);

            sendMyNotification(title, body, click_action, uri, message.getData().get("tag"), message.getData().get("nid"), this);
        }
    }

    public void sendMyNotification(String title, String message, String click_action, String uri, String tag, String nid, Context context) {
        Intent intent;
        if (uri == null || uri.isEmpty() || uri.startsWith("file://")) {
            intent = new Intent(context, MainActivity.class);
        } else {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        }
        intent.setAction(click_action);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent;
        pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int notification_id = nid != null ? Integer.parseInt(nid) : SWVContext.ASWV_FCM_ID;

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, fcm_channel)
            .setSmallIcon(R.mipmap.ic_launcher) // Sekarang baris ini tidak akan error lagi
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(fcm_channel, "SWV Channel", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(notification_id, notificationBuilder.build());
    }
}