/*
 * https://evgenii.com/blog/testing-activity-in-android-studio-tutorial-part-3/
 * https://en.paradigmadigital.com/dev/android-testing-how-to-perform-instrumented-tests/
 * https://stackoverflow.com/questions/51851653/recyclerview-espresso-testing-fails-due-to-runtimeexception
 */
package com.krisstof.imagelinearizer;

import androidx.test.rule.ActivityTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ImageLinearizerInstrumentedTest {
    private static final String TAG = ImageLinearizerInstrumentedTest.class.getSimpleName();

    @Rule
    public ActivityTestRule<MainActivity> mActivityTestRule = new ActivityTestRule<>(
        MainActivity.class, true, true);

    @Ignore("Not implemented yet")
    @Test
    public void testLoadingMinSizedImage() {
        // TODO
    }

    @Ignore("Not implemented yet")
    @Test
    public void testLoadingMaxSizedImage() {
        // TODO
    }

    @Ignore("Not implemented yet")
    @Test
    public void testLoadingTooWideImage() {
        // TODO
    }

    @Ignore("Not implemented yet")
    @Test
    public void testLoadingTooHighImage() {
        // TODO
    }

    @Ignore("Not implemented yet")
    @Test
    public void testLoadingBmp() {
        // TODO also compare with GT
        // (mActivityTestRule.getActivity().getDstImage());
    }

    @Ignore("Not implemented yet")
    @Test
    public void testLoadingJpg() {
        // TODO
    }

    @Ignore("Not implemented yet")
    @Test
    public void testLoadingJpeg() {
        // TODO
    }

    @Ignore("Not implemented yet")
    @Test
    public void testLoadingPng() {
        // TODO
    }

    @Ignore("Not implemented yet")
    @Test
    public void testLinearizingNonCenterPrincipalPointImage() {
        // TODO
    }

    @Ignore("Not implemented yet")
    @Test
    public void testDstImageWidthInput() {
        // TODO
    }

    @Ignore("Not implemented yet")
    @Test
    public void testDstImageHeightInput() {
        // TODO
    }

    @Test
    public void testEnforcingMaxDstImageWidth() {
        final DstCamParameters dstCamParametersBefore =
            mActivityTestRule.getActivity().getDstCamParameters().clone();
        onView(withId(R.id.editTextDstImageWidthPx)).perform(scrollTo());
        onView(withId(R.id.editTextDstImageWidthPx)).perform(typeText("11111"));
        final DstCamParameters dstCamParametersAfter =
            mActivityTestRule.getActivity().getDstCamParameters();
        assertEquals(dstCamParametersBefore.imageWidthPx, dstCamParametersAfter.imageWidthPx);
    }

    @Test
    public void testEnforcingMaxDstImageHeight() {
        final DstCamParameters dstCamParametersBefore =
            mActivityTestRule.getActivity().getDstCamParameters().clone();
        onView(withId(R.id.editTextDstImageHeightPx)).perform(scrollTo());
        onView(withId(R.id.editTextDstImageHeightPx)).perform(typeText("11111"));
        final DstCamParameters dstCamParametersAfter =
            mActivityTestRule.getActivity().getDstCamParameters();
        assertEquals(dstCamParametersBefore.imageHeightPx, dstCamParametersAfter.imageHeightPx);
    }

    @Ignore("Not implemented yet")
    @Test
    public void testTransformingImage() {
        // TODO with all non-default src and dst cam pars
    }

    @Ignore("Not implemented yet")
    @Test
    public void testSavedImage() {
        // TODO if appears in Gallery
        // TODO if appears as latest in Gallery
        // TODO compare with in-memory image
    }

    @Ignore("Not implemented yet")
    @Test
    public void testAdvancedCamParWidgetsVisibilityAfter() {
        // TODO pp x, y, roll, pitch, yaw
        // TODO for after start up, on portrait -> landscape, on landscape -> portrait
    }

    @Ignore("Not implemented yet")
    @Test
    public void testGridOverlayVisibility() {
        // TODO for after start up, on portrait -> landscape, on landscape -> portrait
    }

    @Ignore("Not implemented yet")
    @Test
    public void testHelpPanelVisibility() {
        // TODO for after start up, on portrait -> landscape, on landscape -> portrait
    }

    @Ignore("Not implemented yet")
    @Test
    public void testAppUpdateProcess() {
        // TODO
    }

    @Ignore("Not implemented yet")
    @Test
    public void testStayingAliveOverManySubsequentDeviceOrientationChanges() {
        // TODO
    }

    @Ignore("Not implemented yet")
    @Test
    public void testStayingAliveOverContinuouslyPullingSeekBar() {
        // TODO
    }
}
