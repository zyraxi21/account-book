package io.github.zyraxi21.accountbook.domain

import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * JSON 与 CSV 的编解码。整个文件不依赖 Android 类和第三方序列化库，可在单元测试中直接验证。
 *
 * 结构校验失败、解析失败和值域错误分别抛出带行号或字段说明的 [BookException]，
 * 由界面层翻译成可读提示。任何异常都不会产生部分导入结果：
 * 调用方先完成全部解码，再进入单个数据库事务写入。
 */

/** 导入时限制单条记录数量，超过后返回 `IMPORT_LIMIT_EXCEEDED`，避免异常文件拖垮事务。 */
const val MAX_IMPORT_RECORDS = 10_000

internal enum class BookField(val label: String) {
    VERSION("version"),
    EXPORTED_AT("exportedAt"),
    MONTH("month"),
    REGISTERED_AT("registeredAt"),
    LIABILITY("liability"),
    CHANNEL_ID("channelId"),
    CHANNEL_NAME("channelName"),
    AMOUNT("amount"),
    CHANNELS("channels"),
    SNAPSHOTS("snapshots"),
    BALANCES("balances"),
    NAME("name"),
    ACTIVE("active"),
    POSITION("position"),
    INCOMES("incomes"),
    ID("id"),
    TITLE("title"),
    RECEIVED_AT("receivedAt"),
    SOURCE("source"),
}

/** 带定位信息的异常，最终仍以 [BookError] + 界面文案呈现，不泄露账务数值。 */
internal fun fieldError(field: BookField, row: Int?, detail: String): Nothing {
    val where = if (row == null) "字段 ${field.label}" else "第 $row 条记录的字段 ${field.label}"
    fail(BookError.IMPORT_INVALID_FIELD, "$where：$detail")
}

internal fun missingField(field: BookField, row: Int?): Nothing {
    val where = if (row == null) "字段 ${field.label}" else "第 $row 条记录的字段 ${field.label}"
    fail(BookError.IMPORT_MISSING_FIELD, "缺少 $where")
}

/** 与 [Money] 相同的口径：只接受十进制定点写法，拒绝浮点、科学计数和超范围数值。 */
private val moneyPattern = Regex("^-?[0-9]+(?:\\.[0-9]{1,2})?$")

/**
 * [Money.parse] 只服务于输入框，不接受负号；而资产差额、净资产都允许为负，
 * 因此这里先校验定点写法，再直接按分换算，避免把合法的负数当成非法金额。
 */
internal fun decodeMoney(field: BookField, row: Int?, value: String): Money {
    if (!moneyPattern.matches(value)) fieldError(field, row, "金额必须是最多两位小数的十进制数")
    val fraction = value.substringAfter('.', "").padEnd(2, '0').take(2)
    val whole = value.substringBefore('.').removePrefix("-")
    val magnitude = try {
        Math.multiplyExact(whole.toLong(), 100L).let { Math.addExact(it, fraction.toLong()) }
    } catch (_: ArithmeticException) {
        fieldError(field, row, "金额超出支持范围")
    }
    return Money(if (value.startsWith('-')) -magnitude else magnitude)
}

internal fun decodeInstant(field: BookField, row: Int?, value: String): Instant = try {
    Instant.parse(value)
} catch (_: DateTimeParseException) {
    fail(BookError.IMPORT_INVALID_DATE, "第 ${field.label} 的日期时间无法识别")
}

/**
 * 月份必须与登记时间的月份一致，否则导出后再次导入会落在另一个月份，
 * 让资产表和收入明细对不上。此处按错误处理，不做静默归一。
 */
internal fun decodeMonth(row: Int, value: String, registeredAt: Instant): YearMonth {
    val month = try {
        YearMonth.parse(value)
    } catch (_: DateTimeParseException) {
        fieldError(BookField.MONTH, row, "月份格式应为 yyyy-MM")
    }
    if (YearMonth.from(registeredAt.atZone(BOOK_ZONE)) != month) {
        fieldError(BookField.MONTH, row, "与 registeredAt 所属月份不一致")
    }
    return month
}

internal fun encodeCsvRecords(rows: List<List<String>>): String =
    rows.joinToString(separator = "\r\n", postfix = "\r\n") { row -> row.joinToString(",") { csvEscape(it) } }

