package com.warden.app

import android.app.Application
import android.content.Context
import android.content.DialogInterface
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import com.warden.app.feature.blocker.ui.KeywordBlockerFragment
import com.warden.app.ui.activity.FragmentActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * UI smoke & parity tests locking in C1 (first-launch disclaimer dialog and fragment mounting).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class FragmentActivityParityTest {
    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun awaitMainLooper(waitMs: Long = 100L) {
        Thread.sleep(waitMs)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun c1_firstLaunchShowsDisclaimerAndExitFinishesActivity() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        awaitMainLooper()

        val dialog =
            requireNotNull(ShadowDialog.getLatestDialog() as? AlertDialog) {
                "Disclaimer dialog must be displayed on first launch"
            }
        assertTrue(dialog.isShowing)

        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("Clicking Exit must finish the activity", controller.get().isFinishing)
        val agreed = app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getBoolean("agreed_to_terms", false)
        assertFalse(agreed)
    }

    @Test
    fun c1_firstLaunchAgreePersistsPreferenceAndMountsDashboardFragment() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        awaitMainLooper()

        val dialog = requireNotNull(ShadowDialog.getLatestDialog() as? AlertDialog)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        awaitMainLooper()

        val agreed = app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getBoolean("agreed_to_terms", false)
        assertTrue("Clicking I Agree must persist agreed_to_terms", agreed)
        val mounted = controller.get().supportFragmentManager.findFragmentById(R.id.fragment_holder)
        assertTrue("Dashboard fragment must be mounted after agreement", mounted is KeywordBlockerFragment)
    }

    @Test
    fun c1_returningUserSkipsDisclaimerAndMountsDashboardDirectly() {
        app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("agreed_to_terms", true)
            .commit()

        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        awaitMainLooper()

        assertNull("Disclaimer dialog must not show for returning user", ShadowDialog.getLatestDialog())
        val mounted = controller.get().supportFragmentManager.findFragmentById(R.id.fragment_holder)
        assertTrue("Dashboard fragment must mount immediately", mounted is KeywordBlockerFragment)
    }
}
