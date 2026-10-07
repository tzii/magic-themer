package dev.magicthemer.icons

data class IconPack(val packageName: String, val label: String)

data class PackData(
    val pack: IconPack,
    val mappings: List<IconMapping>,
    val warnings: List<String>,
    val drawables: List<String> = mappings.map { it.drawableName }.distinct(),
    /** Metadata can name resources that no longer exist in the installed APK. */
    val unavailableDrawables: Set<String> = emptySet(),
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
