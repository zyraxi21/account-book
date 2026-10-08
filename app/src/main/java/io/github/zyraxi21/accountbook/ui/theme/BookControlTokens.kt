package io.github.zyraxi21.accountbook.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.microsoft.fluentui.theme.token.ControlTokens
import com.microsoft.fluentui.theme.token.StateBrush
import com.microsoft.fluentui.theme.token.StateColor
import com.microsoft.fluentui.theme.token.controlTokens.*

/** 所有表单和浮层共用表面色，输入框通过底线区分，不再铺设独立黑色底块。 */
internal class BookControlTokens : ControlTokens() {
    init {
        updateToken(ControlType.AppBarControlType, BookAppBarTokens)
        updateToken(ControlType.TextFieldControlType, BookTextFieldTokens)
        updateToken(ControlType.DialogControlType, BookDialogTokens)
        updateToken(ControlType.BottomSheetControlType, BookBottomSheetTokens)
    }
}

private object BookAppBarTokens : AppBarTokens() {
    @Composable override fun backgroundBrush(info: AppBarInfo) = SolidColor(LocalBookPalette.current.topBar)
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
