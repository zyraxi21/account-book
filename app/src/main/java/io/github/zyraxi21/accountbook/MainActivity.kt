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
        setContent { AccountBookTheme { BookApp(bookViewModel) } }
    }

    override fun onPause() {
        if (::bookViewModel.isInitialized) bookViewModel.hidePrivateData()
        super.onPause()
    }
}
