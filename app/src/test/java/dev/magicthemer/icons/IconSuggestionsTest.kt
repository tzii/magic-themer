package dev.magicthemer.icons

import dev.magicthemer.ApplicationTarget
import org.junit.Assert.*
import org.junit.Test

class IconSuggestionsTest {
    private fun pack(
        names: List<String>,
        mappings: List<IconMapping> = emptyList(),
        unavailable: Set<String> = emptySet(),
    ) = PackData(IconPack("dev.test.pack", "Test"), mappings, emptyList(), names, unavailable)

    private fun mapping(pkg: String, activity: String, drawable: String) =
        IconMapping("ComponentInfo{$pkg/$activity}", pkg, activity, drawable)

    private fun target(label: String, pkg: String = "dev.test.unrelated", activities: List<String> = emptyList(),
        categories: Set<AppCategory> = emptySet()) = ApplicationTarget(pkg, label, activities, categories = categories)

    @Test fun explicitLauncherAssociationOutranksOtherMappingsAndInferredNames() {
        val pkg = "com.example.calendar"
        val index = IconSuggestions(pack(listOf("calendar", "brand_hidden", "brand_launcher"), listOf(
            mapping(pkg, "$pkg.Hidden", "brand_hidden"),
            mapping(pkg, "$pkg.Main", "brand_launcher"),
        )))
        val suggestions = index.suggest(target("Calendar", pkg, listOf("$pkg.Main")))
        assertEquals(listOf("brand_launcher", "brand_hidden", "calendar"), suggestions.map { it.drawableName })
        assertEquals("Matches launcher activity", suggestions.first().reason)
        assertEquals("Mapped to this app", suggestions[1].reason)
    }

    @Test fun missingResourcesAreExcludedEvenWhenTheirNamesAndMappingsMatchExactly() {
        val pkg = "com.example.calendar"
        val data = pack(listOf("calendar", "calendar_alt"), listOf(
            mapping(pkg, "$pkg.Main", "calendar"),
            mapping(pkg, "$pkg.Other", "calendar_alt"),
        ), setOf("calendar"))
        assertFalse(data.isDrawableAvailable("calendar"))
        assertTrue(data.isDrawableAvailable("calendar_alt"))
        assertEquals(listOf("calendar_alt"), IconSuggestions(data)
            .suggest(target("Calendar", pkg, listOf("$pkg.Main"))).map { it.drawableName })
    }

    @Test fun normalizationHandlesAccentsCamelCaseAndNumericCataloguePrefixes() {
        val index = IconSuggestions(pack(listOf("_061_cafe_music", "CafeMusic", "cafe_music_alt", "_1_1_1_1")))
        val suggestions = index.suggest(target("Café Music"))
        assertEquals(setOf("_061_cafe_music", "CafeMusic", "cafe_music_alt"), suggestions.map { it.drawableName }.toSet())
        assertEquals("Cafe Music", suggestions.first { it.drawableName == "_061_cafe_music" }.label)
        assertTrue(suggestions.first { it.drawableName == "CafeMusic" }.score > suggestions.first { it.drawableName == "cafe_music_alt" }.score)
    }

    @Test fun appNameFindsUnmappedAlternativesWithoutInventingAssignments() {
        val data = pack(listOf("youtube_alt", "youtube_round", "notebooking", "calendar"))
        val suggestions = IconSuggestions(data).suggest(target("YouTube Morphe", "app.morphe.android.youtube"))
        assertEquals(listOf("youtube_alt", "youtube_round"), suggestions.map { it.drawableName })
        assertTrue(data.mappings.isEmpty())
        assertEquals("Shares app name words", suggestions.first().reason)
    }

    @Test fun fusedAppNameFindsOnlyArtworkContainingItsCompleteSeparatedName() {
        val data = pack(listOf("archive_tune_alt", "archive_tune_round", "archive_alt", "tune_alt", "archive_tuner_alt"))
        val suggestions = IconSuggestions(data).suggest(target("ArchiveTune"))
        // The name matches lead; "tune" then makes it a music app, never an archive one.
        assertEquals(listOf("archive_tune_alt", "archive_tune_round", "tune_alt"), suggestions.map { it.drawableName })
        assertEquals(listOf("Matches app name", "Matches app name", "Music icon"), suggestions.map { it.reason })
        assertTrue(data.mappings.isEmpty())
    }

