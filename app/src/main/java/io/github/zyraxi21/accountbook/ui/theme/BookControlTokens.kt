package io.github.zyraxi21.accountbook.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.microsoft.fluentui.theme.token.ControlTokens
import com.microsoft.fluentui.theme.token.StateBrush
import com.microsoft.fluentui.theme.token.StateColor
import com.microsoft.fluentui.theme.token.controlTokens.*

/** 原生 Fluent 控件共用品牌渐变、表面色与提示配色。 */
internal class BookControlTokens : ControlTokens() {
    init {
        updateToken(ControlType.AppBarControlType, BookAppBarTokens)
        updateToken(ControlType.ButtonControlType, BookButtonTokens)
        updateToken(ControlType.FloatingActionButtonControlType, BookFabTokens)
        updateToken(ControlType.SnackbarControlType, BookSnackbarTokens)
        updateToken(ControlType.TextFieldControlType, BookTextFieldTokens)
        updateToken(ControlType.DialogControlType, BookDialogTokens)
        updateToken(ControlType.BottomSheetControlType, BookBottomSheetTokens)
    }
}

private object BookAppBarTokens : AppBarTokens() {
    // 渐变由外层圆角卡片承托，保持卡片内外连续。
    @Composable override fun backgroundBrush(info: AppBarInfo) = SolidColor(Color.Transparent)
    @Composable override fun titleTextColor(info: AppBarInfo) = LocalBookPalette.current.onTopBar
}

private object BookButtonTokens : ButtonTokens() {
    @Composable override fun backgroundBrush(buttonInfo: ButtonInfo): StateBrush {
        if (buttonInfo.style != ButtonStyle.Button) return super.backgroundBrush(buttonInfo)
        val palette = LocalBookPalette.current
        return StateBrush(rest = palette.accentBrush(), pressed = palette.accentBrush(pressed = true),
            selected = palette.accentBrush(pressed = true), focused = palette.accentBrush(),
            disabled = super.backgroundBrush(buttonInfo).disabled)
    }
}

private object BookFabTokens : FABTokens() {
    @Composable override fun backgroundBrush(fabInfo: FABInfo): StateBrush {
        val palette = LocalBookPalette.current
        return StateBrush(rest = palette.accentBrush(), pressed = palette.accentBrush(pressed = true),
            focused = palette.accentBrush(), disabled = super.backgroundBrush(fabInfo).disabled)
    }
}

private object BookSnackbarTokens : SnackBarTokens() {
    @Composable override fun backgroundBrush(snackBarInfo: SnackBarInfo) = SolidColor(LocalBookPalette.current.snackbarSurface)
    @Composable override fun iconColor(snackBarInfo: SnackBarInfo) = LocalBookPalette.current.onSnackbar
    @Composable override fun titleTypography(snackBarInfo: SnackBarInfo) =
        super.titleTypography(snackBarInfo).copy(color = LocalBookPalette.current.onSnackbar)
    @Composable override fun subtitleTypography(snackBarInfo: SnackBarInfo) =
        super.subtitleTypography(snackBarInfo).copy(color = LocalBookPalette.current.onSnackbar)
}

private object BookTextFieldTokens : TextFieldTokens() {
    @Composable override fun backgroundBrush(textFieldInfo: TextFieldInfo) = SolidColor(Color.Transparent)
    @Composable override fun textAreaBackgroundBrush(textFieldInfo: TextFieldInfo) = StateBrush(
        rest = SolidColor(Color.Transparent), disabled = SolidColor(Color.Transparent),
    )
    @Composable override fun inputTextColor(textFieldInfo: TextFieldInfo) = StateColor(
        rest = LocalBookPalette.current.foreground,
        disabled = LocalBookPalette.current.secondary.copy(alpha = 0.5f),
    )
    @Composable override fun cursorColor(textFieldInfo: TextFieldInfo) = SolidColor(LocalBookPalette.current.brand)
    @Composable override fun dividerColor(textFieldInfo: TextFieldInfo) = SolidColor(when {
        textFieldInfo.isStatusError -> LocalBookPalette.current.negative
        textFieldInfo.isFocused -> LocalBookPalette.current.brand
        else -> LocalBookPalette.current.stroke
    })
    @Composable override fun labelColor(textFieldInfo: TextFieldInfo) = when {
        textFieldInfo.isStatusError -> LocalBookPalette.current.negative
        textFieldInfo.isFocused -> LocalBookPalette.current.brand
        else -> LocalBookPalette.current.secondary
    }
}

private object BookDialogTokens : DialogTokens() {
    @Composable override fun backgroundBrush(dialogInfo: DialogInfo) = SolidColor(LocalBookPalette.current.surface)
    @Composable override fun borderBrush(dialogInfo: DialogInfo) = SolidColor(LocalBookPalette.current.stroke)
}

private object BookBottomSheetTokens : BottomSheetTokens() {
    @Composable override fun backgroundBrush(bottomSheetInfo: BottomSheetInfo) = SolidColor(LocalBookPalette.current.surface)
    @Composable override fun handleColor(bottomSheetInfo: BottomSheetInfo) = LocalBookPalette.current.secondary.copy(alpha = 0.5f)
}
