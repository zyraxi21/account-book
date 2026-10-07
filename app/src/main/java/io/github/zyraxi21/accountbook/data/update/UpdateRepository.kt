package io.github.zyraxi21.accountbook.data.update

import io.github.zyraxi21.accountbook.domain.AppVersion
import io.github.zyraxi21.accountbook.domain.ReleaseInfo
import io.github.zyraxi21.accountbook.domain.ReleaseSelector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * 检查更新失败的原因分类。界面据此给出不同文案，避免一律显示“检查失败”。
 * 对应文案见 `strings.xml` 的 `update_*`。
 */
enum class UpdateError { OFFLINE, TIMEOUT, RATE_LIMITED, NOT_FOUND, SERVER, PARSE, IO }

/** 查询结果：已是最新 / 存在新版本 / 各类失败。 */
sealed interface UpdateResult {
    data class UpToDate(val reason: ReleaseSelector.Failure) : UpdateResult
    data class Available(val release: ReleaseInfo) : UpdateResult
    data class Failed(val error: UpdateError) : UpdateResult
}

/**
 * 从 GitHub Releases 读取最新正式版本。
 *
 * 只发起 GET 请求，不上传任何账务数据；响应体与请求头都不写入日志。
 * GitHub 未认证请求有速率限制，因此失败时给出可区分的原因而不反复重试。
 */
open class UpdateRepository(
    private val owner: String = OWNER,
    private val repository: String = REPOSITORY,
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS,
) {
    /**
     * 查询 [current] 之后是否存在更高版本。
     * 始终在 [Dispatchers.IO] 上执行，调用方可从主线程直接 invoke。
     */
    open suspend fun check(current: AppVersion): UpdateResult = withContext(Dispatchers.IO) {
        val body = try {
            fetchReleases()
        } catch (error: CancellationException) {
            throw error
        } catch (error: UpdateHttpException) {
            return@withContext UpdateResult.Failed(error.error)
        } catch (error: UnknownHostException) {
            return@withContext UpdateResult.Failed(UpdateError.OFFLINE)
        } catch (error: SocketTimeoutException) {
            return@withContext UpdateResult.Failed(UpdateError.TIMEOUT)
        } catch (error: IOException) {
            return@withContext UpdateResult.Failed(UpdateError.IO)
        } catch (error: Exception) {
            return@withContext UpdateResult.Failed(UpdateError.IO)
        }
        val releases = try {
            parseReleases(body)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return@withContext UpdateResult.Failed(UpdateError.PARSE)
        }
        // 标签无法解析成日期版本号的发布不参与比较，直接跳过而不是让整批失败。
        when (val outcome = ReleaseSelector.select(releases, current)) {
            is ReleaseSelector.Outcome.NewVersion -> UpdateResult.Available(outcome.release)
            is ReleaseSelector.Outcome.UpToDate -> UpdateResult.UpToDate(outcome.reason)
            is ReleaseSelector.Outcome.Failed -> UpdateResult.Failed(UpdateError.PARSE)
        }
    }

    private fun fetchReleases(): String {
        val url = URL("https://api.github.com/repos/$owner/$repository/releases?per_page=$PER_PAGE")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub 要求声明客户端版本；不发送 UA 会被拒绝。
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw when (code) {
                    HttpURLConnection.HTTP_NOT_FOUND -> UpdateHttpException(UpdateError.NOT_FOUND)
                    // 未认证请求的限流返回 403，GitHub 用剩余额度为 0 表达。
                    HttpURLConnection.HTTP_FORBIDDEN,
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    -> UpdateHttpException(UpdateError.RATE_LIMITED)
                    else -> UpdateHttpException(UpdateError.SERVER)
                }
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** 解析发布数组；忽略标签不可解析的条目，不因单条脏数据中断整体。 */
    private fun parseReleases(body: String): List<ReleaseInfo> {
        val array = JSONArray(body)
        val result = ArrayList<ReleaseInfo>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val tag = item.optString("tag_name").takeIf { it.isNotBlank() } ?: continue
            val version = AppVersion.parse(tag) ?: continue
            result += ReleaseInfo(
                tag = tag,
                name = item.optString("name").takeIf { it.isNotBlank() } ?: tag,
                notes = item.optString("body").orEmpty(),
                version = version,
                apkUrl = findApkUrl(item.optJSONArray("assets")),
                prerelease = item.optBoolean("prerelease", false),
                draft = item.optBoolean("draft", false),
                publishedAt = item.optString("published_at").takeIf { it.isNotBlank() },
            )
        }
        return result
    }

    /** 取第一个 `.apk` 资源；下载链接优先用 GitHub 给出的 browser_download_url。 */
    private fun findApkUrl(assets: JSONArray?): String? {
        if (assets == null) return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(APK_SUFFIX, ignoreCase = true)) {
                asset.optString("browser_download_url").takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return null
    }

    /** 用状态码区分失败原因，不携带响应体。 */
    private class UpdateHttpException(val error: UpdateError) : Exception()

    companion object {
        const val OWNER = "zyraxi21"
        const val REPOSITORY = "account-book"
        private const val PER_PAGE = 10
        private const val API_VERSION = "2022-11-28"
        private const val USER_AGENT = "account-book-android"
        private const val APK_SUFFIX = ".apk"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
    }
}