    @Test fun fusedNameFallbackDoesNotDropMeaningfulAppNameWordsOrChangeBrandMatches() {
        val index = IconSuggestions(pack(listOf("archive_tune_alt", "youtube_alt", "you_alt", "tube_alt", "chat_gpt_alt")))
        assertEquals(listOf("Music icon"), index.suggest(target("ArchiveTunePlus")).map { it.reason })
        assertEquals(listOf("youtube_alt"), index.suggest(target("YouTube")).map { it.drawableName })
        assertEquals(listOf("chat_gpt_alt"), index.suggest(target("ChatGPT")).map { it.drawableName })
    }

    @Test fun knownPackageAliasesHandleRenamedOrLocalizedAppLabels() {
        val index = IconSuggestions(pack(listOf("twitter", "x", "drive", "calendar", "photos")))
        val x = index.suggest(target("X", "com.twitter.android"))
        assertEquals(listOf("x", "twitter"), x.map { it.drawableName })
        val drive = index.suggest(target("Disque", "com.google.android.apps.docs"))
        assertEquals(listOf("drive"), drive.map { it.drawableName })
        assertEquals("Known app name", drive.first().reason)
    }

    @Test fun meaningfulPackageNameProvidesALowerRankedFallback() {
        val suggestions = IconSuggestions(pack(listOf("youtube_alt", "calendar", "google")))
            .suggest(target("Privato", "app.morphe.android.youtube"))
        assertEquals(listOf("youtube_alt"), suggestions.map { it.drawableName })
        assertEquals("Matches package name", suggestions.first().reason)
    }

    @Test fun commonPlatformBrandAndDigitWordsDoNotSuggestUnrelatedApps() {
        val index = IconSuggestions(pack(listOf("google", "android", "app", "main", "amazon_music", "_1_1_1", "notebooking")))
        assertTrue(index.suggest(target("Google Android App 123", "com.google.android.apps.unknown")).isEmpty())
        assertTrue(index.suggest(target("Amazon Shopping", "com.amazon.android.shopping")).isEmpty())
        assertTrue(index.suggest(target("Note", "dev.test.unrelated")).isEmpty())
    }

    private val mixedPack = listOf(
        "music", "music_player", "spotify", "headphones", "youtube_music", "amazon_music",
        "bus", "train_station", "moovit", "trenitalia", "business_suite",
        "gamepad", "steam", "game_center", "joystick",
        "video_player", "netflix", "vlc", "movies",
        "calendar", "adobe_express", "notebooking",
    )

    @Test fun musicAppWithoutItsOwnIconGetsMusicArtwork() {
        val index = IconSuggestions(pack(mixedPack, listOf(mapping("com.spotify.music", "com.spotify.music.MainActivity", "spotify"))))
        val suggestions = index.suggest(target("ArchiveTune", "moe.rukamori.archivetune"), 12)
        val names = suggestions.map { it.drawableName }
        assertEquals("music", names.first())
        assertTrue(names.indexOf("music_player") < names.indexOf("headphones"))
        assertTrue(names.indexOf("headphones") < names.indexOf("spotify"))
        assertTrue(names.containsAll(listOf("music_player", "youtube_music", "amazon_music", "spotify")))
        assertTrue(names.none { it in setOf("bus", "steam", "netflix", "calendar", "adobe_express") })
        assertEquals("Music icon", suggestions.first().reason)
        assertEquals("Similar music app", suggestions.single { it.drawableName == "spotify" }.reason)
    }

    @Test fun fusedTransportNameFindsTransitArtworkButBusinessDoesNot() {
        val index = IconSuggestions(pack(mixedPack))
        val names = index.suggest(target("Busitalia Veneto", "net.pluservice.busitaliaveneto"), 12).map { it.drawableName }
        assertEquals(listOf("bus", "train_station"), names.take(2))
        assertTrue(names.containsAll(listOf("moovit", "trenitalia")))
        assertFalse("business_suite" in names)
        assertTrue(index.suggest(target("Business Suite", "com.example.business")).none { it.reason.contains("ransport") })
    }

