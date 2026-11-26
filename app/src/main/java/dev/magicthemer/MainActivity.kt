package dev.magicthemer

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.magicthemer.icons.AppCategory
import dev.magicthemer.icons.IconMapping
import dev.magicthemer.icons.IconPack
import dev.magicthemer.icons.IconPackRepository
import dev.magicthemer.icons.IconRequestFormatter
import dev.magicthemer.icons.IconSuggestion
import dev.magicthemer.icons.IconSuggestions
import dev.magicthemer.icons.PackData
import dev.magicthemer.icons.SuggestionReason
import dev.magicthemer.theme.ThemeArchive
import dev.magicthemer.theme.ThemeDocument
import dev.magicthemer.theme.ThemeDynamicAssets
import dev.magicthemer.theme.ThemeDynamicCalendar
import dev.magicthemer.theme.ThemeEditor
import dev.magicthemer.theme.ThemeFallbackStyle
import dev.magicthemer.theme.ThemeIcon
import dev.magicthemer.ui.SearchQuery
import dev.magicthemer.ui.SearchText
import dev.magicthemer.ui.ThumbnailLoader
import dev.magicthemer.ui.VisualAdapter
import dev.magicthemer.ui.VisualItem
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

/** Offline icon-pack conversion and copy-based editing of ordinary local themes. */
class MainActivity : AppCompatActivity() {
    private data class Choice(val pack: String, val drawable: String)
    private data class DocumentResult(val requestCode: Int, val resultCode: Int, val uri: String?)
    private data class RenderCandidate(val data: PackData, val mapping: IconMapping)
    private data class AppIconState(
        val imageKey: String, val status: String, val needsIcon: Boolean = false, val customized: Boolean = false,
        val unthemeable: Boolean = false,
    )
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val repository by lazy { IconPackRepository(this) }
    private val preferences by lazy { getSharedPreferences("builder", MODE_PRIVATE) }
    // Active sessions must survive Android's cache eviction under disk pressure.
    private val themeWorkDir by lazy { File(filesDir, "theme-work").apply {
        require(isDirectory || mkdirs()) { "Could not create theme working storage." }
    } }
    private val loadedPacks = ConcurrentHashMap<String, PackData>()
    private val suggestionIndexes = ConcurrentHashMap<String, IconSuggestions>()
    private val iconSearchIndexes = ConcurrentHashMap<String, Map<String, List<SearchText>>>()
    /** Main thread only: normalized labels and package names reused across keystrokes. */
    private val searchTexts = HashMap<String, SearchText>()
    private val failedIcons = ConcurrentHashMap.newKeySet<String>()
    private val thumbnails by lazy { ThumbnailLoader(::renderThumbnail) }
    private var packs = emptyList<IconPack>()
    private var apps = emptyList<ApplicationTarget>()
    @Volatile private var document: ThemeDocument? = null
    @Volatile private var packData: PackData? = null
    private var appliedPack: PackData? = null
    private val choices = linkedMapOf<String, Choice>()
    private val removedPackages = linkedSetOf<String>()
    private var busy = false
    private var ready = false
    private var initialized = false
    private var deferredDocumentResult: DocumentResult? = null
    private var revision = 0L
    private var extension = "hnt"
    private var pendingExport: String? = null
    private var pendingReport = ""
    private var selectedTab = 0
    private var appFilter = FILTER_ALL
    /** Show each app's own artwork rather than the icon of the theme applied on this phone. */
    private var originalIcons = false
    private var typing = false
    private lateinit var toolbar: MaterialToolbar
    private lateinit var appBar: AppBarLayout
    private lateinit var sourceArea: LinearLayout
    private lateinit var sourceButtons: LinearLayout
    private lateinit var changeSourceButton: MaterialButton
    private lateinit var footer: LinearLayout
    private lateinit var choosePackButton: MaterialButton
    private lateinit var importButton: MaterialButton
    private lateinit var saveButton: MaterialButton
    private lateinit var applyPackButton: MaterialButton
    private lateinit var shareRequestButton: MaterialButton
    private lateinit var sourceIcon: ImageView
    private lateinit var sourceTitle: TextView
    private lateinit var sourceSubtitle: TextView
    private lateinit var status: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var tabs: TabLayout
    private lateinit var pages: List<View>
    private lateinit var appSearch: TextInputEditText
    private lateinit var iconSearch: TextInputEditText
    private lateinit var appSummary: TextView
    private lateinit var originalIconsSwitch: MaterialSwitch
    private lateinit var appFilters: ChipGroup
    private lateinit var allAppsChip: Chip
    private lateinit var missingAppsChip: Chip
    private lateinit var customizedAppsChip: Chip
    private lateinit var unthemeableAppsChip: Chip
    private lateinit var iconSummary: TextView
    private lateinit var appAdapter: VisualAdapter
    private lateinit var appList: RecyclerView
    private lateinit var iconList: RecyclerView
    private lateinit var iconAdapter: VisualAdapter
    private lateinit var titleInput: TextInputEditText
    private lateinit var designerInput: TextInputEditText
    private lateinit var briefInput: TextInputEditText
    private lateinit var wholePack: MaterialSwitch
    private lateinit var styleUnmatched: MaterialSwitch
    private lateinit var styleUnmatchedNote: TextView
    private lateinit var liveCalendar: MaterialSwitch
    private lateinit var liveCalendarNote: TextView
    private lateinit var replaceIcons: MaterialSwitch
    private lateinit var replaceNote: TextView
    private lateinit var repairCompatibility: MaterialSwitch
    private lateinit var repairNote: TextView
    private lateinit var formatInput: MaterialAutoCompleteTextView

