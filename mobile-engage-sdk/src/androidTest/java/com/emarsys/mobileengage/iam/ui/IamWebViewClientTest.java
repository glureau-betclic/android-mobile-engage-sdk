package com.emarsys.mobileengage.iam.ui;

import android.os.Handler;
import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.webkit.WebView;

import com.emarsys.mobileengage.fake.FakeMessageLoadedListener;
import com.emarsys.mobileengage.testUtil.TimeoutUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.concurrent.CountDownLatch;

import static junit.framework.Assert.assertEquals;

public class IamWebViewClientTest {

    private CountDownLatch latch;
    private Handler handler;

    @Rule
    public TestRule timeout = TimeoutUtils.getTimeoutRule();

    @Before
    public void init() {
        latch = new CountDownLatch(1);
        handler = new Handler(Looper.getMainLooper());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_shouldNotAcceptNull() {
        new IamWebViewClient(null);
    }

    @Test
    public void testOnPageFinished_shouldCallListener() throws InterruptedException {
        FakeMessageLoadedListener listener = new FakeMessageLoadedListener(latch);
        final IamWebViewClient client = new IamWebViewClient(listener);

        handler.post(new Runnable() {
            @Override
            public void run() {
                client.onPageFinished(new WebView(InstrumentationRegistry.getContext().getApplicationContext()), "");
            }
        });

        latch.await();
        assertEquals(1, listener.invocationCount);
    }

    @Test
    public void testOnPageFinished_shouldCallListener_shouldCallOnMainThread() throws InterruptedException {
        FakeMessageLoadedListener listener = new FakeMessageLoadedListener(latch, FakeMessageLoadedListener.Mode.MAIN_THREAD);
        final IamWebViewClient client = new IamWebViewClient(listener);

        handler.post(new Runnable() {
            @Override
            public void run() {
                final WebView webView = new WebView(InstrumentationRegistry.getContext().getApplicationContext());

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        client.onPageFinished(webView, "");
                    }
                }).start();
            }
        });

        latch.await();
        assertEquals(1, listener.invocationCount);
    }

}