package io.github.zyraxi21.accountbook

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import io.github.zyraxi21.accountbook.ui.BookApp
import io.github.zyraxi21.accountbook.ui.BookViewModel
import io.github.zyraxi21.accountbook.ui.theme.AccountBookTheme

// Fluent 原生日历通过 AppCompatActivity 查找宿主并构建星期标题。
class MainActivity : AppCompatActivity() {
    private lateinit var bookViewModel: BookViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val darkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = if (darkTheme) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setRecentsScreenshotEnabled(false)
        val container = (application as AccountBookApplication).container
        bookViewModel = ViewModelProvider(this, BookViewModel.Factory(container.repository, container.smsParser, container.transfer))[BookViewModel::class.java]
        bookViewModel.obscureInBackground()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                bookViewModel.state.collect { state ->
                    // 账务可见且用户允许前台截屏时才放开保护。
                    setSecureFlag(!(!state.loading && state.storageError == null && state.data.settings.allowScreenshots))
                }
            }
        }
        setContent { AccountBookTheme { BookApp(bookViewModel) } }
    }

    /**
     * `Window.setFlags` 无论标志位是否变化都会派发一次窗口属性变更，进而重新布局窗口，
     * 在部分机型上表现为整页闪一下。因此先比较当前值，只有真正需要变化时才改写。
     */
    private fun setSecureFlag(secure: Boolean) {
        val enabled = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        if (enabled == secure) return
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onResume() {
        super.onResume()
        bookViewModel.onForeground()
    }

    override fun onPause() {
        // 前台由用户选择，离开前台前始终恢复截图保护。
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (::bookViewModel.isInitialized) bookViewModel.obscureInBackground()
        super.onPause()
    }

    override fun onStop() {
        if (!isChangingConfigurations) bookViewModel.onBackgroundStopped()
        super.onStop()
    }
}