/** RFC 4180：含逗号、引号、换行的字段用双引号包裹，内部引号翻倍。 */
internal fun csvEscape(value: String): String = if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
    '"' + value.replace("\"", "\"\"") + '"'
} else {
    value
}

/** 解析整份 CSV，保留空行以免行号与 `row` 列不一致；返回行号从 1 开始。 */
internal fun parseCsvRecords(text: String): List<CsvRow> {
    val records = mutableListOf<CsvRow>()
    var index = 0
    var line = 1
    var fields = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var closedQuote = false
    while (index < text.length) {
        val char = text[index]
        when {
            quoted -> when {
                char == '"' && index + 1 < text.length && text[index + 1] == '"' -> { field.append('"'); index += 2 }
                char == '"' -> { quoted = false; closedQuote = true; index++ }
                else -> { if (char == '\n') line++; field.append(char); index++ }
            }
            char == '"' -> {
                if (field.isNotEmpty() || closedQuote) fail(BookError.IMPORT_MALFORMED, "第 $line 行的引号位置无效")
                quoted = true
                index++
            }
            char == ',' -> { fields.add(field.toString()); field.setLength(0); closedQuote = false; index++ }
            char == '\r' || char == '\n' -> {
                if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                index++
                fields.add(field.toString())
                field.setLength(0)
                closedQuote = false
                records.add(CsvRow(line, fields))
                fields = mutableListOf()
                line++
            }
            else -> { if (closedQuote) fail(BookError.IMPORT_MALFORMED, "第 $line 行的引号位置无效"); field.append(char); index++ }
        }
    }
    if (quoted) fail(BookError.IMPORT_MALFORMED, "引号未闭合")
    if (field.isNotEmpty() || fields.isNotEmpty() || closedQuote) {
        fields.add(field.toString())
        records.add(CsvRow(line, fields))
    }
    return records
}

private fun fail(error: BookError, detail: String): Nothing = throw BookException(error, IllegalArgumentException(detail))

/** 一行 CSV 记录：`line` 是原始文本行号，用于错误定位。 */
internal data class CsvRow(val line: Int, val cells: List<String>) {
    fun required(column: Int, field: BookField): String {
        val value = cells.getOrNull(column)?.trim()
        if (value.isNullOrEmpty()) missingField(field, line)
        return value
    }

    fun optional(column: Int): String = cells.getOrNull(column)?.trim().orEmpty()
}

/**
 * 按表头名字定位列，缺列时回退到导出时的固定顺序。
 * 这样在表格软件里调整列顺序或删掉一列后，文件仍可导入。
 */
internal class CsvLayout(header: CsvRow) {
    private val columns = header.cells.map { it.trim().removePrefix("\uFEFF").lowercase() }

    fun indexOf(field: BookField, fallback: Int): Int {
        for (index in aliases(field)) {
            val found = columns.indexOf(index.lowercase())
            if (found >= 0) return found
        }
        return fallback
    }

    private fun aliases(field: BookField) = when (field) {
        BookField.MONTH -> listOf("月份", "month")
        BookField.REGISTERED_AT -> listOf("登记时间", "registeredAt")
        BookField.CHANNEL_ID -> listOf("渠道id", "渠道ID", "channelId")
        BookField.CHANNEL_NAME -> listOf("渠道名称", "channelName")
        BookField.AMOUNT -> listOf("余额", "金额", "amount")
        BookField.LIABILITY -> listOf("负债", "liability")
        BookField.NAME -> listOf("渠道名称", "名称", "name")
        BookField.ACTIVE -> listOf("启用", "active")
        BookField.POSITION -> listOf("顺序", "position")
        BookField.ID -> listOf("收入id", "收入ID", "id")
        BookField.TITLE -> listOf("项目", "title")
        BookField.RECEIVED_AT -> listOf("日期时间", "收入时间", "receivedAt")
        BookField.SOURCE -> listOf("来源", "source")
        else -> emptyList()
    }
}

// ---------------------------------------------------------------------------
// 导出
// ---------------------------------------------------------------------------

object BookExporter {
    const val FORMAT_VERSION = 1
    const val ASSET_HEADER = "月份,登记时间,渠道ID,渠道名称,余额,负债"
    const val CHANNEL_HEADER = "渠道ID,渠道名称,启用,顺序"
    const val INCOME_HEADER = "收入ID,项目,金额,日期时间,来源"

