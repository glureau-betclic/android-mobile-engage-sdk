package com.emarsys.mobileengage.notification.command;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.support.test.InstrumentationRegistry;

import com.emarsys.mobileengage.EventHandler;
import com.emarsys.mobileengage.MobileEngage;
import com.emarsys.mobileengage.config.MobileEngageConfig;
import com.emarsys.mobileengage.di.DependencyInjection;
import com.emarsys.mobileengage.testUtil.ReflectionTestUtils;
import com.emarsys.mobileengage.testUtil.TimeoutUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class AppEventCommandTest {

    @Rule
    public TestRule timeout = TimeoutUtils.getTimeoutRule();

    private MobileEngageConfig config;
    private Context applicationContext = InstrumentationRegistry.getTargetContext().getApplicationContext();
    private EventHandler notificationHandler;

    @Before
    public void setUp() {
        DependencyInjection.tearDown();

        notificationHandler = mock(EventHandler.class);
        config = new MobileEngageConfig.Builder()
                .application((Application) applicationContext)
                .credentials("EMSEC-B103E", "RM1ZSuX8mgRBhQIgOsf6m8bn/bMQLAIb")
                .setNotificationEventHandler(notificationHandler)
                .disableDefaultChannel()
                .build();
        MobileEngage.setup(config);
    }

    @After
    public void tearDown() throws Exception {
        DependencyInjection.tearDown();
        Handler coreSdkHandler = ReflectionTestUtils.getStaticField(MobileEngage.class, "coreSdkHandler");
        coreSdkHandler.getLooper().quit();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_shouldThrowException_whenThereIsNoEventName() {
        new AppEventCommand(null, mock(JSONObject.class));
    }

    @Test
    public void testRun_invokeHandleEventMethod_onNotificationEventHandler() throws JSONException {
        String name = "nameOfTheEvent";
        JSONObject payload = new JSONObject()
                .put("payloadKey", "payloadValue");
        new AppEventCommand(name, payload).run();

        verify(notificationHandler).handleEvent(name, payload);
    }

    @Test
    public void testRun_invokeHandleEventMethod_onNotificationEventHandler_whenThereIsNoPayload() throws JSONException {
        String name = "nameOfTheEvent";
        new AppEventCommand(name, null).run();

        verify(notificationHandler).handleEvent(name, null);
    }
}