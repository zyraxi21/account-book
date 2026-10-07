package io.github.zyraxi21.accountbook.domain

import java.util.Locale

/** 合并只返回需要新增的记录；相同标识和月份以本机为准，不重写旧数据。 */
fun prepareBookImport(existing: BookData, incoming: BookData, mode: ImportMode): BookData {
    if (incoming.channels.map { it.id }.distinct().size != incoming.channels.size ||
        incoming.incomes.map { it.id }.distinct().size != incoming.incomes.size) throw BookException(BookError.IMPORT_INVALID_FIELD)
    if (incoming.snapshots.map { it.month }.distinct().size != incoming.snapshots.size) throw BookException(BookError.IMPORT_DUPLICATE_MONTH)
    if (incoming.channels.any { it.id.isBlank() || it.name.isBlank() || it.name.length > 40 } ||
        incoming.incomes.any { it.id.isBlank() || it.title.isBlank() || it.title.length > 120 || it.amount.fen <= 0 }) {
        throw BookException(BookError.IMPORT_INVALID_FIELD)
    }
    val knownIds = incoming.channels.map { it.id }.toSet() +
        if (mode == ImportMode.MERGE) existing.channels.map { it.id }.toSet() else emptySet()
    incoming.snapshots.forEach { snapshot ->
        if (snapshot.balances.isEmpty() || snapshot.liability.fen < 0 ||
            snapshot.balances.any { it.channelId !in knownIds || it.channelName.isBlank() || it.amount.fen < 0 } ||
            snapshot.balances.map { it.channelId }.distinct().size != snapshot.balances.size) {
            throw BookException(BookError.IMPORT_INVALID_FIELD)
        }
        snapshot.net
        try { snapshot.registeredAt.toEpochMilli() } catch (error: ArithmeticException) {
            throw BookException(BookError.IMPORT_INVALID_DATE, error)
        }
    }
    incoming.incomes.forEach {
        try { it.receivedAt.toEpochMilli() } catch (error: ArithmeticException) {
            throw BookException(BookError.IMPORT_INVALID_DATE, error)
        }
    }
    if (mode == ImportMode.REPLACE) { incoming.cumulativeIncome; return incoming }
    val ids = existing.channels.map { it.id }.toSet()
    val names = existing.activeChannels.map { it.name.lowercase(Locale.ROOT) }.toMutableSet()
    var position = existing.channels.maxOfOrNull { it.position } ?: -1
    val channels = incoming.channels.filter { it.id !in ids }.sortedBy { it.position }.map { channel ->
        val name = channel.name.trim()
        var candidate = name
        var suffix = 2
        while (channel.active && candidate.lowercase(Locale.ROOT) in names) {
            val ending = " ($suffix)"
            candidate = name.take(40 - ending.length) + ending
            suffix++
        }
        if (channel.active) names.add(candidate.lowercase(Locale.ROOT))
        channel.copy(name = candidate, position = Math.incrementExact(position).also { position = it })
    }
    val months = existing.snapshots.map { it.month }.toSet()
    val incomeIds = existing.incomes.map { it.id }.toSet()
    val incomes = incoming.incomes.filter { it.id !in incomeIds }
    Money.sum((existing.incomes + incomes).map { it.amount })
    return incoming.copy(channels = channels, snapshots = incoming.snapshots.filter { it.month !in months },
        incomes = incomes, settings = existing.settings)
}