    fun toJson(data: BookData, exportedAt: Instant): String = buildString {
        append("{\n")
        append("  \"version\": $FORMAT_VERSION,\n")
        append("  \"exportedAt\": \"${exportedAt}\",\n")
        append("  \"channels\": [\n")
        data.channels.forEachIndexed { index, channel ->
            append("    {\"id\": ${quote(channel.id)}, \"name\": ${quote(channel.name)}, ")
            append("\"active\": ${channel.active}, \"position\": ${channel.position}}")
            append(if (index == data.channels.lastIndex) "\n" else ",\n")
        }
        append("  ],\n")
        append("  \"snapshots\": [\n")
        data.snapshots.forEachIndexed { index, snapshot ->
            append("    {\"month\": \"${snapshot.month}\", \"registeredAt\": \"${snapshot.registeredAt}\", ")
            append("\"liability\": ${quote(snapshot.liability.inputText())}, \"balances\": [")
            append(snapshot.balances.joinToString(", ") { balance ->
                "{\"channelId\": ${quote(balance.channelId)}, \"channelName\": ${quote(balance.channelName)}, " +
                    "\"amount\": ${quote(balance.amount.inputText())}}"
            })
            append("]}")
            append(if (index == data.snapshots.lastIndex) "\n" else ",\n")
        }
        append("  ],\n")
        append("  \"incomes\": [\n")
        data.incomes.forEachIndexed { index, income ->
            append("    {\"id\": ${quote(income.id)}, \"title\": ${quote(income.title)}, ")
            append("\"amount\": ${quote(income.amount.inputText())}, ")
            append("\"receivedAt\": \"${income.receivedAt}\", \"source\": \"${income.source.name}\"}")
            append(if (index == data.incomes.lastIndex) "\n" else ",\n")
        }
        append("  ]\n}\n")
    }

    /** 一个渠道一行，便于在表格软件中按渠道透视；没有资产表时仍输出表头。 */
    fun toAssetsCsv(data: BookData): String = encodeCsvRecords(
        listOf(ASSET_HEADER.split(",")) + data.snapshots.sortedBy { it.month }.flatMap { snapshot ->
            snapshot.balances.map { balance ->
                listOf(
                    snapshot.month.toString(),
                    snapshot.registeredAt.toString(),
                    balance.channelId,
                    balance.channelName,
                    balance.amount.inputText(),
                    snapshot.liability.inputText(),
                )
            }
        },
    )

    fun toChannelsCsv(data: BookData): String =
        encodeCsvRecords(listOf(CHANNEL_HEADER.split(",")) + data.channels.map {
            listOf(it.id, it.name, it.active.toString(), it.position.toString())
        })

    fun toIncomesCsv(data: BookData): String = encodeCsvRecords(
        listOf(INCOME_HEADER.split(",")) + data.incomes.map {
            listOf(it.id, it.title, it.amount.inputText(), it.receivedAt.toString(), it.source.name)
        },
    )

    private fun quote(value: String) = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }
}

// ---------------------------------------------------------------------------
// 导入解码
// ---------------------------------------------------------------------------

class BookDecoder(private val random: () -> String = { UUID.randomUUID().toString() }) {