    private fun formatLabels(): List<String> = listOf(getString(R.string.format_hnt), getString(R.string.format_hwt))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        pendingExport = savedInstanceState?.getString("pendingExport") ?: preferences.getString("pendingExport", null)
        pendingReport = savedInstanceState?.getString("pendingReport") ?: preferences.getString("pendingReport", "").orEmpty()
        selectedTab = savedInstanceState?.getInt("tab") ?: preferences.getInt("tab", 0)
        appFilter = savedInstanceState?.getInt("appFilter") ?: preferences.getInt("appFilter", FILTER_ALL)
        originalIcons = savedInstanceState?.getBoolean("originalIcons") ?: preferences.getBoolean("originalIcons", false)
        extension = savedInstanceState?.getString("extension") ?: preferences.getString("extension", "hnt") ?: "hnt"
        if (savedInstanceState?.containsKey("documentRequest") == true) {
            deferredDocumentResult = DocumentResult(savedInstanceState.getInt("documentRequest"),
                savedInstanceState.getInt("documentResult"), savedInstanceState.getString("documentUri"))
        }
        buildUi(savedInstanceState)
        ready = true
        val source = savedInstanceState?.getString("source") ?: preferences.getString("source", null)
        val pack = savedInstanceState?.getString("pack") ?: preferences.getString("pack", null)
        val applied = savedInstanceState?.getString("applied") ?: preferences.getString("applied", null)
        initialize(source, pack, applied)
    }

    private fun buildUi(saved: Bundle?) {
        val frame = FrameLayout(this).apply { setBackgroundColor(color(com.google.android.material.R.attr.colorSurface)) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        frame.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL))
        frame.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                val wanted = minOf(right - left - frame.paddingLeft - frame.paddingRight, dp(1000))
                if (column.layoutParams.width != wanted && wanted > 0) column.layoutParams = (column.layoutParams as FrameLayout.LayoutParams).apply { width = wanted }
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            setTyping(insets.isVisible(WindowInsetsCompat.Type.ime()))
            insets
        }
        toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.app_name)
            menu.add(0, MENU_NEW, 0, R.string.menu_new_theme)
            menu.add(0, MENU_OPEN, 1, R.string.menu_open_theme)
            menu.add(0, MENU_REFRESH, 2, R.string.menu_refresh_packs)
            menu.add(0, MENU_DETAILS, 3, R.string.menu_pack_details)
            menu.add(0, MENU_SHARE_REQUEST, 4, R.string.menu_share_request)
            menu.add(0, MENU_THEMES, 5, R.string.menu_open_themes)
            menu.add(0, MENU_HELP, 6, R.string.menu_how_to_apply)
            menu.add(0, MENU_SETTINGS, 7, R.string.menu_settings)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_NEW -> if (!busy) newTheme()
                    MENU_OPEN -> if (!busy) openThemePicker()
                    MENU_REFRESH -> if (!busy) refreshPacks()
                    MENU_DETAILS -> showPackDetails()
                    MENU_SHARE_REQUEST -> if (!busy) shareIconRequest()
                    MENU_THEMES -> openThemes()
                    MENU_HELP -> showGuide()
                    MENU_SETTINGS -> showSettings()
                }
                true
            }
        }
        // The toolbar and source card scroll away with the lists; the tabs stay pinned.
        appBar = AppBarLayout(this).apply {
            setBackgroundColor(color(com.google.android.material.R.attr.colorSurface)); isLiftOnScroll = false; stateListAnimator = null; elevation = 0f
        }
        fun scrollsAway() = AppBarLayout.LayoutParams(-1, -2).apply {
            scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS or AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
        }
        appBar.addView(toolbar, scrollsAway().apply { height = dp(56) })
        sourceArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(8)) }
        // Shown until a source is chosen; afterwards the card changes the pack and the menu opens themes.
        sourceButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        choosePackButton = button(getString(R.string.action_choose_pack), tonal = true) { choosePack() }
        importButton = button(getString(R.string.action_open_hnt), outlined = true) { openThemePicker() }
        sourceButtons.addView(choosePackButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(8) })
        sourceButtons.addView(importButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        sourceArea.addView(sourceButtons, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val card = MaterialCardView(this).apply {
            cardElevation = 0f; radius = dp(20).toFloat(); strokeWidth = 0
            setCardBackgroundColor(color(com.google.android.material.R.attr.colorSurfaceContainerLow))
            setOnClickListener { if (!busy) choosePack() }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(10), dp(8), dp(10)) }
        sourceIcon = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        row.addView(sourceIcon, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) })
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sourceTitle = label(getString(R.string.source_choose_title), 16f, bold = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        sourceSubtitle = label(getString(R.string.source_choose_subtitle), 12f, muted = true).apply { maxLines = 2 }
        labels.addView(sourceTitle); labels.addView(sourceSubtitle)
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        changeSourceButton = button(getString(R.string.action_change), borderless = true) { choosePack() }
        row.addView(changeSourceButton, LinearLayout.LayoutParams(-2, dp(48)).apply { marginStart = dp(4) })
        card.addView(row)
        sourceArea.addView(card, LinearLayout.LayoutParams(-1, -2))
        appBar.addView(sourceArea, scrollsAway())
        tabs = TabLayout(this).apply {
            tabMode = TabLayout.MODE_FIXED
            addTab(newTab().setText(R.string.tab_apps))
            addTab(newTab().setText(R.string.tab_pack_icons))
            addTab(newTab().setText(R.string.tab_theme))
        }
        appBar.addView(tabs, AppBarLayout.LayoutParams(-1, dp(48)).apply { scrollFlags = 0 })
        val coordinator = CoordinatorLayout(this)
        coordinator.addView(appBar, CoordinatorLayout.LayoutParams(-1, -2))
        val body = FrameLayout(this)
        // Pages take focus when a search field clears it, so focus never jumps to another field.
        val appPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), 0); isFocusableInTouchMode = true }
        appSearch = searchField(appPage, getString(R.string.search_apps_hint))
        val filterScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        appFilters = ChipGroup(this).apply { isSingleSelection = true; isSelectionRequired = true; isSingleLine = true; chipSpacingHorizontal = dp(8) }
        fun filterChip(value: String, filter: Int): Chip = Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
            id = View.generateViewId(); text = value; isCheckable = true; isChecked = appFilter == filter
            setOnClickListener { appFilter = filter; updateAppList(); persist() }
        }
        allAppsChip = filterChip(getString(R.string.filter_all), FILTER_ALL)
        missingAppsChip = filterChip(getString(R.string.filter_needs_icon), FILTER_MISSING)
        customizedAppsChip = filterChip(getString(R.string.filter_customized), FILTER_CUSTOMIZED)
        unthemeableAppsChip = filterChip(getString(R.string.filter_unthemeable), FILTER_UNTHEMEABLE).apply { visibility = View.GONE }
        appFilters.addView(allAppsChip); appFilters.addView(missingAppsChip); appFilters.addView(customizedAppsChip)
        appFilters.addView(unthemeableAppsChip)
        filterScroll.addView(appFilters)
        appPage.addView(filterScroll, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        val summaryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        appSummary = label(getString(R.string.summary_finding_apps), 12f, muted = true)
        summaryRow.addView(appSummary, LinearLayout.LayoutParams(0, -2, 1f))
        // HONOR hands out the applied theme's icons, which hides which apps a custom theme already covers.
        originalIconsSwitch = MaterialSwitch(this).apply {
            setText(R.string.switch_original_icons); textSize = 12f; isChecked = originalIcons
            setOnCheckedChangeListener { _, checked -> originalIcons = checked; updateAppList(); if (ready) persist() }
        }
        summaryRow.addView(originalIconsSwitch, LinearLayout.LayoutParams(-2, dp(48)).apply { marginStart = dp(8) })
        appPage.addView(summaryRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        shareRequestButton = button(getString(R.string.action_share_icon_request), outlined = true) { shareIconRequest() }.apply {
            visibility = View.GONE
        }
        appPage.addView(shareRequestButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(4) })
        appAdapter = VisualAdapter(this, thumbnails, false) { item ->
            if (typing) hideKeyboard(appSearch)
            if (!busy) targets().firstOrNull { it.packageName == item.key }?.let { chooseIcon(it) }
        }
        appList = recycler(appAdapter, false)
        appPage.addView(appList, LinearLayout.LayoutParams(-1, 0, 1f))
        val iconPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), 0); isFocusableInTouchMode = true }
        iconSearch = searchField(iconPage, getString(R.string.search_pack_icons_hint))
        iconSummary = label(getString(R.string.icon_summary_empty), 12f, muted = true)
        iconPage.addView(iconSummary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(4); marginStart = dp(4) })
        iconAdapter = VisualAdapter(this, thumbnails, true) { item ->
            if (typing) hideKeyboard(iconSearch)
            if (!busy) packData?.let { previewIcon(it, item.key) }
        }
        iconList = recycler(iconAdapter, true)
        iconPage.addView(iconList, LinearLayout.LayoutParams(-1, 0, 1f))
        val themeScroll = NestedScrollView(this).apply { isFillViewport = true }
        val themePage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(4), dp(20), dp(20)) }
        themeScroll.addView(themePage)
        sectionHeading(themePage, getString(R.string.section_details))
        titleInput = editorField(themePage, getString(R.string.field_theme_name), 64, false).apply { setText(saved?.getString("title") ?: preferences.getString("title", DEFAULT_TITLE)) }
        designerInput = editorField(themePage, getString(R.string.field_designer), 128, false).apply { setText(saved?.getString("designer") ?: preferences.getString("designer", null) ?: defaultDesigner()) }
        briefInput = editorField(themePage, getString(R.string.field_description), 4096, true).apply { setText(saved?.getString("brief") ?: preferences.getString("brief", "")) }
        sectionHeading(themePage, getString(R.string.section_icons))
        wholePack = MaterialSwitch(this).apply {
            setText(R.string.switch_whole_pack)
            isChecked = saved?.getBoolean("whole") ?: preferences.getBoolean("whole", true)
            setOnCheckedChangeListener { _, _ -> if (ready) { persist(); refreshUi() } }
        }
        themePage.addView(wholePack, LinearLayout.LayoutParams(-1, dp(56)))
        themePage.addView(label(getString(R.string.note_whole_pack), 13f, muted = true))
        styleUnmatched = MaterialSwitch(this).apply {
            setText(R.string.switch_style_unmatched)
            isChecked = saved?.getBoolean("styleUnmatched") ?: preferences.getBoolean("styleUnmatched", false)
            setOnCheckedChangeListener { _, _ -> if (ready) persist() }
        }
        themePage.addView(styleUnmatched, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(8) })
        styleUnmatchedNote = label(getString(R.string.note_style_unmatched), 13f, muted = true)
        themePage.addView(styleUnmatchedNote)
        liveCalendar = MaterialSwitch(this).apply {
            setText(R.string.switch_live_calendar)
            isChecked = saved?.getBoolean("liveCalendar") ?: preferences.getBoolean("liveCalendar", false)
            setOnCheckedChangeListener { _, _ -> if (ready) persist() }
        }
        themePage.addView(liveCalendar, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(8) })
        liveCalendarNote = label(getString(R.string.note_live_calendar), 13f, muted = true)
        themePage.addView(liveCalendarNote)
        applyPackButton = button(getString(R.string.action_apply_whole_pack), tonal = true) { applyWholePack() }
        themePage.addView(applyPackButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        replaceIcons = MaterialSwitch(this).apply {
            setText(R.string.switch_replace_icons)
            isChecked = saved?.getBoolean("replace") ?: preferences.getBoolean("replace", false)
            setOnCheckedChangeListener { _, _ -> if (ready) { persist(); refreshUi() } }
        }
        themePage.addView(replaceIcons, LinearLayout.LayoutParams(-1, dp(56)))
        replaceNote = label(getString(R.string.note_replace_icons), 13f, muted = true)
        themePage.addView(replaceNote)
        sectionHeading(themePage, getString(R.string.section_file))
        val formatLayout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputStyle).apply {
            hint = getString(R.string.field_theme_format); endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
        }
        val formats = formatLabels()
        formatInput = MaterialAutoCompleteTextView(formatLayout.context).apply {
            inputType = 0; textSize = 16f; setPadding(dp(16), dp(16), dp(16), dp(16))
            setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, formats))
            setText(formats[if (extension == "hwt") 1 else 0], false)
            setOnItemClickListener { _, _, index, _ -> extension = if (index == 1) "hwt" else "hnt"; if (ready) persist() }
        }
        formatLayout.addView(formatInput)
        themePage.addView(formatLayout, LinearLayout.LayoutParams(-1, -2))
        themePage.addView(label(getString(R.string.note_file_spec), 13f, muted = true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        repairCompatibility = MaterialSwitch(this).apply {
            setText(R.string.switch_repair_compatibility)
            isChecked = saved?.getBoolean("repair") ?: preferences.getBoolean("repair", true)
            setOnCheckedChangeListener { _, _ -> if (ready) persist() }
        }
        themePage.addView(repairCompatibility, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(8) })
        repairNote = label(getString(R.string.note_repair_compatibility), 13f, muted = true)
        themePage.addView(repairNote)
        pages = listOf(appPage, iconPage, themeScroll)
        pages.forEach { body.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        coordinator.addView(body, CoordinatorLayout.LayoutParams(-1, -1).apply { behavior = AppBarLayout.ScrollingViewBehavior() })
        column.addView(coordinator, LinearLayout.LayoutParams(-1, 0, 1f))
        footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
            setBackgroundColor(color(com.google.android.material.R.attr.colorSurfaceContainerLow))
        }
        progress = LinearProgressIndicator(this).apply { isIndeterminate = true; visibility = View.GONE }
        footer.addView(progress, LinearLayout.LayoutParams(-1, dp(4)))
        status = label(getString(R.string.status_initial), 12f, muted = true).apply {
            maxLines = 2; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        footer.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(4) })
        saveButton = button(getString(R.string.action_save_theme)) { exportTheme() }
        footer.addView(saveButton, LinearLayout.LayoutParams(-1, dp(52)))
        column.addView(footer)
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectedTab = tab.position; showPage()
                if (ready) appBar.setExpanded(true, true)
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(tabs.windowToken, 0)
                if (ready) persist()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        tabs.getTabAt(selectedTab.coerceIn(0, 2))?.select(); showPage()
        watchSearch(appSearch) { updateAppList(scrollToTop = true) }; watchSearch(iconSearch) { updateIconList(scrollToTop = true) }
        listOf(titleInput, designerInput, briefInput).forEach { input -> watchText(input) { if (ready) persist() } }
        setContentView(frame)
        ViewCompat.requestApplyInsets(frame)
        updateControls()
    }

    private fun initialize(sourceName: String?, selectedPackage: String?, appliedPackage: String?) {
        setBusy(true, getString(R.string.status_loading_local))
        val pendingName = pendingExport
        background {
            val restoredSource = cachedImport(sourceName)
            val restoredExport = workingExport(pendingName)
            cleanupWorkingFiles(setOfNotNull(restoredSource, restoredExport))
            val found = repository.discover()
            val installed = discoverApps()
            var warning = if (sourceName != null && restoredSource == null) getString(R.string.status_working_theme_unavailable) else ""
            val restored = restoredSource?.let { file ->
                runCatching { ThemeEditor.open(file) }.getOrElse { warning = it.message ?: getString(R.string.status_restore_theme_failed); null }
            }
            val selected = found.firstOrNull { it.packageName == selectedPackage }?.let {
                runCatching { repository.load(it) }.getOrElse { warning = it.message ?: getString(R.string.status_restore_pack_failed); null }
            }
            selected?.let(::cachePack)
            val applied = if (restored == null || appliedPackage == null) null else {
                selected?.takeIf { it.pack.packageName == appliedPackage }
                    ?: found.firstOrNull { it.packageName == appliedPackage }?.let { runCatching { repository.load(it) }.getOrNull() }
            }
            applied?.let(::cachePack)
            ui(onDiscard = { restored?.let(ThemeEditor::close) }) {
                packs = found; apps = installed; document = restored; packData = selected; appliedPack = applied
                pendingExport = restoredExport?.absolutePath
                if (sourceName != null && restored != null && sourceName != restored.sourceFile.name) migrateSession(sourceName, restored.sourceFile.name)
                initialized = true
                restoreSession()
                fillDefaultCredits()
                persist()
                setBusy(false, warning.ifBlank {
                    when {
                        restored != null -> getString(R.string.status_theme_opened_ready)
                        selected != null -> getString(R.string.status_pack_ready)
                        else -> getString(R.string.status_choose_source)
                    }
                })
                refreshUi()
                deferredDocumentResult?.let { result -> deferredDocumentResult = null; handleDocumentResult(result) }
            }
        }
    }

    private fun discoverApps(): List<ApplicationTarget> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val rows = if (Build.VERSION.SDK_INT >= 33) packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        else { @Suppress("DEPRECATION") packageManager.queryIntentActivities(intent, 0) }
        // The query already omits disabled components. ActivityInfo.enabled is only the
        // manifest default, so it would hide launcher aliases enabled at runtime, the
        // usual way apps switch icons, and those apps would never be counted.
        val kinds = mediaCategories()
        return rows.filter { it.activityInfo?.applicationInfo?.enabled == true }
            .groupBy { it.activityInfo.packageName }.map { (pkg, matches) ->
                val info = matches.first().activityInfo
                ApplicationTarget(pkg, info.applicationInfo.loadLabel(packageManager).toString(), matches.map { it.activityInfo.name }.distinct(),
                    iconResourcePackage(info), declaredCategories(info.applicationInfo) + kinds[pkg].orEmpty())
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun declaredCategories(info: ApplicationInfo): Set<AppCategory> {
        @Suppress("DEPRECATION")
        val game = info.flags and ApplicationInfo.FLAG_IS_GAME != 0
        return setOfNotNull(AppCategory.fromAndroid(info.category), AppCategory.GAMES.takeIf { game })
    }

    /** Media players reveal their kind through the intents they handle, even without a declared category. */
    private fun mediaCategories(): Map<String, Set<AppCategory>> {
        fun activities(intent: Intent) = if (Build.VERSION.SDK_INT >= 33) packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            else { @Suppress("DEPRECATION") packageManager.queryIntentActivities(intent, 0) }
        fun viewers(type: String) = runCatching {
            activities(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://$packageName/media"), type))
                .mapNotNullTo(hashSetOf()) { it.activityInfo?.packageName }
        }.getOrDefault(emptySet())
        val browsers = runCatching {
            val intent = Intent("android.media.browse.MediaBrowserService")
            (if (Build.VERSION.SDK_INT >= 33) packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
            else { @Suppress("DEPRECATION") packageManager.queryIntentServices(intent, 0) }).mapNotNullTo(hashSetOf()) { it.serviceInfo?.packageName }
        }.getOrDefault(emptySet())
        val video = viewers("video/*")
        val result = HashMap<String, MutableSet<AppCategory>>()
        video.forEach { result.getOrPut(it) { hashSetOf() } += AppCategory.VIDEO }
        // A player that opens both is usually a video player; audio-only handlers and media browsers play music.
        (viewers("audio/*") - video + browsers).forEach { result.getOrPut(it) { hashSetOf() } += AppCategory.MUSIC }
        return result
    }

    /** The package HONOR's theme engine compares with the installed package; null if unknown. */
    private fun iconResourcePackage(info: ActivityInfo): String? {
        val icon = info.iconResource
        // Only the app's own resource table can carry a renamed package.
        if (icon == 0 || icon ushr 24 != 0x7f) return null
        return runCatching { packageManager.getResourcesForApplication(info.applicationInfo).getResourcePackageName(icon) }.getOrNull()
    }

    private fun refreshPacks() {
        val previous = packData?.pack?.packageName
        val previousApplied = appliedPack?.pack?.packageName
        val referenced = choices.values.mapTo(hashSetOf()) { it.pack }.apply {
            previous?.let(::add); previousApplied?.let(::add)
        }
        setBusy(true, getString(R.string.status_refreshing_packs))
        background {
            repository.clearResourceCache()
            val found = repository.discover(); val installed = discoverApps()
            val refreshed = found.filter { it.packageName in referenced }.associate { pack ->
                val data = repository.load(pack)
                cachePack(data)
                pack.packageName to data
            }
            ui {
                failedIcons.clear()
                loadedPacks.keys.removeIf { it !in refreshed }
                suggestionIndexes.keys.removeIf { it !in refreshed }
                iconSearchIndexes.keys.removeIf { it !in refreshed }
                packs = found; apps = installed; packData = previous?.let(refreshed::get)
                appliedPack = previousApplied?.let(refreshed::get)
                choices.entries.removeAll { (_, choice) -> found.none { it.packageName == choice.pack } }
                thumbnails.clear(); ++revision
                persist(); saveSession(); refreshUi(forceRebind = true)
                setBusy(false, resources.getQuantityString(R.plurals.status_packs_found, found.size, found.size))
            }
        }
    }

    private fun choosePack() {
        if (packs.isEmpty()) {
            MaterialAlertDialogBuilder(this).setTitle(R.string.dialog_install_pack_title)
                .setMessage(R.string.dialog_install_pack_message)
                .setPositiveButton(R.string.action_refresh) { _, _ -> refreshPacks() }
                .setNegativeButton(R.string.action_close, null).show()
            return
        }
        visualSheet(getString(R.string.sheet_choose_pack_title), getString(R.string.sheet_choose_pack_subtitle), false,
            items = { query -> query.rank(packs) { listOf(searchText(it.label), searchText(it.packageName)) }.map { VisualItem(it.packageName, it.label, it.packageName, "app:${it.packageName}") } },
            onSelect = { item -> packs.firstOrNull { it.packageName == item.key }?.let(::loadPack) })
    }

    private fun loadPack(pack: IconPack) {
        saveSession()
        setBusy(true, getString(R.string.status_reading_pack, pack.label))
        background {
            val data = repository.load(pack)
            cachePack(data)
            ui {
                failedIcons.removeIf { it.startsWith("pack:${pack.packageName}:") }
                packData = data
                if (document == null) restoreSession()
                fillDefaultCredits()
                persist(); refreshUi()
                setBusy(false, if (document == null) getString(R.string.status_pack_loaded_new, data.drawables.size)
                else getString(R.string.status_pack_loaded_imported))
            }
        }
    }

    private fun packTitle(): String = packData?.pack?.label?.trim()?.take(64)?.ifBlank { null } ?: DEFAULT_TITLE

    /**
     * A new theme is named after its pack and credits this phone until the user
     * types their own. A title matching any installed pack was filled in here,
     * so switching packs renames it too.
     */
    private fun fillDefaultCredits() {
        if (document != null) return
        val title = titleInput.text.toString().trim()
        if (title.isEmpty() || title == DEFAULT_TITLE || packs.any { it.label.trim().take(64) == title }) titleInput.setText(packTitle())
        if (designerInput.text.toString().trim() == LEGACY_DESIGNER) designerInput.setText(defaultDesigner())
    }

    /** The credit chosen in Settings, else this phone's name. */
    private fun defaultDesigner(): String = preferences.getString("defaultDesigner", null)?.trim()?.ifEmpty { null } ?: deviceDesigner()

    /** The name set in Settings → About phone, else the model. */
    private fun deviceDesigner(): String {
        val named = listOf(Settings.Global.DEVICE_NAME, "unified_device_name").firstNotNullOfOrNull { key ->
            runCatching { Settings.Global.getString(contentResolver, key) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        }
        val model = Build.MODEL.orEmpty().trim()
        val maker = Build.MANUFACTURER.orEmpty().trim()
        val fallback = if (maker.isEmpty() || model.startsWith(maker, ignoreCase = true)) model else "${maker.uppercase(Locale.ROOT)} $model"
        return (named ?: fallback).take(128).ifBlank { LEGACY_DESIGNER }
    }

    private fun applyWholePack() {
        val data = packData ?: return
        if (document == null) return
        appliedPack = data
        saveSession(); persist(); refreshUi()
        status.text = getString(if (replaceIcons.isChecked) R.string.status_pack_will_replace else R.string.status_pack_will_merge, data.pack.label)
    }

    private fun targets(): List<ApplicationTarget> {
        val installed = apps.associateBy { it.packageName }
        val extra = document?.iconEntries.orEmpty().keys.filter { it !in installed }
            .map { ApplicationTarget(it, it.substringAfterLast('.').replaceFirstChar { c -> c.uppercase() }, emptyList()) }
        return (apps + extra).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun defaultMappings(): Map<String, IconMapping> {
        val data = if (document == null) packData else appliedPack
        return data?.let { ThemeMappingSelector.select(it.mappings, apps, wholePack.isChecked).associateBy { mapping -> mapping.packageName } }.orEmpty()
    }

    private fun appIconState(target: ApplicationTarget, defaults: Map<String, IconMapping>): AppIconState {
        val state = themedIconState(target.packageName, defaults)
        if (target.honorThemeable) return state
        // Whatever the theme contains, HONOR shows this build's own icon.
        return AppIconState("app:${target.packageName}",
            getString(if (state.customized) R.string.status_customized_unthemeable else R.string.status_unthemeable),
            customized = state.customized, unthemeable = true)
    }

    private fun themedIconState(packageName: String, defaults: Map<String, IconMapping>): AppIconState {
        val original = "app:$packageName"
        if (packageName in removedPackages) return AppIconState(original, getString(R.string.status_needs_icon_original), needsIcon = true)
        choices[packageName]?.let { choice ->
            val data = loadedPacks[choice.pack]
            val key = "pack:${choice.pack}:${choice.drawable}"
            return if (data?.isDrawableAvailable(choice.drawable) == true && key !in failedIcons)
                AppIconState(key, getString(R.string.status_customized), customized = true)
            else unavailablePackIconState(packageName)
        }
        val defaultData = if (document == null) packData else appliedPack
        defaults[packageName]?.let { mapping ->
            val key = "pack:${defaultData!!.pack.packageName}:${mapping.drawableName}"
            if (defaultData.isDrawableAvailable(mapping.drawableName) && key !in failedIcons)
                return AppIconState(key, getString(R.string.status_themed_pack))
            return unavailablePackIconState(packageName)
        }
        return preservedImportedIconState(packageName) ?: AppIconState(original, getString(R.string.status_needs_icon), needsIcon = true)
    }

    private fun unavailablePackIconState(packageName: String): AppIconState =
        preservedImportedIconState(packageName, replacementUnavailable = true)
            ?: AppIconState("app:$packageName", getString(R.string.status_needs_icon_unavailable), needsIcon = true)

    private fun preservedImportedIconState(packageName: String, replacementUnavailable: Boolean = false): AppIconState? {
        val source = document
        if (source != null && packageName in source.iconEntries && !(replaceIcons.isChecked && appliedPack != null)) {
            val key = "theme:${source.sourceFile.name}:$packageName"
            return if (key !in failedIcons) AppIconState(key,
                getString(if (replacementUnavailable) R.string.status_themed_imported_unavailable else R.string.status_themed_imported))
            else AppIconState("app:$packageName", getString(R.string.status_needs_icon_unreadable), needsIcon = true)
        }
        return null
    }

    private fun shareIconRequest() {
        val defaults = defaultMappings()
        val missingApps = apps.filter { appIconState(it, defaults).needsIcon }
        if (missingApps.isEmpty()) {
            status.text = getString(R.string.status_no_missing_to_share)
            return
        }
        val packLabel = (if (document == null) packData else appliedPack)?.pack?.label ?: document?.title?.ifBlank { null }
        val text = IconRequestFormatter.format(packLabel, missingApps)
        val subject = if (packLabel.isNullOrBlank()) getString(R.string.share_request_subject_generic)
        else getString(R.string.share_request_subject, packLabel)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { startActivity(Intent.createChooser(intent, getString(R.string.share_request_chooser))) }
            .onFailure { status.text = getString(R.string.status_share_unavailable) }
    }

    private fun updateAppList(scrollToTop: Boolean = false) {
        if (!::appAdapter.isInitialized) return
        val query = SearchQuery(appSearch.text.toString())
        val defaults = defaultMappings()
        val all = targets()
        val states = all.associate { it.packageName to appIconState(it, defaults) }
        val missing = states.values.count { it.needsIcon }
        val customized = states.values.count { it.customized }
        val unthemeable = states.values.count { it.unthemeable }
        allAppsChip.text = getString(R.string.filter_all_count, all.size)
        missingAppsChip.text = getString(R.string.filter_needs_icon_count, missing)
        customizedAppsChip.text = getString(R.string.filter_customized_count, customized)
        unthemeableAppsChip.text = getString(R.string.filter_unthemeable_count, unthemeable)
        unthemeableAppsChip.visibility = if (unthemeable > 0 || appFilter == FILTER_UNTHEMEABLE) View.VISIBLE else View.GONE
        shareRequestButton.visibility = if (appFilter == FILTER_MISSING && missing > 0 && apps.any { states[it.packageName]?.needsIcon == true }) View.VISIBLE else View.GONE
        appFilters.check(when (appFilter) {
            FILTER_MISSING -> missingAppsChip.id; FILTER_CUSTOMIZED -> customizedAppsChip.id
            FILTER_UNTHEMEABLE -> unthemeableAppsChip.id; else -> allAppsChip.id
        })
        val filtered = all.filter {
            val state = states.getValue(it.packageName)
            when (appFilter) {
                FILTER_MISSING -> state.needsIcon
                FILTER_CUSTOMIZED -> state.customized
                FILTER_UNTHEMEABLE -> state.unthemeable
                else -> true
            }
        }
        val visible = query.rank(filtered) { listOf(searchText(it.label), searchText(it.packageName)) }
        appAdapter.submit(visible.map { target ->
            val state = states.getValue(target.packageName)
            VisualItem(target.packageName, target.label, target.packageName, appIconKey(target.packageName),
                state.imageKey.takeUnless { it == "app:${target.packageName}" }, state.status, state.needsIcon || state.unthemeable)
        }) { if (scrollToTop) appList.scrollToPosition(0) }
        val themed = apps.count { states[it.packageName]?.let { state -> !state.needsIcon && !state.unthemeable } == true }
        appSummary.text = when {
            visible.isEmpty() && query.isBlank && appFilter == FILTER_MISSING ->
                if (unthemeable > 0) getString(R.string.summary_all_themed_with_unthemeable, unthemeable)
                else getString(R.string.summary_all_themed)
            visible.isEmpty() -> getString(R.string.summary_no_apps_match)
            appFilter == FILTER_UNTHEMEABLE -> getString(R.string.summary_unthemeable_detail)
            !query.isBlank -> resources.getQuantityString(R.plurals.summary_matches, visible.size, visible.size)
            unthemeable > 0 -> getString(R.string.summary_themed_of_total_with_unthemeable, themed, apps.size, unthemeable)
            else -> getString(R.string.summary_themed_of_total, themed, apps.size)
        }
    }

    private fun updateIconList(scrollToTop: Boolean = false) {
        if (!::iconAdapter.isInitialized) return
        val data = packData
        val names = data?.let { searchIcons(it, SearchQuery(iconSearch.text.toString())) }.orEmpty()
        iconAdapter.submit(names.map { VisualItem(it, friendlyName(it), "", "pack:${data!!.pack.packageName}:$it") }) { if (scrollToTop) iconList.scrollToPosition(0) }
        iconSummary.text = if (data == null) getString(R.string.icon_summary_empty)
        else getString(R.string.icon_summary_count, names.size, data.drawables.size)
    }

    /** Ranked artwork; names also match the apps a pack maps to them, so "WhatsApp" finds "wa". */
    private fun searchIcons(data: PackData, query: SearchQuery): List<String> {
        val available = availableIconNames(data)
        if (query.isBlank) return available
        val index = iconSearchIndexes[data.pack.packageName]
        return query.rank(available) { name -> index?.get(name) ?: listOf(searchText(name)) }
    }

    /** An installed app's icon as the launcher shows it now, or its own artwork. */
    private fun appIconKey(packageName: String): String = if (originalIcons) "original:$packageName" else "app:$packageName"

    private fun searchText(value: String): SearchText {
        if (searchTexts.size > MAX_SEARCH_TEXTS) searchTexts.clear()
        return searchTexts.getOrPut(value) { SearchText.of(value) }
    }

    private fun refreshUi(forceRebind: Boolean = false) {
        val source = document
        val data = packData
        val activePack = if (source == null) data else appliedPack
        val empty = source == null && data == null
        sourceButtons.visibility = if (empty) View.VISIBLE else View.GONE
        changeSourceButton.visibility = if (empty) View.GONE else View.VISIBLE
        changeSourceButton.setText(if (data == null) R.string.action_choose_pack else R.string.action_change)
        sourceTitle.text = source?.title?.ifBlank { getString(R.string.source_imported_fallback) }
            ?: data?.pack?.label ?: getString(R.string.source_choose_title)
        sourceSubtitle.text = when {
            source != null && data != null -> getString(R.string.source_editing_copy_with_pack, source.iconEntries.size, data.pack.label)
            source != null -> getString(R.string.source_editing_copy, source.iconEntries.size)
            data != null -> getString(R.string.source_pack_counts, data.drawables.size, data.mappings.map { it.packageName }.distinct().size)
            else -> getString(R.string.source_choose_subtitle)
        }
        if (data != null) thumbnails.bind(sourceIcon, "app:${data.pack.packageName}", 128)
        else if (source?.iconEntries?.isNotEmpty() == true) thumbnails.bind(sourceIcon, "theme:${source.sourceFile.name}:${source.iconEntries.keys.first()}", 128)
        else { thumbnails.unbind(sourceIcon); sourceIcon.setImageResource(android.R.drawable.ic_menu_gallery) }
        val hasFallback = activePack?.fallbackStyle?.hasAssets == true
        styleUnmatchedNote.setText(if (activePack == null || hasFallback) R.string.note_style_unmatched else R.string.note_style_unmatched_unavailable)
        val hasCalendar = activePack?.calendarMappings?.isNotEmpty() == true
        liveCalendarNote.setText(if (activePack == null || hasCalendar) R.string.note_live_calendar else R.string.note_live_calendar_unavailable)
        applyPackButton.visibility = if (source == null) View.GONE else View.VISIBLE
        replaceIcons.visibility = if (source == null) View.GONE else View.VISIBLE
        replaceNote.visibility = if (source == null) View.GONE else View.VISIBLE
        val repairable = source?.compatibilityRepairAvailable == true
        repairCompatibility.visibility = if (repairable) View.VISIBLE else View.GONE
        repairNote.visibility = if (repairable) View.VISIBLE else View.GONE
        applyPackButton.setText(if (data != null && appliedPack?.pack?.packageName == data.pack.packageName) R.string.action_pack_applied_again else R.string.action_apply_whole_pack)
        saveButton.setText(if (source == null) R.string.action_save_theme else R.string.action_save_theme_copy)
        updateAppList(); updateIconList()
        if (forceRebind) { appAdapter.notifyDataSetChanged(); iconAdapter.notifyDataSetChanged() }
        updateControls()
    }

    private fun chooseIcon(target: ApplicationTarget, explained: Boolean = false) {
        if (!target.honorThemeable && !explained) {
            MaterialAlertDialogBuilder(this).setTitle(getString(R.string.dialog_unthemeable_title, target.label))
                .setMessage(getString(R.string.dialog_unthemeable_message, target.packageName, target.resourcePackage.orEmpty()))
                .setPositiveButton(R.string.action_choose_anyway) { _, _ -> chooseIcon(target, explained = true) }
                .setNegativeButton(R.string.action_close, null).show()
            return
        }
        val data = packData
        if (data == null) {
            MaterialAlertDialogBuilder(this).setTitle(getString(R.string.sheet_icon_for_title, target.label))
                .setMessage(R.string.dialog_no_pack_for_app_message)
                .setPositiveButton(R.string.action_choose_pack) { _, _ -> choosePack() }
                .setNeutralButton(R.string.action_use_original) { _, _ -> useOriginal(target.packageName) }
                .setNegativeButton(R.string.action_close, null).show()
            return
        }
        visualSheet(getString(R.string.sheet_icon_for_title, target.label), target.packageName, true,
            items = { query -> searchIcons(data, query).map { VisualItem(it, friendlyName(it), "", "pack:${data.pack.packageName}:$it") } },
            suggested = { query ->
                suggestionIndexes[data.pack.packageName]?.suggest(target, 12).orEmpty()
                    .filter { it.drawableName !in failedPackNames(data) && query.score(listOf(searchText(it.drawableName), searchText(it.label))) != null }
                    .map { VisualItem(it.drawableName, it.label, localizedReason(it), "pack:${data.pack.packageName}:${it.drawableName}") }
            },
            actions = listOf(getString(R.string.action_use_original) to { useOriginal(target.packageName) }, getString(R.string.action_reset) to {
                choices.remove(target.packageName); removedPackages.remove(target.packageName); saveSession(); refreshUi()
                status.text = getString(R.string.status_icon_reset, target.label)
            }),
            onSelect = { item -> assign(target, data, item.key) })
    }

    private fun localizedReason(suggestion: IconSuggestion): String = when (suggestion.reasonKind) {
        SuggestionReason.LAUNCHER_ACTIVITY -> getString(R.string.reason_launcher_activity)
        SuggestionReason.MAPPED_TO_APP -> getString(R.string.reason_mapped_to_app)
        SuggestionReason.APP_NAME -> getString(R.string.reason_app_name)
        SuggestionReason.APP_NAME_WORDS -> getString(R.string.reason_app_name_words)
        SuggestionReason.KNOWN_APP_NAME -> getString(R.string.reason_known_app_name)
        SuggestionReason.PACKAGE_NAME -> getString(R.string.reason_package_name)
        SuggestionReason.CATEGORY_ICON -> getString(R.string.reason_category_icon, localizedCategory(suggestion.category))
        SuggestionReason.SIMILAR_CATEGORY_APP -> getString(R.string.reason_similar_category_app, localizedCategory(suggestion.category).lowercase(Locale.getDefault()))
    }

    private fun localizedCategory(category: AppCategory?): String = when (category) {
        AppCategory.MUSIC -> getString(R.string.category_music)
        AppCategory.VIDEO -> getString(R.string.category_video)
        AppCategory.GAMES -> getString(R.string.category_games)
        AppCategory.TRANSPORT -> getString(R.string.category_transport)
        AppCategory.MAPS -> getString(R.string.category_maps)
        AppCategory.PHOTO -> getString(R.string.category_photo)
        AppCategory.CHAT -> getString(R.string.category_chat)
        AppCategory.BROWSER -> getString(R.string.category_browser)
        AppCategory.MAIL -> getString(R.string.category_mail)
        AppCategory.MONEY -> getString(R.string.category_money)
        AppCategory.SHOPPING -> getString(R.string.category_shopping)
        AppCategory.FOOD -> getString(R.string.category_food)
        AppCategory.NEWS -> getString(R.string.category_news)
        AppCategory.NOTES -> getString(R.string.category_notes)
        AppCategory.FILES -> getString(R.string.category_files)
        AppCategory.HEALTH -> getString(R.string.category_health)
        AppCategory.WEATHER -> getString(R.string.category_weather)
        AppCategory.TRAVEL -> getString(R.string.category_travel)
        AppCategory.TOOLS -> getString(R.string.category_tools)
        null -> ""
    }

    private fun useOriginal(packageName: String) {
        choices.remove(packageName); removedPackages.add(packageName)
        saveSession(); refreshUi(); status.setText(R.string.status_using_original)
    }

    private fun assign(target: ApplicationTarget, data: PackData, drawable: String) {
        choices[target.packageName] = Choice(data.pack.packageName, drawable)
        removedPackages.remove(target.packageName)
        saveSession(); refreshUi(); status.text = getString(R.string.status_icon_selected, target.label)
    }

    private fun previewIcon(data: PackData, drawable: String) {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(24), dp(16), dp(24), dp(12)) }
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = friendlyName(drawable) }
        panel.addView(image, LinearLayout.LayoutParams(dp(144), dp(144)))
        panel.addView(label(drawable, 13f, muted = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        thumbnails.bind(image, "pack:${data.pack.packageName}:$drawable", 256)
        val dialog = MaterialAlertDialogBuilder(this).setTitle(friendlyName(drawable)).setView(panel)
            .setPositiveButton(R.string.action_use_for_app) { _, _ -> chooseTarget(data, drawable) }
            .setNegativeButton(R.string.action_close, null).create()
        dialog.setOnDismissListener { thumbnails.unbind(image) }
        dialog.show()
    }

    private fun chooseTarget(data: PackData, drawable: String) {
        val defaults = defaultMappings()
        visualSheet(getString(R.string.sheet_choose_app_title), getString(R.string.sheet_choose_app_subtitle, friendlyName(drawable)), false,
            items = { query ->
                val states = HashMap<String, AppIconState>()
                fun state(target: ApplicationTarget) = states.getOrPut(target.packageName) { appIconState(target, defaults) }
                val ranked = query.rank(targets()) { listOf(searchText(it.label), searchText(it.packageName)) }
                // Without a query, apps that still need artwork come first.
                (if (query.isBlank) ranked.sortedWith(compareBy<ApplicationTarget> { !state(it).needsIcon }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }) else ranked)
                    .map { target -> VisualItem(target.packageName, target.label, target.packageName, appIconKey(target.packageName), "pack:${data.pack.packageName}:$drawable",
                        state(target).status, state(target).needsIcon || state(target).unthemeable) }
            },
            onSelect = { item -> targets().firstOrNull { it.packageName == item.key }?.let { assign(it, data, drawable) } })
    }

    private fun failedPackNames(data: PackData): Set<String> {
        val prefix = "pack:${data.pack.packageName}:"
        return failedIcons.filter { it.startsWith(prefix) }.mapTo(hashSetOf()) { it.removePrefix(prefix) }
    }

    private fun availableIconNames(data: PackData): List<String> {
        val failed = failedPackNames(data)
        return data.drawables.filter { data.isDrawableAvailable(it) && it !in failed }
    }

    /**
     * A full-screen picker. The keyboard only shortens the result list instead of
     * covering a fixed-height sheet, wide and unfolded screens get more columns,
     * and the subtitle and actions step aside while typing.
     */
    private fun visualSheet(
        title: String,
        subtitle: String,
        grid: Boolean,
        items: (SearchQuery) -> List<VisualItem>,
        suggested: ((SearchQuery) -> List<VisualItem>)? = null,
        actions: List<Pair<String, () -> Unit>> = emptyList(),
        onSelect: (VisualItem) -> Unit,
    ) {
        val dialog = AppCompatDialog(this, R.style.Theme_MagicThemer_Picker)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; isFocusableInTouchMode = true
            setBackgroundColor(color(com.google.android.material.R.attr.colorSurface))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val headings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headings.addView(label(title, 20f, bold = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        val subtitleView = label(subtitle, 12f, muted = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        headings.addView(subtitleView)
        header.addView(headings, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button(getString(R.string.action_close), borderless = true) { dialog.dismiss() }, LinearLayout.LayoutParams(-2, dp(48)).apply { marginStart = dp(8) })
        panel.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val search = searchField(panel, getString(if (grid) R.string.search_icons_or_apps_hint else R.string.search_names_or_packages_hint))
        val adapter = VisualAdapter(this, thumbnails, grid) { item ->
            if (!busy) { dialog.dismiss(); onSelect(item) }
        }
        val suggestedAdapter = suggested?.let { VisualAdapter(this, thumbnails, grid) { item -> if (!busy) { dialog.dismiss(); onSelect(item) } } }
        val suggestedHeader = suggested?.let { SheetSectionHeader(getString(R.string.sheet_suggested_header)) }
        val catalogueHeader = suggested?.let { SheetSectionHeader(getString(R.string.sheet_all_pack_icons)) }
        val contentAdapter = if (suggestedAdapter == null) adapter else ConcatAdapter(suggestedHeader!!, suggestedAdapter, catalogueHeader!!, adapter)
        val list = recycler(contentAdapter, grid)
        if (suggestedAdapter != null) {
            val columns = list.layoutManager as GridLayoutManager
            columns.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    val catalogueStart = suggestedHeader!!.itemCount + suggestedAdapter.itemCount
                    val header = (suggestedHeader.itemCount == 1 && position == 0) || position == catalogueStart
                    return if (header) columns.spanCount else 1
                }
            }
        }
        panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        val controls = if (actions.isEmpty()) null else LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        controls?.let { row ->
            actions.forEachIndexed { index, (text, action) ->
                row.addView(button(text, outlined = true) { dialog.dismiss(); if (!busy) action() },
                    LinearLayout.LayoutParams(0, dp(52), 1f).apply { if (index > 0) marginStart = dp(8) })
            }
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        fun updateResults(text: String, scrollToTop: Boolean) {
            val query = SearchQuery(text)
            val matches = items(query)
            val recommendations = suggested?.invoke(query).orEmpty()
            // While searching, a separate suggestion section would push matches
            // behind the keyboard, so suggestions lead one ranked result grid.
            val separate = query.isBlank
            suggestedAdapter?.submit(if (separate) recommendations else emptyList())
            suggestedHeader?.update(
                if (recommendations.isEmpty()) getString(R.string.sheet_suggested_none)
                else getString(R.string.sheet_suggested_count, recommendations.size),
                visible = separate,
            )
            val results = if (separate || recommendations.isEmpty()) matches else {
                val byKey = matches.associateBy { it.key }
                recommendations.map { byKey[it.key] ?: it.copy(subtitle = "") } + matches.filter { item -> recommendations.none { it.key == item.key } }
            }
            catalogueHeader?.update(
                if (separate) getString(R.string.sheet_all_pack_icons_count, matches.size)
                else getString(R.string.sheet_matching_icons_count, results.size),
            )
            adapter.submit(results) { if (scrollToTop) list.scrollToPosition(0) }
        }
        updateResults("", false)
        watchSearch(search) { updateResults(search.text.toString(), true) }
        ViewCompat.setOnApplyWindowInsetsListener(panel) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left + dp(16), bars.top + dp(8), bars.right + dp(16), maxOf(bars.bottom, ime.bottom) + dp(8))
            val keyboard = insets.isVisible(WindowInsetsCompat.Type.ime())
            subtitleView.visibility = if (keyboard) View.GONE else View.VISIBLE
            controls?.visibility = if (keyboard) View.GONE else View.VISIBLE
            WindowInsetsCompat.CONSUMED
        }
        dialog.setContentView(panel)
        dialog.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.setOnDismissListener { list.adapter = null }
        dialog.show()
    }

    private inner class SheetSectionHeader(private var value: String) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var shown = true
        override fun getItemCount(): Int = if (shown) 1 else 0
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(label("", 14f, muted = true, bold = true).apply {
                setPadding(dp(4), dp(14), dp(4), dp(10)); layoutParams = RecyclerView.LayoutParams(-1, -2)
                ViewCompat.setAccessibilityHeading(this, true)
            }) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) { (holder.itemView as TextView).text = value }
        fun update(text: String, visible: Boolean = true) {
            value = text
            when {
                visible && !shown -> { shown = true; notifyItemInserted(0) }
                !visible && shown -> { shown = false; notifyItemRemoved(0) }
                visible -> notifyItemChanged(0)
            }
        }
    }

    private fun openThemePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, OPEN_THEME)
    }

    private fun importTheme(uri: Uri) {
        setBusy(true, getString(R.string.status_opening_theme))
        background {
            val copy = File.createTempFile("imported-", ".hnt", themeWorkDir)
            var opened: ThemeDocument? = null
            try {
                val input = contentResolver.openInputStream(uri) ?: error("This file cannot be read.")
                input.use { source -> copy.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024); var total = 0L
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        val count = source.read(buffer); if (count < 0) break
                        total += count; require(total <= ThemeEditor.MAX_ARCHIVE_BYTES) { "Theme exceeds 256 MiB." }
                        output.write(buffer, 0, count)
                    }
                } }
                val imported = ThemeEditor.open(copy)
                opened = imported
                val name = runCatching { contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "" } }.getOrNull().orEmpty()
                ui(onDiscard = { ThemeEditor.close(imported); copy.delete() }) {
                    saveSession()
                    val old = document
                    document = imported; appliedPack = null; choices.clear(); removedPackages.clear()
                    ready = false
                    titleInput.setText(imported.title.ifBlank { DEFAULT_IMPORTED_TITLE }); designerInput.setText(imported.designer); briefInput.setText(imported.briefInfo)
                    replaceIcons.isChecked = false
                    repairCompatibility.isChecked = true
                    extension = if (name.endsWith(".hwt", true)) "hwt" else "hnt"
                    formatInput.setText(formatLabels()[if (extension == "hwt") 1 else 0], false)
                    ready = true
                    old?.let { ThemeEditor.close(it); if (it.sourceFile != copy) it.sourceFile.delete() }
                    saveSession(); persist(); refreshUi()
                    setBusy(false, getString(R.string.status_theme_opened_copy))
                    if (imported.warnings.isNotEmpty()) showThemeDetails()
                }
            } catch (failure: Exception) {
                opened?.let(ThemeEditor::close); copy.delete(); throw failure
            }
        }
    }

    private fun newTheme() {
        val old = document
        document = null; appliedPack = null; choices.clear(); removedPackages.clear()
        old?.let { ThemeEditor.close(it); it.sourceFile.delete() }
        ready = false
        titleInput.setText(packTitle()); designerInput.setText(defaultDesigner()); briefInput.setText("")
        wholePack.isChecked = true; replaceIcons.isChecked = false
        repairCompatibility.isChecked = true
        ready = true
        saveSession(); persist(); refreshUi()
        status.setText(R.string.status_new_theme)
    }

    private fun exportCandidates(
        data: PackData?, assignments: Map<String, Choice>, exportApps: List<ApplicationTarget>,
        exportPacks: List<IconPack>, includeAll: Boolean, imported: Boolean, removed: Set<String>,
    ): List<RenderCandidate> {
        val selected = linkedMapOf<String, RenderCandidate>()
        data?.let { source ->
            ThemeMappingSelector.select(source.mappings, exportApps, includeAll).forEach { selected[it.packageName] = RenderCandidate(source, it) }
        }
        val installed = exportApps.mapTo(hashSetOf()) { it.packageName }
        assignments.forEach { (pkg, choice) ->
            if (imported || includeAll || pkg in installed) {
                val source = loadedPacks[choice.pack]
                    ?: exportPacks.firstOrNull { it.packageName == choice.pack }?.let { repository.load(it).also(::cachePack) }
                    ?: error("The icon pack for $pkg is no longer installed.")
                val activity = exportApps.firstOrNull { it.packageName == pkg }?.activities?.firstOrNull().orEmpty()
                selected[pkg] = RenderCandidate(source, IconMapping("ComponentInfo{$pkg/$activity}", pkg, activity, choice.drawable))
            }
        }
        removed.forEach(selected::remove)
        require(selected.size <= ThemeArchive.MAX_ICON_COUNT) { "This selection exceeds 20,000 apps. Turn Whole icon pack off to export installed apps only." }
        return selected.values.toList()
    }

    private fun encodePng(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { buffer ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)) { "Could not encode an icon." }
        buffer.toByteArray()
    }

    private fun buildFallbackStyle(data: PackData?): ThemeFallbackStyle? {
        val style = data?.fallbackStyle?.takeIf { it.hasAssets } ?: return null
        val packPkg = data.pack.packageName
        val backgroundPngs = ArrayList<ByteArray>()
        var firstBackgroundPixels: IntArray? = null
        for (name in style.backgrounds.take(ThemeDynamicAssets.MAX_BACKGROUNDS)) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val bmp = repository.renderPackResource(packPkg, name, ICON_SIZE) ?: continue
            try {
                val png = encodePng(bmp)
                if (png.size <= ThemeArchive.MAX_ICON_BYTES) {
                    if (firstBackgroundPixels == null) {
                        firstBackgroundPixels = IntArray(ICON_SIZE * ICON_SIZE).also { bmp.getPixels(it, 0, ICON_SIZE, 0, 0, ICON_SIZE, ICON_SIZE) }
                    }
                    backgroundPngs += png
                }
            } finally { bmp.recycle() }
        }
        val maskScale = if (backgroundPngs.isNotEmpty()) style.scale else 1f
        var maskPng: ByteArray? = null
        for (name in style.masks) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val bmp = repository.renderPackResource(packPkg, name, ICON_SIZE) ?: continue
            val src = IntArray(ICON_SIZE * ICON_SIZE).also {
                try { bmp.getPixels(it, 0, ICON_SIZE, 0, 0, ICON_SIZE, ICON_SIZE) } finally { bmp.recycle() }
            }
            val honorPixels = ThemeDynamicAssets.invertAlphaToBlackMask(src, ICON_SIZE, ICON_SIZE, maskScale)
            val out = Bitmap.createBitmap(honorPixels, ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
            try {
                val png = encodePng(out)
                if (png.size <= ThemeArchive.MAX_ICON_BYTES) { maskPng = png; break }
            } finally { out.recycle() }
        }
        if (maskPng == null && firstBackgroundPixels != null) {
            val silhouette = ThemeDynamicAssets.alphaSilhouetteToBlackMask(firstBackgroundPixels, ICON_SIZE, ICON_SIZE, style.scale)
            val out = Bitmap.createBitmap(silhouette, ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
            try {
                val png = encodePng(out)
                if (png.size <= ThemeArchive.MAX_ICON_BYTES) maskPng = png
            } finally { out.recycle() }
        }
        var borderPng: ByteArray? = null
        for (name in style.upons) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val bmp = repository.renderPackResource(packPkg, name, ICON_SIZE) ?: continue
            try {
                val png = encodePng(bmp)
                if (png.size <= ThemeArchive.MAX_ICON_BYTES) { borderPng = png; break }
            } finally { bmp.recycle() }
        }
        // EMUI/HONOR HwResourcesImpl.getAllBgImage() only loads icon_background*.png
        // when icon_border.png is present, so supply a transparent border if needed.
        if (borderPng == null && backgroundPngs.isNotEmpty()) {
            val transparent = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
            try { borderPng = encodePng(transparent) } finally { transparent.recycle() }
        }
        return ThemeFallbackStyle(maskPng, borderPng, backgroundPngs).takeUnless { it.isEmpty }
    }

    private fun buildDynamicCalendar(
        data: PackData?,
        installedPackages: Set<String>,
        removed: Set<String>,
        assignments: Map<String, Choice>,
    ): ThemeDynamicCalendar? {
        if (data == null || data.calendarMappings.isEmpty()) return null
        val calendarPkgs = setOf("com.hihonor.calendar", "com.android.calendar", "com.huawei.calendar")
        if (calendarPkgs.any { it in removed || it in assignments }) return null
        val chosen = ThemeDynamicAssets.selectCalendarMapping(data.calendarMappings, installedPackages) ?: return null
        val packPkg = data.pack.packageName
        val samples = ArrayList<IntArray>()
        val daysToTry = ThemeDynamicAssets.CALENDAR_SAMPLE_DAYS + (1..31).filter { it !in ThemeDynamicAssets.CALENDAR_SAMPLE_DAYS }
        for (day in daysToTry) {
            if (samples.size >= ThemeDynamicAssets.CALENDAR_SAMPLE_DAYS.size) break
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val bmp = repository.renderPackResource(packPkg, chosen.prefix + day, ICON_SIZE) ?: continue
            val pixels = IntArray(ICON_SIZE * ICON_SIZE)
            try { bmp.getPixels(pixels, 0, ICON_SIZE, 0, 0, ICON_SIZE, ICON_SIZE) } finally { bmp.recycle() }
            samples += pixels
        }
        if (samples.size < 4) return null
        val (bgPixels, dateColorHex) = ThemeDynamicAssets.extractBackgroundAndDateColor(samples, ICON_SIZE, ICON_SIZE)
        val out = Bitmap.createBitmap(bgPixels, ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        return try {
            val png = encodePng(out)
            if (png.size <= ThemeArchive.MAX_ICON_BYTES) ThemeDynamicCalendar(png, dateColorHex) else null
        } finally { out.recycle() }
    }

    private fun exportTheme() {
        val title = titleInput.text.toString().trim()
        if (title.isBlank()) { titleInput.error = getString(R.string.error_enter_theme_name); tabs.getTabAt(2)?.select(); return }
        val source = document
        val designer = designerInput.text.toString()
        val brief = briefInput.text.toString()
        val format = extension
        val removed = removedPackages.toSet()
        val replaceAll = source != null && appliedPack != null && replaceIcons.isChecked
        val repair = source?.compatibilityRepairAvailable == true && repairCompatibility.isChecked
        val base = if (source == null) packData else appliedPack
        val includeStyle = styleUnmatched.isChecked
        val includeLiveCalendar = liveCalendar.isChecked
        val assignments = choices.toMap()
        val exportApps = apps.toList()
        val exportPacks = packs.toList()
        val includeAll = wholePack.isChecked
        val sourceLabel = packData?.pack?.label ?: "Selected icons"
        setBusy(true, getString(R.string.status_preparing_theme))
        background {
            val candidates = exportCandidates(base, assignments, exportApps, exportPacks, includeAll, source != null, removed)
            require(source != null || candidates.isNotEmpty()) { "No app icons selected. Assign icons manually or select a pack with app mappings." }
            val replacements = linkedMapOf<String, ByteArray>()
            val previewIcons = ArrayList<Bitmap>()
            var missing = 0; var bytes = 0L
            val output = File.createTempFile("export-", ".$format", themeWorkDir)
            try {
                candidates.forEachIndexed { index, candidate ->
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val bitmap = repository.render(candidate.data, candidate.mapping, ICON_SIZE)
                    if (bitmap == null) missing++ else try {
                        val png = encodePng(bitmap)
                        require(png.size <= ThemeArchive.MAX_ICON_BYTES) { "An icon exceeds 256 KiB." }
                        bytes += png.size; require(bytes <= ThemeArchive.MAX_TOTAL_ICON_BYTES) { "Rendered icons exceed 64 MiB. Turn Whole icon pack off." }
                        replacements[candidate.mapping.packageName] = png
                        if ((source == null || repair) && previewIcons.size < 12) previewIcons += Bitmap.createScaledBitmap(bitmap, 80, 80, true)
                    } finally { bitmap.recycle() }
                    if (index % 20 == 0) ui { status.text = getString(R.string.status_rendering_icons, index + 1, candidates.size) }
                }
                if (candidates.isNotEmpty() && (source == null || replaceAll)) require(replacements.isNotEmpty()) { "None of the selected icons could be read. Try another icon pack." }
                val fallbackStyle = if (includeStyle) buildFallbackStyle(base) else null
                val dynamicCalendar = if (includeLiveCalendar) buildDynamicCalendar(base, exportApps.mapTo(hashSetOf()) { it.packageName }, removed, assignments) else null
                if (source != null && !replaceAll && repair && previewIcons.size < 12) {
                    source.iconEntries.keys.filter { it !in replacements && it !in removed }.take(12 - previewIcons.size).forEach { pkg ->
                        ThemeEditor.readIcon(source, pkg)?.let { png -> decodeThemePng(png, 80)?.let(previewIcons::add) }
                    }
                }
                // Keep an imported working theme's authored previews intact.
                val preview = if (source == null || repair) createPreview(title, previewIcons, 720, 1280) else null
                val iconsPreview = if (source == null || repair) createPreview(title, previewIcons, 720, 1280) else null
                output.outputStream().use { stream ->
                    if (source == null) ThemeArchive.write(
                        stream, title, sourceLabel, replacements.map { ThemeIcon(it.key, it.value) }, format,
                        preview = preview, iconsPreview = iconsPreview, tempDirectory = themeWorkDir, designer = designer, briefInfo = brief,
                        fallbackStyle = fallbackStyle, dynamicCalendar = dynamicCalendar,
                    ) else ThemeEditor.write(source, stream, title, designer, brief, replacements, removed, replaceAll, preview, iconsPreview,
                        upgradeMinimalProfile = repair, fallbackStyle = fallbackStyle, dynamicCalendar = dynamicCalendar)
                }
                val count = if (source == null || replaceAll) replacements.size else (source.iconEntries.keys - removed + replacements.keys).size
                val calendarSuppressedByUser = setOf("com.hihonor.calendar", "com.android.calendar", "com.huawei.calendar").any { it in removed || it in assignments }
                val reportParts = buildList {
                    add(resources.getQuantityString(R.plurals.report_icons_saved, count, count))
                    when {
                        fallbackStyle != null -> add(getString(R.string.report_fallback_included))
                        includeStyle && base?.fallbackStyle?.hasAssets == true -> add(getString(R.string.report_fallback_skipped))
                    }
                    when {
                        dynamicCalendar != null && source?.hasLauncherModule == true -> add(getString(R.string.report_live_calendar_existing_launcher))
                        dynamicCalendar != null -> add(getString(R.string.report_live_calendar_included))
                        includeLiveCalendar && base?.calendarMappings?.isNotEmpty() == true && !calendarSuppressedByUser -> add(getString(R.string.report_live_calendar_skipped))
                    }
                    if (missing > 0) add(resources.getQuantityString(R.plurals.report_missing_skipped, missing, missing))
                }
                val report = reportParts.joinToString("; ") + "."
                ui(onDiscard = { output.delete() }) {
                    pendingExport?.let(::File)?.takeIf(::isWorkingExport)?.delete()
                    pendingExport = output.absolutePath; pendingReport = report
                    persist(); setBusy(false, getString(R.string.status_export_choose_location, report))
                    val safeName = title.replace(Regex("[^\\p{L}\\p{N} _-]"), "_").take(64).ifBlank { "Magic Theme" }
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"
                        putExtra(Intent.EXTRA_TITLE, safeName + if (source == null) ".$format" else "-edited.$format")
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, SAVE_THEME)
                }
            } catch (failure: Exception) { output.delete(); throw failure }
            finally { previewIcons.forEach { it.recycle() } }
        }
    }

    private fun createPreview(title: String, icons: List<Bitmap>, width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(239, 244, 240))
            val ratio = width / 510f
            canvas.scale(ratio, ratio)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(31, 67, 55); textSize = 22f; isFakeBoldText = true }
            canvas.drawText(title.take(32), 24f, 36f, paint)
            val rowGap = if (height > width) 150 else 94
            icons.take(12).forEachIndexed { index, icon ->
                val left = 29 + index % 4 * 122; val top = 54 + index / 4 * rowGap
                canvas.drawBitmap(icon, null, Rect(left, top, left + 80, top + 80), null)
            }
            return ByteArrayOutputStream().use { stream -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)); stream.toByteArray() }
        } finally { bitmap.recycle() }
    }

    @Deprecated("Platform document picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val result = DocumentResult(requestCode, resultCode, data?.data?.toString())
        if (!initialized && requestCode in setOf(OPEN_THEME, SAVE_THEME)) {
            deferredDocumentResult = result
            return
        }
        handleDocumentResult(result)
    }

    private fun handleDocumentResult(result: DocumentResult) {
        val requestCode = result.requestCode
        val resultCode = result.resultCode
        val uri = result.uri?.let(Uri::parse)
        if (requestCode == OPEN_THEME) {
            if (resultCode == Activity.RESULT_OK && uri != null) importTheme(uri)
            return
        }
        if (requestCode != SAVE_THEME) return
        if (resultCode != Activity.RESULT_OK || uri == null) { status.setText(R.string.status_save_cancelled); return }
        val file = pendingExport?.let(::File)
        if (file == null || !isWorkingExport(file) || !file.isFile) {
            pendingExport = null; persist()
            status.setText(R.string.status_working_export_unavailable)
            return
        }
        setBusy(true, getString(R.string.status_saving_file))
        background {
            val expectedBytes = file.length()
            var expectedHash = byteArrayOf()
            var verifiedName: String? = null
            var destination = uri
            try {
                require(expectedBytes > 0) { "The working export is empty. Save your theme again." }
                // ACTION_CREATE_DOCUMENT initially exposes an empty .hnt. Drop that
                // extension while writing, then publish the verified file by rename,
                // like moving a completed export into the Themes folder.
                val (requestedName, flags) = savedDocumentInfo(destination)
                val stagedName = requestedName?.takeIf {
                    it.endsWith(".${file.extension}", ignoreCase = true) &&
                        flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0
                }?.let { "$it.${file.nameWithoutExtension}.partial" }
                val stagedUri = stagedName?.let { name ->
                    runCatching { DocumentsContract.renameDocument(contentResolver, destination, name) }.getOrNull()
                }
                if (stagedUri != null) destination = stagedUri
                expectedHash = file.inputStream().use { verifiedHash(it, expectedBytes) }
                val stream = contentResolver.openOutputStream(destination, "w") ?: error("This location cannot be written.")
                val writtenBytes = stream.use { output -> file.inputStream().use { it.copyTo(output, 32 * 1024) } }
                require(writtenBytes == expectedBytes) { "The saved file has an unexpected length." }
                verifySavedDocument(destination, expectedBytes, expectedHash)
                if (stagedUri != null) {
                    destination = DocumentsContract.renameDocument(contentResolver, destination, checkNotNull(requestedName))
                        ?: error("This location could not finalize the theme filename.")
                    // A rename may return a different document URI and invalidate the old one.
                    verifySavedDocument(destination, expectedBytes, expectedHash)
                }
                verifiedName = savedDocumentInfo(destination).first
                if (stagedUri != null) require(verifiedName?.endsWith(".${file.extension}", ignoreCase = true) == true) {
                    "This location did not restore the theme filename."
                }
            } catch (failure: Exception) {
                // This URI was just created by ACTION_CREATE_DOCUMENT; the
                // imported source and private working export remain untouched.
                val removed = runCatching { DocumentsContract.deleteDocument(contentResolver, destination) }.getOrDefault(false)
                val cleanup = if (removed) " The unverified destination was removed." else " The destination could not be verified; remove it before retrying."
                throw IllegalStateException("Save verification failed: ${failure.message ?: "the saved file could not be read"}. Working export retained; save again.$cleanup", failure)
            }
            val filename = verifiedName?.takeIf { it.isNotBlank() }
            val suffix = ".${file.extension}"
            val filenameWarning = when {
                filename == null -> getString(R.string.warning_provider_no_filename, suffix)
                !filename.endsWith(suffix, ignoreCase = true) -> getString(R.string.warning_provider_bad_extension, filename, suffix)
                else -> ""
            }
            val hashLabel = expectedHash.joinToString("") { byte -> String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff) }
            val verifiedReport = getString(
                R.string.report_verified_details,
                pendingReport,
                filename ?: getString(R.string.report_name_unavailable),
                expectedBytes,
                hashLabel,
            ) + if (filenameWarning.isBlank()) "" else "\n\n$filenameWarning"
            ui {
                file.delete(); pendingExport = null; persist()
                setBusy(false, getString(R.string.status_verified_save, pendingReport, filename ?: getString(R.string.status_saved_file_fallback), expectedBytes))
                MaterialAlertDialogBuilder(this).setTitle(R.string.dialog_theme_saved_title)
                    .setMessage(getString(R.string.dialog_theme_saved_body, verifiedReport))
                    .setPositiveButton(R.string.action_open_themes) { _, _ -> openThemes() }
                    .setNeutralButton(R.string.action_app_info) { _, _ -> openThemesAppInfo() }
                    .setNegativeButton(R.string.action_done, null).show()
            }
        }
    }

    private fun savedDocumentInfo(uri: Uri): Pair<String?, Int> = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(cursor::getString)
            val flags = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS).takeIf { it >= 0 }?.let(cursor::getInt) ?: 0
            name to flags
        }
    }.getOrNull() ?: (null to 0)

    private fun verifySavedDocument(uri: Uri, expectedBytes: Long, expectedHash: ByteArray) {
        val input = contentResolver.openInputStream(uri) ?: error("This location does not allow verification of the saved file.")
        val savedHash = input.use { verifiedHash(it, expectedBytes) }
        require(MessageDigest.isEqual(expectedHash, savedHash)) { "The saved file differs from the working export." }
    }

    /** Read at most the known working export's size, rejecting extra/missing bytes. */
    private fun verifiedHash(input: InputStream, expectedBytes: Long): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val count = input.read(buffer); if (count < 0) break
            total += count
            require(total <= expectedBytes) { "The saved file is longer than expected." }
            digest.update(buffer, 0, count)
        }
        require(total == expectedBytes) { "The saved file is shorter than expected ($total of $expectedBytes bytes)." }
        return digest.digest()
    }

    private fun renderThumbnail(key: String, size: Int): Bitmap? {
        val parts = key.split(':', limit = 3)
        val image = when (parts.firstOrNull()) {
            "pack" -> if (parts.size == 3) loadedPacks[parts[1]]?.let { repository.renderDrawable(it, parts[2], size) } else null
            "theme" -> {
                val source = document
                if (parts.size == 3 && source?.sourceFile?.name == parts[1]) ThemeEditor.readIcon(source, parts[2])?.let { decodeThemePng(it, size) } else null
            }
            "app" -> if (parts.size >= 2) runCatching { drawIcon(packageManager.getApplicationIcon(parts[1]), size) }.getOrNull() else null
            "original" -> if (parts.size >= 2) originalAppIcon(parts[1], size)
                ?: runCatching { drawIcon(packageManager.getApplicationIcon(parts[1]), size) }.getOrNull() else null
            else -> null
        }
        // Resource lookup is cheap and used for all rows. Actual decode failures
        // are learned only as images become visible, without decoding the pack.
        if (image == null && (key.startsWith("pack:") || key.startsWith("theme:")) && failedIcons.add(key)) {
            ui { if (ready) { updateAppList(); updateIconList() } }
        }
        return image
    }

    private fun drawIcon(drawable: Drawable, size: Int): Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        drawable.mutate().apply { setBounds(0, 0, size, size) }.draw(Canvas(bitmap))
    }

    /**
     * The launcher icon an app ships with. HONOR swaps in the applied theme's
     * icon while loading the registered icon drawable (HwResourcesImpl.loadDrawable),
     * so this reads the icon resource itself: raw PNG/WebP bytes, or the XML
     * whose layers are ordinary drawables. Null when the icon cannot be read this way.
     */
    private fun originalAppIcon(packageName: String, size: Int): Bitmap? = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
        val activity = (if (Build.VERSION.SDK_INT >= 33) packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            else { @Suppress("DEPRECATION") packageManager.queryIntentActivities(intent, 0) }).firstOrNull()?.activityInfo
        val info = activity?.applicationInfo ?: packageManager.getApplicationInfo(packageName, 0)
        val id = activity?.iconResource?.takeIf { it != 0 } ?: info.icon
        if (id == 0) return@runCatching null
        val resources = packageManager.getResourcesForApplication(info)
        val value = TypedValue().also { resources.getValue(id, it, true) }
        if (value.string?.endsWith(".xml") == true) drawIcon(Drawable.createFromXml(resources, resources.getXml(id)), size)
        else resources.openRawResource(id, value).use(BitmapFactory::decodeStream)?.let { decoded ->
            if (decoded.width == size && decoded.height == size) decoded
            else Bitmap.createScaledBitmap(decoded, size, size, true).also { decoded.recycle() }
        }
    }.getOrNull()

    private fun decodeThemePng(bytes: ByteArray, size: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return if (decoded.width == size && decoded.height == size) decoded else {
            Bitmap.createScaledBitmap(decoded, size, size, true).also { decoded.recycle() }
        }
    }

    private fun showPackDetails() {
        val data = packData
        if (data == null) { showThemeDetails(); return }
        val mappings = data.mappings.map { it.packageName }.distinct().size
        val extras = when {
            data.fallbackStyle.hasAssets && data.calendarMappings.isNotEmpty() -> getString(R.string.pack_details_extras_both)
            data.fallbackStyle.hasAssets -> getString(R.string.pack_details_extras_fallback)
            data.calendarMappings.isNotEmpty() -> getString(R.string.pack_details_extras_calendar)
            else -> getString(R.string.pack_details_extras_none)
        }
        val warningsText = data.warnings.ifEmpty { listOf(getString(R.string.pack_details_no_warnings)) }.joinToString("\n\n")
        MaterialAlertDialogBuilder(this).setTitle(data.pack.label).setMessage(
            getString(R.string.dialog_pack_details_body, data.pack.packageName, data.drawables.size, mappings, extras, warningsText),
        ).setPositiveButton(R.string.action_close, null).show()
    }

    private fun showThemeDetails() {
        val source = document ?: run { showGuide(); return }
        val warningsText = source.warnings.ifEmpty { listOf(getString(R.string.imported_theme_default_note)) }.joinToString("\n\n")
        MaterialAlertDialogBuilder(this).setTitle(R.string.dialog_imported_theme_title).setMessage(
            getString(R.string.dialog_imported_theme_body, source.title, source.iconEntries.size, warningsText),
        ).setPositiveButton(R.string.action_close, null).show()
    }

    private fun showGuide() {
        MaterialAlertDialogBuilder(this).setTitle(R.string.dialog_guide_title)
            .setMessage(R.string.dialog_guide_message)
            .setPositiveButton(R.string.action_open_themes) { _, _ -> openThemes() }
            .setNeutralButton(R.string.action_app_info) { _, _ -> openThemesAppInfo() }
            .setNegativeButton(R.string.action_close, null).show()
    }

    private fun showSettings() {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, dp(24), dp(8)) }
        sectionHeading(panel, getString(R.string.settings_section_app_list))
        val original = MaterialSwitch(this).apply { setText(R.string.settings_show_original); isChecked = originalIcons }
        panel.addView(original, LinearLayout.LayoutParams(-1, dp(56)))
        panel.addView(label(getString(R.string.settings_show_original_note), 13f, muted = true))
        sectionHeading(panel, getString(R.string.settings_section_new_themes))
        val designer = editorField(panel, getString(R.string.settings_designer_credit), 128, false).apply { setText(preferences.getString("defaultDesigner", "")) }
        panel.addView(label(getString(R.string.settings_designer_note, deviceDesigner()), 13f, muted = true))
        sectionHeading(panel, getString(R.string.settings_section_about))
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        panel.addView(label(getString(R.string.settings_about_text, version), 13f, muted = true))
        panel.addView(button(getString(R.string.action_source_code), outlined = true) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))) }.onFailure { status.text = SOURCE_URL }
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        val scroll = NestedScrollView(this).apply { addView(panel) }
        MaterialAlertDialogBuilder(this).setTitle(R.string.settings_title).setView(scroll)
            .setPositiveButton(R.string.action_done, null)
            .setOnDismissListener {
                val credit = designer.text.toString().trim()
                val previous = defaultDesigner()
                preferences.edit().putString("defaultDesigner", credit).apply()
                // A new theme still carrying the old default picks up the new credit.
                if (document == null && designerInput.text.toString().trim() == previous) designerInput.setText(defaultDesigner())
                originalIconsSwitch.isChecked = original.isChecked
            }.show()
    }

    private fun sectionHeading(parent: LinearLayout, text: String) {
        val heading = label(text, 14f, bold = true).apply {
            setTextColor(color(androidx.appcompat.R.attr.colorPrimary)); ViewCompat.setAccessibilityHeading(this, true)
        }
        parent.addView(heading, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20); bottomMargin = dp(10) })
    }

    private fun openThemes() {
        val intent = THEME_PACKAGES.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (intent == null) MaterialAlertDialogBuilder(this).setTitle(R.string.dialog_open_themes_manual_title)
            .setMessage(R.string.dialog_open_themes_manual_message).setPositiveButton(R.string.action_close, null).show()
        else runCatching { startActivity(intent) }.onFailure { status.setText(R.string.status_open_themes_fallback) }
    }

    private fun openThemesAppInfo() {
        val pkg = THEME_PACKAGES.firstOrNull { packageManager.getLaunchIntentForPackage(it) != null }
        if (pkg == null) { openThemes(); return }
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
        runCatching { startActivity(intent) }.onFailure { status.setText(R.string.status_open_themes_info_fallback) }
    }

    private fun sessionKey(): String = document?.sourceFile?.name ?: "new:${packData?.pack?.packageName ?: "none"}"

    private fun saveSession() {
        if (!initialized) return
        val json = JSONObject()
        choices.forEach { (pkg, choice) -> json.put(pkg, JSONObject().put("pack", choice.pack).put("drawable", choice.drawable)) }
        preferences.edit().putString("choices:${sessionKey()}", json.toString())
            .putString("removed:${sessionKey()}", JSONArray(removedPackages.toList()).toString()).apply()
    }

    private fun restoreSession() {
        choices.clear(); removedPackages.clear()
        val json = runCatching { JSONObject(preferences.getString("choices:${sessionKey()}", "{}") ?: "{}") }.getOrNull()
        json?.keys()?.forEach { pkg ->
            val choice = json.optJSONObject(pkg) ?: return@forEach
            val pack = choice.optString("pack"); val drawable = choice.optString("drawable")
            if (packs.any { it.packageName == pack } && drawable.isNotBlank()) choices[pkg] = Choice(pack, drawable)
        }
        val removed = runCatching { JSONArray(preferences.getString("removed:${sessionKey()}", "[]") ?: "[]") }.getOrNull()
        if (removed != null) for (i in 0 until removed.length()) removedPackages.add(removed.optString(i))
        // Ensure icons from previously selected packs are renderable after restart.
        val missing = choices.values.map { it.pack }.distinct().filter { !loadedPacks.containsKey(it) }
        if (missing.isNotEmpty()) background {
            missing.forEach { pkg -> packs.firstOrNull { it.packageName == pkg }?.let { runCatching { repository.load(it) }.getOrNull()?.let(::cachePack) } }
            ui { thumbnails.clear(); ++revision; refreshUi(forceRebind = true) }
        }
    }

    private fun persist() {
        if (!ready || !initialized) return
        preferences.edit().putString("pack", packData?.pack?.packageName).putString("source", document?.sourceFile?.name)
            .putString("applied", appliedPack?.pack?.packageName).putString("title", titleInput.text.toString())
            .putString("designer", designerInput.text.toString()).putString("brief", briefInput.text.toString())
            .putBoolean("whole", wholePack.isChecked).putBoolean("replace", replaceIcons.isChecked)
            .putBoolean("styleUnmatched", styleUnmatched.isChecked).putBoolean("liveCalendar", liveCalendar.isChecked)
            .putBoolean("repair", repairCompatibility.isChecked)
            .putString("extension", extension).putInt("tab", selectedTab).putInt("appFilter", appFilter)
            .putBoolean("originalIcons", originalIcons)
            .putString("pendingExport", pendingExport).putString("pendingReport", pendingReport).apply()
    }

    private fun cachedImport(name: String?): File? = name?.takeIf(::isImportName)?.let { filename ->
        File(themeWorkDir, filename).takeIf(File::isFile)
            ?: File(cacheDir, filename).takeIf(File::isFile)?.let(::migrateWorkingFile)
    }

    private fun isImportName(name: String): Boolean = name.startsWith("imported-") && name.endsWith(".hnt") && '/' !in name && '\\' !in name
    private fun isExportName(name: String): Boolean = name.startsWith("export-") && (name.endsWith(".hnt") || name.endsWith(".hwt")) && '/' !in name && '\\' !in name
    private fun isWorkingExport(file: File): Boolean = isExportName(file.name) && file.absoluteFile.parentFile == themeWorkDir.absoluteFile

    /** Recover pre-0.3 working files only from this app's private cache. */
    private fun workingExport(path: String?): File? {
        val old = path?.let(::File) ?: return null
        if (!isExportName(old.name)) return null
        if (isWorkingExport(old)) return old.takeIf(File::isFile)
        if (old.absoluteFile.parentFile != cacheDir.absoluteFile) return null
        return File(themeWorkDir, old.name).takeIf(File::isFile)
            ?: old.takeIf(File::isFile)?.let(::migrateWorkingFile)
    }

    /** Rename is atomic on the app's volume; a bounded copy handles a fallback. */
    private fun migrateWorkingFile(source: File): File {
        require(source.absoluteFile.parentFile == cacheDir.absoluteFile && (isImportName(source.name) || isExportName(source.name))) { "Invalid legacy working file." }
        val destination = File(themeWorkDir, source.name)
        fun migratedByAnotherActivity(): Boolean = !source.isFile && destination.isFile && destination.length() in 1..ThemeEditor.MAX_ARCHIVE_BYTES
        if (migratedByAnotherActivity()) return destination
        require(source.length() in 1..ThemeEditor.MAX_ARCHIVE_BYTES) { "Legacy working file exceeds its supported size." }
        if (!destination.exists() && source.renameTo(destination)) return destination
        if (migratedByAnotherActivity()) return destination
        val prefix = if (isImportName(source.name)) "imported-" else "export-"
        val copy = File.createTempFile(prefix, ".${source.extension}", themeWorkDir)
        try {
            source.inputStream().use { input -> copy.outputStream().use { output ->
                val buffer = ByteArray(32 * 1024); var total = 0L
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val count = input.read(buffer); if (count < 0) break
                    total += count; require(total <= ThemeEditor.MAX_ARCHIVE_BYTES) { "Legacy working file exceeds its supported size." }
                    output.write(buffer, 0, count)
                }
                require(total > 0) { "Legacy working file is empty." }
            } }
            source.delete()
            return copy
        } catch (failure: Exception) { copy.delete(); throw failure }
    }

    private fun migrateSession(oldName: String, newName: String) {
        preferences.edit().putString("choices:$newName", preferences.getString("choices:$oldName", "{}"))
            .putString("removed:$newName", preferences.getString("removed:$oldName", "[]")).apply()
    }

    /** Leave fresh files to workers from a rotating Activity; remove aged orphans. */
    private fun cleanupWorkingFiles(retained: Set<File>) {
        val keep = retained.mapTo(hashSetOf()) { it.absoluteFile }
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        themeWorkDir.listFiles()?.filter { file -> file.isFile && file.lastModified() in 1 until cutoff && file.absoluteFile !in keep && (
            isImportName(file.name) || isExportName(file.name) ||
                (file.name.endsWith(".zip") && listOf("theme-icons-", "theme-edited-", "theme-export-icons-", "theme-export-launcher-", "theme-edited-launcher-").any { prefix -> file.name.startsWith(prefix) }) ||
                (file.name.startsWith("theme-zip-payload-") && file.name.endsWith(".tmp"))
            ) }?.forEach { it.delete() }
    }

    /** Pack reads and index construction stay on the shared background worker. */
    private fun cachePack(data: PackData) {
        if (loadedPacks[data.pack.packageName] !== data || !suggestionIndexes.containsKey(data.pack.packageName)) {
            suggestionIndexes[data.pack.packageName] = IconSuggestions(data)
            iconSearchIndexes[data.pack.packageName] = iconSearchIndex(data)
        }
        loadedPacks[data.pack.packageName] = data
    }

    /** Each artwork name plus the packages the pack maps to it, normalized once per pack. */
    private fun iconSearchIndex(data: PackData): Map<String, List<SearchText>> {
        val mapped = HashMap<String, MutableSet<String>>()
        data.mappings.forEach { mapping ->
            val packages = mapped.getOrPut(mapping.drawableName) { linkedSetOf() }
            if (packages.size < 8) packages += mapping.packageName
        }
        return data.drawables.associateWith { name ->
            val packages = mapped[name]
            if (packages == null) listOf(SearchText.of(name)) else listOf(SearchText.of(name), SearchText.of(packages.joinToString(" ")))
        }
    }

    private fun background(block: () -> Unit) {
        if (worker.isShutdown) return
        worker.execute {
            try { block() } catch (failure: Exception) {
                if (failure !is InterruptedException) ui { setBusy(false, failure.message ?: getString(R.string.status_operation_failed)) }
            }
        }
    }

    private fun ui(onDiscard: () -> Unit = {}, block: () -> Unit) {
        handler.post { if (!isDestroyed && !isFinishing) block() else onDiscard() }
    }

    private fun setBusy(value: Boolean, message: String) {
        busy = value; status.text = message; progress.visibility = if (value) View.VISIBLE else View.GONE; updateControls()
    }

    private fun updateControls() {
        if (!::saveButton.isInitialized) return
        val activePack = if (document == null) packData else appliedPack
        choosePackButton.isEnabled = !busy; importButton.isEnabled = !busy; changeSourceButton.isEnabled = !busy
        saveButton.isEnabled = !busy && (document != null || packData != null)
        applyPackButton.isEnabled = !busy && document != null && packData != null
        shareRequestButton.isEnabled = !busy
        titleInput.isEnabled = !busy; designerInput.isEnabled = !busy; briefInput.isEnabled = !busy
        wholePack.isEnabled = !busy; replaceIcons.isEnabled = !busy && appliedPack != null; formatInput.isEnabled = !busy
        styleUnmatched.isEnabled = !busy && activePack?.fallbackStyle?.hasAssets == true
        liveCalendar.isEnabled = !busy && activePack?.calendarMappings?.isNotEmpty() == true
        repairCompatibility.isEnabled = !busy
        appSearch.isEnabled = !busy; iconSearch.isEnabled = !busy
        toolbar.menu.findItem(MENU_NEW)?.isEnabled = !busy
        toolbar.menu.findItem(MENU_REFRESH)?.isEnabled = !busy
        toolbar.menu.findItem(MENU_OPEN)?.isEnabled = !busy
        toolbar.menu.findItem(MENU_SHARE_REQUEST)?.isEnabled = !busy
    }

    private fun showPage() { if (::pages.isInitialized) pages.forEachIndexed { index, view -> view.visibility = if (index == selectedTab) View.VISIBLE else View.GONE } }

    /** While the keyboard is open, the header and save bar give their height to search results. */
    private fun setTyping(value: Boolean) {
        if (typing == value || !::footer.isInitialized) return
        typing = value
        if (value) appBar.setExpanded(false, true)
        footer.visibility = if (value) View.GONE else View.VISIBLE
    }

    private fun hideKeyboard(view: View) {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken, 0)
        view.rootView.findFocus()?.takeIf { it is TextInputEditText }?.clearFocus()
    }

    /**
     * Columns follow the list's actual width, so an unfolded inner screen shows
     * more results than the cover screen and refits after folding or resizing.
     */
    private fun recycler(adapter: RecyclerView.Adapter<*>, grid: Boolean): RecyclerView = RecyclerView(this).apply {
        val columns = GridLayoutManager(this@MainActivity, 1)
        fun span(width: Int): Int = if (grid) (width / dp(104)).coerceIn(3, 10) else (width / dp(340)).coerceIn(1, 3)
        layoutManager = columns
        columns.spanCount = span(resources.displayMetrics.widthPixels)
        addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            val width = right - left
            if (width > 0 && width != oldRight - oldLeft && span(width) != columns.spanCount) post { columns.spanCount = span(width) }
        }
        // Dragging the results puts the keyboard away so every match is visible.
        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING &&
                    ViewCompat.getRootWindowInsets(recyclerView)?.isVisible(WindowInsetsCompat.Type.ime()) == true) hideKeyboard(recyclerView)
            }
        })
        this.adapter = adapter; clipToPadding = false; setPadding(0, 0, 0, dp(8)); itemAnimator = null
    }

    private fun searchField(parent: LinearLayout, hint: String): TextInputEditText {
        val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputStyle).apply { this.hint = hint; endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT }
        val input = TextInputEditText(layout.context).apply {
            setSingleLine(); textSize = 15f; inputType = android.text.InputType.TYPE_CLASS_TEXT
            // No full-screen landscape editor: results must stay visible beside the keyboard.
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setOnEditorActionListener { view, action, _ -> (action == EditorInfo.IME_ACTION_SEARCH).also { if (it) hideKeyboard(view) } }
        }
        layout.addView(input); parent.addView(layout, LinearLayout.LayoutParams(-1, dp(60)))
        return input
    }

    private fun editorField(parent: LinearLayout, hint: String, limit: Int, multiline: Boolean): TextInputEditText {
        val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputStyle).apply { this.hint = hint; isCounterEnabled = true; counterMaxLength = limit }
        val input = TextInputEditText(layout.context).apply {
            filters = arrayOf(InputFilter.LengthFilter(limit)); textSize = 16f
            if (multiline) { minLines = 2; maxLines = 4; gravity = Gravity.TOP; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE }
            else setSingleLine()
        }
        layout.addView(input); parent.addView(layout, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        return input
    }

    private fun button(text: String, tonal: Boolean = false, outlined: Boolean = false, borderless: Boolean = false, action: () -> Unit): MaterialButton =
        MaterialButton(this, null, when {
            borderless -> androidx.appcompat.R.attr.borderlessButtonStyle
            outlined -> com.google.android.material.R.attr.materialButtonOutlinedStyle
            tonal -> com.google.android.material.R.attr.materialButtonTonalStyle
            else -> com.google.android.material.R.attr.materialButtonStyle
        }).apply {
            this.text = text; isAllCaps = false; textSize = 14f; minHeight = dp(48); setOnClickListener { if (!busy) action() }
        }

    private fun label(value: String, size: Float, muted: Boolean = false, bold: Boolean = false): TextView = TextView(this).apply {
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        text = value; textSize = size
        setTextColor(color(if (muted) com.google.android.material.R.attr.colorOnSurfaceVariant else com.google.android.material.R.attr.colorOnSurface))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun watchText(input: TextInputEditText, changed: () -> Unit) { input.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed()
        override fun afterTextChanged(s: Editable?) = Unit
    }) }

    private fun watchSearch(input: TextInputEditText, changed: () -> Unit) {
        var callback: Runnable? = null
        watchText(input) {
            callback?.let(handler::removeCallbacks)
            callback = Runnable { if (!isDestroyed && !isFinishing) changed() }.also { handler.postDelayed(it, 180) }
        }
    }

    private fun friendlyName(value: String): String = value.trim('_').replace('_', ' ').replaceFirstChar { it.uppercase() }
    private fun color(attribute: Int): Int = MaterialColors.getColor(this, attribute, Color.GRAY)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        persist(); saveSession()
        outState.putString("source", document?.sourceFile?.name); outState.putString("pack", packData?.pack?.packageName)
        outState.putString("applied", appliedPack?.pack?.packageName); outState.putString("title", titleInput.text.toString())
        outState.putString("designer", designerInput.text.toString()); outState.putString("brief", briefInput.text.toString())
        outState.putBoolean("whole", wholePack.isChecked); outState.putBoolean("replace", replaceIcons.isChecked)
        outState.putBoolean("styleUnmatched", styleUnmatched.isChecked); outState.putBoolean("liveCalendar", liveCalendar.isChecked)
        outState.putBoolean("repair", repairCompatibility.isChecked)
        outState.putString("extension", extension); outState.putInt("tab", selectedTab); outState.putInt("appFilter", appFilter)
        outState.putBoolean("originalIcons", originalIcons)
        outState.putString("pendingExport", pendingExport); outState.putString("pendingReport", pendingReport)
        deferredDocumentResult?.let { result ->
            outState.putInt("documentRequest", result.requestCode); outState.putInt("documentResult", result.resultCode)
            outState.putString("documentUri", result.uri)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onPause() { if (ready) { persist(); saveSession() }; super.onPause() }

    override fun onDestroy() {
        ready = false; worker.shutdownNow()
        thumbnails.close(); document?.let(ThemeEditor::close)
        super.onDestroy()
    }

    companion object {
        private val THEME_PACKAGES = listOf("com.hihonor.android.thememanager", "com.huawei.android.thememanager")
        private const val ICON_SIZE = 192
        private const val DEFAULT_TITLE = "My Magic Icons"
        private const val DEFAULT_IMPORTED_TITLE = "My Magic Theme"
        /** Designer credit before 0.7; still replaced by the phone's name on new themes. */
        private const val LEGACY_DESIGNER = "Magic Themer"
        private const val OPEN_THEME = 101
        private const val SAVE_THEME = 100
        private const val MENU_NEW = 201
        private const val MENU_REFRESH = 202
        private const val MENU_DETAILS = 203
        private const val MENU_THEMES = 204
        private const val MENU_HELP = 205
        private const val MENU_OPEN = 206
        private const val MENU_SETTINGS = 207
        private const val MENU_SHARE_REQUEST = 208
        private const val SOURCE_URL = "https://github.com/tzii/magic-themer"
        private const val FILTER_ALL = 0
        private const val FILTER_MISSING = 1
        private const val FILTER_CUSTOMIZED = 2
        private const val FILTER_UNTHEMEABLE = 3
        private const val MAX_SEARCH_TEXTS = 200_000
    }
}
