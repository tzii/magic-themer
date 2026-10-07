package dev.magicthemer

import dev.magicthemer.icons.IconMapping

data class ApplicationTarget(val packageName: String, val label: String, val activities: List<String>)

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