    fun fromJson(text: String): BookData {
        val root = JsonReader(text).parseRoot() as? Map<*, *> ?: fail(BookError.IMPORT_MALFORMED, "JSON 顶层必须是对象")
        requireVersion(root[BookField.VERSION.label])
        val channels = list(root[BookField.CHANNELS.label], BookField.CHANNELS)
        val snapshots = list(root[BookField.SNAPSHOTS.label], BookField.SNAPSHOTS)
        val incomes = list(root[BookField.INCOMES.label], BookField.INCOMES)
        checkLimit(channels.size + snapshots.size + incomes.size)

        var row = 0
        val decodedChannels = channels.map { entry ->
            row++
            val record = entry as? Map<*, *> ?: fieldError(BookField.CHANNELS, row, "必须是对象")
            val id = identifier(record[BookField.ID.label], BookField.CHANNEL_ID, row)
            val name = text(record[BookField.NAME.label], BookField.NAME, row)
            if (name.length > 40) fieldError(BookField.NAME, row, "名称不超过40个字符")
            val active = record[BookField.ACTIVE.label] as? Boolean
                ?: fieldError(BookField.ACTIVE, row, "必须是布尔值")
            val position = record[BookField.POSITION.label] as? Long
                ?: fieldError(BookField.POSITION, row, "必须是整数")
            Channel(id, name, active, position.toInt())
        }
        if (decodedChannels.map { it.id }.distinct().size != decodedChannels.size) {
            fail(BookError.IMPORT_INVALID_FIELD, "渠道 ID 重复")
        }
        if (decodedChannels.map { it.name.lowercase() }.distinct().size != decodedChannels.size) {
            fail(BookError.IMPORT_DUPLICATE_TITLE, "渠道名称重复")
        }

        row = 0
        val decodedSnapshots = snapshots.map { entry ->
            row++
            val record = entry as? Map<*, *> ?: fieldError(BookField.SNAPSHOTS, row, "必须是对象")
            val registeredAt = decodeInstant(BookField.REGISTERED_AT, row, text(record[BookField.REGISTERED_AT.label], BookField.REGISTERED_AT, row))
            val month = decodeMonth(row, text(record[BookField.MONTH.label], BookField.MONTH, row), registeredAt)
            val liability = decodeMoney(BookField.LIABILITY, row, amount(record[BookField.LIABILITY.label], BookField.LIABILITY, row))
            val balances = list(record[BookField.BALANCES.label], BookField.BALANCES, row)
            if (balances.isEmpty()) fieldError(BookField.BALANCES, row, "至少需要一个渠道余额")
            val decodedBalances = balances.map { item ->
                val balance = item as? Map<*, *> ?: fieldError(BookField.BALANCES, row, "必须是对象")
                ChannelBalance(
                    identifier(balance[BookField.CHANNEL_ID.label], BookField.CHANNEL_ID, row),
                    text(balance[BookField.CHANNEL_NAME.label], BookField.CHANNEL_NAME, row),
                    decodeMoney(BookField.AMOUNT, row, amount(balance[BookField.AMOUNT.label], BookField.AMOUNT, row)),
                )
            }
            if (decodedBalances.map { it.channelId }.distinct().size != decodedBalances.size) {
                fieldError(BookField.CHANNEL_ID, row, "同一月份内渠道 ID 重复")
            }
            val snapshot = MonthlyAssetSnapshot(registeredAt, decodedBalances, liability)
            snapshot.total
            snapshot
        }
        if (decodedSnapshots.map { it.month }.distinct().size != decodedSnapshots.size) {
            fail(BookError.IMPORT_DUPLICATE_MONTH, "存在重复月份，同一个月只能有一份资产表")
        }

        row = 0
        val decodedIncomes = incomes.map { entry ->
            row++
            val record = entry as? Map<*, *> ?: fieldError(BookField.INCOMES, row, "必须是对象")
            val amount = decodeMoney(BookField.AMOUNT, row, amount(record[BookField.AMOUNT.label], BookField.AMOUNT, row))
            if (amount.fen <= 0) fieldError(BookField.AMOUNT, row, "收入金额必须大于0")
            val title = text(record[BookField.TITLE.label], BookField.TITLE, row).trim()
            if (title.isEmpty() || title.length > 120) fieldError(BookField.TITLE, row, "项目长度应为1到120个字符")
            Income(
                identifier(record[BookField.ID.label], BookField.ID, row),
                title,
                amount,
                decodeInstant(BookField.RECEIVED_AT, row, text(record[BookField.RECEIVED_AT.label], BookField.RECEIVED_AT, row)),
                decodeSource(record[BookField.SOURCE.label], row),
            )
        }
        if (decodedIncomes.map { it.id }.distinct().size != decodedIncomes.size) {
            fieldError(BookField.ID, null, "收入 ID 重复")
        }
        return BookData(decodedChannels, decodedSnapshots, decodedIncomes, BookSettings())
    }

    fun fromAssetsCsv(text: String): BookData = buildCsv(parseCsvRecords(text), BookSections.ASSETS)
    fun fromChannelsCsv(text: String): BookData = buildCsv(parseCsvRecords(text), BookSections.CHANNELS)
    fun fromIncomesCsv(text: String): BookData = buildCsv(parseCsvRecords(text), BookSections.INCOMES)

    private enum class BookSections { ASSETS, CHANNELS, INCOMES }

    /** 空行在解析后是 `[""]` 而不是空列表，必须按"所有单元格都为空"判断才会被跳过。 */
    private fun CsvRow.isBlank() = cells.all { it.isBlank() }