    @Test fun gameAndVideoAppsGetTheirKindOfArtwork() {
        val index = IconSuggestions(pack(mixedPack))
        val games = index.suggest(target("GameNative", "app.gamenative"), 12).map { it.drawableName }
        assertEquals(setOf("gamepad", "joystick", "game_center", "steam"), games.toSet())
        val video = index.suggest(target("Nuvio", "com.nuvio.app"), 12).map { it.drawableName }
        assertEquals(setOf("video_player", "movies", "netflix", "vlc"), video.toSet())
        assertTrue(video.indexOf("video_player") < video.indexOf("netflix"))
    }

    @Test fun androidDeclaredCategoryWorksWithoutAnyNameHint() {
        val index = IconSuggestions(pack(mixedPack))
        assertTrue(index.suggest(target("Zorbly", "dev.test.zorbly")).isEmpty())
        val video = index.suggest(target("Zorbly", "dev.test.zorbly", categories = setOf(AppCategory.VIDEO)), 12)
        assertEquals(listOf("movies", "video_player"), video.take(2).map { it.drawableName })
        assertTrue(video.all { it.reason == "Video icon" || it.reason == "Similar video app" })
    }

    @Test fun categoryLookAlikesNeverOutrankNameMatches() {
        val index = IconSuggestions(pack(mixedPack + "archive_tune"))
        val suggestions = index.suggest(target("ArchiveTune"), 12)
        assertEquals("archive_tune", suggestions.first().drawableName)
        assertTrue(suggestions.drop(1).all { it.score < suggestions.first().score })
    }

    @Test fun completeNameMatchesAreSafeEvenWhenTheirWordsAreOtherwiseTooGeneric() {
        val index = IconSuggestions(pack(listOf("google", "x", "app_alt")))
        assertEquals(listOf("google"), index.suggest(target("Google")).map { it.drawableName })
        assertEquals(listOf("x"), index.suggest(target("X")).map { it.drawableName })
    }

    @Test fun orderingAndDeduplicationAreStableAcrossMetadataReordering() {
        val names = listOf("calendar_blue", "calendar_red", "calendar_green", "calendar_blue")
        val first = IconSuggestions(pack(names)).suggest(target("Calendar"))
        val reversed = IconSuggestions(pack(names.reversed())).suggest(target("Calendar"))
        assertEquals(first, reversed)
        assertEquals(listOf("calendar_blue", "calendar_green", "calendar_red"), first.map { it.drawableName })
    }

    @Test fun resultLimitIsBoundedAndEmptyQueriesDoNotFallBackToRandomArtwork() {
        val index = IconSuggestions(pack((1..30).map { "calendar_variant_$it" }))
        assertEquals(8, index.suggest(target("Calendar")).size)
        assertEquals(12, index.suggest(target("Calendar"), Int.MAX_VALUE).size)
        assertEquals(3, index.suggest(target("Calendar"), 3).size)
        assertTrue(index.suggest(target("Calendar"), 0).isEmpty())
        assertTrue(index.suggest(target("___ 123", "com.android.app")).isEmpty())
    }

    @Test fun maximumCatalogueStillFindsDistinctiveNamesBeyondCommonTokenPostingLimit() {
        val names = (0 until 99_999).map { "calendar_variant_$it" } + "zz_unique_app"
        val index = IconSuggestions(pack(names))
        assertEquals(listOf("zz_unique_app"), index.suggest(target("ZZ Unique App")).map { it.drawableName })
        assertEquals(12, index.suggest(target("Calendar"), 12).size)
    }

    @Test fun invalidDrawableIdentifiersAndOversizedCataloguesAreRejected() {
        assertTrue(IconSuggestions(pack(listOf("../calendar", "calendar-icon", "")))
            .suggest(target("Calendar")).isEmpty())
        try {
            IconSuggestions(pack(List(100_001) { "calendar" }))
            fail("Oversized metadata must not be indexed")
        } catch (_: IllegalArgumentException) {
            // The same size boundary applies to repository metadata and indexes.
        }
    }
}
