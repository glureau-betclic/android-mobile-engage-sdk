package com.emarsys.mobileengage;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.emarsys.core.request.RequestManager;
import com.emarsys.core.request.model.RequestModel;
import com.emarsys.core.util.Assert;
import com.emarsys.core.util.TimestampUtils;
import com.emarsys.core.util.log.EMSLogger;
import com.emarsys.mobileengage.config.MobileEngageConfig;
import com.emarsys.mobileengage.event.applogin.AppLoginParameters;
import com.emarsys.mobileengage.experimental.MobileEngageExperimental;
import com.emarsys.mobileengage.experimental.MobileEngageFeature;
import com.emarsys.mobileengage.storage.MeIdStorage;
import com.emarsys.mobileengage.util.RequestUtils;
import com.emarsys.mobileengage.util.log.MobileEngageTopic;
import com.google.firebase.iid.FirebaseInstanceId;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.emarsys.mobileengage.endpoint.Endpoint.ME_LAST_MOBILE_ACTIVITY_V2;
import static com.emarsys.mobileengage.endpoint.Endpoint.ME_LOGIN_V2;
import static com.emarsys.mobileengage.endpoint.Endpoint.ME_LOGOUT_V2;

public class MobileEngageInternal {
    public static final String MOBILEENGAGE_SDK_VERSION = BuildConfig.VERSION_NAME;

    String pushToken;
    AppLoginParameters appLoginParameters;

    MobileEngageConfig config;
    RequestManager manager;
    MobileEngageCoreCompletionHandler coreCompletionHandler;
    Handler uiHandler;
    private final RequestContext requestContext;

