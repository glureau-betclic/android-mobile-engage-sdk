package com.emarsys.mobileengage.responsehandler;

import com.emarsys.core.response.ResponseModel;
import com.emarsys.mobileengage.MobileEngageInternal;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class MeIdResponseHandlerTest {

    private String meId;
    private MeIdResponseHandler handler;
    private MobileEngageInternal internal;

    @Rule
    public Timeout globalTimeout = Timeout.seconds(30);
    private ResponseModel responseModelWithMeId;

    @Before
    public void init() {
        meId = "123";
        internal = mock(MobileEngageInternal.class);
        handler = new MeIdResponseHandler(internal);

        responseModelWithMeId = new ResponseModel.Builder()
                .statusCode(200)
                .message("OK")
                .body(String.format("{ 'api_me_id': '%s' }", meId))
                .build();
    }

    @Test
    public void testConstructor_initializesField() {
        internal = mock(MobileEngageInternal.class);
        handler = new MeIdResponseHandler(internal);

        assertEquals(internal, handler.mobileEngageInternal);
    }

    @Test
    public void testShouldHandleResponse_shouldReturnTrue_whenResponseBodyIncludesMeId() {
        boolean result = handler.shouldHandleResponse(responseModelWithMeId);

        Assert.assertTrue(result);
    }

    @Test
    public void testShouldHandleResponse_shouldReturnTrue_whenResponseBodyLacksMeId() {
        ResponseModel responseModel = new ResponseModel.Builder()
                .statusCode(200)
                .message("OK")
                .body("{ 'yolo': '123' }")
                .build();

        boolean result = handler.shouldHandleResponse(responseModel);

        Assert.assertFalse(result);
    }

    @Test
    public void testHandleResponse_shouldSetMeIdInMobileEngageInternal() {
        handler.handleResponse(responseModelWithMeId);

        verify(internal).setMeId(meId);
    }

}