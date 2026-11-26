package dev.magicthemer.icons

data class IconPack(val packageName: String, val label: String)

/** Fallback styling declared by a pack's appfilter.xml for apps without explicit icons. */
data class PackFallbackStyle(
    val backgrounds: List<String> = emptyList(),
    val masks: List<String> = emptyList(),
    val upons: List<String> = emptyList(),
    val scale: Float = 1f,
) {
    val hasAssets: Boolean get() = backgrounds.isNotEmpty() || masks.isNotEmpty() || upons.isNotEmpty()
}

/** A dynamic day-of-month calendar entry from appfilter.xml (`prefix` + 1..31). */
data class CalendarMapping(
    val component: String,
    val packageName: String,
    val activityName: String,
    val prefix: String,
)

data class PackData(
    val pack: IconPack,
    val mappings: List<IconMapping>,
    val warnings: List<String>,
    val drawables: List<String> = mappings.map { it.drawableName }.distinct(),
    /** Metadata can name resources that no longer exist in the installed APK. */
    val unavailableDrawables: Set<String> = emptySet(),
    val fallbackStyle: PackFallbackStyle = PackFallbackStyle(),
    val calendarMappings: List<CalendarMapping> = emptyList(),
) {
    /** Existence only: a resource may still fail to decode when its preview is requested. */
    fun isDrawableAvailable(name: String): Boolean = name !in unavailableDrawables
}

/** Components are canonicalized; activityName is empty for package-only mappings. */
data class IconMapping(
    val component: String,
    val packageName: String,
    val activityName: String,
    val drawableName: String,
)
