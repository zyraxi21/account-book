package io.github.zyraxi21.accountbook.ui

import android.content.pm.ApplicationInfo
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.zyraxi21.accountbook.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivitySecurityTest {
    @Test fun backgroundAndRecreationHideDataAndScreenshotProtectionIsEnabled() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(37, activity.applicationInfo.targetSdkVersion)
                assertEquals(0, activity.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                val vm = ViewModelProvider(activity)[BookViewModel::class.java]
                assertTrue(vm.privacyHidden.value)
                vm.togglePrivacy()
                assertFalse(vm.privacyHidden.value)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertTrue(ViewModelProvider(it)[BookViewModel::class.java].privacyHidden.value) }
            scenario.recreate()
            scenario.onActivity { assertTrue(ViewModelProvider(it)[BookViewModel::class.java].privacyHidden.value) }
        }
    }
}
