package com.snap.codec.exerciser

import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import androidx.test.ext.junit.rules.activityScenarioRule
import org.hamcrest.CoreMatchers.endsWith
import org.hamcrest.Matchers


@RunWith(AndroidJUnit4::class)
class StressTest {

    /**
     * Use [ActivityScenarioRule] to create and launch the activity under test before each test,
     * and close it after each test. This is a replacement for
     * [androidx.test.rule.ActivityTestRule].
     */
    @get:Rule var activityScenarioRule = activityScenarioRule<MainActivity>()

    @Test
    fun surfaceShufflingStressTest() {
        Espresso.onView(ViewMatchers.withText(R.string.title_activity_play_movie_surface)).perform(
            ViewActions.click())

        Espresso.onView(ViewMatchers.withId(R.id.play_stop_button)).perform(ViewActions.click())

        for ( i in 0 until 1000) {
            Espresso.onView(ViewMatchers.withId(R.id.shuffle_surface_button))
                .perform(ViewActions.click())
        }
    }

    /**
     * This test plays two videos with different resolutions using the same player.
     * There is no codec reuse, but dummy surface is used to perform initial cofiguration for
     * the MediaCodec
     */
    @Test
    fun exoPlayerSimulationStressTest() {
        Espresso.onView(ViewMatchers.withText(R.string.title_exoplayer_simulation_test)).perform(
            ViewActions.click())

        for ( i in 0 until 200) {
            playMovieScenario("gen-eight-rects.mp4")
            playMovieScenario("gen-sliders.mp4")
        }
    }

    @Test
    fun startExoPlayerStressTest() {
        Espresso.onView(ViewMatchers.withText(R.string.title_exoplayer_test)).perform(
            ViewActions.click())

        for ( i in 0 until 200) {
            playMovieScenario("gen-eight-rects.mp4")
            playMovieScenario("gen-sliders.mp4")
        }
    }

    private fun playMovieScenario(name: String) {
        Espresso.onView(ViewMatchers.withId(R.id.playMovieFile_spinner))
            .perform(ViewActions.click())
        Espresso.onView(Matchers.allOf(
            ViewMatchers.withText(name),
            ViewMatchers.withClassName(endsWith("TextView"))
        )).perform(ViewActions.click())

        // start video
        Espresso.onView(ViewMatchers.withId(R.id.play_stop_button))
            .perform(ViewActions.click())

        // stop video with delay
        //idle(100) {
            Espresso.onView(ViewMatchers.withId(R.id.play_stop_button))
                .perform(ViewActions.click())
        //}
    }
}