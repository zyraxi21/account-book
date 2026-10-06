package io.github.zyraxi21.accountbook.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microsoft.fluentui.theme.token.controlTokens.ButtonStyle
import com.microsoft.fluentui.tokenized.controls.Button
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.BookData
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette

/**
 * 导入导出入口。按钮只在数据已显示、账本可用且没有任务在跑时启用，
 * 保证隐藏隐私时账务内容不会被写出应用。
 */
@Composable
fun FileTransferCard(
    data: BookData,
    hidden: Boolean,
    enabled: Boolean,
    busy: Boolean,
    progress: Int?,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImport: () -> Unit,
) {
    val palette = LocalBookPalette.current
    LedgerCard {
        SectionHeading(stringResource(R.string.transfer_title), stringResource(R.string.transfer_hint))
        BookText(stringResource(R.string.last_export_at,
            data.settings.exportedAt?.let { formatDateTime(it) } ?: stringResource(R.string.last_export_none)),
            size = 13.sp, color = palette.secondary)
        progress?.let {
            // 导出/导入期间给出明确进度说明，避免只有按钮置灰而无反馈。
            BookText(stringResource(it), size = 13.sp, color = palette.brand)
        }
        LedgerDivider()
        BookText(stringResource(R.string.export_section), weight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onExportJson, style = ButtonStyle.OutlinedButton, text = stringResource(R.string.export_json),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp), enabled = enabled && !busy)
            Button(onClick = onExportCsv, style = ButtonStyle.OutlinedButton, text = stringResource(R.string.export_csv),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp), enabled = enabled && !busy)
        }
        LedgerDivider()
        BookText(stringResource(R.string.import_section), weight = FontWeight.Medium)
        BookText(stringResource(R.string.import_merge_hint), size = 13.sp, color = palette.secondary)
        BookText(stringResource(R.string.import_replace_hint), size = 13.sp, color = palette.secondary)
        Button(onClick = onImport, text = stringResource(R.string.import_button),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled && !busy)
    }
}
