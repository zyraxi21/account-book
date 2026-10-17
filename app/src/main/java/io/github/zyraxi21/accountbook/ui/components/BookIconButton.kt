package io.github.zyraxi21.accountbook.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.Button

/** 常见操作只显示图标，保留无障碍描述及 48dp 点击区域。 */
@Composable
fun BookIconButton(@DrawableRes icon: Int, @StringRes description: Int, onClick: () -> Unit,
                   modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, style = ButtonStyle.TextButton, icon = ImageVector.vectorResource(icon),
        contentDescription = stringResource(description), modifier = modifier.size(48.dp), enabled = enabled)
}
