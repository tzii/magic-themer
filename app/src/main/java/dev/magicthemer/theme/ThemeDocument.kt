package dev.magicthemer.theme

import java.io.File

/** A validated theme plus a private, file-backed working copy of its icons module. */
@ConsistentCopyVisibility
data class ThemeDocument internal constructor(
    val sourceFile: File,
    val title: String,
    val designer: String,
    val briefInfo: String,
    val iconEntries: Map<String, String>,
    val warnings: List<String>,
    val compatibilityRepairAvailable: Boolean,
    internal val iconsFile: File,
    internal val packageEntries: Map<String, List<String>>,
    internal val sourceDigest: ByteArray,
    internal val iconsDigest: ByteArray,
)
