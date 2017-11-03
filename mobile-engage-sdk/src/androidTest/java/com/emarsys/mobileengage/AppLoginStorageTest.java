package com.emarsys.mobileengage;

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AppLoginStorageTest {
    private AppLoginStorage storage;
    private Context context;

    @Before
    public void init() {
        context = InstrumentationRegistry.getTargetContext().getApplicationContext();
        storage = new AppLoginStorage(context);
        storage.clear();
    }

    @Test
    public void getLastAppLoginPayloadHashCode_shouldReturnNilIfTheStorageIsEmpty() throws Exception {
        assertNull(storage.getLastAppLoginPayloadHashCode());
    }

    @Test
    public void setLastAppLoginPayloadHashCode() throws Exception {
        storage.setLastAppLoginPayloadHashCode(42);
        assertEquals((Integer) 42, storage.getLastAppLoginPayloadHashCode());
    }

    @Test
    public void clear_shouldRemovelastAppLoginPayloadHashCodeValue() {
        storage.setLastAppLoginPayloadHashCode(42);
        storage.clear();

        assertNull(storage.getLastAppLoginPayloadHashCode());
    }


    @Test
    public void setLastAppLoginPayloadHashCodeShouldPreserveValues() throws Exception {
        storage.setLastAppLoginPayloadHashCode(42);
        storage = new AppLoginStorage(context);

        assertEquals((Integer) 42, storage.getLastAppLoginPayloadHashCode());
    }

}