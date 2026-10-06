package io.github.zyraxi21.accountbook.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth

class BookSerializationTest {
    private val exportedAt = Instant.parse("2026-10-07T02:00:00Z")
    private val sample = BookData(
        channels = listOf(
            Channel("bank", "银行", true, 0),
            Channel("alipay", "支付宝, \"主\"", true, 1),
            Channel("旧渠道", "已停用", false, 2),
        ),
        snapshots = listOf(
            MonthlyAssetSnapshot(instant("2026-09-30T12:00:00"), listOf(
                ChannelBalance("bank", "银行", Money.parse("10000.50")),
                ChannelBalance("alipay", "支付宝, \"主\"", Money.parse("0.01")),
            ), Money.parse("1500")),
            MonthlyAssetSnapshot(instant("2026-10-06T04:35:00"), listOf(
                ChannelBalance("bank", "银行改名后", Money(-1)),
            ), Money.ZERO),
        ),
        incomes = listOf(
            Income("salary", "工资 含,逗号 \"引号\"", Money.parse("1000.00"), instant("2026-10-06T12:34:00"), IncomeSource.SMS),
            Income("bonus", "奖金", Money.parse("0.01"), instant("2026-09-01T00:00:00")),
        ),
    )

    @Test fun jsonRoundTripKeepsAmountsSignsAndSources() {
        val restored = BookDecoder().fromJson(BookExporter.toJson(sample, exportedAt))
        assertEquals(sample.channels, restored.channels)
        assertEquals(sample.snapshots.sortedBy { it.month }, restored.snapshots.sortedBy { it.month })
        assertEquals(sample.incomes.sortedBy { it.id }, restored.incomes.sortedBy { it.id })
        // 金额以分存储，往返后不能出现浮点误差或符号丢失。
        val negative = restored.snapshots.first { it.balances.any { balance -> balance.amount.fen < 0 } }
        assertEquals(Money(-1), negative.balances.first { it.amount.fen < 0 }.amount)
        assertEquals(Money.parse("10000.50"), restored.snapshots.flatMap { it.balances }.first { it.channelId == "bank" && it.amount.fen > 0 }.amount)
    }

    @Test fun jsonRejectsUnsupportedAndMalformedInput() {
        // 顶层不是对象，或对象缺少必要字段（此时按"缺少字段"报错）。
        assertError(BookError.IMPORT_MALFORMED) { BookDecoder().fromJson("[]") }
        assertError(BookError.IMPORT_MISSING_FIELD) { BookDecoder().fromJson("{\"version\": 1, \"channels\": []}") }
        // 顶层完整但存在多余的尾随内容，属于结构性损坏。
        assertError(BookError.IMPORT_MALFORMED) {
            BookDecoder().fromJson("{\"version\":1,\"channels\":[],\"snapshots\":[],\"incomes\":[]} {\"a\":2}")
        }
        assertError(BookError.IMPORT_MISSING_FIELD) { BookDecoder().fromJson("{\"channels\": [], \"snapshots\": [], \"incomes\": []}") }
        assertError(BookError.IMPORT_UNSUPPORTED_VERSION) {
            BookDecoder().fromJson("{\"version\": 2, \"channels\": [], \"snapshots\": [], \"incomes\": []}")
        }
    }

    @Test fun jsonRejectsFloatingPointMoneyAndInconsistentMonth() {
        // 金额写成 JSON 数字会丢失精度，必须被拒绝并提示改用字符串。
        assertError(BookError.IMPORT_MALFORMED) { BookDecoder().fromJson(jsonWithAmount("100.50")) }
        assertError(BookError.IMPORT_INVALID_FIELD) { BookDecoder().fromJson(jsonWithAmount("\"100.505\"")) }
        assertError(BookError.IMPORT_INVALID_FIELD) { BookDecoder().fromJson(jsonWithAmount("\"1e3\"")) }
        // 月份与登记时间不一致会让资产表和收入明细对不上，属于错误而不是可静默归一。
        assertError(BookError.IMPORT_INVALID_FIELD) {
            BookDecoder().fromJson(jsonWithMonth("2026-09", "2026-10-01T00:00:00Z"))
        }
        assertError(BookError.IMPORT_INVALID_DATE) {
            BookDecoder().fromJson(jsonWithMonth("2026-10", "昨天"))
        }
    }

    private fun jsonWithAmount(amount: String) = baseJson(
        snapshots = """[{"month":"2026-10","registeredAt":"2026-10-01T00:00:00Z","liability":"0","balances":[{"channelId":"a","channelName":"甲","amount": $amount}]}]""",
    )

    /** 指定资产表的月份与登记时间，用来验证两者一致性校验。 */
    private fun jsonWithMonth(month: String, registeredAt: String) = baseJson(
        snapshots = """[{"month":"$month","registeredAt":"$registeredAt","liability":"0","balances":[{"channelId":"a","channelName":"甲","amount":"1"}]}]""",
    )

