package io.github.zyraxi21.accountbook.ui.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.icons.appbaricons.AppBarIcons
import com.microsoft.fluentui.icons.appbaricons.appbaricons.Arrowback
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.ui.components.*
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName.orEmpty()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Button(onClick = onBack, text = stringResource(R.string.back_to_settings),
            icon = AppBarIcons.Arrowback, style = ButtonStyle.OutlinedButton) }
        item { SectionHeading(stringResource(R.string.app_name), stringResource(R.string.about_version, version)) }
        item { LedgerCard {
            SectionHeading(stringResource(R.string.encrypted_local_title))
            BookText(stringResource(R.string.encrypted_local_hint), size = 14.sp, color = LocalBookPalette.current.secondary)
            LedgerDivider()
            BookText(stringResource(R.string.about_export_hint), size = 14.sp, color = LocalBookPalette.current.secondary)
        } }
    }
}
