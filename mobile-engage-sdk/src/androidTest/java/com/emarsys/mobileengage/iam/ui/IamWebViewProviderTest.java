package com.emarsys.mobileengage.iam.ui;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.emarsys.mobileengage.MobileEngage;
import com.emarsys.mobileengage.config.MobileEngageConfig;
import com.emarsys.mobileengage.fake.FakeMessageLoadedListener;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TestJSInterface {

    @JavascriptInterface
    public void onPageLoaded(String json) {
    }
}

public class IamWebViewProviderTest {
    public static final String BASIC_HTML = "<html><head></head><body>webview content</body></html>";

    private IamWebViewProvider provider;
    private MessageLoadedListener listener;
    private Handler handler;
    private CountDownLatch latch;
    private Object dummyJsBridge;

    String html = String.format("<!DOCTYPE html>\n" +
            "<html lang=\"en\">\n" +
            "  <head>\n" +
            "    <script>\n" +
            "      window.onload = function() {\n" +
            "      };\n" +
            "        Android.%s(\"{success:true}\");\n" +
            "    </script>\n" +
            "  </head>\n" +
            "  <body style=\"background: transparent;\">\n" +
            "  </body>\n" +
            "</html>", "onPageLoaded");

    @Rule
    public Timeout globalTimeout = Timeout.seconds(30);

    @Before
    public void init() throws NoSuchFieldException, IllegalAccessException {
        injectMobileEngageConfig();
        IamWebViewProvider.webView = null;

        provider = new IamWebViewProvider();
        listener = mock(MessageLoadedListener.class);

        handler = new Handler(Looper.getMainLooper());
        latch = new CountDownLatch(1);
        dummyJsBridge = new Object();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLoadMessageAsync_htmlShouldNotBeNull() {
        provider.loadMessageAsync(null, listener, dummyJsBridge);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLoadMessageAsync_listenerShouldNotBeNull() {
        provider.loadMessageAsync(BASIC_HTML, null, dummyJsBridge);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLoadMessageAsync_jsBridgeShouldNotBeNull() {
        provider.loadMessageAsync(BASIC_HTML, listener, null);
    }

    @Test
    public void testLoadMessageAsync_shouldInvokeJsBridge_whenPageIsLoaded() throws InterruptedException {
        TestJSInterface jsInterface = mock(TestJSInterface.class);
        provider.loadMessageAsync(html, new FakeMessageLoadedListener(latch), jsInterface);
        latch.await();
        verify(jsInterface).onPageLoaded("{success:true}");
    }

    @Test
    public void testProvideWebView_shouldReturnTheStaticInstance() throws InterruptedException {
        handler.post(new Runnable() {
            @Override
            public void run() {
                IamWebViewProvider.webView = new WebView(InstrumentationRegistry.getContext());
                latch.countDown();
            }
        });

        latch.await();

        assertEquals(IamWebViewProvider.webView, provider.provideWebView());
    }

    private void injectMobileEngageConfig() throws NoSuchFieldException, IllegalAccessException {
        MobileEngageConfig config = new MobileEngageConfig.Builder()
                .application((Application) InstrumentationRegistry.getContext().getApplicationContext())
                .credentials("code", "pwd")
                .disableDefaultChannel()
                .build();

        Field configField = MobileEngage.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(null, config);
    }
}