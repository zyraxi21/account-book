package io.github.zyraxi21.accountbook.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.FluentStyle
import com.microsoft.fluentui.theme.token.StateColor
import com.microsoft.fluentui.theme.token.controlTokens.ButtonInfo
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.theme.token.controlTokens.ButtonTokens
import com.microsoft.fluentui.tokenized.AppBar
import com.microsoft.fluentui.tokenized.controls.Button
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.ui.theme.AccountBookTheme
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette

private object PrivacyButtonTokens : ButtonTokens() {
    @Composable
    private fun foreground() = LocalBookPalette.current.onTopBar.let { color ->
        StateColor(rest = color, pressed = color, focused = color, selected = color, disabled = color)
    }

    @Composable override fun textColor(buttonInfo: ButtonInfo) = foreground()
    @Composable override fun iconColor(buttonInfo: ButtonInfo) = foreground()
}

/** 背景先铺满窗口，再对顶栏内容应用状态栏、桌面标题栏和横向挖孔边衬。 */
@Composable
fun BookTopBar(hidden: Boolean, onPrivacyClick: () -> Unit, @StringRes title: Int = R.string.app_name) {
    val palette = LocalBookPalette.current
    Box(
        Modifier.fillMaxWidth().background(palette.topBar)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        AppBar(
            title = stringResource(title),
            style = FluentStyle.Brand,
            bottomBorder = false,
            modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            rightAccessoryView = {
                Button(
                    onClick = onPrivacyClick,
                    style = ButtonStyle.TextButton,
                    icon = ImageVector.vectorResource(if (hidden) R.drawable.ic_eye else R.drawable.ic_eye_off),
                    contentDescription = stringResource(if (hidden) R.string.privacy_show else R.string.privacy_hide),
                    modifier = Modifier.size(48.dp),
                    buttonTokens = PrivacyButtonTokens,
                )
            },
        )
    }
}

private enum class BookTab(@get:StringRes val label: Int, @get:DrawableRes val icon: Int) {
    Assets(R.string.tab_assets, R.drawable.ic_assets),
    Income(R.string.tab_income, R.drawable.ic_income),
    Settings(R.string.tab_settings, R.drawable.ic_settings),
}

/**
 * 沿用参考项目的完整点击区：每个导航项包含底部系统边衬，涟漪铺到手势区域。
 * 使用内容的固有高度，让大字体下图标、标签和系统导航区域都保留足够空间。
 */
@Composable
fun BookBottomBar(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val palette = LocalBookPalette.current
    Column(Modifier.fillMaxWidth().background(palette.surface)) {
        LedgerDivider()
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .selectableGroup(),
        ) {
            BookTab.entries.forEachIndexed { index, tab ->
                val selected = selectedIndex == index
                val color = if (selected) palette.brand else palette.secondary
                val description = stringResource(if (selected) R.string.tab_selected else R.string.tab_available)
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .semantics { stateDescription = description }
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = palette.foreground),
                            onClick = { onSelect(index) },
                        )
                        .padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painterResource(tab.icon), contentDescription = null,
                        modifier = Modifier.size(24.dp), colorFilter = ColorFilter.tint(color),
                    )
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        stringResource(tab.label),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        style = FluentTheme.aliasTokens.typography[FluentAliasTokens.TypographyTokens.Caption1].copy(
                            color = color,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    }
}

@Preview(name = "浅色 · 系统栏与导航", widthDp = 393, heightDp = 852, showSystemUi = true)
@Composable
private fun LightChromePreview() { ChromePreview(darkTheme = false) }

@Preview(name = "深色 · 系统栏与导航", widthDp = 393, heightDp = 852, showSystemUi = true)
@Composable
private fun DarkChromePreview() { ChromePreview(darkTheme = true) }

@Composable
private fun ChromePreview(darkTheme: Boolean) {
    AccountBookTheme(darkTheme = darkTheme, dynamicColor = false) {
        Column(Modifier.fillMaxSize().background(LocalBookPalette.current.background)) {
            BookTopBar(hidden = true, onPrivacyClick = {})
            Column(Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SectionHeading(stringResource(R.string.statement_title), stringResource(R.string.statement_subtitle))
                LedgerCard { BookText(stringResource(R.string.privacy_hint)) }
            }
            BookBottomBar(selectedIndex = 0, onSelect = {})
        }
    }
}