    private fun buildCsv(rows: List<CsvRow>, section: BookSections): BookData {
        if (rows.isEmpty()) fail(BookError.IMPORT_EMPTY_FILE, "文件没有表头行")
        val layout = CsvLayout(rows.first())
        // 空行的行号必须保留在内容行里，错误提示才能指向用户在表格软件里看到的行号。
        val content = rows.filterIndexed { index, row -> index != 0 && !row.isBlank() }
        checkLimit(content.size)
        when (section) {
            BookSections.CHANNELS -> {
                val channels = content.mapIndexed { order, row ->
                    val name = row.required(layout.indexOf(BookField.NAME, 1), BookField.NAME)
                    if (name.length > 40) fieldError(BookField.NAME, row.line, "名称不超过40个字符")
                    // 手工整理过的渠道表常常省略启用状态，此时按"启用"补默认值。
                    val active = when (val value = row.optional(layout.indexOf(BookField.ACTIVE, 2)).lowercase()) {
                        "true", "1", "是", "" -> true
                        "false", "0", "否" -> false
                        else -> fieldError(BookField.ACTIVE, row.line, "应为 true 或 false")
                    }
                    // 没有标识列时按表内顺序生成标识，方便重建渠道表。
                    val id = row.optional(layout.indexOf(BookField.CHANNEL_ID, 0)).ifEmpty { random() }
                    val position = row.optional(layout.indexOf(BookField.POSITION, 3)).toIntOrNull() ?: order
                    Channel(id, name, active, position)
                }
                if (channels.map { it.id }.toSet().size < channels.size) fieldError(BookField.CHANNEL_ID, null, "渠道 ID 重复")
                return BookData(channels = channels)
            }
            BookSections.ASSETS -> {
                val grouped = LinkedHashMap<YearMonth, Pair<Instant, MutableList<ChannelBalance>>>()
                val liabilities = HashMap<YearMonth, Money>()
                content.forEach { row ->
                    val monthText = row.required(layout.indexOf(BookField.MONTH, 0), BookField.MONTH)
                    val registeredAt = decodeInstant(BookField.REGISTERED_AT, row.line,
                        row.required(layout.indexOf(BookField.REGISTERED_AT, 1), BookField.REGISTERED_AT))
                    val month = decodeMonth(row.line, monthText, registeredAt)
                    val channelId = row.optional(layout.indexOf(BookField.CHANNEL_ID, 2)).ifEmpty { random() }
                    val balance = ChannelBalance(
                        channelId,
                        row.required(layout.indexOf(BookField.CHANNEL_NAME, 3), BookField.CHANNEL_NAME),
                        decodeMoney(BookField.AMOUNT, row.line, row.required(layout.indexOf(BookField.AMOUNT, 4), BookField.AMOUNT)),
                    )
                    val liability = decodeMoney(BookField.LIABILITY, row.line,
                        row.required(layout.indexOf(BookField.LIABILITY, 5), BookField.LIABILITY))
                    // 同一月份的所有行必须来自同一次登记，否则无法还原一份资产表。
                    liabilities[month]?.let { existing ->
                        if (existing != liability) fieldError(BookField.LIABILITY, row.line, "同一月份的负债不一致")
                    }
                    liabilities[month] = liability
                    grouped.getOrPut(month) { registeredAt to mutableListOf() }.let { entry ->
                        if (entry.first != registeredAt) fieldError(BookField.REGISTERED_AT, row.line, "同一月份的登记时间不一致")
                        entry.second.add(balance)
                    }
                }
                val snapshots = grouped.map { (month, value) ->
                    if (value.second.map { it.channelId }.distinct().size != value.second.size) {
                        fieldError(BookField.CHANNEL_ID, null, "月份 $month 内渠道 ID 重复")
                    }
                    MonthlyAssetSnapshot(value.first, value.second, liabilities.getValue(month))
                }
                snapshots.forEach { it.total }
                return BookData(snapshots = snapshots)
            }
            BookSections.INCOMES -> {
                val incomes = content.map { row ->
                    val title = row.required(layout.indexOf(BookField.TITLE, 1), BookField.TITLE)
                    if (title.length > 120) fieldError(BookField.TITLE, row.line, "项目不超过120个字符")
                    val amount = decodeMoney(BookField.AMOUNT, row.line, row.required(layout.indexOf(BookField.AMOUNT, 2), BookField.AMOUNT))
                    if (amount.fen <= 0) fieldError(BookField.AMOUNT, row.line, "收入金额必须大于0")
                    Income(
                        row.optional(layout.indexOf(BookField.ID, 0)),
                        title,
                        amount,
                        decodeInstant(BookField.RECEIVED_AT, row.line,
                            row.required(layout.indexOf(BookField.RECEIVED_AT, 3), BookField.RECEIVED_AT)),
                        decodeTextSource(row.optional(layout.indexOf(BookField.SOURCE, 4)), row.line),
                    )
                }
                if (incomes.map { it.id }.distinct().size != incomes.size) fieldError(BookField.ID, null, "收入 ID 重复")
                return BookData(incomes = incomes)
            }
        }
    }

