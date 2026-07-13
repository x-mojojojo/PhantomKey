package com.phantomkey.app.data

import com.phantomkey.app.crypto.PasswordType
import kotlinx.serialization.Serializable

/**
 * Local metadata for a previously used site.
 * NEVER stores passwords or the master key — only generation parameters.
 */
@Serializable
data class SiteHistoryEntry(
    val site: String,
    val counter: Int = 1,
    val passwordType: String = PasswordType.MAXIMUM.name,
    val lastUsedAt: Long = System.currentTimeMillis(),
) {
    fun type(): PasswordType = PasswordType.fromName(passwordType)
}

@Serializable
data class HistoryExport(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val entries: List<SiteHistoryEntry> = emptyList(),
)
