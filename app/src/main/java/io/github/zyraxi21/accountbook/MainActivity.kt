package io.github.zyraxi21.accountbook

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import io.github.zyraxi21.accountbook.ui.BookApp
import io.github.zyraxi21.accountbook.ui.BookViewModel
import io.github.zyraxi21.accountbook.ui.theme.AccountBookTheme

class MainActivity : ComponentActivity() {
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                bookViewModel.state.collect { state ->
                    if (!state.loading && state.storageError == null && state.data.settings.allowScreenshots) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        setContent { AccountBookTheme { BookApp(bookViewModel) } }
    }

    override fun onPause() {
        // 前台由用户选择，离开前台前始终恢复截图保护。
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (::bookViewModel.isInitialized) bookViewModel.hidePrivateData()
        super.onPause()
    }
}