    @Test fun csvExportRoundTripsAndEscapesDelimiters() {
        val text = BookExporter.toAssetsCsv(sample)
        // 含逗号和引号的渠道名必须被正确转义，解析后逐字还原。
        val restored = BookDecoder().fromAssetsCsv(text)
        assertEquals(sample.snapshots.map { it.month }.toSet(), restored.snapshots.map { it.month }.toSet())
        assertEquals("支付宝, \"主\"", restored.snapshots.flatMap { it.balances }.first { it.channelId == "alipay" }.channelName)
        assertEquals(Money.parse("10000.50"), restored.snapshots.flatMap { it.balances }.first { it.channelId == "bank" && it.amount.fen > 0 }.amount)
    }

    @Test fun channelAndIncomeCsvRoundTrip() {
        assertEquals(sample.channels, BookDecoder().fromChannelsCsv(BookExporter.toChannelsCsv(sample)).channels)
        val restored = BookDecoder().fromIncomesCsv(BookExporter.toIncomesCsv(sample))
        // 项目里含逗号和引号，往返后必须逐字还原。
        assertEquals("工资 含,逗号 \"引号\"", restored.incomes.first { it.id == "salary" }.title)
        assertEquals("奖金", restored.incomes.first { it.id == "bonus" }.title)
        assertEquals(2, restored.incomes.size)
        // 导出以换行结尾，末尾的空行不应被当成一条空记录。
        assertEquals(2, BookDecoder().fromIncomesCsv(BookExporter.toIncomesCsv(sample) + "\r\n").incomes.size)
    }

    @Test fun negativeLiabilitySurvivesCsvAndJsonRoundTrip() {
        // 负债允许为负。Money.parse 服务于输入框、不接受负号，因此这条覆盖解码器自己处理符号的路径。
        val negative = BookData(
            channels = listOf(Channel("bank", "银行", true, 0)),
            snapshots = listOf(MonthlyAssetSnapshot(instant("2026-10-06T04:35:00"), listOf(
                ChannelBalance("bank", "银行", Money(-1)),
            ), Money(-1))),
        )
        val viaCsv = BookDecoder().fromAssetsCsv(BookExporter.toAssetsCsv(negative)).snapshots.single()
        assertEquals(-1L, viaCsv.balances.single().amount.fen)
        assertEquals(-1L, viaCsv.liability.fen)
        val viaJson = BookDecoder().fromJson(BookExporter.toJson(negative, exportedAt)).snapshots.single()
        assertEquals(-1L, viaJson.balances.single().amount.fen)
        assertEquals(-1L, viaJson.liability.fen)
    }

    @Test fun channelCsvAcceptsHeaderWithoutTheIdColumn() {
        // 手工整理过的渠道表常常省略标识列，此时不应被当成"缺少标识"而拒绝。
        val decoded = BookDecoder().fromChannelsCsv("渠道名称,启用\r\n银行,true\r\n支付宝,true\r\n")
        assertEquals(listOf("银行", "支付宝"), decoded.channels.map { it.name })
        assertTrue(decoded.channels.all { it.active })
        assertEquals(listOf(0, 1), decoded.channels.map { it.position })
    }

    @Test fun csvReportsLineNumberAndRejectsInvalidCells() {
        val header = "月份,登记时间,渠道ID,渠道名称,余额,负债"
        // 缺字段、非法月份、非法负债、损坏的引号各自对应不同的错误，提示需要能区分。
        assertError(BookError.IMPORT_MISSING_FIELD) { BookDecoder().fromAssetsCsv("$header\r\n2026-10,,") }
        assertError(BookError.IMPORT_INVALID_FIELD) {
            BookDecoder().fromAssetsCsv("$header\r\n坏月份,2026-10-01T00:00:00Z,bank,银行,1.00,0")
        }
        assertError(BookError.IMPORT_INVALID_FIELD) {
            BookDecoder().fromAssetsCsv("$header\r\n2026-10,2026-10-01T00:00:00Z,bank,银行,1.00,坏值")
        }
        assertError(BookError.IMPORT_MALFORMED) {
            BookDecoder().fromAssetsCsv("$header\r\n\"未闭合,2026-10-01T00:00:00Z,bank,银行,1.00,0")
        }
        assertError(BookError.IMPORT_INVALID_FIELD) {
            BookDecoder().fromIncomesCsv("收入ID,项目,金额,日期时间,来源\r\nid,项目,100.00,2026-10-01T00:00:00Z,OTHER")
        }
        // 收入标识缺失时补一个，但已经写出来的重复标识仍然要拒绝。
        assertError(BookError.IMPORT_INVALID_FIELD) {
            BookDecoder().fromIncomesCsv(
                "收入ID,项目,金额,日期时间,来源\r\nid,项目一,100.00,2026-10-01T00:00:00Z,SMS\r\nid,项目二,200.00,2026-10-02T00:00:00Z,SMS",
            )
        }
    }