    private fun checkLimit(size: Int) {
        if (size > MAX_IMPORT_RECORDS) fail(BookError.IMPORT_LIMIT_EXCEEDED, "记录数超过 $MAX_IMPORT_RECORDS 条")
    }

    private fun requireVersion(value: Any?) {
        val version = value as? Long ?: missingField(BookField.VERSION, null)
        if (version > BookExporter.FORMAT_VERSION) {
            fail(BookError.IMPORT_UNSUPPORTED_VERSION, "文件版本 $version 高于当前支持的 ${BookExporter.FORMAT_VERSION}")
        }
        if (version < 1) fieldError(BookField.VERSION, null, "版本号必须大于0")
    }

    private fun list(value: Any?, field: BookField, row: Int? = null): List<*> = value as? List<*>
        ?: if (value == null) missingField(field, row) else fieldError(field, row, "必须是数组")

    private fun identifier(value: Any?, field: BookField, row: Int?): String {
        val text = text(value, field, row)
        if (text.length > 128) fieldError(field, row, "标识长度不超过128个字符")
        return text
    }

    private fun text(value: Any?, field: BookField, row: Int?): String = value as? String
        ?: if (value == null) missingField(field, row) else fieldError(field, row, "必须是字符串")

    private fun amount(value: Any?, field: BookField, row: Int?): String = value as? String
        ?: if (value == null) missingField(field, row) else fieldError(field, row, "金额必须用字符串表示，避免浮点误差")

    private fun decodeSource(value: Any?, row: Int): IncomeSource {
        val text = text(value, BookField.SOURCE, row)
        return try {
            IncomeSource.valueOf(text)
        } catch (_: IllegalArgumentException) {
            fieldError(BookField.SOURCE, row, "来源只能是 MANUAL 或 SMS")
        }
    }

    private fun decodeTextSource(value: String, row: Int): IncomeSource = when (value.uppercase()) {
        "" -> IncomeSource.MANUAL
        "MANUAL" -> IncomeSource.MANUAL
        "SMS" -> IncomeSource.SMS
        else -> fieldError(BookField.SOURCE, row, "来源只能是 MANUAL 或 SMS")
    }
}

// ---------------------------------------------------------------------------
// 最小 JSON 读取器
// ---------------------------------------------------------------------------

/**
 * 只读取 JSON 的子集：对象、数组、字符串、整数和布尔值。
 * 金额一律用字符串表达，因此不需要处理浮点数，也就不会出现二进制浮点误差。
 * 重复键、尾随内容、非法转义都会直接报错，避免"看似成功但内容被截断"。
 */
internal class JsonReader(private val source: String) {
    private var index = 0
    private var depth = 0

    fun parseRoot(): Any? {
        skipWhitespace()
        val value = readValue()
        skipWhitespace()
        if (index != source.length) fail(BookError.IMPORT_MALFORMED, "JSON 结尾存在多余内容")
        return value
    }

    private fun readValue(): Any? {
        if (index >= source.length) fail(BookError.IMPORT_MALFORMED, "JSON 意外结束")
        return when (val char = source[index]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't', 'f', 'n' -> readLiteral()
            else -> if (char == '-' || char.isDigit()) readNumber() else fail(BookError.IMPORT_MALFORMED, "无法识别的位置 $index 处的字符")
        }
    }

