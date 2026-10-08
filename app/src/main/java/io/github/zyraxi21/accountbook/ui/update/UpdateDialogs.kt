package io.github.zyraxi21.accountbook.ui.update

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.microsoft.fluentui.tokenized.progress.LinearProgressIndicator
import io.github.zyraxi21.accountbook.R
import io.github.zyraxi21.accountbook.domain.ReleaseNotesFormatter
import io.github.zyraxi21.accountbook.ui.BookViewModel
import io.github.zyraxi21.accountbook.ui.UpdateUiState
import io.github.zyraxi21.accountbook.ui.components.BookText
import io.github.zyraxi21.accountbook.ui.components.EditorDialog
import io.github.zyraxi21.accountbook.ui.components.bookDialogMaxHeight
import io.github.zyraxi21.accountbook.ui.theme.LocalBookPalette

/** 启动与手动检查共用应用层弹窗，下载和安装不依赖关于页是否打开。 */
@Composable
fun UpdateDialogs(state: UpdateUiState, vm: BookViewModel, snackbarHost: Boolean) {
    when (state) {
        is UpdateUiState.Available -> UpdateAvailableDialog(state, vm, snackbarHost)
        is UpdateUiState.Downloading -> UpdateProgressDialog(state, vm, snackbarHost)
        is UpdateUiState.ReadyToInstall -> UpdateInstallDialog(vm, snackbarHost)
        else -> Unit
    }
}

/** 只有用户确认后才下载；没有安装包时禁用下载按钮。 */
@Composable
private fun UpdateAvailableDialog(state: UpdateUiState.Available, vm: BookViewModel, snackbarHost: Boolean) {
    val release = state.release
    val maxHeight = bookDialogMaxHeight()
    val fallback = stringResource(R.string.update_no_notes)
    val notes = remember(release.notes, fallback) {
        ReleaseNotesFormatter.format(release.notes).ifEmpty { fallback }
    }
    EditorDialog(title = stringResource(R.string.update_available_title), busy = false,
        onClose = vm::dismissUpdate, cancelLabel = stringResource(R.string.update_later),
        saveLabel = stringResource(R.string.update_download_now), onSave = vm::startUpdateDownload,
        saveEnabled = release.apkUrl != null, snackbarHost = snackbarHost) {
        BookText(stringResource(R.string.update_available_message, release.version.toString()),
            size = 18.sp, weight = FontWeight.Medium)
        if (release.apkUrl == null) {
            BookText(stringResource(R.string.update_no_apk), size = 14.sp, color = LocalBookPalette.current.secondary)
        }
        BookText(stringResource(R.string.update_release_notes), size = 14.sp, color = LocalBookPalette.current.secondary)
        BookText(notes, Modifier.heightIn(max = maxHeight * 0.5f).verticalScroll(rememberScrollState()), 14.sp)
    }
}

/** 进度使用 Fluent 控件，下载期间允许用户取消。 */
@Composable
private fun UpdateProgressDialog(state: UpdateUiState.Downloading, vm: BookViewModel, snackbarHost: Boolean) {
    EditorDialog(title = stringResource(R.string.update_downloading), busy = false,
        onClose = vm::cancelUpdateDownload, saveLabel = stringResource(R.string.update_cancel_download),
        onSave = vm::cancelUpdateDownload, snackbarHost = snackbarHost) {
        BookText(if (state.progress >= 0) stringResource(R.string.update_progress, state.progress)
            else stringResource(R.string.update_progress_unknown), size = 16.sp)
        if (state.progress >= 0) {
            LinearProgressIndicator(progress = state.progress / 100f, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** 安装授权说明留在弹窗中，从系统设置返回后仍可继续安装。 */
@Composable
private fun UpdateInstallDialog(vm: BookViewModel, snackbarHost: Boolean) {
    val settingsMissing by vm.installSettingsMissing.collectAsStateWithLifecycle()
    EditorDialog(title = stringResource(R.string.update_download_done), busy = false,
        onClose = { vm.dismissUpdate(); vm.clearInstallSettingsMissing() },
        saveLabel = stringResource(R.string.update_install),
        onSave = { vm.clearInstallSettingsMissing(); vm.installUpdate() }, snackbarHost = snackbarHost) {
        BookText(stringResource(R.string.update_install_permission_message), size = 14.sp,
            color = LocalBookPalette.current.secondary)
        if (settingsMissing) {
            BookText(stringResource(R.string.update_install_manual_path), size = 14.sp,
                color = LocalBookPalette.current.secondary)
        }
    }
}