    @Test fun csvDistinguishesEmptyFileFromHeaderOnlyFile() {
        // 完全没有内容：连表头都没有，单独给一个提示。
        assertError(BookError.IMPORT_EMPTY_FILE) { BookDecoder().fromAssetsCsv("") }
        // 只有表头没有数据行：这不是损坏，而是没有可写入的记录，按空账本处理。
        val headerOnly = BookDecoder().fromAssetsCsv("月份,登记时间,渠道ID,渠道名称,余额,负债\r\n")
        assertTrue(headerOnly.snapshots.isEmpty())
        assertTrue(headerOnly.incomes.isEmpty())
    }

    @Test fun csvRejectsInconsistentMonthRows() {
        val header = "月份,登记时间,渠道ID,渠道名称,余额,负债"
        // 同一月份的两行负债不同，说明文件被手工改坏，不能静默取其中一行。
        assertError(BookError.IMPORT_INVALID_FIELD) {
            BookDecoder().fromAssetsCsv(
                "$header\r\n2026-10-01T00:00:00Z,2026-10-01T00:00:00Z,bank,银行,1.00,0" +
                    "\r\n2026-10-01T00:00:00Z,2026-10-01T00:00:00Z,alipay,支付宝,2.00,50",
            )
        }
    }

    @Test fun csvKeepsBlankRowsFromShiftingLineNumbers() {
        val header = "月份,登记时间,渠道ID,渠道名称,余额,负债"
        // 文件中间的空行必须保留行号：用户在表格软件里看到的第 3 行就是报错里的第 3 条记录。
        // 空行里塞一个非法月份，才能把"行号错位"和"缺字段"两类问题区分开。
        try {
            BookDecoder().fromAssetsCsv("$header\r\n\r\n坏月份,2026-10-01T00:00:00Z,bank,银行,1.00,0")
            fail("非法月份应当被拒绝")
        } catch (error: BookException) {
            assertEquals(BookError.IMPORT_INVALID_FIELD, error.error)
            assertTrue("错误说明应指向第 3 条记录，实际为 ${error.cause?.message}",
                error.cause?.message?.contains("第 3 条记录") == true)
        }
        // 只有行尾的空行不携带内容，应当被跳过而不是报错；导出文件本来就以换行结尾。
        assertEquals(1, BookDecoder().fromAssetsCsv(
            "$header\r\n2026-10,2026-10-01T00:00:00Z,bank,银行,1.00,0\r\n\r\n",
        ).snapshots.size)
        assertEquals(1, BookDecoder().fromAssetsCsv(
            "$header\r\n2026-10,2026-10-01T00:00:00Z,bank,银行,1.00,0\r\n",
        ).snapshots.size)
    }

    @Test fun csvUsesHeaderNamesSoColumnsCanBeReordered() {
        val header = "负债,余额,渠道名称,渠道ID,登记时间,月份"
        val snapshot = BookDecoder().fromAssetsCsv(
            "$header\r\n1500.00,10000.50,银行,bank,2026-09-30T04:00:00Z,2026-09",
        ).snapshots.single()
        assertEquals(Money.parse("1500"), snapshot.liability)
        assertEquals(Money.parse("10000.50"), snapshot.total)
        assertEquals("银行", snapshot.balances.single().channelName)
    }

    @Test fun jsonRejectsDuplicateMonthsAndChannels() {
        assertError(BookError.IMPORT_DUPLICATE_MONTH) {
            BookDecoder().fromJson(
                baseJson(
                    snapshots = """[{"month":"2026-10","registeredAt":"2026-10-01T00:00:00Z","liability":"0","balances":[{"channelId":"a","channelName":"甲","amount":"1"}]},
                        {"month":"2026-10","registeredAt":"2026-10-01T00:00:00Z","liability":"0","balances":[{"channelId":"a","channelName":"甲","amount":"1"}]}]""",
                ),
            )
        }
        assertError(BookError.IMPORT_DUPLICATE_TITLE) {
            BookDecoder().fromJson(
                baseJson(channels = """[{"id":"a","name":"银行","active":true,"position":0},{"id":"b","name":"银行","active":true,"position":1}]"""),
            )
        }
    }

    @Test fun jsonRejectsRecordCountBeyondLimit() {
        val channels = (0..MAX_IMPORT_RECORDS).joinToString(",") { """{"id":"c$it","name":"渠道$it","active":true,"position":$it}""" }
        assertError(BookError.IMPORT_LIMIT_EXCEEDED) { BookDecoder().fromJson(baseJson(channels = "[$channels]")) }
    }

    private fun jsonWithBalance(overridden: String) = baseJson(
        snapshots = """[{"month":"2026-10","registeredAt":"2026-10-01T00:00:00Z","liability":"0","balances":[{"channelId":"a","channelName":"甲",$overridden}]}]""",
    )

    private fun baseJson(channels: String = "[]", snapshots: String = "[]", incomes: String = "[]") =
        """{"version":1,"channels":$channels,"snapshots":$snapshots,"incomes":$incomes}"""

    private fun instant(value: String) = LocalDateTime.parse(value).atZone(BOOK_ZONE).toInstant()

    private fun assertError(expected: BookError, block: () -> Unit) {
        try {
            block()
            fail("应当以 $expected 拒绝输入")
        } catch (error: BookException) {
            assertEquals(expected, error.error)
        }
    }
}
