package io.github.zyraxi21.accountbook.ui.components

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.microsoft.fluentui.datetimepicker.DateTimePickerDialog
import com.microsoft.fluentui.theme.token.controlTokens.BasicCardInfo
import com.microsoft.fluentui.theme.token.controlTokens.BasicCardTokens
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.theme.token.controlTokens.CardType
import com.microsoft.fluentui.tokenized.controls.BasicCard
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.TextField
import com.microsoft.fluentui.tokenized.menu.Dialog
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.BOOK_ZONE
import io.github.zyraxi21.accountbook.domain.Money
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BookText(text: String, modifier: Modifier = Modifier, size: TextUnit = 16.sp,
             weight: FontWeight = FontWeight.Normal, color: Color = LocalBookPalette.current.foreground,
             numeric: Boolean = false) {
    BasicText(text, modifier, style = TextStyle(color = color, fontSize = size, lineHeight = size * 1.45f,
        fontWeight = weight, fontFamily = if (numeric) FontFamily.Monospace else FontFamily.Default))
}

@Composable
fun PrivateText(value: String, hidden: Boolean, modifier: Modifier = Modifier, size: TextUnit = 16.sp,
                weight: FontWeight = FontWeight.Normal, color: Color = LocalBookPalette.current.foreground,
                numeric: Boolean = false) {
    val hiddenDescription = stringResource(R.string.privacy_hidden)
    BookText(if (hidden) stringResource(R.string.privacy_mask) else value,
        if (hidden) modifier.clearAndSetSemantics { contentDescription = hiddenDescription } else modifier,
        size, weight, if (hidden) LocalBookPalette.current.secondary else color, numeric)
}

@Composable
fun MoneyText(money: Money, hidden: Boolean, modifier: Modifier = Modifier, large: Boolean = false,
              color: Color = LocalBookPalette.current.foreground) {
    PrivateText(stringResource(R.string.currency_value, money.formatted()), hidden, modifier,
        if (large) 28.sp else 18.sp, FontWeight.Medium, color, numeric = true)
}

private object LedgerCardTokens : BasicCardTokens() {
    @Composable override fun backgroundBrush(basicCardInfo: BasicCardInfo): Brush = SolidColor(LocalBookPalette.current.surface)
    @Composable override fun cornerRadius(basicCardInfo: BasicCardInfo) = 12.dp
}

@Composable
fun LedgerCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    BasicCard(modifier.fillMaxWidth(), CardType.Outlined, basicCardTokens = LedgerCardTokens) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
fun LedgerDivider() { Box(Modifier.fillMaxWidth().height(1.dp).background(LocalBookPalette.current.stroke)) }

@Composable
fun SectionHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BookText(title, size = 24.sp, weight = FontWeight.SemiBold)
        if (subtitle != null) BookText(subtitle, size = 14.sp, color = LocalBookPalette.current.secondary)
    }
}

@Composable
fun AmountField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    TextField(value, { onValueChange(it.take(32)) }, modifier.fillMaxWidth(), label = label,
        hintText = stringResource(R.string.amount_placeholder), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textFieldContentDescription = label)
}

@Composable
fun formatDateTime(time: Instant): String {
    val pattern = stringResource(R.string.datetime_pattern)
    return remember(time, pattern) { DateTimeFormatter.ofPattern(pattern, Locale.SIMPLIFIED_CHINESE).format(time.atZone(BOOK_ZONE)) }
}

@Composable
fun DateTimeField(time: Instant, onChange: (Instant) -> Unit, label: String) {
    val context = LocalContext.current
    val allowScreenshots = LocalAllowScreenshots.current
    var pickerVisible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BookText(label, size = 14.sp, color = LocalBookPalette.current.secondary)
        Button(onClick = { pickerVisible = true }, style = ButtonStyle.OutlinedButton,
            text = formatDateTime(time), contentDescription = stringResource(R.string.select_datetime))
    }
    if (pickerVisible) {
        DisposableEffect(context, allowScreenshots) {
            val dialog = DateTimePickerDialog(context, DateTimePickerDialog.Mode.DATE_TIME, dateTime = time.atZone(BOOK_ZONE))
            dialog.onDateTimePickedListener = object : DateTimePickerDialog.OnDateTimePickedListener {
                override fun onDateTimePicked(dateTime: ZonedDateTime, duration: Duration) {
                    onChange(dateTime.toInstant())
                    pickerVisible = false
                }
            }
            dialog.setOnDismissListener { pickerVisible = false }
            dialog.show()
            if (allowScreenshots) dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose { dialog.setOnDismissListener(null); dialog.dismiss() }
        }
    }
}

@Composable
fun EditorDialog(title: String, busy: Boolean, onClose: () -> Unit, saveLabel: String, onSave: () -> Unit,
                 content: @Composable ColumnScope.() -> Unit) {
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val maxHeight = (windowHeight - 72.dp).coerceAtLeast(200.dp)
    Dialog(onDismiss = onClose, dialogProperties = DialogProperties(dismissOnBackPress = !busy,
        dismissOnClickOutside = !busy, securePolicy = SecureFlagPolicy.Inherit, usePlatformDefaultWidth = false)) {
        // 同时禁止控件内部把输入值写入系统保存状态，敏感草稿只由 ViewModel 持有。
        CompositionLocalProvider(LocalSaveableStateRegistry provides null) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight).imePadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BookText(title, size = 22.sp, weight = FontWeight.SemiBold)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onClose, modifier = Modifier.weight(1f).heightIn(min = 48.dp), style = ButtonStyle.OutlinedButton,
                        text = stringResource(R.string.cancel), enabled = !busy)
                    Button(onClick = onSave, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        text = if (busy) stringResource(R.string.saving) else saveLabel, enabled = !busy)
                }
                BookSnackbarHost()
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, hint: String, busy: Boolean, onClose: () -> Unit, onConfirm: () -> Unit) {
    EditorDialog(title, busy, onClose, stringResource(R.string.delete), onConfirm) { BookText(hint) }
}
