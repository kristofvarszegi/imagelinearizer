/*
 * TODO
 * - Check jpg, jpeg, png, bmp
 * - Check small image
 * - Check max sized image
 * - Check too wide image
 * - Check too high image
 * - Check pp-offsetted image
 * - Check dst image size X field
 * - Check dst image size Y field
 * - Check 3 warped images against GT output image
 * - Check loaded images against GT output image
 * - Check if saved image appears as latest in Gallery
 * - Check saved image against GT output image
 * - Check enforcement of max output image size field limits
 * - Check UI elements' visibility according to release config
 *
 */
package com.krisstof.imagelinearizer;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ImageLinearizerInstrumentedTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertEquals("com.krisstof.imagelinearizer", appContext.getPackageName());
    }
}
