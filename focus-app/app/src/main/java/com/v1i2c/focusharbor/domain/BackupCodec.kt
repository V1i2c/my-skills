package com.v1i2c.focusharbor.domain

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val MAX_USAGE_RECORDS = 10_000
private const val MAX_DAILY_STATS = 730
private const val MAX_BLOCKED_NOTIFICATIONS = 2_000
private const val MAX_DOMAINS = 20_000
private const val MAX_CSV_CELLS = MAX_DOMAINS + 1

object BackupCodec {
    const val MAX_BACKUP_BYTES = 5 * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(state: FocusAppState): String {
        val encoded = json.encodeToString(state)
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Backup is larger than 5 MiB" }
        return encoded
    }

    fun decode(raw: String): FocusAppState {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Backup is larger than 5 MiB" }
        val decoded = json.decodeFromString<FocusAppState>(raw)
        require(decoded.schemaVersion in 1..FocusAppState.CURRENT_SCHEMA_VERSION) {
            "This backup was created by a newer or unsupported version"
        }
        require(decoded.profiles.size <= 10_000) { "Backup has too many profiles" }
        require(decoded.profiles.map { it.id }.toSet().size == decoded.profiles.size) { "Backup has duplicate profile IDs" }
        require(decoded.usage.size <= MAX_USAGE_RECORDS) { "Backup has too many usage records" }
        require(decoded.dailyStats.size <= MAX_DAILY_STATS) { "Backup has too many daily-stat records" }
        require(decoded.blockedNotifications.size <= MAX_BLOCKED_NOTIFICATIONS) { "Backup has too many notification records" }
        decoded.quickBlock?.let { quickBlock ->
            require(quickBlock.appPackages.size <= 10_000) { "Quick Block has too many apps" }
            require(quickBlock.websiteDomains.size <= MAX_DOMAINS) { "Quick Block has too many domains" }
            require(quickBlock.websiteDomains.all { DomainMatcher.normalize(it) != null }) { "Quick Block contains an invalid domain" }
        }
        require(decoded.settings.adultDomains.size <= MAX_DOMAINS) { "Adult-domain list is too large" }
        require(decoded.settings.adultDomains.all { DomainMatcher.normalize(it) != null }) { "Backup contains an invalid adult-domain entry" }
        require(decoded.settings.quickBlockDomains.size <= MAX_DOMAINS) { "Quick Block domain list is too large" }
        require(decoded.settings.quickBlockDomains.all { DomainMatcher.normalize(it) != null }) { "Backup contains an invalid Quick Block domain" }
        decoded.profiles.forEach { profile ->
            require(profile.id.isNotBlank()) { "Profile IDs cannot be blank" }
            require(profile.name.isNotBlank() && profile.name.length <= 80) { "Profile names must contain 1–80 characters" }
            require(profile.appPackages.size <= 10_000) { "Profile has too many apps" }
            require(profile.websiteDomains.size <= MAX_DOMAINS) { "Profile has too many domains" }
            require(profile.websiteDomains.all { DomainMatcher.normalize(it) != null }) { "Backup contains an invalid website domain" }
            require(profile.quotas.size <= 10_000) { "Profile has too many quotas" }
            require(profile.schedules.size <= 512) { "Profile has too many schedule rules" }
        }
        return decoded
    }
}

object WebsiteCsvCodec {
    fun parseDomains(csv: String): Set<String> {
        require(csv.toByteArray(Charsets.UTF_8).size <= BackupCodec.MAX_BACKUP_BYTES) { "CSV is larger than 5 MiB" }
        val cells = parseCsvCells(csv)
        val domains = cells.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filterNot { it.equals("domain", ignoreCase = true) || it.equals("website", ignoreCase = true) || it.equals("url", ignoreCase = true) }
            .map { value ->
                DomainMatcher.normalize(value)
                    ?: throw IllegalArgumentException("Invalid domain in CSV: ${value.take(120)}")
            }
            .toCollection(linkedSetOf())
        require(domains.size <= MAX_DOMAINS) { "CSV contains too many website domains" }
        return domains
    }

    fun encodeDomains(domains: Set<String>): String = buildString {
        appendLine("domain")
        domains.mapNotNull(DomainMatcher::normalize).sorted().forEach { appendLine(quote(it)) }
    }

    private fun quote(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun parseCsvCells(input: String): List<String> {
        val output = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < input.length) {
            val char = input[index]
            when {
                char == '"' && quoted && input.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index += 1
                }
                char == '"' -> quoted = !quoted
                (char == ',' || char == '\n' || char == '\r') && !quoted -> {
                    require(output.size < MAX_CSV_CELLS) { "CSV contains too many fields" }
                    output += cell.toString()
                    cell.clear()
                    if (char == '\r' && input.getOrNull(index + 1) == '\n') index += 1
                }
                else -> cell.append(char)
            }
            index += 1
        }
        require(!quoted) { "CSV contains an unclosed quoted field" }
        require(output.size < MAX_CSV_CELLS) { "CSV contains too many fields" }
        output += cell.toString()
        return output
    }
}
