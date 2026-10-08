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
import com.microsoft.fluentui.theme.token.StateColor
import com.microsoft.fluentui.theme.token.controlTokens.ButtonInfo
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.theme.token.controlTokens.ButtonTokens
import com.microsoft.fluentui.tokenized.controls.Button
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette

private object DestructiveIconTokens : ButtonTokens() {
    @Composable override fun iconColor(buttonInfo: ButtonInfo): StateColor {
        val palette = LocalBookPalette.current
        return StateColor(rest = palette.negative, pressed = palette.negative, focused = palette.negative,
            selected = palette.negative, disabled = palette.secondary.copy(alpha = 0.38f))
    }
}

/** 常见操作只显示图标，保留无障碍描述及 48dp 点击区域。 */
@Composable
fun BookIconButton(@DrawableRes icon: Int, @StringRes description: Int, onClick: () -> Unit,
                   modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false) {
    Button(onClick = onClick, style = ButtonStyle.TextButton, icon = ImageVector.vectorResource(icon),
        contentDescription = stringResource(description), modifier = modifier.size(48.dp), enabled = enabled,
        buttonTokens = if (destructive) DestructiveIconTokens else null)
}
