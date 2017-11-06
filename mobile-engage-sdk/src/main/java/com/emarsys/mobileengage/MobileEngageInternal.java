package com.emarsys.mobileengage;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.emarsys.core.CoreCompletionHandler;
import com.emarsys.core.DeviceInfo;
import com.emarsys.core.request.RequestManager;
import com.emarsys.core.request.RequestModel;
import com.emarsys.core.util.Assert;
import com.emarsys.core.util.log.EMSLogger;
import com.emarsys.mobileengage.config.MobileEngageConfig;
import com.emarsys.mobileengage.event.applogin.AppLoginParameters;
import com.emarsys.mobileengage.event.applogin.AppLoginStorage;
import com.emarsys.mobileengage.util.RequestUtils;
import com.emarsys.mobileengage.util.log.MobileEngageTopic;
import com.google.firebase.iid.FirebaseInstanceId;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

public class MobileEngageInternal {
    public static final String MOBILEENGAGE_SDK_VERSION = BuildConfig.VERSION_NAME;

    String pushToken;
    AppLoginParameters appLoginParameters;

    MobileEngageConfig config;
    DeviceInfo deviceInfo;
    Application application;
    RequestManager manager;
    AppLoginStorage appLoginStorage;
    Handler handler;
    CoreCompletionHandler coreCompletionHandler;

    MobileEngageInternal(MobileEngageConfig config, RequestManager manager, AppLoginStorage appLoginStorage, MobileEngageCoreCompletionHandler coreCompletionHandler) {
        Assert.notNull(config, "Config must not be null!");
        Assert.notNull(manager, "Manager must not be null!");
        Assert.notNull(coreCompletionHandler, "CoreCompletionHandler must not be null!");
        Assert.notNull(appLoginStorage, "AppLoginStorage must not be null!");
        EMSLogger.log(MobileEngageTopic.MOBILE_ENGAGE, "Arguments: config %s, manager %s, coreCompletionHandler %s", config, manager, coreCompletionHandler);

        this.config = config;
        this.application = config.getApplication();
        this.appLoginStorage = appLoginStorage;
        this.coreCompletionHandler = coreCompletionHandler;

        this.manager = manager;
        manager.setDefaultHeaders(RequestUtils.createDefaultHeaders(config));

        this.deviceInfo = new DeviceInfo(application.getApplicationContext());

        try {
            this.pushToken = FirebaseInstanceId.getInstance().getToken();
        } catch (Exception e) {
            //no token for you
        }

        this.handler = new Handler(Looper.getMainLooper());
    }

    RequestManager getManager() {
        return manager;
    }

    String getPushToken() {
        return pushToken;
    }

    void setPushToken(String pushToken) {
        EMSLogger.log(MobileEngageTopic.MOBILE_ENGAGE, "Argument: %s", pushToken);
        this.pushToken = pushToken;
        if (appLoginParameters != null) {
            appLogin();
        }
    }

    void setAppLoginParameters(AppLoginParameters parameters) {
        EMSLogger.log(MobileEngageTopic.MOBILE_ENGAGE, "Argument: %s", parameters);

        this.appLoginParameters = parameters;
    }

    String appLogin() {
        EMSLogger.log(MobileEngageTopic.MOBILE_ENGAGE, "Called");

        RequestModel model;
        Map<String, Object> payload = injectLoginPayload(RequestUtils.createBasePayload(config, appLoginParameters));

        Integer storedHashCode = appLoginStorage.getLastAppLoginPayloadHashCode();
        int currentHashCode = payload.hashCode();

        if (storedHashCode == null || currentHashCode != storedHashCode) {
            model = new RequestModel.Builder()
                    .url(RequestUtils.ENDPOINT_LOGIN)
                    .payload(payload)
                    .build();
            appLoginStorage.setLastAppLoginPayloadHashCode(currentHashCode);
        } else {
            model = new RequestModel.Builder()
                    .url(RequestUtils.ENDPOINT_LAST_MOBILE_ACTIVITY)
                    .payload(RequestUtils.createBasePayload(config, appLoginParameters))
                    .build();
        }

        MobileEngageUtils.incrementIdlingResource();
        manager.submit(model);
        return model.getId();
    }

    String appLogout() {
        EMSLogger.log(MobileEngageTopic.MOBILE_ENGAGE, "Called");

        RequestModel model = new RequestModel.Builder()
                .url(RequestUtils.ENDPOINT_LOGOUT)
                .payload(RequestUtils.createBasePayload(config, appLoginParameters))
                .build();

        MobileEngageUtils.incrementIdlingResource();
        manager.submit(model);
        return model.getId();
    }

    String trackCustomEvent(@NonNull String eventName,
                            @Nullable Map<String, String> eventAttributes) {
        EMSLogger.log(MobileEngageTopic.MOBILE_ENGAGE, "Arguments: eventName %s, eventAttributes %s", eventName, eventAttributes);

        Map<String, Object> payload = RequestUtils.createBasePayload(config, appLoginParameters);
        if (eventAttributes != null && !eventAttributes.isEmpty()) {
            payload.put("attributes", eventAttributes);
        }
        RequestModel model = new RequestModel.Builder()
                .url(RequestUtils.createEventUrl(eventName))
                .payload(payload)
                .build();

        MobileEngageUtils.incrementIdlingResource();
        manager.submit(model);
        return model.getId();
    }

    String trackMessageOpen(Intent intent) {
        EMSLogger.log(MobileEngageTopic.MOBILE_ENGAGE, "Argument: %s", intent);

        String messageId = getMessageId(intent);
        EMSLogger.log(MobileEngageTopic.MOBILE_ENGAGE, "MessageId %s", messageId);

        return handleMessageOpen(messageId);
    }

    String getMessageId(Intent intent) {
        String sid = null;
        Bundle payload = intent.getBundleExtra("payload");
        if (payload != null) {
            String customData = payload.getString("u");
            try {
                sid = new JSONObject(customData).getString("sid");
            } catch (JSONException e) {
            }
        }
        return sid;
    }

    private String handleMessageOpen(String messageId) {
        if (messageId != null) {
            Map<String, Object> payload = RequestUtils.createBasePayload(config, appLoginParameters);
            payload.put("sid", messageId);
            RequestModel model = new RequestModel.Builder()
                    .url(RequestUtils.createEventUrl("message_open"))
                    .payload(payload)
                    .build();

            MobileEngageUtils.incrementIdlingResource();
            manager.submit(model);
            return model.getId();
        } else {
            final String uuid = RequestModel.nextId();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    coreCompletionHandler.onError(uuid, new IllegalArgumentException("No messageId found!"));
                }
            });
            return uuid;
        }
    }

    private Map<String, Object> injectLoginPayload(Map<String, Object> payload) {
        payload.put("platform", deviceInfo.getPlatform());
        payload.put("language", deviceInfo.getLanguage());
        payload.put("timezone", deviceInfo.getTimezone());
        payload.put("device_model", deviceInfo.getModel());
        payload.put("application_version", deviceInfo.getApplicationVersion());
        payload.put("os_version", deviceInfo.getOsVersion());
        payload.put("ems_sdk", MOBILEENGAGE_SDK_VERSION);

        if (pushToken == null) {
            payload.put("push_token", false);
        } else {
            payload.put("push_token", pushToken);
        }

        return payload;
    }

}
