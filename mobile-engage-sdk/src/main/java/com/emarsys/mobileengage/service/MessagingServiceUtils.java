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
import com.emarsys.core.validate.JsonObjectValidator;
import com.emarsys.mobileengage.config.OreoConfig;
import com.emarsys.mobileengage.experimental.MobileEngageExperimental;
import com.emarsys.mobileengage.experimental.MobileEngageFeature;
import com.emarsys.mobileengage.inbox.InboxParseUtils;
import com.emarsys.mobileengage.inbox.model.NotificationCache;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
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

    public static Notification createNotification(
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
        List<Action> actions = createActions(context, remoteMessageData);

        if (OreoConfig.DEFAULT_CHANNEL_ID.equals(channelId)) {
            createDefaultChannel(context, oreoConfig);
        }

        PendingIntent resultPendingIntent = createPendingIntent(context, remoteMessageData);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(smallIconResourceId)
                .setAutoCancel(true)
                .setContentIntent(resultPendingIntent);

        int usableActionCount = actions.size() > 3 ? 3 : actions.size();

        for (int i = 0; i < usableActionCount; i++) {
            builder.addAction(actions.get(i));
        }

        if (colorResourceId != 0) {
            builder.setColor(ContextCompat.getColor(context, colorResourceId));
        }

        styleNotification(builder, title, body, image);

        return builder.build();
    }

    private static List<Action> createActions(Context context, Map<String, String> remoteMessageData) {
        List<Action> result = new ArrayList<>();
        String actionsString = remoteMessageData.get("actions");
        if (actionsString != null) {
            try {
                JSONObject actions = new JSONObject(actionsString);
                Iterator<String> iterator = actions.keys();
                while (iterator.hasNext()) {
                    Action action = createAction(
                            iterator.next(),
                            actions,
                            context,
                            remoteMessageData);
                    if (action != null) {
                        result.add(action);
                    }
                }
            } catch (JSONException ignored) {
            }
        }
        return result;
    }

    private static Action createAction(
            String uniqueId,
            JSONObject actions,
            Context context,
            Map<String, String> remoteMessageData) {
        Action result = null;

        try {
            JSONObject action = actions.getJSONObject(uniqueId);
            String actionType = action.getString("type");
            Action notificationAction = new Action.Builder(
                    0,
                    action.getString("title"),
                    createPendingIntent(context, remoteMessageData, uniqueId)).build();

            List<String> errors = new ArrayList<>();
            if ("MEAppEvent".equals(actionType)) {
                errors = JsonObjectValidator.from(action)
                        .hasField("name")
                        .validate();
            }

            if (errors.isEmpty()) {
                result = notificationAction;
            }

        } catch (JSONException ignored) {
        }

        return result;
    }

    private static PendingIntent createPendingIntent(Context context, Map<String, String> remoteMessageData) {
        return createPendingIntent(context, remoteMessageData, null);
    }

    private static PendingIntent createPendingIntent(Context context, Map<String, String> remoteMessageData, String action) {
        Intent intent = createIntent(remoteMessageData, context, action);
        return PendingIntent.getService(
                context,
                (int) (System.currentTimeMillis() % Integer.MAX_VALUE),
                intent,
                0);
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

    static Intent createIntent(Map<String, String> remoteMessageData, Context context, String action) {
        Intent intent = new Intent(context, TrackMessageOpenService.class);

        if (action != null) {
            intent.setAction(action);
        }

        Bundle bundle = new Bundle();
        for (Map.Entry<String, String> entry : remoteMessageData.entrySet()) {
            bundle.putString(entry.getKey(), entry.getValue());
        }

        intent.putExtra("payload", bundle);
        return intent;
    }

    static void cacheNotification(Map<String, String> remoteMessageData) {
        Assert.notNull(remoteMessageData, "RemoteMessageData must not be null!");
        boolean isUserCentric = MobileEngageExperimental.isFeatureEnabled(MobileEngageFeature.USER_CENTRIC_INBOX);
        notificationCache.cache(InboxParseUtils.parseNotificationFromPushMessage(remoteMessageData, isUserCentric));
    }

}
