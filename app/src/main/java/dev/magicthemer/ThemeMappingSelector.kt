package dev.magicthemer

import dev.magicthemer.icons.AppCategory
import dev.magicthemer.icons.IconMapping

data class ApplicationTarget(
    val packageName: String,
    val label: String,
    val activities: List<String>,
    /** Package named by the launcher icon's resource table; null when unknown. */
    val resourcePackage: String? = null,
    /** Kinds Android reports for the app: declared category, games, media players. */
    val categories: Set<AppCategory> = emptySet(),
) {
    /**
     * HONOR's theme engine registers an icon under the installed package but
     * resolves it under the icon's resource package. Builds renamed after
     * compilation, such as DroidDeck's AnTuTu variant, never match, so no theme
     * file can change their icon.
     */
    val honorThemeable: Boolean get() = resourcePackage == null || resourcePackage == packageName
}

/** HONOR's package-level icon filenames can represent one launcher icon per package. */
object ThemeMappingSelector {
    fun select(
        mappings: List<IconMapping>,
        apps: List<ApplicationTarget>,
        includeAll: Boolean,
        overrides: Map<String, IconMapping> = emptyMap(),
    ): List<IconMapping> {
        val appByPackage = apps.associateBy { it.packageName }
        val groups = mappings.groupBy { it.packageName }
        val packages = if (includeAll) groups.keys + overrides.keys else appByPackage.keys
        return packages.sorted().mapNotNull { packageName ->
            overrides[packageName] ?: groups[packageName]?.let { options ->
                val activities = appByPackage[packageName]?.activities.orEmpty()
                options.firstOrNull { it.activityName in activities } ?: options.firstOrNull()
            }
        }
    }
}