    public MobileEngageInternal(
            MobileEngageConfig config,
            RequestManager manager,
            Handler uiHandler,
            MobileEngageCoreCompletionHandler coreCompletionHandler,
            RequestContext requestContext
    ) {
        Assert.notNull(config, "Config must not be null!");
        Assert.notNull(manager, "Manager must not be null!");
        Assert.notNull(requestContext, "RequestContext must not be null!");
        Assert.notNull(coreCompletionHandler, "CoreCompletionHandler must not be null!");
        EMSLogger.log(MobileEngageTopic.MOBILE_ENGAGE, "Arguments: config %s, manager %s, coreCompletionHandler %s", config, manager, coreCompletionHandler);

        this.config = config;
        this.manager = manager;
        this.requestContext = requestContext;
        this.uiHandler = uiHandler;
        this.coreCompletionHandler = coreCompletionHandler;
        try {
            this.pushToken = FirebaseInstanceId.getInstance().getToken();
        } catch (Exception ignore) {
        }
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

    public String appLogin() {
        EMSLogger.log(MobileEngageTopic.MOBILE_ENGAGE, "Called");

        RequestModel model;
        Map<String, Object> payload = injectLoginPayload(RequestUtils.createBasePayload(config, appLoginParameters));

        Integer storedHashCode = requestContext.getAppLoginStorage().get();
        int currentHashCode = payload.hashCode();

        Map<String, String> headers = RequestUtils.createBaseHeaders_V2(config);

        if (shouldDoAppLogin(storedHashCode, currentHashCode, requestContext.getMeIdStorage())) {
            model = new RequestModel.Builder()
                    .url(ME_LOGIN_V2)
                    .payload(payload)
                    .headers(headers)
                    .build();
            requestContext.getAppLoginStorage().set(currentHashCode);
        } else {
            model = new RequestModel.Builder()
                    .url(ME_LAST_MOBILE_ACTIVITY_V2)
                    .payload(RequestUtils.createBasePayload(config, appLoginParameters))
                    .headers(headers)
                    .build();
        }

        MobileEngageUtils.incrementIdlingResource();
        manager.submit(model);
        return model.getId();
    }

    public String appLogout() {
        EMSLogger.log(MobileEngageTopic.MOBILE_ENGAGE, "Called");

        RequestModel model = new RequestModel.Builder()
                .url(ME_LOGOUT_V2)
                .payload(RequestUtils.createBasePayload(config, appLoginParameters))
                .headers(RequestUtils.createBaseHeaders_V2(config))
                .build();

        MobileEngageUtils.incrementIdlingResource();
        manager.submit(model);
        requestContext.getMeIdStorage().remove();
        requestContext.getAppLoginStorage().remove();
        return model.getId();
    }

    public String trackCustomEvent(@NonNull String eventName,
                                   @Nullable Map<String, String> eventAttributes) {
        if (MobileEngageExperimental.isFeatureEnabled(MobileEngageFeature.IN_APP_MESSAGING)) {
            return trackCustomEvent_V3(eventName, eventAttributes);
        } else {
            return trackCustomEvent_V2(eventName, eventAttributes);
        }
    }

    String trackCustomEvent_V2(@NonNull String eventName,
                               @Nullable Map<String, String> eventAttributes) {
        EMSLogger.log(MobileEngageTopic.MOBILE_ENGAGE, "Arguments: eventName %s, eventAttributes %s", eventName, eventAttributes);

        Map<String, Object> payload = RequestUtils.createBasePayload(config, appLoginParameters);
        if (eventAttributes != null && !eventAttributes.isEmpty()) {
            payload.put("attributes", eventAttributes);
        }
        RequestModel model = new RequestModel.Builder()
                .url(RequestUtils.createEventUrl_V2(eventName))
                .payload(payload)
                .headers(RequestUtils.createBaseHeaders_V2(config))
                .build();

        MobileEngageUtils.incrementIdlingResource();
        manager.submit(model);
        return model.getId();
    }

    String trackCustomEvent_V3(@NonNull String eventName,
                               @Nullable Map<String, String> eventAttributes) {
        EMSLogger.log(MobileEngageTopic.MOBILE_ENGAGE, "Arguments: eventName %s, eventAttributes %s", eventName, eventAttributes);

        Map<String, Object> event = new HashMap<>();
        event.put("type", "custom");
        event.put("name", eventName);
        event.put("timestamp", TimestampUtils.formatTimestampWithUTC(requestContext.getTimestampProvider().provideTimestamp()));
        if (eventAttributes != null && !eventAttributes.isEmpty()) {
            event.put("attributes", eventAttributes);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("clicks", new ArrayList<>());
        payload.put("viewed_messages", new ArrayList<>());
        payload.put("events", Collections.singletonList(event));

        RequestModel model = new RequestModel.Builder()
                .url(RequestUtils.createEventUrl_V3(requestContext.getMeIdStorage().get()))
                .payload(payload)
                .headers(RequestUtils.createBaseHeaders_V3(
                        requestContext.getApplicationCode(),
                        requestContext.getMeIdStorage(),
                        requestContext.getMeIdSignatureStorage()))
                .build();

        MobileEngageUtils.incrementIdlingResource();
        manager.submit(model);
        return model.getId();
    }

    public String trackInternalCustomEvent(@NonNull String eventName,
                                           @Nullable Map<String, String> eventAttributes) {
        Assert.notNull(eventName, "EventName must not be null!");
        EMSLogger.log(MobileEngageTopic.MOBILE_ENGAGE, "Arguments: eventName %s, eventAttributes %s", eventName, eventAttributes);

        if (requestContext.getMeIdStorage().get() != null && requestContext.getMeIdSignatureStorage().get() != null) {
            RequestModel model = RequestUtils.createInternalCustomEvent(
                    eventName,
                    eventAttributes,
                    requestContext.getApplicationCode(),
                    requestContext.getMeIdStorage(),
                    requestContext.getMeIdSignatureStorage(),
                    requestContext.getTimestampProvider());

            MobileEngageUtils.incrementIdlingResource();
            manager.submit(model);
            return model.getId();
        } else {
            return RequestModel.nextId();
        }
    }

    public String trackMessageOpen(Intent intent) {
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
                    .url(RequestUtils.createEventUrl_V2("message_open"))
                    .payload(payload)
                    .headers(RequestUtils.createBaseHeaders_V2(config))
                    .build();

            MobileEngageUtils.incrementIdlingResource();
            manager.submit(model);
            return model.getId();
        } else {
            final String uuid = RequestModel.nextId();
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    coreCompletionHandler.onError(uuid, new IllegalArgumentException("No messageId found!"));
                }
            });
            return uuid;
        }
    }

    private boolean shouldDoAppLogin(Integer storedHashCode, int currentHashCode, MeIdStorage meIdStorage) {
        boolean result = storedHashCode == null || currentHashCode != storedHashCode;

        if (MobileEngageExperimental.isFeatureEnabled(MobileEngageFeature.IN_APP_MESSAGING)) {
            result = result || meIdStorage.get() == null;
        }

        return result;
    }

    private Map<String, Object> injectLoginPayload(Map<String, Object> payload) {
        payload.put("platform", requestContext.getDeviceInfo().getPlatform());
        payload.put("language", requestContext.getDeviceInfo().getLanguage());
        payload.put("timezone", requestContext.getDeviceInfo().getTimezone());
        payload.put("device_model", requestContext.getDeviceInfo().getModel());
        payload.put("application_version", requestContext.getDeviceInfo().getApplicationVersion());
        payload.put("os_version", requestContext.getDeviceInfo().getOsVersion());
        payload.put("ems_sdk", MOBILEENGAGE_SDK_VERSION);

        if (pushToken == null) {
            payload.put("push_token", false);
        } else {
            payload.put("push_token", pushToken);
        }

        return payload;
    }

}