    private fun readObject(): Map<String, Any?> {
        enter()
        index++
        val result = LinkedHashMap<String, Any?>()
        skipWhitespace()
        if (peek() == '}') { index++; leave(); return result }
        while (true) {
            skipWhitespace()
            if (peek() != '"') fail(BookError.IMPORT_MALFORMED, "对象键必须是字符串")
            val key = readString()
            if (result.containsKey(key)) fail(BookError.IMPORT_INVALID_FIELD, "字段 $key 重复出现")
            skipWhitespace()
            if (peek() != ':') fail(BookError.IMPORT_MALFORMED, "字段 $key 缺少冒号")
            index++
            skipWhitespace()
            result[key] = readValue()
            skipWhitespace()
            when (peek()) {
                ',' -> { index++; skipWhitespace(); if (peek() == '}') fail(BookError.IMPORT_MALFORMED, "对象存在多余的逗号") }
                '}' -> { index++; leave(); return result }
                else -> fail(BookError.IMPORT_MALFORMED, "对象在位置 $index 处缺少逗号或右花括号")
            }
        }
    }

    private fun readArray(): List<Any?> {
        enter()
        index++
        val result = mutableListOf<Any?>()
        skipWhitespace()
        if (peek() == ']') { index++; leave(); return result }
        while (true) {
            skipWhitespace()
            result.add(readValue())
            skipWhitespace()
            when (peek()) {
                ',' -> { index++; skipWhitespace(); if (peek() == ']') fail(BookError.IMPORT_MALFORMED, "数组存在多余的逗号") }
                ']' -> { index++; leave(); return result }
                else -> fail(BookError.IMPORT_MALFORMED, "数组在位置 $index 处缺少逗号或右方括号")
            }
        }
    }

    private fun readString(): String {
        index++
        val builder = StringBuilder()
        while (true) {
            if (index >= source.length) fail(BookError.IMPORT_MALFORMED, "字符串未闭合")
            when (val char = source[index]) {
                '"' -> { index++; return builder.toString() }
                '\\' -> {
                    index++
                    if (index >= source.length) fail(BookError.IMPORT_MALFORMED, "转义字符不完整")
                    when (val escape = source[index]) {
                        '"', '\\', '/' -> builder.append(escape)
                        'b' -> builder.append('\b')
                        'f' -> builder.append('\u000C')
                        'n' -> builder.append('\n')
                        'r' -> builder.append('\r')
                        't' -> builder.append('\t')
                        'u' -> {
                            if (index + 4 >= source.length) fail(BookError.IMPORT_MALFORMED, "unicode 转义不完整")
                            val hex = source.substring(index + 1, index + 5)
                            val code = hex.toIntOrNull(16) ?: fail(BookError.IMPORT_MALFORMED, "unicode 转义无效")
                            builder.append(code.toChar())
                            index += 4
                        }
                        else -> fail(BookError.IMPORT_MALFORMED, "不支持的转义字符")
                    }
                    index++
                }
                else -> {
                    if (char < ' ') fail(BookError.IMPORT_MALFORMED, "字符串中出现未转义的控制字符")
                    builder.append(char)
                    index++
                }
            }
        }
    }

    private fun readNumber(): Long {
        val start = index
        if (peek() == '-') index++
        while (index < source.length && source[index].isDigit()) index++
        if (index < source.length && source[index] == '.') fail(BookError.IMPORT_MALFORMED, "JSON 中不支持小数，金额请用字符串")
        if (index == start || source.substring(start, index) == "-") fail(BookError.IMPORT_MALFORMED, "数字格式无效")
        return source.substring(start, index).toLongOrNull() ?: fail(BookError.IMPORT_MALFORMED, "整数超出范围")
    }

    private fun readLiteral(): Any = when {
        source.startsWith("true", index) -> { index += 4; true }
        source.startsWith("false", index) -> { index += 5; false }
        source.startsWith("null", index) -> { index += 4; Unit }
        else -> fail(BookError.IMPORT_MALFORMED, "无法识别的字面量")
    }

    private fun peek(): Char = source.getOrNull(index) ?: '\u0000'

    private fun skipWhitespace() {
        while (index < source.length && source[index].let { it == ' ' || it == '\t' || it == '\n' || it == '\r' }) index++
    }

    /** 限制嵌套深度，防止深层嵌套的恶意文件触发栈溢出。 */
    private fun enter() {
        depth++
        if (depth > 64) fail(BookError.IMPORT_MALFORMED, "嵌套层级过深")
    }

    private fun leave() { depth-- }
}
