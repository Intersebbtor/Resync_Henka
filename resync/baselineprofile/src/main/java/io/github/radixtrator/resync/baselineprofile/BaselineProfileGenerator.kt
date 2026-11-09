package io.github.radixtrator.resync.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code a typical session runs, for app/src/release/generated/baselineProfiles: launch, scroll the feed,
 * open a post and scroll its comments, go back. It browses the bundled demo feed, so no Reddit account is needed.
 *
 * ./gradlew :app:generateBaselineProfile with an emulator or phone (API 33+) connected.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "io.github.radixtrator.resync", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()

        // A fresh install starts at Setup: take the demo feed.
        device.findObject(By.textContains("demo data"))?.let {
            it.click()
            device.wait(Until.hasObject(By.text("Frontpage")), 5_000)
        }
        device.wait(Until.hasObject(By.text("Frontpage")), 5_000)

        val feed = device.findObject(By.scrollable(true)) ?: return@collect
        feed.setGestureMargin(device.displayWidth / 5)
        repeat(3) { feed.fling(Direction.DOWN) }
        repeat(3) { feed.fling(Direction.UP) }

        // Open a post and read its comments.
        device.findObject(By.textContains("comments"))?.click()
        device.wait(Until.hasObject(By.textContains("View more")), 5_000)
        device.findObject(By.scrollable(true))?.let { comments ->
            comments.setGestureMargin(device.displayWidth / 5)
            comments.fling(Direction.DOWN)
            comments.fling(Direction.UP)
        }
        device.pressBack()
        device.wait(Until.hasObject(By.text("Frontpage")), 5_000)
    }
}
