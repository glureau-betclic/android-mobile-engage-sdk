package com.emarsys.mobileengage;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.emarsys.core.CoreCompletionHandler;
import com.emarsys.core.concurrency.CoreSdkHandlerProvider;
import com.emarsys.core.connection.ConnectionWatchDog;
import com.emarsys.core.request.RequestManager;
import com.emarsys.core.request.model.RequestModel;
import com.emarsys.core.request.model.RequestModelRepository;
import com.emarsys.core.response.ResponseModel;
import com.emarsys.mobileengage.config.MobileEngageConfig;
import com.emarsys.mobileengage.event.applogin.AppLoginParameters;
import com.emarsys.mobileengage.responsehandler.AbstractResponseHandler;
import com.emarsys.mobileengage.storage.AppLoginStorage;
import com.emarsys.mobileengage.storage.MeIdStorage;
import com.emarsys.mobileengage.testUtil.TimeoutUtils;
import com.emarsys.mobileengage.util.MobileEngageIdlingResource;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

@RunWith(AndroidJUnit4.class)
public class MobileEngageInternalIdlingResourceTest {
    private MobileEngageInternal mobileEngage;
    private MobileEngageInternal mobileEngageWithRealRequestManager;
    private CoreCompletionHandler coreCompletionHandler;
    private MobileEngageIdlingResource idlingResource;

    @Rule
    public TestRule timeout = TimeoutUtils.getTimeoutRule();

    @Before
    public void init() throws Exception {
        Application application = (Application) InstrumentationRegistry.getTargetContext().getApplicationContext();

        MobileEngageConfig config = new MobileEngageConfig.Builder()
                .application(application)
                .credentials("user", "pass")
                .enableIdlingResource(true)
                .disableDefaultChannel()
                .build();

        mobileEngageWithRealRequestManager = mobileEngageWithRealRequestManager(config);

        coreCompletionHandler = mobileEngageWithRealRequestManager.coreCompletionHandler;

        mobileEngage = new MobileEngageInternal(config, mock(RequestManager.class), mock(AppLoginStorage.class), mock(MobileEngageCoreCompletionHandler.class));

        MobileEngageUtils.setup(config);
        idlingResource = mock(MobileEngageIdlingResource.class);
        Field idlingResourceField = MobileEngageUtils.class.getDeclaredField("idlingResource");
        idlingResourceField.setAccessible(true);
        idlingResourceField.set(null, idlingResource);
        new MeIdStorage(InstrumentationRegistry.getContext()).set("test_me_id");
    }

    @After
    public void tearDown() {
        new MeIdStorage(InstrumentationRegistry.getContext()).remove();
    }

    @Test
    public void testAppLogin_anonymous_callsIdlingResource() {
        mobileEngage.appLogin();

        verify(idlingResource, times(1)).increment();
    }

    @Test
    public void testAppLogin_callsIdlingResource() {
        mobileEngage.setAppLoginParameters(new AppLoginParameters(3, "test@test.com"));
        mobileEngage.appLogin();

        verify(idlingResource, times(1)).increment();
    }

    @Test
    public void testAppLogout_callsIdlingResource() {
        mobileEngage.appLogout();

        verify(idlingResource, times(1)).increment();
    }

    @Test
    public void testTrackCustomEvent_callsIdlingResource() {
        mobileEngage.trackCustomEvent("eventName", null);

        verify(idlingResource, times(1)).increment();
    }

    @Test
    public void testTrackCustomEvent_withAttributes_callsIdlingResource() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("key", "value");
        mobileEngage.trackCustomEvent("eventName", attributes);

        verify(idlingResource, times(1)).increment();
    }

    @Test
    public void testTrackMessageOpen_emptyIntent_doesNotCallIdlingResource(){
        mobileEngage.trackMessageOpen(new Intent());

        verifyZeroInteractions(idlingResource);
    }

    @Test
    public void testTrackMessageOpen_correctIntent_callsIdlingResource(){
        Intent intent = new Intent();
        Bundle bundlePayload = new Bundle();
        bundlePayload.putString("key1", "value1");
        bundlePayload.putString("u", "{\"sid\": \"+43c_lODSmXqCvdOz\"}");
        intent.putExtra("payload", bundlePayload);

        mobileEngage.trackMessageOpen(intent);

        verify(idlingResource, times(1)).increment();
    }

    @Test
    public void testCoreCompletionHandler_onSuccess_callsIdlingResource(){
        coreCompletionHandler.onSuccess(
                "id",
                new ResponseModel.Builder()
                        .statusCode(200)
                        .message("OK")
                        .requestModel(mock(RequestModel.class))
                        .build());

        verify(idlingResource, times(1)).decrement();
    }

    @Test
    public void testCoreCompletionHandler_onError_responseModel_callsIdlingResource(){
        coreCompletionHandler.onError(
                "id",
                new ResponseModel.Builder()
                        .statusCode(404)
                        .message("Not found")
                        .requestModel(mock(RequestModel.class))
                        .build());

        verify(idlingResource, times(1)).decrement();
    }

    @Test
    public void testCoreCompletionHandler_onError_exception_callsIdlingResource(){
        coreCompletionHandler.onError("id", new Exception("exception"));

        verify(idlingResource, times(1)).decrement();
    }

    private MobileEngageInternal mobileEngageWithRealRequestManager(MobileEngageConfig config) {
        MobileEngageCoreCompletionHandler completionHandler = new MobileEngageCoreCompletionHandler(new ArrayList<AbstractResponseHandler>(), new MobileEngageStatusListener() {
            @Override
            public void onError(String id, Exception cause) {

            }

            @Override
            public void onStatusLog(String id, String log) {

            }
        });
        Handler handler = new CoreSdkHandlerProvider().provideHandler();
        RequestManager manager = new RequestManager(handler, new ConnectionWatchDog(config.getApplication(), handler), new RequestModelRepository(config.getApplication()), completionHandler);
        return new MobileEngageInternal(config, manager, new AppLoginStorage(config.getApplication().getApplicationContext()), completionHandler);
    }

}