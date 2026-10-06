package io.github.zyraxi21.accountbook.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.zyraxi21.accountbook.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ActivitySecurityTest {
    @Test fun backgroundAndRecreationHideDataAndScreenshotProtectionIsEnabled() {
        // 显式指定被测组件，不依赖设备启动器提供的启动 Intent。
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            var opened = false
            do {
                scenario.onActivity {
                    val state = ViewModelProvider(it)[BookViewModel::class.java].state.value
                    opened = !state.loading && state.storageError == null
                }
                if (!opened) Thread.sleep(50)
            } while (!opened && System.nanoTime() < deadline)
            assertTrue("启动后必须成功打开加密账本", opened)
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
