package com.avetero.relay.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code Relay runs on the paths people use most (start, For you, Live, Guide, Library, a title), so
 * Android compiles it ahead of time. Signs in first when the app opens on the sign-in screen.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun generate() = rule.collect("com.avetero.relay", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        if (device.wait(Until.hasObject(By.text("Sign in and load playlist")), 3_000)) {
            val args = InstrumentationRegistry.getArguments()
            device.findObject(By.text("Server URL"))?.text = args.getString("server")
            device.findObject(By.text("Username"))?.text = args.getString("user")
            device.findObject(By.text("Password"))?.text = args.getString("pass")
            device.findObject(By.text("Sign in and load playlist"))?.click()
            // the first download (channels, movies, series, guide) can take a couple of minutes
            device.wait(Until.hasObject(By.text("For you")), 300_000)
        }
        device.wait(Until.hasObject(By.text("For you")), 30_000)
        device.waitForIdle()
        fun scroll() = device.findObject(By.scrollable(true))?.apply { setGestureMargin(device.displayWidth / 5); fling(Direction.DOWN); fling(Direction.UP) }
        scroll()
        listOf("Live", "Guide", "Library").forEach { tab ->
            device.findObject(By.text(tab))?.click()
            device.waitForIdle(); Thread.sleep(1_500)
            scroll()
        }
    }
}
