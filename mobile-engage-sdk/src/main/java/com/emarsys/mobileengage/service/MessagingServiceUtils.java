package com.emarsys.mobileengage.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Action;
import android.support.v4.content.ContextCompat;

import com.emarsys.core.resource.MetaDataReader;
import com.emarsys.core.util.Assert;
import com.emarsys.core.util.ImageUtils;
import com.emarsys.mobileengage.config.OreoConfig;
import com.emarsys.mobileengage.experimental.MobileEngageExperimental;
import com.emarsys.mobileengage.experimental.MobileEngageFeature;
import com.emarsys.mobileengage.inbox.InboxParseUtils;
import com.emarsys.mobileengage.inbox.model.NotificationCache;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

public class MessagingServiceUtils {

    public static final String MESSAGE_FILTER = "ems_msg";
    public static final String METADATA_SMALL_NOTIFICATION_ICON_KEY = "com.emarsys.mobileengage.small_notification_icon";
    public static final String METADATA_NOTIFICATION_COLOR = "com.emarsys.mobileengage.notification_color";
    public static final int DEFAULT_SMALL_NOTIFICATION_ICON = com.emarsys.mobileengage.R.drawable.default_small_notification_icon;

    static NotificationCache notificationCache = new NotificationCache();

    public static boolean isMobileEngageMessage(Map<String, String> remoteMessageData) {
        return remoteMessageData != null && remoteMessageData.size() > 0 && remoteMessageData.containsKey(MESSAGE_FILTER);
    }

    static void dismissNotification(Context context, Intent intent) {
        Assert.notNull(context, "Context must not be null!");
        Assert.notNull(intent, "Intent must not be null!");

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Bundle bundle = intent.getBundleExtra("payload");
        if (bundle != null) {
            int notificationId = bundle.getInt("notification_id", Integer.MIN_VALUE);
            if (notificationId != Integer.MIN_VALUE) {
                manager.cancel(notificationId);
            }
        }
    }

    public static Notification createNotification(
            int notificationId,
            Context context,
            Map<String, String> remoteMessageData,
            OreoConfig oreoConfig,
            MetaDataReader metaDataReader) {

        int smallIconResourceId = metaDataReader.getInt(context, METADATA_SMALL_NOTIFICATION_ICON_KEY, DEFAULT_SMALL_NOTIFICATION_ICON);
        int colorResourceId = metaDataReader.getInt(context, METADATA_NOTIFICATION_COLOR);
        Bitmap image = ImageUtils.loadOptimizedBitmap(context, remoteMessageData.get("image_url"));
        String title = getTitle(remoteMessageData, context);
        String body = remoteMessageData.get("body");
        String channelId = getChannelId(remoteMessageData, oreoConfig);
        List<Action> actions = NotificationActionUtils.createActions(context, remoteMessageData, notificationId);

        if (OreoConfig.DEFAULT_CHANNEL_ID.equals(channelId)) {
            createDefaultChannel(context, oreoConfig);
        }

        PendingIntent resultPendingIntent = IntentUtils.createTrackMessageOpenServicePendingIntent(context, remoteMessageData, notificationId);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(smallIconResourceId)
                .setAutoCancel(false)
                .setContentIntent(resultPendingIntent);

        for (int i = 0; i < actions.size(); i++) {
            builder.addAction(actions.get(i));
        }

        if (colorResourceId != 0) {
            builder.setColor(ContextCompat.getColor(context, colorResourceId));
        }

        styleNotification(builder, title, body, image);

        return builder.build();
    }

    private static void styleNotification(NotificationCompat.Builder builder, String title, String body, Bitmap bitmap) {
        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
                    .setStyle(new NotificationCompat
                            .BigPictureStyle()
                            .bigPicture(bitmap)
                            .bigLargeIcon(null)
                            .setBigContentTitle(title)
                            .setSummaryText(body));
        } else {
            builder.setStyle(new NotificationCompat
                    .BigTextStyle()
                    .bigText(body)
                    .setBigContentTitle(title));
        }
    }

    static String getTitle(Map<String, String> remoteMessageData, Context context) {
        String title = remoteMessageData.get("title");
        if (title == null || title.isEmpty()) {
            title = getDefaultTitle(remoteMessageData, context);
        }
        return title;
    }

    static String getChannelId(Map<String, String> remoteMessageData, OreoConfig oreoConfig) {
        String result = remoteMessageData.get("channel_id");
        if (result == null && oreoConfig.isDefaultChannelEnabled()) {
            result = OreoConfig.DEFAULT_CHANNEL_ID;
        }
        return result;
    }

    static void createDefaultChannel(Context context, OreoConfig oreoConfig) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(OreoConfig.DEFAULT_CHANNEL_ID, oreoConfig.getDefaultChannelName(), NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(oreoConfig.getDefaultChannelDescription());
            notificationManager.createNotificationChannel(channel);
        }
    }

    private static String getDefaultTitle(Map<String, String> remoteMessageData, Context context) {
        String title = "";
        if (Build.VERSION.SDK_INT < 23) {
            ApplicationInfo applicationInfo = context.getApplicationInfo();
            int stringId = applicationInfo.labelRes;
            title = stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);

            try {
                String u = remoteMessageData.get("u");
                if (u != null) {
                    JSONObject customData = new JSONObject(u);
                    title = customData.getString("ems_default_title");
                }
            } catch (JSONException ignored) {
            }
        }
        return title;
    }

    static void cacheNotification(Map<String, String> remoteMessageData) {
        Assert.notNull(remoteMessageData, "RemoteMessageData must not be null!");
        boolean isUserCentric = MobileEngageExperimental.isFeatureEnabled(MobileEngageFeature.USER_CENTRIC_INBOX);
        notificationCache.cache(InboxParseUtils.parseNotificationFromPushMessage(remoteMessageData, isUserCentric));
    }

}
