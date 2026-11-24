package com.example.audiodrop

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.VelocityTracker
import android.view.animation.DecelerateInterpolator
import android.window.OnBackInvokedCallback
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.PopupWindow
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private enum class Tab { HOME, HISTORY, SETTINGS }

    private data class HistoryItem(
        val title: String,
        val uri: String,
        val format: String,
        val mimeType: String,
        val quality: String,
        val sizeBytes: Long,
        val artworkPath: String? = null,
        val savedAt: Long,
        val isArchived: Boolean = false
    )

    private data class QueueMedia(val title: String, val url: String)

    private data class ScrollEdgeHapticState(
        var lastTime: Long = 0L,
        var lastY: Int = 0,
        var topLatched: Boolean = false,
        var bottomLatched: Boolean = false
    )

    private data class PendingArtwork(val path: String?, val uri: String, val identity: String)

    private data class DownloadSession(
        val id: String,
        val sourceUrl: String,
        val video: Boolean,
        val maxVideoHeight: Int?,
        val audioQuality: Int,
        var queue: List<QueueMedia> = listOf(QueueMedia("Preparing…", sourceUrl)),
        var playlistResolved: Boolean = false,
        var isPlaylist: Boolean = false,
        @Volatile var currentIndex: Int = 0,
        @Volatile var downloadedCount: Int = 0,
        @Volatile var paused: Boolean = false,
        @Volatile var cancelled: Boolean = false,
        @Volatile var currentProcessId: String? = null,
        @Volatile var currentPercent: Int = 0,
        val failedItems: MutableMap<Int, String> = linkedMapOf()
    )

    private val worker = Executors.newSingleThreadExecutor()
    private val artworkWorker = Executors.newFixedThreadPool(2)
    private val preferences by lazy { getSharedPreferences("audio_drop", MODE_PRIVATE) }
    private lateinit var linkInput: EditText
    private lateinit var qualityPicker: TextView
    private lateinit var downloadButton: Button
    private lateinit var videoButton: Button
    private lateinit var cancelButton: Button
    private lateinit var pauseButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var pageHost: FrameLayout
    private lateinit var navigationBar: LinearLayout
    private lateinit var currentPageView: View
    private var nextPageView: View? = null
    private var appearanceMode = MODE_SYSTEM
    private var darkMode = false
    private var accentColor = ACCENT_BLUE
    private var selectedTab = Tab.HOME
    private var activeProcessId: String? = null
    @Volatile private var isDownloading = false
    @Volatile private var activeSession: DownloadSession? = null
    private var activityDestroyed = false
    private val downloadedThisSession = java.util.concurrent.CopyOnWriteArrayList<HistoryItem>()
    private var homeQueueList: TextView? = null
    private var homeQueueSection: LinearLayout? = null
    private var sessionDownloadsList: LinearLayout? = null
    private var savedLink = ""
    private var savedQualityIndex = 0
    private var savedVideoQualityIndex = 0
    private var statusMessage = ""
    private var statusProgress = 0
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeDistanceX = 0f
    private var swipeActive = false
    private var swipeTargetTab: Tab? = null
    private var pageTransitioning = false
    private var pageTransitionGeneration = 0
    private var velocityTracker: VelocityTracker? = null
    private var pageTopInset = 0
    private var navigationBottomInset = 0
    private val selectedHistoryUris = linkedSetOf<String>()
    private val historyCheckboxes = linkedMapOf<String, CheckBox>()
    private val historyCards = linkedMapOf<String, LinearLayout>()
    private val historyItemViews = linkedMapOf<String, LinearLayout>()
    private val historyArtworkViews = linkedMapOf<String, ImageView>()
    private val pendingArtwork = java.util.WeakHashMap<ImageView, PendingArtwork>()
    private var historyRoot: LinearLayout? = null
    private var historyHeaderFrame: FrameLayout? = null
    private var historyExpandedHeader: LinearLayout? = null
    private var historyHeaderEyebrow: TextView? = null
    private var historyExpandedTitle: TextView? = null
    private var historyExpandedSubtitle: TextView? = null
    private var historyCompactHeader: TextView? = null
    private var historySortButton: ImageView? = null
    private var historySearchButton: ImageView? = null
    private var historySearchInput: EditText? = null
    private var historyArchiveFolderButton: Button? = null
    private var historyScrollView: ScrollView? = null
    private var historyListContainer: LinearLayout? = null
    private var historyClearButton: Button? = null
    private var historySelectionToolbar: LinearLayout? = null
    private var historySelectAllButton: Button? = null
    private var historyDeleteButton: Button? = null
    private var historyArchiveButton: Button? = null
    private var historyShareButton: Button? = null
    private var historySelectionMode = false
    private var historySortMode = HISTORY_SORT_NEWEST
    private var displayedHistoryItems: List<HistoryItem> = emptyList()
    private var historySearchQuery = ""
    private var historySearchActive = false
    private var historyHeaderFraction = 0f
    private var historyShowingArchive = false
    private var historyClearRevealed = false
    private var historyArchiveRevealed = false
    private var historyPullStartY = 0f
    private var historyPullStartedAtBottom = false
    private var sessionDownloadsHeader: TextView? = null
    private var sessionSelectionToolbar: LinearLayout? = null
    private var sessionSelectAllButton: Button? = null
    private var sessionArchiveButton: Button? = null
    private var sessionShareButton: Button? = null
    private var sessionDeleteButton: Button? = null
    private val selectedSessionUris = linkedSetOf<String>()
    private val sessionCheckboxes = linkedMapOf<String, CheckBox>()
    private val sessionCards = linkedMapOf<String, LinearLayout>()
    private var sessionSelectionMode = false
    private var homeSelectionToolbar: LinearLayout? = null
    private var homeHeaderRoot: FrameLayout? = null
    private var homeExpandedHeader: LinearLayout? = null
    private var homeCompactHeader: TextView? = null
    private var homeScrollView: ScrollView? = null
    private var homeHeaderFraction = 0f
    private var settingsHeaderFrame: FrameLayout? = null
    private var settingsExpandedHeader: LinearLayout? = null
    private var settingsCompactHeader: TextView? = null
    private var settingsScrollView: ScrollView? = null
    private var settingsHeaderFraction = 0f
    private var historyHeaderActions: LinearLayout? = null
    private var historyReachedBottom = false
    private var homeHeaderCanCollapse = false
    private var settingsHeaderCanCollapse = false
    private var homeHeaderBlur: ImageView? = null
    private var homeHeaderBlurTint: View? = null
    private var settingsHeaderBlur: ImageView? = null
    private var settingsHeaderBlurTint: View? = null
    private var historyHeaderBlur: ImageView? = null
    private var historyHeaderBlurTint: View? = null
    private var lastBlurUpdateAt = 0L
    private val scrollEdgeHapticStates = java.util.WeakHashMap<ScrollView, ScrollEdgeHapticState>()
    private var homeExpandedMenu: ImageView? = null
    private var homeCompactMenu: ImageView? = null
    private var commandReceiverRegistered = false
    private var systemBackCallback: Any? = null
    private val downloadCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DownloadNotificationService.ACTION_TOGGLE_PAUSE -> togglePauseDownload()
                DownloadNotificationService.ACTION_CANCEL -> cancelDownloadConfirmed()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appearanceMode = preferences.getString(KEY_APPEARANCE, null)
            ?: if (preferences.getBoolean(KEY_DARK_MODE, false)) MODE_BLACK else MODE_SYSTEM
        savedQualityIndex = preferences.getInt(KEY_AUDIO_QUALITY, 0).coerceIn(0, AUDIO_QUALITY_KBPS.lastIndex)
        savedVideoQualityIndex = preferences.getInt(KEY_VIDEO_QUALITY, 0).coerceIn(0, VIDEO_QUALITY_HEIGHTS.lastIndex)
        darkMode = isDarkAppearance()
        accentColor = preferences.getInt(KEY_ACCENT, ACCENT_BLUE)
        renderScreen()
        registerDownloadCommands()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val callback = OnBackInvokedCallback { handleSystemBack() }
            systemBackCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback
            )
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
        }
        acceptSharedLink(intent)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!preferences.getBoolean(KEY_SWIPE_NAV_ENABLED, false)) return super.dispatchTouchEvent(event)
        if (pageTransitioning) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                swipeStartX = event.rawX
                swipeStartY = event.rawY
                swipeDistanceX = 0f
                swipeActive = false
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.rawX - swipeStartX
                val dy = event.rawY - swipeStartY
                if (!swipeActive && kotlin.math.abs(dx) > ViewConfiguration.get(this).scaledTouchSlop &&
                    kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.2f && isInsidePageHost(swipeStartX, swipeStartY)
                ) {
                    val target = adjacentTab(if (dx < 0) 1 else -1)
                    if (target != null) {
                        rememberHomeForm()
                        swipeTargetTab = target
                        nextPageView = createTabPage(target).also { incoming ->
                            incoming.translationX = if (dx < 0) pageHost.width.toFloat() else -pageHost.width.toFloat()
                            pageHost.addView(incoming, FrameLayout.LayoutParams(-1, -1))
                        }
                        swipeActive = true
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                    }
                }
                if (swipeActive) {
                    swipeDistanceX = dx.coerceIn(-pageHost.width.toFloat(), pageHost.width.toFloat())
                    currentPageView.translationX = swipeDistanceX
                    nextPageView?.translationX = swipeDistanceX + if (swipeDistanceX < 0) pageHost.width else -pageHost.width
                    return true
                }
            }
            MotionEvent.ACTION_UP -> if (swipeActive) {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val velocity = velocityTracker?.xVelocity ?: 0f
                val directionMatches = velocity == 0f || kotlin.math.sign(velocity) == kotlin.math.sign(swipeDistanceX)
                val commit = kotlin.math.abs(swipeDistanceX) > pageHost.width * 0.22f ||
                    (kotlin.math.abs(velocity) > dp(650) && directionMatches)
                finishSwipe(commit)
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            } else {
                velocityTracker?.recycle()
                velocityTracker = null
            }
            MotionEvent.ACTION_CANCEL -> if (swipeActive) {
                finishSwipe(commit = false)
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            } else {
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.action) {
            DownloadNotificationService.ACTION_TOGGLE_PAUSE -> togglePauseDownload()
            DownloadNotificationService.ACTION_CANCEL -> cancelDownloadConfirmed()
        }
        acceptSharedLink(intent)
    }

    @Deprecated("Deprecated by Android; the back dispatcher is used on newer versions")
    override fun onBackPressed() {
        handleSystemBack()
    }

    private fun handleSystemBack() {
        if (sessionSelectionMode) {
            selectedSessionUris.clear()
            setSessionSelectionMode(false)
            renderHomeDownloadLists()
            return
        }
        if (historySelectionMode) {
            selectedHistoryUris.clear()
            setHistorySelectionMode(false)
            historyCheckboxes.values.forEach { it.isChecked = false }
            updateHistorySelectionControls()
            return
        }
        if (selectedTab == Tab.HISTORY && historySearchActive) {
            toggleDownloadsSearch()
            return
        }
        if (selectedTab != Tab.HOME) {
            navigateTo(Tab.HOME)
            return
        }
        super.onBackPressed()
    }

    private fun registerDownloadCommands() {
        val filter = IntentFilter().apply {
            addAction(DownloadNotificationService.ACTION_TOGGLE_PAUSE)
            addAction(DownloadNotificationService.ACTION_CANCEL)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(downloadCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(downloadCommandReceiver, filter)
        }
        commandReceiverRegistered = true
    }

    @Deprecated("Deprecated by Android; the document picker still returns through this callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SAVE_FOLDER || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val permissionFlags = data.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            contentResolver.takePersistableUriPermission(uri, permissionFlags)
            preferences.edit().putString(KEY_SAVE_FOLDER, uri.toString()).apply()
            toast("Save folder updated")
            renderScreen()
        } catch (error: Exception) {
            toast("Could not use that folder: ${error.message ?: "permission denied"}")
        }
    }

    override fun onDestroy() {
        // The foreground notification service keeps an active transfer alive while this screen is gone.
        activityDestroyed = true
        if (commandReceiverRegistered) unregisterReceiver(downloadCommandReceiver)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            (systemBackCallback as? OnBackInvokedCallback)?.let { callback ->
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
            }
        }
        artworkWorker.shutdownNow()
        if (activeSession == null) worker.shutdownNow()
        super.onDestroy()
    }

    private fun renderScreen() {
        pageTransitionGeneration++
        if (::pageHost.isInitialized) {
            currentPageView.animate().cancel()
            nextPageView?.animate()?.cancel()
            pageHost.removeAllViews()
        }
        nextPageView = null
        swipeTargetTab = null
        swipeActive = false
        pageTransitioning = false
        applyWindowTheme()
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageColor())
        }
        pageHost = FrameLayout(this)
        currentPageView = createTabPage(selectedTab)
        pageHost.addView(currentPageView, FrameLayout.LayoutParams(-1, -1))
        navigationBar = buildBottomNavigation()
        shell.addView(pageHost, LinearLayout.LayoutParams(-1, 0, 1f))
        shell.addView(navigationBar, LinearLayout.LayoutParams(-1, dp(68)))
        shell.setOnApplyWindowInsetsListener { _, insets: WindowInsets ->
            val imeVisible: Boolean
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                pageTopInset = insets.getInsets(WindowInsets.Type.statusBars()).top
                navigationBottomInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                imeVisible = insets.isVisible(WindowInsets.Type.ime())
            } else {
                pageTopInset = insets.systemWindowInsetTop
                imeVisible = insets.systemWindowInsetBottom > dp(120)
                navigationBottomInset = if (imeVisible) 0 else insets.systemWindowInsetBottom
            }
            for (index in 0 until pageHost.childCount) applyPageInsets(pageHost.getChildAt(index))
            navigationBar.visibility = if (imeVisible) View.GONE else View.VISIBLE
            navigationBar.layoutParams = (navigationBar.layoutParams as LinearLayout.LayoutParams).apply {
                height = if (imeVisible) 0 else dp(68) + navigationBottomInset
            }
            if (!imeVisible) navigationBar.setPadding(dp(8), dp(4), dp(8), dp(4) + navigationBottomInset)
            insets
        }
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(shell)
        if (selectedTab == Tab.HOME) {
            linkInput.setText(savedLink)
            updateAudioQualityLabel()
            if (isDownloading) setBusy(true)
            renderHomeDownloadLists()
        }
    }

    private fun createTabPage(tab: Tab): View {
        if (tab == Tab.HISTORY) return createHistoryPage()
        val root = FrameLayout(this).apply {
            tag = PAGE_ROOT_TAG
            setBackgroundColor(pageColor())
            clipChildren = true
        }
        val headerFrame = FrameLayout(this).apply {
            tag = if (tab == Tab.HOME) HOME_HEADER_TAG else SETTINGS_HEADER_TAG
        }
        val blurImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            visibility = View.GONE
        }
        val blurTint = View(this).apply {
            setHeaderBlurTint(this)
            visibility = View.GONE
        }
        headerFrame.addView(blurImage, FrameLayout.LayoutParams(-1, -1))
        headerFrame.addView(blurTint, FrameLayout.LayoutParams(-1, -1))
        if (tab == Tab.HOME) {
            homeHeaderBlur = blurImage
            homeHeaderBlurTint = blurTint
        } else {
            settingsHeaderBlur = blurImage
            settingsHeaderBlurTint = blurTint
        }
        val expanded = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20) + pageTopInset, dp(22), 0)
            alpha = 1f
        }
        when (tab) {
            Tab.HOME -> addPageHeader(expanded, "AUDIODROP", "Save video, or audio.", "Download a video or just its MP3 audio.")
            Tab.SETTINGS -> addPageHeader(expanded, "PERSONALIZE", "Settings", "Make AudioDrop feel like yours.")
            else -> Unit
        }
        headerFrame.addView(expanded, FrameLayout.LayoutParams(-1, -1))
        val compact = text(if (tab == Tab.HOME) "AudioDrop" else "Settings", 22, primaryText(), bold = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), 0, dp(82), 0)
            alpha = 0f
        }
        headerFrame.addView(compact, FrameLayout.LayoutParams(-1, -1))
        val headerHeight = when (tab) {
            Tab.HOME -> dp(HOME_HEADER_EXPANDED_DP) + pageTopInset
            Tab.SETTINGS -> dp(SETTINGS_HEADER_EXPANDED_DP) + pageTopInset
            else -> dp(SETTINGS_HEADER_EXPANDED_DP) + pageTopInset
        }
        headerFrame.layoutParams = FrameLayout.LayoutParams(-1, headerHeight, Gravity.TOP)
        homeHeaderRoot = if (tab == Tab.HOME) headerFrame else null
        if (tab == Tab.HOME) {
            homeExpandedHeader = expanded
            homeCompactHeader = compact
            homeHeaderFraction = 0f
            val expandedMenu = headerIconButton(R.drawable.ic_action_more, "More options") {
                showHomeMenu(homeExpandedMenu ?: return@headerIconButton)
            }.apply {
                background = null
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            homeExpandedMenu = expandedMenu
            headerFrame.addView(expandedMenu, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END or Gravity.TOP).apply {
                topMargin = pageTopInset + dp(14)
                rightMargin = dp(22)
            })
            val compactMenu = headerIconButton(R.drawable.ic_action_more, "More options") {
                showHomeMenu(homeCompactMenu ?: return@headerIconButton)
            }.apply {
                background = null
                setPadding(dp(8), dp(8), dp(8), dp(8))
                alpha = 0f
                visibility = View.INVISIBLE
            }
            homeCompactMenu = compactMenu
            headerFrame.addView(compactMenu, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END or Gravity.CENTER_VERTICAL).apply {
                rightMargin = dp(22)
            })
        } else if (tab == Tab.SETTINGS) {
            settingsHeaderFrame = headerFrame
            settingsExpandedHeader = expanded
            settingsCompactHeader = compact
            settingsHeaderFraction = 0f
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), headerHeight, dp(22), dp(28))
            setBackgroundColor(pageColor())
        }
        when (tab) {
            Tab.HOME -> buildHome(page)
            Tab.HISTORY -> Unit
            Tab.SETTINGS -> buildSettings(page)
        }
        if (tab == Tab.HOME) {
            homeSelectionToolbar?.let { toolbar ->
                headerFrame.addView(toolbar, FrameLayout.LayoutParams(-1, dp(52), Gravity.CENTER_VERTICAL))
            }
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(page, ViewGroup.LayoutParams(-1, -2))
        }
        if (tab == Tab.HOME) {
            homeScrollView = scroll
            scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                homeHeaderCanCollapse = page.height - scroll.height > dp(1)
                updatePageCollapsingHeader(headerFrame, expanded, compact, scrollY, HOME_HEADER_EXPANDED_DP, HOME_HEADER_COLLAPSE_RANGE_DP, homeHeaderCanCollapse) {
                    homeHeaderFraction = it
                }
                updateHeaderBlur(scroll, headerFrame, blurImage, blurTint, scrollY)
                provideScrollEdgeHaptic(scroll, scrollY)
                loadVisibleArtwork()
            }
            scroll.post {
                homeHeaderCanCollapse = page.height - scroll.height > dp(1)
                updateHomeHeaderState()
                updateHeaderBlur(scroll, headerFrame, blurImage, blurTint, scroll.scrollY, force = true)
                loadVisibleArtwork()
            }
        } else {
            settingsScrollView = scroll
            scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                settingsHeaderCanCollapse = page.height - scroll.height > dp(1)
                updatePageCollapsingHeader(headerFrame, expanded, compact, scrollY, SETTINGS_HEADER_EXPANDED_DP, SETTINGS_HEADER_COLLAPSE_RANGE_DP, settingsHeaderCanCollapse) {
                    settingsHeaderFraction = it
                }
                updateHeaderBlur(scroll, headerFrame, blurImage, blurTint, scrollY)
                provideScrollEdgeHaptic(scroll, scrollY)
            }
            scroll.post {
                settingsHeaderCanCollapse = page.height - scroll.height > dp(1)
                updatePageCollapsingHeader(headerFrame, expanded, compact, scroll.scrollY, SETTINGS_HEADER_EXPANDED_DP, SETTINGS_HEADER_COLLAPSE_RANGE_DP, settingsHeaderCanCollapse) {
                    settingsHeaderFraction = it
                }
                updateHeaderBlur(scroll, headerFrame, blurImage, blurTint, scroll.scrollY, force = true)
            }
        }
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        root.addView(headerFrame)
        if (tab == Tab.HOME) updateHomeHeaderState()
        return root
    }

    private fun applyPageInsets(view: View) {
        if (view.tag == PAGE_ROOT_TAG) {
            val root = view as FrameLayout
            val scroll = root.getChildAt(0) as? ScrollView ?: return
            val page = scroll.getChildAt(0) as? LinearLayout ?: return
            val headerFrame = root.getChildAt(1) as? FrameLayout ?: return
            val expandedHeader = headerFrame.getChildAt(2) as? LinearLayout
            expandedHeader?.setPadding(dp(22), dp(20) + pageTopInset, dp(22), 0)
            val isHomeHeader = headerFrame.tag == HOME_HEADER_TAG
            val expandedDp = if (isHomeHeader) HOME_HEADER_EXPANDED_DP else SETTINGS_HEADER_EXPANDED_DP
            val expanded = headerFrame.getChildAt(2)
            val compact = headerFrame.getChildAt(3)
            val expandedHeight = dp(expandedDp) + pageTopInset
            page.setPadding(dp(22), expandedHeight, dp(22), dp(28))
            compact.setPadding(dp(22), 0, dp(82), 0)
            (headerFrame.getChildAt(4)?.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.topMargin = pageTopInset + dp(14)
                params.rightMargin = dp(22)
                headerFrame.getChildAt(4).layoutParams = params
            }
            (headerFrame.getChildAt(5)?.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.rightMargin = dp(22)
                headerFrame.getChildAt(5).layoutParams = params
            }
            (headerFrame.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.height = expandedHeight
                headerFrame.layoutParams = params
            }
            val scrollY = scroll.scrollY
            val range = if (isHomeHeader) HOME_HEADER_COLLAPSE_RANGE_DP else SETTINGS_HEADER_COLLAPSE_RANGE_DP
            val canCollapse = page.height - scroll.height > dp(1)
            if (isHomeHeader) homeHeaderCanCollapse = canCollapse else settingsHeaderCanCollapse = canCollapse
            updatePageCollapsingHeader(headerFrame, expanded, compact, scrollY, expandedDp, range, canCollapse
            ) {
                if (isHomeHeader) homeHeaderFraction = it else settingsHeaderFraction = it
            }
            return
        }
        if (view.tag == HISTORY_ROOT_TAG) {
            view.setPadding(dp(22), dp(20) + pageTopInset, dp(22), dp(12))
            return
        }
        val page = (view as? ScrollView)?.getChildAt(0) as? LinearLayout ?: return
        page.setPadding(dp(22), dp(20) + pageTopInset, dp(22), dp(28))
    }

    private fun updatePageCollapsingHeader(
        frame: FrameLayout,
        expanded: View,
        compact: View,
        scrollY: Int,
        expandedDp: Int,
        collapseRangeDp: Int,
        allowCollapse: Boolean = true,
        onFraction: (Float) -> Unit
    ) {
        val fraction = if (allowCollapse) (scrollY.toFloat() / dp(collapseRangeDp)).coerceIn(0f, 1f) else 0f
        onFraction(fraction)
        val collapsedHeight = dp(PAGE_HEADER_COLLAPSED_DP) + pageTopInset
        val expandedHeight = dp(expandedDp) + pageTopInset
        val targetHeight = (expandedHeight - (expandedHeight - collapsedHeight) * fraction).toInt()
        val params = frame.layoutParams as? ViewGroup.LayoutParams
        if (params != null && params.height != targetHeight) {
            params.height = targetHeight
            frame.layoutParams = params
        }
        val isSessionSelecting = frame === homeHeaderRoot && sessionSelectionMode
        expanded.alpha = if (isSessionSelecting) 0f else 1f - fraction
        compact.alpha = if (isSessionSelecting) 0f else fraction
        if (frame === homeHeaderRoot) {
            homeExpandedMenu?.apply {
                alpha = if (isSessionSelecting) 0f else 1f - fraction
                visibility = if (isSessionSelecting || fraction >= 1f) View.INVISIBLE else View.VISIBLE
            }
            homeCompactMenu?.apply {
                alpha = if (isSessionSelecting) 0f else fraction
                visibility = if (isSessionSelecting || fraction <= 0f) View.INVISIBLE else View.VISIBLE
            }
        }
    }

    private fun updateHomeHeaderState() {
        val frame = homeHeaderRoot ?: return
        val expanded = homeExpandedHeader ?: return
        val compact = homeCompactHeader ?: return
        updatePageCollapsingHeader(
            frame, expanded, compact, homeScrollView?.scrollY ?: 0,
            HOME_HEADER_EXPANDED_DP, HOME_HEADER_COLLAPSE_RANGE_DP, homeHeaderCanCollapse
        ) { homeHeaderFraction = it }
    }

    private fun updateHeaderBlur(
        scroll: ScrollView,
        frame: FrameLayout,
        image: ImageView,
        tint: View,
        scrollY: Int,
        force: Boolean = false
    ) {
        if (!preferences.getBoolean(KEY_HEADER_BLUR_ENABLED, true) || scrollY <= dp(2) || frame.width <= 0 || frame.height <= 0) {
            image.visibility = View.GONE
            tint.visibility = View.GONE
            return
        }
        val now = System.currentTimeMillis()
        if (!force && now - lastBlurUpdateAt < 100L) return
        lastBlurUpdateAt = now
        runCatching {
            val captureWidth = (frame.width / 8).coerceAtLeast(1)
            val captureHeight = (frame.height / 8).coerceAtLeast(1)
            val source = Bitmap.createBitmap(captureWidth, captureHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(source)
            canvas.scale(captureWidth.toFloat() / frame.width, captureHeight.toFloat() / frame.height)
            scroll.draw(canvas)
            val bitmap = fastBlurSnapshot(source)
            val oldBitmap = (image.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            image.setImageBitmap(bitmap)
            if (oldBitmap != null && oldBitmap !== bitmap && !oldBitmap.isRecycled) oldBitmap.recycle()
            image.visibility = View.VISIBLE
            tint.visibility = View.VISIBLE
        }
    }

    private fun fastBlurSnapshot(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val input = IntArray(width * height)
        val horizontal = IntArray(input.size)
        val output = IntArray(input.size)
        source.getPixels(input, 0, width, 0, 0, width, height)
        val radius = 4

        for (y in 0 until height) {
            for (x in 0 until width) {
                var red = 0; var green = 0; var blue = 0; var alpha = 0; var count = 0
                for (sampleX in (x - radius).coerceAtLeast(0)..(x + radius).coerceAtMost(width - 1)) {
                    val color = input[y * width + sampleX]
                    red += Color.red(color); green += Color.green(color); blue += Color.blue(color); alpha += Color.alpha(color); count++
                }
                horizontal[y * width + x] = Color.argb(alpha / count, red / count, green / count, blue / count)
            }
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                var red = 0; var green = 0; var blue = 0; var alpha = 0; var count = 0
                for (sampleY in (y - radius).coerceAtLeast(0)..(y + radius).coerceAtMost(height - 1)) {
                    val color = horizontal[sampleY * width + x]
                    red += Color.red(color); green += Color.green(color); blue += Color.blue(color); alpha += Color.alpha(color); count++
                }
                output[y * width + x] = Color.argb(alpha / count, red / count, green / count, blue / count)
            }
        }
        val blurred = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        blurred.setPixels(output, 0, width, 0, 0, width, height)
        source.recycle()
        return blurred
    }

    private fun setHeaderBlurTint(view: View) {
        val amount = preferences.getInt(KEY_HEADER_BLUR_ALPHA, 58).coerceIn(0, 100)
        val alpha = ((100 - amount) * 2.2f).toInt().coerceIn(0, 220)
        val color = if (darkMode) Color.BLACK else 0xFFF3F4F7.toInt()
        view.setBackgroundColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)))
    }

    private fun refreshHeaderBlurSettings() {
        listOf(homeHeaderBlurTint, settingsHeaderBlurTint, historyHeaderBlurTint).forEach { it?.let(::setHeaderBlurTint) }
        if (!preferences.getBoolean(KEY_HEADER_BLUR_ENABLED, true)) {
            listOf(homeHeaderBlur, settingsHeaderBlur, historyHeaderBlur).forEach { it?.visibility = View.GONE }
            listOf(homeHeaderBlurTint, settingsHeaderBlurTint, historyHeaderBlurTint).forEach { it?.visibility = View.GONE }
            return
        }
        when (selectedTab) {
            Tab.HOME -> {
                val scroll = homeScrollView
                val frame = homeHeaderRoot
                val image = homeHeaderBlur
                val tint = homeHeaderBlurTint
                if (scroll != null && frame != null && image != null && tint != null)
                    updateHeaderBlur(scroll, frame, image, tint, scroll.scrollY, force = true)
            }
            Tab.SETTINGS -> {
                val scroll = settingsScrollView
                val frame = settingsHeaderFrame
                val image = settingsHeaderBlur
                val tint = settingsHeaderBlurTint
                if (scroll != null && frame != null && image != null && tint != null)
                    updateHeaderBlur(scroll, frame, image, tint, scroll.scrollY, force = true)
            }
            Tab.HISTORY -> {
                val scroll = historyScrollView
                val frame = historyHeaderFrame
                val image = historyHeaderBlur
                val tint = historyHeaderBlurTint
                if (scroll != null && frame != null && image != null && tint != null)
                    updateHeaderBlur(scroll, frame, image, tint, scroll.scrollY, force = true)
            }
        }
    }

    private fun performAppHaptic() {
        if (!preferences.getBoolean(KEY_HAPTICS_ENABLED, true)) return
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        val strength = preferences.getInt(KEY_HAPTIC_STRENGTH, 1).coerceIn(0, 2)
        val amplitudes = intArrayOf(35, 90, 170)
        runCatching { vibrator.vibrate(VibrationEffect.createOneShot(10L, amplitudes[strength])) }
    }

    private fun hapticStrengthLabel(value: Int): String = when (value.coerceIn(0, 2)) {
        0 -> "Light"
        2 -> "Strong"
        else -> "Medium"
    }

    private fun provideScrollEdgeHaptic(scroll: ScrollView, scrollY: Int) {
        val state = scrollEdgeHapticStates.getOrPut(scroll) { ScrollEdgeHapticState() }
        val now = System.currentTimeMillis()
        val maxScroll = ((scroll.getChildAt(0)?.height ?: 0) - scroll.height).coerceAtLeast(0)
        val atTop = scrollY <= dp(1)
        val atBottom = maxScroll > dp(1) && scrollY >= maxScroll - dp(12)
        if (state.lastTime > 0L) {
            val elapsed = (now - state.lastTime).coerceAtLeast(1L)
            val speedDpPerSecond = kotlin.math.abs(scrollY - state.lastY) * 1000f / elapsed / resources.displayMetrics.density
            if (speedDpPerSecond >= 150f) {
                if (atTop && !state.topLatched) {
                    performAppHaptic()
                    state.topLatched = true
                }
                if (atBottom && !state.bottomLatched) {
                    performAppHaptic()
                    state.bottomLatched = true
                }
            }
        }
        if (!atTop) state.topLatched = false
        if (!atBottom) state.bottomLatched = false
        state.lastTime = now
        state.lastY = scrollY
    }

    private inner class DownloadsQuickScroller(private val scrollView: ScrollView) : View(this@MainActivity) {
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(75, 150, 155, 165) }
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        private var dragging = false
        private var dragOffset = 0f
        private var previousTargetY = 0

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val contentHeight = scrollView.getChildAt(0)?.height ?: return
            if (contentHeight <= scrollView.height || height <= 0) return
            val inset = dp(12).toFloat()
            val centerX = width / 2f
            val trackTop = inset
            val trackBottom = height - inset
            canvas.drawRoundRect(RectF(centerX - dp(2), trackTop, centerX + dp(2), trackBottom), dp(4).toFloat(), dp(4).toFloat(), trackPaint)
            val maxScroll = (contentHeight - scrollView.height).coerceAtLeast(1)
            val thumbHeight = thumbHeight()
            val fraction = scrollView.scrollY.toFloat() / maxScroll
            val top = trackTop + (trackBottom - trackTop - thumbHeight).coerceAtLeast(1f) * fraction
            thumbPaint.color = accentColor
            canvas.drawRoundRect(RectF(centerX - dp(2), top, centerX + dp(2), top + thumbHeight), dp(4).toFloat(), dp(4).toFloat(), thumbPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if ((scrollView.getChildAt(0)?.height ?: 0) <= scrollView.height) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val (thumbTop, thumbSize) = thumbPositionAndSize()
                    if (event.y in (thumbTop - dp(5))..(thumbTop + thumbSize + dp(5))) {
                        dragOffset = event.y - thumbTop
                    } else {
                        dragOffset = thumbSize / 2f
                        scrollToFraction(event.y - dragOffset)
                    }
                    previousTargetY = scrollView.scrollY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) return false
                    scrollToFraction(event.y - dragOffset)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return true
        }

        private fun thumbHeight(): Float {
            val contentHeight = scrollView.getChildAt(0)?.height ?: return dp(38).toFloat()
            val usableHeight = (height - dp(24)).coerceAtLeast(dp(1))
            val minThumb = dp(38).toFloat().coerceAtMost(usableHeight.toFloat())
            val maxThumb = (usableHeight / 2f).coerceAtLeast(minThumb)
            return (usableHeight.toFloat() * scrollView.height / contentHeight).coerceIn(minThumb, maxThumb)
        }

        private fun thumbPositionAndSize(): Pair<Float, Float> {
            val contentHeight = scrollView.getChildAt(0)?.height ?: return 0f to dp(38).toFloat()
            val inset = dp(12).toFloat()
            val size = thumbHeight()
            val travel = (height - inset * 2 - size).coerceAtLeast(1f)
            val maxScroll = (contentHeight - scrollView.height).coerceAtLeast(1)
            return (inset + travel * scrollView.scrollY.toFloat() / maxScroll) to size
        }

        private fun scrollToFraction(y: Float) {
            val contentHeight = scrollView.getChildAt(0)?.height ?: return
            val maxScroll = (contentHeight - scrollView.height).coerceAtLeast(1)
            val inset = dp(12).toFloat()
            val travel = (height - inset * 2 - thumbHeight()).coerceAtLeast(1f)
            val fraction = ((y - inset) / travel).coerceIn(0f, 1f)
            val target = (maxScroll * fraction).toInt()
            scrollView.scrollTo(0, target)
            if (kotlin.math.abs(target - previousTargetY) >= dp(40)) {
                performAppHaptic()
                previousTargetY = target
            }
            invalidate()
        }
    }

    private fun buildHome(page: LinearLayout) {
        val card = card()
        page.addView(card)
        card.addView(label("VIDEO LINK"))
        val linkRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        linkInput = EditText(this).apply {
            hint = "Paste a video link"
            textSize = 15f
            setTextColor(primaryText())
            setHintTextColor(secondaryText())
            setSingleLine(true)
            setPadding(dp(14), 0, dp(10), 0)
            background = rounded(fieldColor(), dp(16), borderColor())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            setSelectAllOnFocus(true)
            setOnClickListener { post { selectAll() } }
        }
        linkInput.setText(savedLink)
        linkRow.addView(linkInput, LinearLayout.LayoutParams(0, dp(54), 1f))
        linkRow.addView(actionButton("Paste", filled = false) { pasteFromClipboard() },
            LinearLayout.LayoutParams(dp(72), dp(50)).apply { leftMargin = dp(8) })
        card.addView(linkRow)

        val qualityLabel = label("MP3 QUALITY").apply {
            setPadding(0, dp(22), 0, dp(8))
        }
        qualityPicker = text(audioQualityLabel(savedQualityIndex), 15, primaryText()).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            isClickable = false
            isFocusable = false
        }
        val qualityBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(fieldColor(), dp(16), borderColor())
        }
        qualityBox.addView(qualityPicker, LinearLayout.LayoutParams(0, -1, 1f))
        qualityBox.addView(text("⌄", 22, secondaryText()).apply {
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { if (!isDownloading) showAudioQualityDialog() }
        }, LinearLayout.LayoutParams(dp(44), -1))
        qualityBox.setOnClickListener { if (!isDownloading) showAudioQualityDialog() }

        val downloadChoices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        downloadButton = actionButton("Audio · MP3", filled = true) { startDownload(video = false) }.apply {
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_tab_download, 0, 0, 0)
            compoundDrawablePadding = dp(8)
            compoundDrawableTintList = ColorStateList.valueOf(Color.WHITE)
        }
        videoButton = actionButton("Video", filled = false) { showVideoQualityDialog() }.apply {
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_tab_download, 0, 0, 0)
            compoundDrawablePadding = dp(8)
            compoundDrawableTintList = ColorStateList.valueOf(accentColor)
        }
        downloadChoices.addView(downloadButton, LinearLayout.LayoutParams(0, dp(56), 1f))
        downloadChoices.addView(videoButton, LinearLayout.LayoutParams(0, dp(56), 1f).apply {
            leftMargin = dp(10)
        })
        card.addView(downloadChoices, LinearLayout.LayoutParams(-1, dp(56)).apply {
            topMargin = dp(20)
        })
        card.addView(qualityLabel)
        card.addView(qualityBox, LinearLayout.LayoutParams(-1, dp(54)))
        homeQueueSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        homeQueueSection?.addView(label("DOWNLOAD QUEUE").apply {
            setPadding(0, dp(18), 0, dp(6))
        })
        homeQueueList = text("", 12, secondaryText()).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(fieldColor(), dp(14))
        }
        homeQueueSection?.addView(homeQueueList, LinearLayout.LayoutParams(-1, -2))
        card.addView(homeQueueSection)

        val transferActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        cancelButton = actionButton("Cancel", filled = false) { confirmCancelDownload() }.apply {
            visibility = View.GONE
            setTextColor(DELETE_RED)
            background = rounded(Color.argb(24, 229, 57, 53), dp(18), DELETE_RED)
        }
        pauseButton = actionButton("Pause", filled = false) { togglePauseDownload() }.apply {
            visibility = View.GONE
        }
        transferActions.addView(cancelButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        transferActions.addView(pauseButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            leftMargin = dp(8)
        })
        card.addView(transferActions, LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(8)
        })

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = statusProgress
            progressTintList = ColorStateList.valueOf(accentColor)
            isIndeterminate = isDownloading && statusProgress == 0
            visibility = if (isDownloading) View.VISIBLE else View.GONE
        }
        card.addView(progressBar, LinearLayout.LayoutParams(-1, dp(5)).apply {
            topMargin = dp(18)
        })
        statusText = text(statusMessage, 13, secondaryText()).apply {
            visibility = if (isDownloading || statusMessage.isNotBlank()) View.VISIBLE else View.GONE
            setPadding(0, dp(10), 0, 0)
        }
        card.addView(statusText)

        homeSelectionToolbar = makeSelectionToolbar(
            selectAll = { toggleSelectAllSession() },
            share = { shareSelectedSession() },
            archive = { archiveSelectedSession() },
            delete = { confirmDeleteSelectedSession() },
            shareButtonSetter = { sessionShareButton = it },
            archiveButtonSetter = { sessionArchiveButton = it },
            deleteButtonSetter = { sessionDeleteButton = it },
            selectAllSetter = { sessionSelectAllButton = it }
        ).apply {
            visibility = if (sessionSelectionMode) View.VISIBLE else View.GONE
            alpha = if (sessionSelectionMode) 1f else 0f
        }
        sessionSelectionToolbar = homeSelectionToolbar

        renderHomeDownloadLists()
    }

    private fun showHomeMenu(anchor: View) {
        val popupContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = rounded(surfaceColor(), dp(18), borderColor())
        }
        val popup = PopupWindow(popupContent, dp(188), -2, true).apply {
            isOutsideTouchable = true
            elevation = dp(10).toFloat()
            setBackgroundDrawable(rounded(surfaceColor(), dp(18), borderColor()))
        }
        fun addOption(title: String, action: () -> Unit) {
            popupContent.addView(text(title, 15, primaryText()).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(14), 0)
                background = rounded(Color.TRANSPARENT, dp(12))
                isClickable = true
                isFocusable = true
                setOnTouchListener { view, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) view.background = rounded(accentTint(), dp(12))
                    else if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        view.background = rounded(Color.TRANSPARENT, dp(12))
                    }
                    false
                }
                setOnClickListener {
                    popup.dismiss()
                    action()
                }
            }, LinearLayout.LayoutParams(-1, dp(48)))
        }
        addOption("About") { showAboutDialog() }
        popup.showAsDropDown(anchor, 0, dp(4), Gravity.END)
    }

    private fun showAboutDialog() {
        val version = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
            }.getOrNull().orEmpty().ifBlank { "1.15" }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(10))
        }
        content.addView(text("AudioDrop", 24, primaryText(), bold = true))
        content.addView(text("Version $version", 14, secondaryText()).apply { setPadding(0, dp(6), 0, dp(18)) })
        content.addView(text("Vibecoded by Sandeep", 15, primaryText()).apply { setPadding(0, 0, 0, dp(16)) })
        content.addView(text("Buy me a coffee", 15, accentColor, bold = true).apply {
            isClickable = true
            setOnClickListener {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BUY_ME_A_COFFEE_URL))) }
            }
        })
        val dialog = AlertDialog.Builder(this).setTitle("About").setView(content)
            .setPositiveButton("Done", null).create()
        showStyledDialog(dialog)
    }

    private fun createHistoryPage(): View {
        historyClearRevealed = false
        historyReachedBottom = false
        historyArchiveRevealed = false
        historyListContainer = null
        historyScrollView = null
        val items = visibleHistoryItems()
        selectedHistoryUris.retainAll(items.map { it.uri }.toSet())
        displayedHistoryItems = items
        historySelectionMode = selectedHistoryUris.isNotEmpty()
        historySortMode = preferences.getString(KEY_HISTORY_SORT, HISTORY_SORT_NEWEST) ?: HISTORY_SORT_NEWEST
        historyCheckboxes.clear()
        historyCards.clear()
        historyItemViews.clear()
        historyArtworkViews.clear()
        pendingArtwork.clear()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = HISTORY_ROOT_TAG
            setPadding(dp(22), dp(20) + pageTopInset, dp(22), dp(12))
            setBackgroundColor(pageColor())
        }
        historyRoot = root

        val header = FrameLayout(this)
        historyHeaderFrame = header
        val historyBlur = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            visibility = View.GONE
        }
        val historyBlurTint = View(this).apply {
            setHeaderBlurTint(this)
            visibility = View.GONE
        }
        historyHeaderBlur = historyBlur
        historyHeaderBlurTint = historyBlurTint
        header.addView(historyBlur, FrameLayout.LayoutParams(-1, -1))
        header.addView(historyBlurTint, FrameLayout.LayoutParams(-1, -1))
        val expanded = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = 1f
            setPadding(0, 0, dp(104), 0)
        }
        historyHeaderEyebrow = text("YOUR MEDIA", 12, accentColor, bold = true).apply {
            letterSpacing = 0.12f
            setPadding(0, 0, 0, dp(6))
        }
        expanded.addView(historyHeaderEyebrow)
        historyExpandedTitle = text("Downloads", 34, primaryText(), bold = true)
        expanded.addView(historyExpandedTitle)
        historyExpandedSubtitle = text("Audio and videos saved with AudioDrop.", 14, secondaryText()).apply {
            setPadding(0, dp(4), 0, 0)
        }
        expanded.addView(historyExpandedSubtitle)
        historyExpandedHeader = expanded
        header.addView(expanded, FrameLayout.LayoutParams(-1, -1))
        val compact = text("Downloads", 22, primaryText(), bold = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            alpha = 0f
            setPadding(0, 0, dp(104), dp(2))
        }
        historyCompactHeader = compact
        header.addView(compact, FrameLayout.LayoutParams(-1, -1))

        val actionHost = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        historyHeaderActions = actionHost
        val sortButton = headerIconButton(R.drawable.ic_action_sort, "Sort downloads") { showHistorySortDialog() }
        historySortButton = sortButton
        sortButton.visibility = if (historySelectionMode) View.GONE else View.VISIBLE
        sortButton.alpha = if (historySelectionMode) 0f else 1f
        actionHost.addView(sortButton, LinearLayout.LayoutParams(dp(44), dp(44)))
        historySearchButton = headerIconButton(
            if (historySearchActive) R.drawable.ic_action_close else R.drawable.ic_action_search,
            if (historySearchActive) "Close search" else "Search downloads"
        ) { toggleDownloadsSearch() }.apply {
            visibility = if (historySelectionMode) View.GONE else View.VISIBLE
            alpha = if (historySelectionMode) 0f else 1f
        }
        actionHost.addView(historySearchButton, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            leftMargin = dp(4)
        })
        val headerActions = FrameLayout(this)
        headerActions.addView(actionHost, FrameLayout.LayoutParams(-2, -1, Gravity.END or Gravity.CENTER_VERTICAL))
        header.addView(headerActions, FrameLayout.LayoutParams(dp(100), dp(48), Gravity.END or Gravity.CENTER_VERTICAL))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(HISTORY_HEADER_EXPANDED_DP)))

        historySearchInput = EditText(this).apply {
            hint = "Search downloads"
            textSize = 14f
            setTextColor(primaryText())
            setHintTextColor(secondaryText())
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(fieldColor(), dp(18), borderColor())
            setText(historySearchQuery)
            visibility = if (historySearchActive && !historySelectionMode) View.VISIBLE else View.GONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    historySearchQuery = s?.toString().orEmpty()
                    historyClearRevealed = false
                    hideArchiveFolder()
                    populateHistoryList(visibleHistoryItems(), historyScrollView?.scrollY ?: 0, animate = true)
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        header.addView(historySearchInput, FrameLayout.LayoutParams(-1, dp(48), Gravity.CENTER_VERTICAL).apply {
            rightMargin = dp(100)
        })
        val selectionToolbar = makeSelectionToolbar(
            selectAll = { toggleSelectAllHistory() },
            share = { shareSelectedHistory() },
            archive = { archiveSelectedHistory() },
            delete = { confirmDeleteSelectedHistory() },
            shareButtonSetter = { historyShareButton = it },
            archiveButtonSetter = { historyArchiveButton = it },
            deleteButtonSetter = { historyDeleteButton = it },
            selectAllSetter = { historySelectAllButton = it }
        ).apply {
            visibility = if (historySelectionMode) View.VISIBLE else View.GONE
            alpha = if (historySelectionMode) 1f else 0f
        }
        historySelectionToolbar = selectionToolbar
        header.addView(selectionToolbar, FrameLayout.LayoutParams(-1, dp(52), Gravity.CENTER_VERTICAL))
        expanded.alpha = if (historySelectionMode || historySearchActive) 0f else 1f
        compact.alpha = if (historySelectionMode || historySearchActive) 0f else 0f
        historyArchiveFolderButton = null

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = true
            setPadding(0, 0, dp(18), 0)
        }
        historyScrollView = scroll
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        historyListContainer = list
        scroll.addView(list, ViewGroup.LayoutParams(-1, -2))
        val listHost = FrameLayout(this)
        listHost.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val quickScroller = DownloadsQuickScroller(scroll).apply {
            contentDescription = "Quick scroll downloads"
        }
        listHost.addView(quickScroller, FrameLayout.LayoutParams(dp(16), -1, Gravity.END).apply {
            rightMargin = dp(1)
        })
        root.addView(listHost, LinearLayout.LayoutParams(-1, 0, 1f))
        scroll.setOnScrollChangeListener { _, _, scrollY, _, oldY ->
            updateHistoryCollapsingHeader(scrollY)
            updateHeaderBlur(scroll, header, historyBlur, historyBlurTint, scrollY)
            val maxY = (scroll.getChildAt(0)?.height ?: 0) - scroll.height
            val atBottom = maxY > 0 && scrollY >= maxY - dp(12)
            if (atBottom || maxY <= dp(1)) historyReachedBottom = true
            if (oldY - scrollY > dp(6)) {
                hideClearHistory()
                hideArchiveFolder()
            }
            provideScrollEdgeHaptic(scroll, scrollY)
            loadVisibleArtwork()
            quickScroller.invalidate()
        }
        scroll.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    historyPullStartY = event.y
                    val maxY = (scroll.getChildAt(0)?.height ?: 0) - scroll.height
                    historyPullStartedAtBottom = maxY <= dp(1) || (historyReachedBottom && scroll.scrollY >= maxY - dp(6))
                }
                MotionEvent.ACTION_UP -> {
                    val maxY = (scroll.getChildAt(0)?.height ?: 0) - scroll.height
                    if (!historySearchActive && historyPullStartedAtBottom &&
                        (maxY <= 0 || scroll.scrollY >= maxY - dp(12)) && historyPullStartY - event.y > dp(32)
                    ) revealClearHistory()
                    historyPullStartedAtBottom = false
                }
                MotionEvent.ACTION_CANCEL -> historyPullStartedAtBottom = false
            }
            false
        }

        populateHistoryList(items, 0, animate = false)
        scroll.post { loadVisibleArtwork() }
        updateHistoryCollapsingHeader(scroll.scrollY)
        updateHeaderBlur(scroll, header, historyBlur, historyBlurTint, scroll.scrollY, force = true)
        updateHistorySelectionControls()
        return root
    }

    private fun populateHistoryList(items: List<HistoryItem>, scrollY: Int, animate: Boolean) {
        val list = historyListContainer ?: return
        val scroll = historyScrollView
        historyClearButton = null
        displayedHistoryItems = items
        selectedHistoryUris.retainAll(items.map { it.uri }.toSet())
        val uris = items.map { it.uri }.toSet()
        (historyItemViews.keys - uris).forEach { uri ->
            historyItemViews.remove(uri)
            historyCheckboxes.remove(uri)
            historyCards.remove(uri)
            historyArtworkViews.remove(uri)
        }
        items.forEach { item ->
            if (item.uri !in historyItemViews) historyItemViews[item.uri] = createHistoryItemView(item)
        }

        if (animate) TransitionManager.beginDelayedTransition(
            list,
            ChangeBounds().apply {
                duration = 210L
                interpolator = DecelerateInterpolator(1.3f)
            }
        )
        list.removeAllViews()
        if (items.isEmpty()) {
            val empty = card()
            empty.addView(text(if (historySearchActive) "No matching downloads" else "All downloads", 19, primaryText(), bold = true))
            empty.addView(text(if (historySearchActive) "Try a different search." else "Your saved audio and videos will appear here.", 14, secondaryText()).apply {
                setPadding(0, dp(7), 0, 0)
            })
            list.addView(empty, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        } else {
            val itemUris = items.map { it.uri }.toSet()
            val sessionItems = downloadedThisSession
                .filter { it.uri in itemUris }
                .distinctBy { it.uri }
                .sortedByDescending { it.savedAt }
            if (sessionItems.isNotEmpty()) {
                list.addView(text("DOWNLOADED THIS SESSION", 12, secondaryText(), bold = true).apply {
                    letterSpacing = 0.1f
                    setPadding(dp(4), dp(12), dp(4), dp(8))
                })
                sessionItems.forEach { item ->
                    list.addView(historyItemViews.getValue(item.uri), LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = dp(10)
                    })
                }
            }
            val sessionUris = sessionItems.map { it.uri }.toSet()
            val remainingItems = items.filterNot { it.uri in sessionUris }
            val grouped = remainingItems.groupBy { startOfDay(it.savedAt) }
            val dayOrder = if (historySortMode == HISTORY_SORT_OLDEST) grouped.keys.sorted()
                else grouped.keys.sortedDescending()
            dayOrder.forEach { day ->
                list.addView(text(formatHistoryDay(day), 15, primaryText(), bold = true).apply {
                    setPadding(dp(4), dp(14), dp(4), dp(8))
                })
                val dayItems = when (historySortMode) {
                    HISTORY_SORT_OLDEST -> grouped.getValue(day).sortedBy { it.savedAt }
                    HISTORY_SORT_TITLE -> grouped.getValue(day).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                    else -> grouped.getValue(day).sortedByDescending { it.savedAt }
                }
                dayItems.forEach { item ->
                    list.addView(historyItemViews.getValue(item.uri), LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = dp(10)
                    })
                }
            }
        }
        if (!historySearchActive) {
            val archivedCount = readHistory().count { it.isArchived }
            val footer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val archiveButton = actionButton(
                if (historyShowingArchive) "Downloads" else "Archive · $archivedCount",
                filled = false
            ) { showArchivedDownloads(!historyShowingArchive) }.apply {
                textSize = 13f
                setPadding(dp(8), 0, dp(8), 0)
                setTextColor(accentColor)
                background = rounded(fieldColor(), dp(16), borderColor())
                val shouldShow = historyArchiveRevealed && (historyShowingArchive || archivedCount > 0)
                visibility = if (shouldShow) View.VISIBLE else View.INVISIBLE
                alpha = if (shouldShow) 1f else 0f
                contentDescription = if (historyShowingArchive) "Show downloads" else "Show archived downloads"
            }
            historyArchiveFolderButton = archiveButton
            footer.addView(archiveButton, LinearLayout.LayoutParams(0, dp(44), 1f))
            if (!historyShowingArchive) {
                val clearButton = actionButton("Clear all", filled = false) { confirmClearHistory() }.apply {
                    textSize = 13f
                    setPadding(dp(8), 0, dp(8), 0)
                    setTextColor(DELETE_RED)
                    background = rounded(Color.argb(24, 229, 57, 53), dp(16))
                    val shouldShow = historyClearRevealed && items.isNotEmpty()
                    visibility = if (shouldShow) View.VISIBLE else View.INVISIBLE
                    alpha = if (shouldShow) 1f else 0f
                    contentDescription = "Clear all downloads"
                }
                historyClearButton = clearButton
                footer.addView(clearButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    leftMargin = dp(8)
                })
            } else {
                val clearButton = actionButton("Clear archive", filled = false) { confirmClearArchive() }.apply {
                    textSize = 12f
                    setPadding(dp(6), 0, dp(6), 0)
                    setTextColor(DELETE_RED)
                    background = rounded(Color.argb(24, 229, 57, 53), dp(16))
                    val shouldShow = historyClearRevealed && items.isNotEmpty()
                    visibility = if (shouldShow) View.VISIBLE else View.INVISIBLE
                    alpha = if (shouldShow) 1f else 0f
                    contentDescription = "Clear archived downloads"
                }
                historyClearButton = clearButton
                footer.addView(clearButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    leftMargin = dp(8)
                })
            }
            list.addView(footer, LinearLayout.LayoutParams(-1, dp(44)).apply {
                topMargin = dp(10)
                bottomMargin = dp(14)
            })
        }
        scroll?.post { scroll.scrollTo(0, scrollY.coerceAtLeast(0)) }
        if (items.isEmpty() && historySelectionMode) setHistorySelectionMode(false)
        updateHistorySelectionControls()
    }

    private fun createHistoryItemView(item: HistoryItem): LinearLayout {
        val itemCard = card().apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            id = View.generateViewId()
            background = rounded(surfaceColor(), dp(22))
            isClickable = true
            setOnClickListener {
                if (historySelectionMode) toggleHistoryItem(item.uri) else openHistoryItem(item)
            }
            setOnLongClickListener {
                parent?.requestDisallowInterceptTouchEvent(true)
                enterHistorySelectionMode(item.uri)
                true
            }
        }
        historyCards[item.uri] = itemCard
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
        }
        row.setOnClickListener {
            if (historySelectionMode) toggleHistoryItem(item.uri) else openHistoryItem(item)
        }
        row.setOnLongClickListener {
            itemCard.parent?.requestDisallowInterceptTouchEvent(true)
            enterHistorySelectionMode(item.uri)
            true
        }
        val artwork = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(fieldColor(), dp(14))
            clipToOutline = true
            contentDescription = "Album art for ${item.title}"
            setImageResource(R.drawable.ic_launcher_foreground)
        }
        historyArtworkViews[item.uri] = artwork
        loadHistoryArtwork(artwork, item.artworkPath, item.uri)
        row.addView(artwork, LinearLayout.LayoutParams(dp(62), dp(62)).apply { rightMargin = dp(12) })

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        details.addView(text(item.title, 16, primaryText(), bold = true).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val kind = if (item.format == "MP3") "MP3 · ${item.quality}" else "${item.quality} · ${item.format} video"
        details.addView(text("$kind · ${formatBytes(historyFileSize(item))}", 12, secondaryText()).apply {
            setPadding(0, dp(4), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))

        val checkbox = CheckBox(this).apply {
            buttonTintList = ColorStateList.valueOf(accentColor)
            contentDescription = "Select ${item.title} for deletion"
            isChecked = item.uri in selectedHistoryUris
            visibility = if (historySelectionMode) View.VISIBLE else View.INVISIBLE
            setOnCheckedChangeListener { _, checked -> onHistoryCheckboxChanged(item.uri, checked) }
        }
        historyCheckboxes[item.uri] = checkbox
        row.addView(checkbox, LinearLayout.LayoutParams(dp(44), dp(54)))

        itemCard.addView(row)
        return itemCard
    }

    private fun loadHistoryArtwork(view: ImageView, path: String?, uri: String) {
        val identity = "$path|$uri"
        view.tag = identity
        pendingArtwork[view] = PendingArtwork(path, uri, identity)
        view.post { loadVisibleArtwork() }
    }

    private fun loadVisibleArtwork() {
        val visible = mutableListOf<Pair<ImageView, PendingArtwork>>()
        val rect = Rect()
        pendingArtwork.entries.forEach { (view, artwork) ->
            if (view.isAttachedToWindow && view.getLocalVisibleRect(rect)) visible += view to artwork
        }
        visible.forEach { (view, artwork) ->
            pendingArtwork.remove(view)
            decodeHistoryArtwork(view, artwork)
        }
    }

    private fun decodeHistoryArtwork(view: ImageView, artwork: PendingArtwork) {
        val targetSize = dp(124)
        runCatching { artworkWorker.execute {
            var bitmap: android.graphics.Bitmap? = null
            if (!artwork.path.isNullOrBlank() && File(artwork.path).isFile) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(artwork.path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > targetSize * 2 || bounds.outHeight / sample > targetSize * 2) {
                    sample *= 2
                }
                bitmap = BitmapFactory.decodeFile(artwork.path, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            if (bitmap == null) bitmap = extractEmbeddedArtwork(artwork.uri, targetSize)
            bitmap?.let { decoded -> runOnUiThread {
                if (view.tag == artwork.identity && view.isAttachedToWindow) {
                    view.setImageBitmap(decoded)
                    view.clearColorFilter()
                } else decoded.recycle()
            } }
        } }.onFailure { android.util.Log.w("AudioDrop", "Could not load a download thumbnail", it) }
    }

    private fun extractEmbeddedArtwork(uri: String, targetSize: Int): android.graphics.Bitmap? = runCatching {
        val retriever = MediaMetadataRetriever()
        val data = try {
            retriever.setDataSource(applicationContext, Uri.parse(uri))
            retriever.embeddedPicture
        } finally {
            retriever.release()
        } ?: return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > targetSize * 2 || bounds.outHeight / sample > targetSize * 2) sample *= 2
        BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    private fun historySortLabel(): String = when (historySortMode) {
        HISTORY_SORT_OLDEST -> "Oldest first"
        HISTORY_SORT_TITLE -> "Title A–Z"
        else -> "Newest first"
    }

    private fun showHistorySortDialog() {
        val options = listOf(
            HISTORY_SORT_NEWEST to "Newest first",
            HISTORY_SORT_OLDEST to "Oldest first",
            HISTORY_SORT_TITLE to "Title A–Z"
        )
        val choices = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(2))
        }
        var dialog: AlertDialog? = null
        options.forEach { (mode, label) ->
            val selected = mode == historySortMode
            val option = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(if (selected) accentColor else fieldColor(), dp(16), if (selected) accentColor else borderColor())
                setPadding(dp(16), 0, dp(14), 0)
                isClickable = true
                setOnClickListener {
                    if (historySortMode != mode) {
                        val currentScroll = historyScrollView?.scrollY ?: 0
                        historySortMode = mode
                        preferences.edit().putString(KEY_HISTORY_SORT, mode).apply()
                        dialog?.dismiss()
                        historyListContainer?.postDelayed({
                            populateHistoryList(visibleHistoryItems(), currentScroll, animate = true)
                        }, 90L)
                    } else dialog?.dismiss()
                }
            }
            option.addView(text(label, 15, if (selected) Color.WHITE else primaryText(), bold = selected), LinearLayout.LayoutParams(0, -2, 1f))
            option.addView(text(if (selected) "✓" else "", 20, if (selected) Color.WHITE else accentColor, bold = true).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(28), -1))
            choices.addView(option, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(4) })
        }
        dialog = AlertDialog.Builder(this)
            .setTitle("Sort downloads")
            .setView(choices)
            .setNegativeButton("Close", null)
            .create()
        dialog?.let { showStyledDialog(it) }
    }

    private fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun formatHistoryDay(day: Long): String {
        val today = startOfDay(System.currentTimeMillis())
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        return when (day) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> DateFormat.getDateInstance(DateFormat.FULL).format(Date(day))
        }
    }

    private fun enterHistorySelectionMode(uri: String) {
        setHistorySelectionMode(true)
        historyCheckboxes[uri]?.isChecked = true
        if (uri !in selectedHistoryUris) selectedHistoryUris.add(uri)
        updateHistorySelectionControls()
    }

    private fun toggleHistoryItem(uri: String) {
        val checkbox = historyCheckboxes[uri] ?: return
        checkbox.isChecked = !checkbox.isChecked
    }

    private fun onHistoryCheckboxChanged(uri: String, checked: Boolean) {
        if (checked) {
            selectedHistoryUris.add(uri)
            setHistorySelectionMode(true)
        } else {
            selectedHistoryUris.remove(uri)
            if (selectedHistoryUris.isEmpty()) setHistorySelectionMode(false)
        }
        updateHistorySelectionControls()
    }

    private fun setHistorySelectionMode(enabled: Boolean) {
        if (historySelectionMode == enabled) return
        historySelectionMode = enabled
        val duration = 190L
        val selection = historySelectionToolbar
        if (enabled) {
            hideClearHistory()
            hideArchiveFolder()
            historySortButton?.visibility = View.GONE
            historySearchButton?.visibility = View.GONE
            historySearchInput?.visibility = View.GONE
            historySearchInput?.let { input ->
                (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                    ?.hideSoftInputFromWindow(input.windowToken, 0)
            }
            historyExpandedHeader?.alpha = 0f
            historyCompactHeader?.alpha = 0f
            selection?.apply {
                animate().cancel()
                visibility = View.VISIBLE
                alpha = 0f
                translationY = 0f
                animate().alpha(1f).setDuration(duration)
                    .setInterpolator(DecelerateInterpolator(1.2f)).start()
            }
        } else {
            historySortButton?.visibility = View.VISIBLE
            historySearchButton?.visibility = View.VISIBLE
            historySearchButton?.setImageResource(if (historySearchActive) R.drawable.ic_action_close else R.drawable.ic_action_search)
            historySearchInput?.visibility = if (historySearchActive) View.VISIBLE else View.GONE
            selection?.animate()?.cancel()
            selection?.animate()?.alpha(0f)?.translationY(0f)?.setDuration(duration)
                ?.withEndAction { if (!historySelectionMode) selection.visibility = View.GONE }?.start()
        }
        updateHistoryCollapsingHeader(historyScrollView?.scrollY ?: 0)
        historyCheckboxes.values.forEach { checkbox ->
            checkbox.animate().cancel()
            if (enabled) {
                checkbox.visibility = View.VISIBLE
                checkbox.alpha = 0f
                checkbox.scaleX = 0.78f
                checkbox.scaleY = 0.78f
                checkbox.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(190L).setInterpolator(DecelerateInterpolator()).start()
            } else {
                checkbox.animate().alpha(0f).scaleX(0.82f).scaleY(0.82f).setDuration(160L)
                    .withEndAction {
                        if (!historySelectionMode) {
                            checkbox.visibility = View.INVISIBLE
                            checkbox.alpha = 1f
                            checkbox.scaleX = 1f
                            checkbox.scaleY = 1f
                        }
                    }.start()
            }
        }
        updateHistorySelectionControls()
    }

    private fun toggleSelectAllHistory() {
        val allUris = displayedHistoryItems.map { it.uri }
        if (allUris.isNotEmpty() && selectedHistoryUris.containsAll(allUris)) {
            selectedHistoryUris.clear()
            setHistorySelectionMode(false)
        } else {
            selectedHistoryUris.addAll(allUris)
            setHistorySelectionMode(true)
        }
        historyCheckboxes.forEach { (uri, checkbox) -> checkbox.isChecked = uri in selectedHistoryUris }
        updateHistorySelectionControls()
    }

    private fun updateHistorySelectionControls() {
        val selectedCount = selectedHistoryUris.size
        val selectedTitle = if (selectedCount == 1) "1 selected" else "$selectedCount selected"
        historyHeaderEyebrow?.text = if (historySelectionMode) "YOUR MEDIA · SELECTING" else if (historyShowingArchive) "ARCHIVED MEDIA" else "YOUR MEDIA"
        historyExpandedTitle?.apply {
            text = if (historySelectionMode) selectedTitle else if (historyShowingArchive) "Archive" else "Downloads"
            textSize = if (historySelectionMode) 27f else 34f
        }
        historyExpandedSubtitle?.text = if (historySelectionMode) "Choose an action for the selected items." else if (historyShowingArchive) "Saved items hidden from Downloads." else "Audio and videos saved with AudioDrop."
        historyCompactHeader?.text = if (historySelectionMode) selectedTitle else if (historyShowingArchive) "Archive" else "Downloads"
        val allSelected = displayedHistoryItems.isNotEmpty() &&
            displayedHistoryItems.all { it.uri in selectedHistoryUris }
        historySelectAllButton?.text = if (allSelected) "Deselect all" else "Select all"
        historyArchiveButton?.apply {
            text = if (historyShowingArchive) "Restore" else "Archive"
            isEnabled = selectedCount > 0
            alpha = if (selectedCount > 0) 1f else 0.45f
        }
        historyShareButton?.isEnabled = selectedCount > 0
        historyShareButton?.alpha = if (selectedCount > 0) 1f else 0.45f
        historyDeleteButton?.apply {
            text = if (selectedCount == 0) "Delete" else "Delete ($selectedCount)"
            isEnabled = selectedCount > 0
            alpha = if (isEnabled) 1f else 0.45f
        }
        historyCards.forEach { (uri, itemCard) ->
            val selected = uri in selectedHistoryUris
            itemCard.background = rounded(
                if (selected) accentTint() else surfaceColor(),
                dp(26),
                if (selected) accentColor else null
            )
        }
    }

    private fun updateHistoryCollapsingHeader(scrollY: Int) {
        val header = historyHeaderFrame ?: return
        val fraction = (scrollY.toFloat() / dp(HISTORY_COLLAPSE_RANGE_DP)).coerceIn(0f, 1f)
        val expandedHeight = dp(HISTORY_HEADER_EXPANDED_DP)
        val collapsedHeight = dp(HISTORY_HEADER_COLLAPSED_DP)
        val params = header.layoutParams as? LinearLayout.LayoutParams ?: return
        val targetHeight = (expandedHeight - (expandedHeight - collapsedHeight) * fraction).toInt()
        if (params.height != targetHeight) {
            params.height = targetHeight
            header.layoutParams = params
        }
        historyExpandedHeader?.alpha = 1f - fraction
        historyCompactHeader?.alpha = fraction
        if (historySelectionMode || historySearchActive) {
            historyExpandedHeader?.alpha = 0f
            historyCompactHeader?.alpha = 0f
        }
        historySearchInput?.let { input ->
            input.visibility = if (historySearchActive && !historySelectionMode) View.VISIBLE else View.GONE
        }
        historySortButton?.visibility = if (historySelectionMode) View.GONE else View.VISIBLE
        historySearchButton?.visibility = if (historySelectionMode) View.GONE else View.VISIBLE
        historySearchButton?.setImageResource(if (historySearchActive) R.drawable.ic_action_close else R.drawable.ic_action_search)
        historySearchButton?.contentDescription = if (historySearchActive) "Close search" else "Search downloads"
    }

    private fun makeSelectionToolbar(
        selectAll: () -> Unit,
        share: () -> Unit,
        archive: () -> Unit,
        delete: () -> Unit,
        shareButtonSetter: (Button) -> Unit,
        archiveButtonSetter: (Button) -> Unit,
        deleteButtonSetter: (Button) -> Unit,
        selectAllSetter: (Button) -> Unit
    ): LinearLayout {
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
        }
        val buttons = listOf(
            actionButton("Select all", filled = false, action = selectAll),
            actionButton("Share", filled = false, action = share),
            actionButton("Archive", filled = false, action = archive),
            actionButton("Delete", filled = true, action = delete).apply {
                setTextColor(Color.WHITE)
                background = rounded(DELETE_RED, dp(18))
            }
        )
        buttons.forEachIndexed { index, button ->
            button.textSize = 11f
            button.setPadding(dp(4), 0, dp(4), 0)
            toolbar.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                if (index > 0) leftMargin = dp(4)
            })
        }
        selectAllSetter(buttons[0])
        shareButtonSetter(buttons[1])
        archiveButtonSetter(buttons[2])
        deleteButtonSetter(buttons[3])
        return toolbar
    }

    private fun visibleHistoryItems(): List<HistoryItem> {
        val query = historySearchQuery.trim()
        return readHistory().filter { item ->
            item.isArchived == historyShowingArchive &&
                (query.isEmpty() || listOf(item.title, item.format, item.quality).any { it.contains(query, true) })
        }
    }

    private fun showArchivedDownloads(show: Boolean) {
        historyShowingArchive = show
        historyClearRevealed = false
        historyReachedBottom = false
        historyArchiveRevealed = false
        historySearchActive = false
        selectedHistoryUris.clear()
        setHistorySelectionMode(false)
        historySearchQuery = ""
        historySearchInput?.setText("")
        historySearchButton?.setImageResource(R.drawable.ic_action_search)
        historySearchButton?.contentDescription = "Search downloads"
        historyArchiveFolderButton?.apply {
            animate().cancel()
            alpha = 0f
            visibility = View.GONE
        }
        historyExpandedTitle?.text = if (show) "Archive" else "Downloads"
        historyCompactHeader?.text = if (show) "Archive" else "Downloads"
        historyArchiveFolderButton?.text = if (show) "Downloads" else "Archive · ${readHistory().count { it.isArchived }}"
        populateHistoryList(visibleHistoryItems(), 0, animate = true)
        updateHistorySelectionControls()
    }

    private fun revealArchiveFolder() {
        if (historySearchActive || historySelectionMode) return
        val button = historyArchiveFolderButton ?: return
        if (!historyShowingArchive && readHistory().none { it.isArchived }) return
        button.text = if (historyShowingArchive) "Downloads" else "Archive · ${readHistory().count { it.isArchived }}"
        button.animate().cancel()
        button.visibility = View.VISIBLE
        historyArchiveRevealed = true
        button.alpha = 0f
        button.translationY = dp(-8).toFloat()
        button.animate().alpha(1f).translationY(0f).setDuration(180L)
            .setInterpolator(DecelerateInterpolator(1.2f)).start()
    }

    private fun hideArchiveFolder() {
        if (!historyArchiveRevealed) return
        historyArchiveRevealed = false
        val button = historyArchiveFolderButton ?: return
        button.animate().cancel()
        button.animate().alpha(0f).translationY(dp(-6).toFloat()).setDuration(140L)
            .withEndAction { if (!historyArchiveRevealed) button.visibility = View.GONE }.start()
    }

    private fun revealClearHistory() {
        if (historyClearRevealed || historyArchiveRevealed || historySearchActive) return
        historyClearRevealed = true
        historyClearButton?.let { button ->
            button.animate().cancel()
            button.visibility = if (historyClearRevealed && displayedHistoryItems.isNotEmpty()) View.VISIBLE else View.INVISIBLE
            button.alpha = 0f
            button.translationY = dp(6).toFloat()
            if (historyClearRevealed && displayedHistoryItems.isNotEmpty()) {
                button.animate().alpha(1f).translationY(0f).setDuration(180L)
                    .setInterpolator(DecelerateInterpolator(1.2f)).start()
            }
        }
        revealArchiveFolder()
    }

    private fun hideClearHistory() {
        if (!historyClearRevealed) return
        historyClearRevealed = false
        val button = historyClearButton ?: return
        button.animate().cancel()
        button.animate().alpha(0f).translationY(dp(5).toFloat()).setDuration(140L)
            .withEndAction { if (!historyClearRevealed) button.visibility = View.INVISIBLE }.start()
    }

    private fun toggleDownloadsSearch() {
        if (historySelectionMode) return
        historySearchActive = !historySearchActive
        if (historySearchActive) {
            historyClearRevealed = false
            hideArchiveFolder()
            historySearchInput?.visibility = View.VISIBLE
            historySearchInput?.alpha = 0f
            historySearchInput?.animate()?.alpha(1f)?.setDuration(150L)?.start()
            historyExpandedHeader?.alpha = 0f
            historyCompactHeader?.alpha = 0f
            historySearchButton?.setImageResource(R.drawable.ic_action_close)
            historySearchButton?.contentDescription = "Close search"
            historySearchInput?.requestFocus()
            historySearchInput?.post {
                (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                    ?.showSoftInput(historySearchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        } else {
            historySearchInput?.let { input ->
                (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                    ?.hideSoftInputFromWindow(input.windowToken, 0)
                input.setText("")
                input.clearFocus()
                input.visibility = View.GONE
            }
            historySearchQuery = ""
            historySearchButton?.setImageResource(R.drawable.ic_action_search)
            historySearchButton?.contentDescription = "Search downloads"
            updateHistoryCollapsingHeader(historyScrollView?.scrollY ?: 0)
        }
        populateHistoryList(visibleHistoryItems(), historyScrollView?.scrollY ?: 0, animate = true)
    }

    private fun archiveSelectedHistory() {
        val selected = displayedHistoryItems.filter { it.uri in selectedHistoryUris }
        if (selected.isEmpty()) return
        archiveItems(selected, archive = !historyShowingArchive)
    }

    private fun archiveSelectedSession() {
        val selected = downloadedThisSession.filter { it.uri in selectedSessionUris && !it.isArchived }
        if (selected.isEmpty()) return
        archiveItems(selected, archive = true)
    }

    private fun archiveItems(items: List<HistoryItem>, archive: Boolean) {
        val byUri = items.associate { it.uri to it.copy(isArchived = archive) }
        writeHistory(readHistory().map { byUri[it.uri] ?: it })
        for (index in downloadedThisSession.indices) {
            val changed = byUri[downloadedThisSession[index].uri] ?: continue
            downloadedThisSession[index] = changed
        }
        selectedHistoryUris.clear()
        selectedSessionUris.clear()
        setHistorySelectionMode(false)
        setSessionSelectionMode(false)
        if (selectedTab == Tab.HISTORY) populateHistoryList(visibleHistoryItems(), historyScrollView?.scrollY ?: 0, animate = true)
        renderHomeDownloadLists()
        historyArchiveFolderButton?.text = if (historyShowingArchive) "Downloads"
            else "Archive · ${readHistory().count { it.isArchived }}"
        toast(if (archive) "Archived ${items.size} item${if (items.size == 1) "" else "s"}" else "Restored ${items.size} item${if (items.size == 1) "" else "s"}")
    }

    private fun shareMedia(items: List<HistoryItem>) {
        if (items.isEmpty()) return
        val uris = ArrayList(items.map { Uri.parse(it.uri) })
        val multiple = uris.size > 1
        val intent = Intent(if (multiple) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
            type = if (items.all { it.mimeType.startsWith("audio/") }) "audio/*"
            else if (items.all { it.mimeType.startsWith("video/") }) "video/*" else "*/*"
            if (multiple) putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) else putExtra(Intent.EXTRA_STREAM, uris.first())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share downloads"))
    }

    private fun shareSelectedHistory() = shareMedia(displayedHistoryItems.filter { it.uri in selectedHistoryUris })
    private fun shareSelectedSession() = shareMedia(downloadedThisSession.filter { it.uri in selectedSessionUris })

    private fun toggleSelectAllSession() {
        val visible = downloadedThisSession.filterNot { it.isArchived }
        if (visible.isNotEmpty() && selectedSessionUris.containsAll(visible.map { it.uri })) selectedSessionUris.clear()
        else selectedSessionUris.addAll(visible.map { it.uri })
        setSessionSelectionMode(selectedSessionUris.isNotEmpty())
        sessionCheckboxes.forEach { (uri, checkbox) -> checkbox.isChecked = uri in selectedSessionUris }
        renderHomeDownloadLists()
    }

    private fun setSessionSelectionMode(enabled: Boolean) {
        if (sessionSelectionMode == enabled) return
        sessionSelectionMode = enabled
        val toolbar = homeSelectionToolbar
        if (enabled) {
            toolbar?.apply {
                visibility = View.VISIBLE
                alpha = 0f
                translationY = 0f
                animate().alpha(1f).setDuration(180L)
                    .setInterpolator(DecelerateInterpolator(1.2f)).start()
            }
        } else {
            toolbar?.animate()?.alpha(0f)?.translationY(0f)?.setDuration(150L)
                ?.withEndAction { if (!sessionSelectionMode) toolbar.visibility = View.GONE }?.start()
        }
        sessionCheckboxes.values.forEach { checkbox ->
            checkbox.animate().cancel()
            if (enabled) {
                checkbox.visibility = View.VISIBLE
                checkbox.alpha = 0f
                checkbox.animate().alpha(1f).setDuration(160L).start()
            } else {
                checkbox.animate().alpha(0f).setDuration(140L).withEndAction {
                    if (!sessionSelectionMode) {
                        checkbox.visibility = View.INVISIBLE
                        checkbox.alpha = 1f
                    }
                }.start()
            }
        }
        updateSessionSelectionControls()
        updateHomeHeaderState()
    }

    private fun updateSessionSelectionControls() {
        val count = selectedSessionUris.size
        sessionDownloadsHeader?.text = if (sessionSelectionMode) "$count SELECTED" else "DOWNLOADED THIS SESSION"
        val visible = downloadedThisSession.filterNot { it.isArchived }
        sessionSelectAllButton?.text = if (visible.isNotEmpty() && selectedSessionUris.containsAll(visible.map { it.uri })) "Deselect all" else "Select all"
        sessionArchiveButton?.text = "Archive"
        sessionArchiveButton?.isEnabled = count > 0
        sessionShareButton?.isEnabled = count > 0
        sessionShareButton?.alpha = if (count > 0) 1f else 0.45f
        sessionDeleteButton?.apply {
            text = if (count > 0) "Delete ($count)" else "Delete"
            isEnabled = count > 0
            alpha = if (count > 0) 1f else 0.45f
        }
        sessionCards.forEach { (uri, itemCard) ->
            val selected = uri in selectedSessionUris
            itemCard.background = rounded(
                if (selected) accentTint() else fieldColor(),
                dp(18),
                if (selected) accentColor else null
            )
        }
    }

    private fun toggleSessionItem(uri: String) {
        if (uri in selectedSessionUris) selectedSessionUris.remove(uri) else selectedSessionUris.add(uri)
        setSessionSelectionMode(selectedSessionUris.isNotEmpty())
        updateSessionSelectionControls()
    }

    private fun confirmDeleteSelectedSession() {
        val selected = downloadedThisSession.filter { it.uri in selectedSessionUris }
        if (selected.isEmpty()) return
        confirmDeleteItems(selected, "Remove ${selected.size} ${if (selected.size == 1) "item" else "items"} from Downloads?") {
            deleteSelectedMedia(selected)
        }
    }

    private fun buildSettings(page: LinearLayout) {
        page.addView(sectionLabel("APPEARANCE"))
        val appearance = card()
        appearance.addView(text("Appearance", 17, primaryText(), bold = true))
        appearance.addView(text("Black mode uses a true black background. You can also follow your phone theme.", 13, secondaryText()).apply {
            setPadding(0, dp(5), 0, dp(12))
        })
        val modes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf(
            MODE_LIGHT to "Light",
            MODE_BLACK to "Black",
            MODE_SYSTEM to "System"
        ).forEachIndexed { index, (mode, label) ->
            modes.addView(actionButton(label, filled = appearanceMode == mode) {
                performAppHaptic()
                chooseAppearance(mode)
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                if (index > 0) leftMargin = dp(8)
            })
        }
        appearance.addView(modes)
        page.addView(appearance)

        page.addView(sectionLabel("HEADER EFFECTS"))
        val blurCard = card()
        val blurRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val blurCopy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        blurCopy.addView(text("Scrolling header blur", 16, primaryText(), bold = true))
        blurCopy.addView(text("Apply the same frosted effect to expanded and collapsed headers.", 12, secondaryText()).apply {
            setPadding(0, dp(4), 0, 0)
        })
        blurRow.addView(blurCopy, LinearLayout.LayoutParams(0, -2, 1f))
        blurRow.addView(Switch(this).apply {
            isChecked = preferences.getBoolean(KEY_HEADER_BLUR_ENABLED, true)
            buttonTintList = ColorStateList.valueOf(accentColor)
            setOnCheckedChangeListener { _, checked ->
                performAppHaptic()
                preferences.edit().putBoolean(KEY_HEADER_BLUR_ENABLED, checked).apply()
                refreshHeaderBlurSettings()
            }
        })
        blurCard.addView(blurRow)
        val blurAlpha = preferences.getInt(KEY_HEADER_BLUR_ALPHA, 58).coerceIn(0, 100)
        val blurAlphaLabel = text("Blur transparency · $blurAlpha%", 13, secondaryText()).apply {
            setPadding(0, dp(14), 0, 0)
        }
        blurCard.addView(blurAlphaLabel)
        blurCard.addView(SeekBar(this).apply {
            max = 100
            progress = blurAlpha
            progressTintList = ColorStateList.valueOf(accentColor)
            thumbTintList = ColorStateList.valueOf(accentColor)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    preferences.edit().putInt(KEY_HEADER_BLUR_ALPHA, progress).apply()
                    blurAlphaLabel.text = "Blur transparency · $progress%"
                    listOf(homeHeaderBlurTint, settingsHeaderBlurTint, historyHeaderBlurTint)
                        .forEach { it?.let(::setHeaderBlurTint) }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) { performAppHaptic() }
            })
        })
        page.addView(blurCard)

        val accentCard = card()
        accentCard.addView(text("Accent color", 17, primaryText(), bold = true))
        accentCard.addView(text("Choose the highlight color for buttons and controls.", 13, secondaryText()).apply {
            setPadding(0, dp(5), 0, dp(12))
        })
        val swatches = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        ACCENT_OPTIONS.forEach { color ->
            val selected = color == accentColor
            val swatch = TextView(this).apply {
                text = if (selected) "✓" else ""
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                background = oval(color, if (selected) dp(3) else 0, if (selected) Color.WHITE else null)
                contentDescription = "Set accent color"
                isClickable = true
                setOnClickListener {
                    performAppHaptic()
                    rememberHomeForm()
                    accentColor = color
                    preferences.edit().putInt(KEY_ACCENT, color).apply()
                    renderScreen()
                }
            }
            swatches.addView(swatch, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                rightMargin = dp(12)
            })
        }
        accentCard.addView(swatches)
        page.addView(accentCard)

        page.addView(sectionLabel("STORAGE & DOWNLOADS"))
        val folderCard = card()
        folderCard.addView(text("Save folder", 17, primaryText(), bold = true))
        folderCard.addView(text(saveFolderLabel(), 13, secondaryText()).apply {
            setPadding(0, dp(5), 0, dp(14))
        })
        folderCard.addView(actionButton("Choose folder", filled = true) { chooseSaveFolder() },
            LinearLayout.LayoutParams(-1, dp(48)))
        if (preferences.contains(KEY_SAVE_FOLDER)) {
            folderCard.addView(actionButton("Use Downloads / AudioDrop", filled = false) {
                preferences.edit().remove(KEY_SAVE_FOLDER).apply()
                renderScreen()
            }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        }
        page.addView(folderCard)

        val deleteCard = card()
        val deleteRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val deleteText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        deleteText.addView(text("Delete downloaded media", 16, primaryText(), bold = true))
        deleteText.addView(text("Also remove the file from your device when deleting from Downloads.", 12, secondaryText()).apply {
            setPadding(0, dp(4), 0, 0)
        })
        deleteRow.addView(deleteText, LinearLayout.LayoutParams(0, -2, 1f))
        deleteRow.addView(Switch(this).apply {
            isChecked = preferences.getBoolean(KEY_DELETE_DEVICE_MEDIA, false)
            setOnCheckedChangeListener { _, checked ->
                performAppHaptic()
                preferences.edit().putBoolean(KEY_DELETE_DEVICE_MEDIA, checked).apply()
            }
            buttonTintList = ColorStateList.valueOf(accentColor)
        })
        deleteCard.addView(deleteRow)
        page.addView(deleteCard)

        page.addView(sectionLabel("GENERAL"))
        val swipeCard = card()
        val swipeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val swipeText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        swipeText.addView(text("Swipe between tabs", 16, primaryText(), bold = true))
        swipeText.addView(text("Drag sideways to switch Home, Downloads, and Settings.", 12, secondaryText()).apply {
            setPadding(0, dp(4), 0, 0)
        })
        swipeRow.addView(swipeText, LinearLayout.LayoutParams(0, -2, 1f))
        swipeRow.addView(Switch(this).apply {
            isChecked = preferences.getBoolean(KEY_SWIPE_NAV_ENABLED, false)
            buttonTintList = ColorStateList.valueOf(accentColor)
            setOnCheckedChangeListener { _, checked ->
                performAppHaptic()
                preferences.edit().putBoolean(KEY_SWIPE_NAV_ENABLED, checked).apply()
            }
        })
        swipeCard.addView(swipeRow)
        page.addView(swipeCard)

        val hapticsCard = card()
        val hapticsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val hapticsText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        hapticsText.addView(text("Haptic feedback", 16, primaryText(), bold = true))
        hapticsText.addView(text("Small taps for navigation and download actions.", 12, secondaryText()).apply {
            setPadding(0, dp(4), 0, 0)
        })
        hapticsRow.addView(hapticsText, LinearLayout.LayoutParams(0, -2, 1f))
        hapticsRow.addView(Switch(this).apply {
            isChecked = preferences.getBoolean(KEY_HAPTICS_ENABLED, true)
            buttonTintList = ColorStateList.valueOf(accentColor)
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    preferences.edit().putBoolean(KEY_HAPTICS_ENABLED, true).apply()
                    performAppHaptic()
                } else {
                    performAppHaptic()
                    preferences.edit().putBoolean(KEY_HAPTICS_ENABLED, false).apply()
                }
            }
        })
        hapticsCard.addView(hapticsRow)
        hapticsCard.addView(text("Strength", 14, primaryText(), bold = true).apply {
            setPadding(0, dp(16), 0, dp(2))
        })
        val strengthLabel = text(hapticStrengthLabel(preferences.getInt(KEY_HAPTIC_STRENGTH, 1)), 12, secondaryText())
        hapticsCard.addView(strengthLabel)
        hapticsCard.addView(SeekBar(this).apply {
            max = 2
            progress = preferences.getInt(KEY_HAPTIC_STRENGTH, 1).coerceIn(0, 2)
            progressTintList = ColorStateList.valueOf(accentColor)
            thumbTintList = ColorStateList.valueOf(accentColor)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    preferences.edit().putInt(KEY_HAPTIC_STRENGTH, progress).apply()
                    strengthLabel.text = hapticStrengthLabel(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) { performAppHaptic() }
            })
        })
        page.addView(hapticsCard)
        page.addView(text("MP3 quality can also be changed on the Home tab. 320 kbps is the default.", 13, secondaryText()).apply {
            setPadding(dp(4), dp(5), dp(4), dp(12))
        })
    }

    private fun addPageHeader(page: LinearLayout, eyebrow: String, title: String, subtitle: String) {
        page.addView(text(eyebrow, 12, accentColor, bold = true).apply {
            letterSpacing = 0.12f
            setPadding(0, dp(6), 0, dp(8))
        })
        if (title.isNotBlank()) page.addView(text(title, if (title.startsWith("Save video")) 27 else 34, primaryText(), bold = true).apply {
            maxLines = 1
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
            if (title.startsWith("Save video")) setPadding(0, 0, dp(42), 0)
        })
        if (subtitle.isNotBlank()) page.addView(text(subtitle, 14, secondaryText()).apply {
            setPadding(0, dp(8), 0, dp(22))
        })
    }

    private fun sectionLabel(value: String) = text(value, 11, secondaryText(), bold = true).apply {
        letterSpacing = 0.12f
        setPadding(dp(4), dp(10), dp(4), dp(8))
    }

    private fun buildBottomNavigation(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(surfaceColor())
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        populateBottomNavigation(bar)
        return bar
    }

    private fun populateBottomNavigation(bar: LinearLayout) {
        bar.removeAllViews()
        listOf(
            Triple(Tab.HOME, R.drawable.ic_tab_home, "Home"),
            Triple(Tab.HISTORY, R.drawable.ic_tab_downloads, "Downloads"),
            Triple(Tab.SETTINGS, R.drawable.ic_tab_settings, "Settings")
        ).forEach { (tab, icon, title) ->
            val selected = selectedTab == tab
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(if (selected) accentTint() else Color.TRANSPARENT, dp(18))
                isClickable = true
                setOnClickListener { navigateTo(tab) }
            }
            item.addView(ImageView(this).apply {
                setImageResource(icon)
                setColorFilter(if (selected) accentColor else secondaryText())
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            item.addView(text(title, 11, if (selected) accentColor else secondaryText(), bold = selected).apply {
                includeFontPadding = false
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(18)))
            bar.addView(item, LinearLayout.LayoutParams(0, -1, 1f).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
            })
        }
    }

    private fun startDownload(video: Boolean) {
        startDownload(video, null)
    }

    private fun startDownload(video: Boolean, maxVideoHeight: Int?) {
        if (isDownloading) return
        val url = linkInput.text.toString().trim()
        if (!isSupportedUrl(url)) {
            linkInput.error = "Enter a valid https video link"
            return
        }

        savedLink = url
        savedQualityIndex = savedQualityIndex.coerceIn(0, AUDIO_QUALITY_KBPS.lastIndex)
        val quality = AUDIO_QUALITY_KBPS[savedQualityIndex]
        val session = DownloadSession(UUID.randomUUID().toString(), url, video, maxVideoHeight, quality)
        activeSession = session
        isDownloading = true
        setBusy(true)
        val initialMessage = if (video) "Preparing video…" else "Preparing MP3…"
        updateStatus(initialMessage, 0)
        DownloadNotificationService.start(this, session.queue.first().title)
        DownloadNotificationService.update(this, false, 0, 1, 0, "Preparing download", initialMessage, false)
        renderHomeDownloadLists()
        worker.execute { runDownloadSession(session) }
    }

    private fun runDownloadSession(session: DownloadSession) {
        var currentTempDir: File? = null
        try {
            if (session.cancelled || session.paused) {
                handleDownloadInterruption(session, currentTempDir)
                return
            }

            if (!session.playlistResolved) {
                var resolvedQueue: List<QueueMedia>? = null
                if (looksLikePlaylist(session.sourceUrl)) {
                    val probeId = "${session.id}-playlist-${UUID.randomUUID()}"
                    session.currentProcessId = probeId
                    activeProcessId = probeId
                    val preparing = "Checking playlist and building download queue…"
                    runOnUiThread {
                        if (activeSession === session) {
                            updateStatus(preparing, 0)
                            publishDownloadNotification(session, preparing)
                        }
                    }
                    resolvedQueue = try {
                        resolvePlaylist(session.sourceUrl, probeId)
                    } catch (error: Exception) {
                        if (session.paused || session.cancelled) throw error
                        android.util.Log.w("AudioDrop", "Playlist lookup failed; treating link as one item", error)
                        null
                    }
                    if (session.paused || session.cancelled) {
                        handleDownloadInterruption(session, currentTempDir)
                        return
                    }
                }
                val queue = resolvedQueue?.takeIf { it.isNotEmpty() }
                    ?: listOf(QueueMedia(guessTitle(session.sourceUrl), session.sourceUrl))
                session.queue = queue
                session.isPlaylist = resolvedQueue?.isNotEmpty() == true
                session.playlistResolved = true
                runOnUiThread {
                    if (activeSession === session) {
                        renderHomeQueue()
                        publishDownloadNotification(session, "Queued ${session.queue.size} items")
                    }
                }
            }

            for (index in session.currentIndex until session.queue.size) {
                if (session.cancelled || session.paused) {
                    handleDownloadInterruption(session, currentTempDir)
                    return
                }
                session.currentIndex = index
                session.currentPercent = 0
                val queued = session.queue[index]
                currentTempDir = File(cacheDir, "audio-${session.id}-$index").apply { mkdirs() }
                val processId = "${session.id}-$index-${UUID.randomUUID()}"
                session.currentProcessId = processId
                activeProcessId = processId
                val startMessage = if (session.isPlaylist) {
                    "Downloading song ${index + 1} of ${session.queue.size}: ${queued.title}"
                } else if (session.video) "Preparing video…" else "Preparing MP3…"
                runOnUiThread {
                    if (activeSession === session) {
                        updateStatus(startMessage, 0)
                        setBusy(true)
                        renderHomeQueue()
                        publishDownloadNotification(session, startMessage)
                    }
                }

                try {
                    fun hasExpectedMediaFile(): Boolean = currentTempDir?.listFiles()?.any { file ->
                        file.isFile && if (session.video) file.extension.lowercase(Locale.ROOT) in VIDEO_EXTENSIONS
                        else file.extension.equals("mp3", ignoreCase = true)
                    } == true
                    val progressCallback: (Float, Long, String) -> Unit = { progress, _, line ->
                        val percent = progress.toInt().coerceIn(0, 100)
                        session.currentPercent = percent
                        val message = publicProgressMessage(session.video, percent, line)
                        runOnUiThread {
                            if (activeSession === session && !session.paused && !session.cancelled) {
                                updateStatus(
                                    if (session.isPlaylist) "Song ${index + 1} of ${session.queue.size} · ${message}" else message,
                                    percent
                                )
                                publishDownloadNotification(session, queued.title, message)
                            }
                        }
                    }
                    try {
                        YoutubeDL.getInstance().execute(
                            buildMediaRequest(session, queued.url, currentTempDir!!, fast = true),
                            processId,
                            progressCallback
                        )
                        if (!hasExpectedMediaFile()) error("No media file was produced")
                    } catch (fastError: Exception) {
                        if (session.paused || session.cancelled) throw fastError
                        currentTempDir?.listFiles()?.forEach { it.deleteRecursively() }
                        if (session.paused || session.cancelled) throw fastError
                        val backupProcessId = "$processId-backup"
                        session.currentProcessId = backupProcessId
                        activeProcessId = backupProcessId
                        YoutubeDL.getInstance().execute(
                            buildMediaRequest(session, queued.url, currentTempDir!!, fast = false),
                            backupProcessId,
                            progressCallback
                        )
                        if (!hasExpectedMediaFile()) error("No media file was produced")
                    }
                    if (session.cancelled || session.paused) {
                        handleDownloadInterruption(session, currentTempDir)
                        return
                    }

                    val media = currentTempDir!!.listFiles()
                        ?.filter {
                            it.isFile && if (session.video) it.extension.lowercase(Locale.ROOT) in VIDEO_EXTENSIONS
                            else it.extension.equals("mp3", ignoreCase = true)
                        }
                        ?.maxByOrNull(File::lastModified)
                        ?: error(if (session.video) "The download finished without a video file" else "The download finished without an MP3 file")
                    val mimeType = mediaMimeType(media.extension)
                    val info = currentTempDir!!.listFiles()
                        ?.firstOrNull { it.isFile && it.name.endsWith(".info.json", ignoreCase = true) }
                        ?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
                    val title = info?.optString("title")?.takeIf { !it.isNullOrBlank() && it != "null" }
                        ?: media.nameWithoutExtension
                    val actualHeight = info?.optInt("height", 0)?.takeIf { it > 0 }
                    val qualityLabel = if (session.video) actualHeight?.let { "${it}p" }
                        ?: session.maxVideoHeight?.let { "Up to ${it}p" } ?: "Best available"
                    else "${session.audioQuality} kbps"
                    val fileSizeBytes = media.length()
                    val artworkPath = saveHistoryArtwork(currentTempDir!!, media)
                    val savedUri = saveMedia(media, mimeType)
                    val format = if (session.video) media.extension.uppercase(Locale.ROOT) else "MP3"
                    val historyItem = HistoryItem(
                        title,
                        savedUri.toString(),
                        format,
                        mimeType,
                        qualityLabel,
                        fileSizeBytes,
                        artworkPath,
                        System.currentTimeMillis()
                    )
                    addHistory(historyItem)
                    downloadedThisSession.add(historyItem)
                    session.downloadedCount += 1
                    session.currentIndex = index + 1
                    currentTempDir!!.deleteRecursively()
                    currentTempDir = null
                    runOnUiThread {
                        if (activeSession === session || session.cancelled) {
                            renderHomeQueue()
                            if (selectedTab == Tab.HOME) appendSessionDownloadItem(historyItem)
                            if (selectedTab == Tab.HISTORY) {
                                populateHistoryList(visibleHistoryItems(), historyScrollView?.scrollY ?: 0, animate = true)
                            }
                        }
                    }
                } catch (error: Exception) {
                    if (session.paused || session.cancelled) throw error
                    if (!session.isPlaylist) throw error
                    session.failedItems[index] = usefulError(error)
                    currentTempDir?.deleteRecursively()
                    currentTempDir = null
                    session.currentIndex = index + 1
                    runOnUiThread {
                        if (activeSession === session) {
                            val skipped = "Skipped ${queued.title}: ${usefulError(error)}"
                            updateStatus(skipped, 0)
                            renderHomeQueue()
                            publishDownloadNotification(session, queued.title, "Skipping unavailable item")
                        }
                    }
                }
                session.currentProcessId = null
                activeProcessId = null
            }

            if (session.cancelled || session.paused) {
                handleDownloadInterruption(session, currentTempDir)
                return
            }
            val completed = session.downloadedCount
            val endingMessage = if (session.isPlaylist) {
                if (session.failedItems.isEmpty()) "Downloaded $completed of ${session.queue.size} songs"
                else "Downloaded $completed of ${session.queue.size} songs · ${session.failedItems.size} skipped"
            } else "Download complete"
            runOnUiThread {
                if (activeSession === session) finishDownloadSession(session, endingMessage, success = true)
            }
        } catch (error: Exception) {
            when {
                session.paused -> runOnUiThread {
                    if (activeSession === session) {
                        updateStatus("Download paused", session.currentPercent)
                        setBusy(true)
                        renderHomeQueue()
                        publishDownloadNotification(session, "Paused")
                    }
                }
                session.cancelled -> {
                    currentTempDir?.deleteRecursively()
                    cleanupSessionDirectories(session)
                    runOnUiThread {
                        if (activeSession === session) finishDownloadSession(session, "Download canceled", success = false, canceled = true)
                    }
                }
                else -> {
                    currentTempDir?.deleteRecursively()
                    cleanupSessionDirectories(session)
                    val message = "Download failed: ${usefulError(error)}"
                    runOnUiThread {
                        if (activeSession === session) finishDownloadSession(session, message, false)
                    }
                }
            }
        } finally {
            if (!session.paused) {
                session.currentProcessId = null
                if (activeSession === session && (session.cancelled || session.currentIndex >= session.queue.size)) {
                    activeProcessId = null
                }
            }
            if (activityDestroyed && activeSession !== session) worker.shutdown()
        }
    }

    private fun buildMediaRequest(session: DownloadSession, url: String, tempDir: File, fast: Boolean) = YoutubeDLRequest(url).apply {
        addOption("--no-playlist")
        addOption("--continue")
        addOption("--no-mtime")
        addOption("--no-check-formats")
        addOption("--socket-timeout", if (fast) "15" else "30")
        addOption("--retries", if (fast) "4" else "10")
        addOption("--fragment-retries", if (fast) "4" else "10")
        addOption("--extractor-retries", if (fast) "1" else "5")
        addOption("--concurrent-fragments", if (fast) "8" else "1")
        addOption("--buffer-size", if (fast) "64K" else "16K")
        if (fast) addOption("--http-chunk-size", "10M")
        if (!fast) addOption("--force-ipv4")
        addOption("--embed-metadata")
        addOption("--embed-thumbnail")
        addOption("--convert-thumbnails", "jpg")
        addOption("--parse-metadata", "%(artist,uploader|)s:%(meta_artist)s")
        addOption("--no-embed-chapters")
        addOption("--no-embed-info-json")
        addOption("--write-info-json")
        if (session.video) {
            addOption("-f", if (fast) "bestvideo*+bestaudio/best" else "bestvideo+bestaudio/best")
            session.maxVideoHeight?.let { addOption("-S", "res:$it") }
            addOption("--merge-output-format", "mp4")
        } else {
            addOption("-f", "bestaudio/best")
            addOption("-x")
            addOption("--audio-format", "mp3")
            addOption("--audio-quality", "${session.audioQuality}K")
        }
        addOption("-o", File(tempDir, "%(title)s.%(ext)s").absolutePath)
    }

    private fun resolvePlaylist(url: String, processId: String): List<QueueMedia> {
        val request = YoutubeDLRequest(url).apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            addOption("--skip-download")
            addOption("--no-warnings")
            addOption("--no-check-formats")
        }
        val output = YoutubeDL.getInstance().execute(request, processId).out
        val jsonStart = output.indexOf('{')
        val jsonEnd = output.lastIndexOf('}')
        if (jsonStart < 0 || jsonEnd <= jsonStart) return emptyList()
        val playlistInfo = JSONObject(output.substring(jsonStart, jsonEnd + 1))
        val entries = playlistInfo.optJSONArray("entries") ?: return emptyList()
        val isYoutube = url.contains("youtube.com", true) || url.contains("youtu.be", true)
        return (0 until entries.length()).map { index ->
            val entry = entries.optJSONObject(index)
            if (entry == null) return@map QueueMedia("Unavailable track ${index + 1}", "")
            val id = entry.optString("id").takeIf { it.isNotBlank() && it != "null" }
            val directUrl = entry.optString("webpage_url").takeIf { it.startsWith("http", true) }
                ?: entry.optString("url").takeIf { it.startsWith("http", true) }
                ?: if (isYoutube && id != null) "https://www.youtube.com/watch?v=$id" else ""
            val title = entry.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: "Track ${index + 1}"
            QueueMedia(title, directUrl)
        }
    }

    private fun looksLikePlaylist(url: String): Boolean =
        url.contains("list=", ignoreCase = true) ||
            url.contains("/playlist", ignoreCase = true) ||
            url.contains("mix=", ignoreCase = true)

    private fun guessTitle(url: String): String = runCatching {
        Uri.parse(url).getQueryParameter("v")?.let { "YouTube video $it" }
    }.getOrNull() ?: "Video"

    private fun finishDownloadSession(
        session: DownloadSession,
        message: String,
        success: Boolean,
        canceled: Boolean = false
    ) {
        if (activeSession !== session) return
        savedLink = ""
        statusMessage = if (canceled) "" else message
        updateStatus(statusMessage, if (success) 100 else 0)
        when {
            canceled && session.isPlaylist && session.downloadedCount > 0 -> toast("Canceled · ${session.downloadedCount} of ${session.queue.size} songs downloaded")
            canceled -> toast("Download canceled")
            success && session.isPlaylist -> toast(message)
            success -> toast("Download complete · saved to ${saveFolderLabel()}")
        }
        if (success && (!session.isPlaylist || session.downloadedCount >= session.queue.size)) performAppHaptic()
        activeProcessId = null
        isDownloading = false
        activeSession = null
        setBusy(false)
        homeQueueSection?.visibility = View.GONE
        DownloadNotificationService.stop(this)
        if (!activityDestroyed) queueEngineUpdateCheck()
        if (activityDestroyed) worker.shutdown()
    }

    private fun handleDownloadInterruption(session: DownloadSession, currentTempDir: File?) {
        if (session.cancelled) {
            currentTempDir?.deleteRecursively()
            cleanupSessionDirectories(session)
            runOnUiThread {
                if (activeSession === session) finishDownloadSession(session, "Download canceled", success = false, canceled = true)
            }
        } else if (session.paused) {
            runOnUiThread {
                if (activeSession === session) {
                    updateStatus("Download paused", session.currentPercent)
                    setBusy(true)
                    renderHomeQueue()
                    publishDownloadNotification(session, "Paused")
                }
            }
        }
    }

    private fun cleanupSessionDirectories(session: DownloadSession) {
        (0 until maxOf(session.queue.size, 1)).forEach { index ->
            File(cacheDir, "audio-${session.id}-$index").deleteRecursively()
        }
    }

    private fun publishDownloadNotification(session: DownloadSession, title: String, status: String = "") {
        DownloadNotificationService.update(
            this,
            session.isPlaylist,
            session.downloadedCount,
            session.queue.size.coerceAtLeast(1),
            session.currentPercent,
            title,
            status,
            session.paused
        )
    }

    private fun renderHomeQueue() {
        val session = activeSession
        homeQueueSection?.visibility = if (session?.isPlaylist == true) View.VISIBLE else View.GONE
        if (session?.isPlaylist == true) {
            val queueText = session.queue.mapIndexed { index, item ->
                val name = suggestedQueueFilename(item.title, session.video)
                val state = when {
                    index < session.currentIndex && session.failedItems[index] == null -> "✓"
                    session.failedItems[index] != null -> "!"
                    index == session.currentIndex && session.paused -> "Ⅱ"
                    index == session.currentIndex && isDownloading -> "↓"
                    else -> "·"
                }
                "$state  $name"
            }.joinToString("\n")
            homeQueueList?.text = queueText
        }
    }

    private fun renderHomeDownloadLists() {
        renderHomeQueue()
        val list = sessionDownloadsList ?: return
        val visibleItems = downloadedThisSession.filterNot { it.isArchived }
        if (visibleItems.isEmpty()) {
            list.removeAllViews()
            sessionCheckboxes.clear()
            sessionCards.clear()
            list.addView(text("Nothing downloaded in this session yet.", 13, secondaryText()).apply {
                tag = EMPTY_SESSION_TAG
            })
            updateSessionSelectionControls()
            return
        }
        if (list.childCount == 1 && list.getChildAt(0).tag == EMPTY_SESSION_TAG) list.removeAllViews()
        val visibleUris = visibleItems.mapTo(hashSetOf()) { it.uri }
        (sessionCards.keys - visibleUris).forEach { uri ->
            sessionCards.remove(uri)?.let(list::removeView)
            sessionCheckboxes.remove(uri)
        }
        visibleItems.forEach(::appendSessionDownloadItem)
        updateSessionSelectionControls()
    }

    private fun appendSessionDownloadItem(item: HistoryItem) {
        val list = sessionDownloadsList ?: return
        if (list.findViewWithTag<View>(item.uri) != null) return
        if (list.childCount == 1 && list.getChildAt(0).tag == EMPTY_SESSION_TAG) list.removeAllViews()
        val itemCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(8), dp(10))
            background = rounded(fieldColor(), dp(18))
            tag = item.uri
            isClickable = true
            setOnClickListener {
                if (sessionSelectionMode) toggleSessionItem(item.uri) else openHistoryItem(item)
            }
            setOnLongClickListener {
                parent?.requestDisallowInterceptTouchEvent(true)
                selectedSessionUris.add(item.uri)
                setSessionSelectionMode(true)
                sessionCheckboxes[item.uri]?.isChecked = true
                updateSessionSelectionControls()
                true
            }
        }
        sessionCards[item.uri] = itemCard
        val artwork = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(surfaceColor(), dp(12))
            clipToOutline = true
            contentDescription = "Album art for ${item.title}"
            setImageResource(R.drawable.ic_launcher_foreground)
        }
        loadHistoryArtwork(artwork, item.artworkPath, item.uri)
        itemCard.addView(artwork, LinearLayout.LayoutParams(dp(52), dp(52)).apply { rightMargin = dp(10) })
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        details.addView(text(item.title, 14, primaryText(), bold = true).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        details.addView(text("${item.format} · ${item.quality} · ${formatBytes(item.sizeBytes)}", 11, secondaryText()).apply {
            setPadding(0, dp(3), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        itemCard.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        val checkbox = CheckBox(this).apply {
            buttonTintList = ColorStateList.valueOf(accentColor)
            isChecked = item.uri in selectedSessionUris
            visibility = if (sessionSelectionMode) View.VISIBLE else View.INVISIBLE
            contentDescription = "Select ${item.title}"
            setOnCheckedChangeListener { _, checked ->
                if (checked != (item.uri in selectedSessionUris)) toggleSessionItem(item.uri)
            }
        }
        sessionCheckboxes[item.uri] = checkbox
        itemCard.addView(checkbox, LinearLayout.LayoutParams(dp(42), dp(48)))
        list.addView(itemCard, LinearLayout.LayoutParams(-1, -2).apply {
            if (list.childCount > 0) topMargin = dp(8)
        })
        updateSessionSelectionControls()
    }

    private fun suggestedQueueFilename(title: String, video: Boolean): String {
        val base = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(140).ifBlank { "media" }
        return "$base.${if (video) "mp4" else "mp3"}"
    }

    private fun publicProgressMessage(video: Boolean, percent: Int, line: String): String {
        val processing = line.contains("ffmpeg", ignoreCase = true) ||
            line.contains("postprocess", ignoreCase = true) ||
            line.contains("thumbnail", ignoreCase = true) ||
            line.contains("extractaudio", ignoreCase = true) ||
            line.contains("metadata", ignoreCase = true)
        if (processing) return if (video) "Adding video details…" else "Adding song details and cover art…"
        val kind = if (video) "video" else "MP3"
        return if (percent > 0) "Downloading $kind · $percent%" else "Downloading $kind…"
    }

    private fun usefulError(error: Exception): String {
        val lines = error.message.orEmpty().lineSequence()
            .map { it.trim() }
            .filter {
                it.isNotBlank() &&
                    !it.startsWith("WARNING: Your yt-dlp version") &&
                    !it.startsWith("It is strongly recommended") &&
                    !it.startsWith("Run \"yt-dlp")
            }
            .toList()
        return (lines.lastOrNull() ?: "Please check your connection and try again.").take(240)
    }

    private fun queueEngineUpdateCheck() {
        worker.execute {
            try {
                updateDownloaderIfDue(null)
            } catch (error: Exception) {
                android.util.Log.w("AudioDrop", "Background yt-dlp update check failed", error)
            }
        }
    }

    private fun updateDownloaderIfDue(processId: String?) {
        val now = System.currentTimeMillis()
        val lastCheck = preferences.getLong(KEY_ENGINE_UPDATE, 0L)
        val oneWeek = 7L * 24L * 60L * 60L * 1000L
        if (now - lastCheck < oneWeek) return
        if (processId != null) {
            runOnUiThread {
                if (activeProcessId == processId) updateStatus("Updating audio engine…", 0)
            }
        }
        YoutubeDL.getInstance().updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.NIGHTLY)
        preferences.edit().putLong(KEY_ENGINE_UPDATE, now).apply()
    }

    private fun saveMedia(source: File, mimeType: String): Uri {
        val customFolder = preferences.getString(KEY_SAVE_FOLDER, null)?.let(Uri::parse)
        return if (customFolder == null) saveToDownloads(source, mimeType) else saveToTree(source, customFolder, mimeType)
    }

    private fun saveToDownloads(source: File, mimeType: String): Uri {
        val outputUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeMediaName(source))
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AudioDrop")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: error("Could not create the file in Downloads")
        try {
            copyFile(source, outputUri)
            val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (contentResolver.update(outputUri, ready, null, null) == 0) {
                error("Could not finish saving the MP3")
            }
            return outputUri
        } catch (error: Exception) {
            contentResolver.delete(outputUri, null, null)
            throw error
        }
    }

    private fun saveToTree(source: File, treeUri: Uri, mimeType: String): Uri {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        val outputUri = DocumentsContract.createDocument(contentResolver, parent, mimeType, safeMediaName(source))
            ?: error("Could not create the file in the selected folder")
        try {
            copyFile(source, outputUri)
            return outputUri
        } catch (error: Exception) {
            DocumentsContract.deleteDocument(contentResolver, outputUri)
            throw error
        }
    }

    private fun copyFile(source: File, destination: Uri) {
        val output = contentResolver.openOutputStream(destination, "w")
            ?: error("Could not open the selected save folder")
        output.use { stream -> source.inputStream().use { it.copyTo(stream) } }
    }

    private fun safeMediaName(source: File): String {
        val extension = source.extension.lowercase(Locale.ROOT).take(8)
        val base = source.nameWithoutExtension
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(140)
            .ifBlank { "audio" }
        return if (extension.isBlank()) base else "$base.$extension"
    }

    private fun saveHistoryArtwork(tempDir: File, media: File): String? = runCatching {
        val imageExtensions = setOf("jpg", "jpeg", "png", "webp")
        val thumbnail = tempDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in imageExtensions }
            ?.minByOrNull { if (it.nameWithoutExtension.equals(media.nameWithoutExtension, ignoreCase = true)) 0 else 1 }
        val imageBytes = thumbnail?.takeIf { it.length() > 0 }?.readBytes()
            ?: readEmbeddedArtwork(media)
            ?: return@runCatching null
        val extension = thumbnail?.extension?.lowercase(Locale.ROOT)?.ifBlank { "jpg" } ?: "jpg"
        val directory = File(filesDir, "history_artwork").apply { mkdirs() }
        val destination = File(directory, "${UUID.randomUUID()}.$extension")
        destination.writeBytes(imageBytes)
        destination.absolutePath
    }.getOrNull()

    private fun readEmbeddedArtwork(media: File): ByteArray? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(media.absolutePath)
            retriever.embeddedPicture
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private fun deleteHistoryArtwork(item: HistoryItem) {
        item.artworkPath?.let { path -> runCatching { File(path).delete() } }
    }

    private fun addHistory(
        newItem: HistoryItem
    ) {
        val items = readHistory().toMutableList()
        items.add(0, newItem)
        val json = JSONArray()
        items.take(100).forEach { item ->
            json.put(JSONObject().apply {
                put("title", item.title)
                put("uri", item.uri)
                put("format", item.format)
                put("mimeType", item.mimeType)
                put("quality", item.quality)
                put("sizeBytes", item.sizeBytes)
                item.artworkPath?.let { put("artworkPath", it) }
                put("savedAt", item.savedAt)
                put("archived", item.isArchived)
            })
        }
        if (!preferences.edit().putString(KEY_HISTORY, json.toString()).commit()) {
            android.util.Log.e("AudioDrop", "Could not persist the latest download history")
        }
    }

    private fun readHistory(): List<HistoryItem> {
        val json = runCatching { JSONArray(preferences.getString(KEY_HISTORY, "[]")) }
            .getOrElse { JSONArray() }
        return (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("title")
            val uri = item.optString("uri")
            if (title.isBlank() || uri.isBlank()) null else {
                val format = item.optString("format", "MP3")
                val rawQuality = item.opt("quality")
                val quality = when {
                    rawQuality is String -> rawQuality
                    format == "MP3" -> "${rawQuality?.toString()?.toIntOrNull() ?: 320} kbps"
                    (rawQuality?.toString()?.toIntOrNull() ?: 0) > 0 -> "${rawQuality.toString().toInt()}p"
                    else -> "Best available"
                }
                HistoryItem(
                    title,
                    uri,
                    format,
                    item.optString("mimeType", "audio/mpeg"),
                    quality,
                    item.optLong("sizeBytes", 0L),
                    item.optString("artworkPath").takeIf { it.isNotBlank() },
                    item.optLong("savedAt", 0L),
                    item.optBoolean("archived", false)
                )
            }
        }
    }

    private fun historyFileSize(item: HistoryItem): Long {
        if (item.sizeBytes > 0L) return item.sizeBytes
        return runCatching {
            contentResolver.openAssetFileDescriptor(Uri.parse(item.uri), "r")?.use { it.length }
        }.getOrNull()?.takeIf { it > 0L } ?: 0L
    }

    private fun formatBytes(size: Long): String {
        if (size <= 0L) return "Size unknown"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = size.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        return if (unit == 0) "$size ${units[unit]}" else String.format(Locale.ROOT, "%.1f %s", value, units[unit])
    }

    private fun openHistoryItem(item: HistoryItem) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(item.uri), item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            toast("No media player is available")
        }
    }

    private fun confirmDeleteSelectedHistory() {
        val selected = readHistory().filter { it.uri in selectedHistoryUris }
        if (selected.isEmpty()) return
        confirmDeleteItems(selected, "Remove ${selected.size} ${if (selected.size == 1) "item" else "items"} from Downloads?") {
            deleteSelectedMedia(selected)
        }
    }

    private fun confirmDeleteItems(items: List<HistoryItem>, title: String, action: () -> Unit) {
        val deletesFiles = preferences.getBoolean(KEY_DELETE_DEVICE_MEDIA, false)
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(if (deletesFiles) "The item will be removed from Downloads and deleted from your device." else "The item will be removed from Downloads. The saved file will stay on your device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete", null)
            .create()
        showStyledDialog(dialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                dialog.dismiss()
                action()
            }
        }
    }

    private fun deleteSelectedMedia(items: List<HistoryItem>) {
        val urisToDelete = items.map { it.uri }.toSet()
        val deleteDeviceFiles = preferences.getBoolean(KEY_DELETE_DEVICE_MEDIA, false)
        worker.execute {
            val deleted = linkedSetOf<String>()
            items.forEach { item ->
                if (!deleteDeviceFiles || deleteSavedMedia(item)) deleted.add(item.uri)
            }
            runOnUiThread {
                val failed = urisToDelete - deleted
                if (deleteDeviceFiles) items.filter { it.uri in deleted }.forEach(::deleteHistoryArtwork)
                writeHistory(readHistory().filterNot { it.uri in deleted })
                downloadedThisSession.removeIf { it.uri in deleted }
                selectedHistoryUris.retainAll(failed)
                selectedSessionUris.retainAll(failed)
                if (selectedHistoryUris.isEmpty()) setHistorySelectionMode(false)
                if (selectedSessionUris.isEmpty()) setSessionSelectionMode(false)
                if (selectedTab == Tab.HISTORY && historyListContainer != null) {
                    populateHistoryList(visibleHistoryItems(), historyScrollView?.scrollY ?: 0, animate = true)
                } else renderScreen()
                renderHomeDownloadLists()
                toast(if (failed.isEmpty()) "Removed ${deleted.size} item${if (deleted.size == 1) "" else "s"} from Downloads"
                else "Removed ${deleted.size}; ${failed.size} could not be removed")
            }
        }
    }

    private fun deleteSavedMedia(item: HistoryItem): Boolean {
        val uri = Uri.parse(item.uri)
        return if (DocumentsContract.isDocumentUri(this, uri)) {
            runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
                .getOrElse { runCatching { contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false) }
        } else runCatching { contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
    }

    private fun confirmClearHistory() {
        val items = readHistory()
        if (items.isEmpty()) return
        confirmDeleteItems(items, "Clear all downloads?") {
            hideClearHistory()
            hideArchiveFolder()
            deleteSelectedMedia(items)
        }
    }

    private fun confirmClearArchive() {
        val archived = readHistory().filter { it.isArchived }
        if (archived.isEmpty()) return
        confirmDeleteItems(archived, "Clear archived items?") {
            hideClearHistory()
            hideArchiveFolder()
            deleteSelectedMedia(archived)
        }
    }

    private fun writeHistory(items: List<HistoryItem>) {
        val json = JSONArray()
        items.forEach { item ->
            json.put(JSONObject().apply {
                put("title", item.title)
                put("uri", item.uri)
                put("format", item.format)
                put("mimeType", item.mimeType)
                put("quality", item.quality)
                put("sizeBytes", item.sizeBytes)
                item.artworkPath?.let { put("artworkPath", it) }
                put("savedAt", item.savedAt)
                put("archived", item.isArchived)
            })
        }
        if (!preferences.edit().putString(KEY_HISTORY, json.toString()).commit()) {
            android.util.Log.e("AudioDrop", "Could not persist download history")
        }
    }

    private fun chooseSaveFolder() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivityForResult(picker, REQUEST_SAVE_FOLDER)
    }

    private fun saveFolderLabel(): String {
        val uri = preferences.getString(KEY_SAVE_FOLDER, null)?.let(Uri::parse)
            ?: return "Downloads / AudioDrop"
        val name = runCatching {
            DocumentsContract.getTreeDocumentId(uri).substringAfter(':').substringAfterLast('/')
        }.getOrNull().orEmpty()
        return if (name.isBlank()) "Selected folder" else name
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData? = clipboard.primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank()) {
            toast("Clipboard is empty")
            return
        }
        linkInput.setText(findSupportedLink(text) ?: text.trim())
        linkInput.setSelection(linkInput.text.length)
    }

    private fun acceptSharedLink(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND) return
        val sharedText = incoming.getStringExtra(Intent.EXTRA_TEXT)
            ?: (incoming.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.toString()
            ?: return
        val link = findSupportedLink(sharedText) ?: return
        rememberHomeForm()
        selectedTab = Tab.HOME
        savedLink = link
        renderScreen()
        showSharedDownloadPrompt()
    }

    private fun findSupportedLink(text: String): String? {
        val match = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).find(text)?.value ?: return null
        return match.trimEnd('.', ',', ')', ']', '}', '"', '\'')
            .takeIf(::isSupportedUrl)
    }

    private fun isSupportedUrl(value: String): Boolean {
        val uri = Uri.parse(value)
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }

    private fun showSharedDownloadPrompt() {
        val choices = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Choose what to download")
            .setMessage("Save the full video or audio only.")
            .setView(choices)
            .setNegativeButton("Cancel", null)
            .create()
        choices.addView(actionButton("Full video", filled = true) {
            dialog.dismiss()
            showVideoQualityDialog()
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) })
        choices.addView(actionButton("Audio only · MP3", filled = false) {
            dialog.dismiss()
            startDownload(video = false)
        }, LinearLayout.LayoutParams(-1, dp(50)))
        showStyledDialog(dialog)
    }

    private fun showAudioQualityDialog() {
        val options = listOf("320 kbps  ·  Default", "256 kbps", "192 kbps", "128 kbps")
        val choices = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        var dialog: AlertDialog? = null
        options.forEachIndexed { index, label ->
            val selected = index == savedQualityIndex
            val option = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(16), 0)
                background = rounded(if (selected) accentTint() else surfaceColor(), dp(16), if (selected) accentColor else borderColor())
                isClickable = true
                setOnClickListener {
                    savedQualityIndex = index
                    preferences.edit().putInt(KEY_AUDIO_QUALITY, index).apply()
                    updateAudioQualityLabel()
                    dialog?.dismiss()
                }
            }
            option.addView(text(label, 15, primaryText(), bold = selected).apply {
                setPadding(0, 0, dp(8), 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            option.addView(text(if (selected) "✓" else "", 19, accentColor, bold = true), LinearLayout.LayoutParams(dp(24), -2))
            choices.addView(option, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(6) })
        }
        dialog = AlertDialog.Builder(this)
            .setTitle("MP3 quality")
            .setView(choices)
            .setNegativeButton("Done", null)
            .create()
        showStyledDialog(dialog)
    }

    private fun showVideoQualityDialog() {
        val options = listOf(
            "1080p  ·  Default",
            "720p",
            "480p",
            "360p",
            "Best available"
        )
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(6), dp(24), dp(8))
        }
        content.addView(text(
            "1080p is selected by default. If it is not available, the highest available quality is used.",
            13,
            secondaryText()
        ).apply { setPadding(0, 0, 0, dp(12)) })
        val selector = Spinner(this).apply {
            adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_item, options) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                    (convertView as? TextView ?: TextView(this@MainActivity)).apply {
                        text = options[position]
                        textSize = 15f
                        setTextColor(primaryText())
                        setPadding(dp(12), 0, dp(8), 0)
                        includeFontPadding = false
                        gravity = Gravity.CENTER_VERTICAL
                    }

                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                    (convertView as? TextView ?: TextView(this@MainActivity)).apply {
                        text = options[position]
                        textSize = 15f
                        setTextColor(primaryText())
                        setBackgroundColor(surfaceColor())
                        setPadding(dp(16), dp(14), dp(16), dp(14))
                    }
            }
            setPopupBackgroundDrawable(rounded(surfaceColor(), dp(16)))
            background = rounded(fieldColor(), dp(14), borderColor())
            setPadding(dp(8), 0, dp(8), 0)
            setSelection(savedVideoQualityIndex.coerceIn(0, options.lastIndex))
        }
        content.addView(selector, LinearLayout.LayoutParams(-1, dp(52)))
        val dialog = AlertDialog.Builder(this)
            .setTitle("Video quality")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Download now", null)
            .create()
        showStyledDialog(dialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                savedVideoQualityIndex = selector.selectedItemPosition.coerceIn(0, VIDEO_QUALITY_HEIGHTS.lastIndex)
                preferences.edit().putInt(KEY_VIDEO_QUALITY, savedVideoQualityIndex).apply()
                val maxHeight = VIDEO_QUALITY_HEIGHTS[savedVideoQualityIndex]
                dialog.dismiss()
                startDownload(video = true, maxVideoHeight = maxHeight)
            }
        }
    }

    private fun audioQualityLabel(index: Int): String =
        if (index == 0) "${AUDIO_QUALITY_KBPS[0]} kbps  ·  Default" else "${AUDIO_QUALITY_KBPS[index.coerceIn(0, AUDIO_QUALITY_KBPS.lastIndex)]} kbps"

    private fun updateAudioQualityLabel() {
        if (::qualityPicker.isInitialized) qualityPicker.text = audioQualityLabel(savedQualityIndex)
    }

    private fun showStyledDialog(dialog: AlertDialog, onShown: (() -> Unit)? = null) {
        dialog.window?.apply {
            setWindowAnimations(R.style.DialogWindowAnimation)
        }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(rounded(surfaceColor(), dp(26)))
                addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0.38f)
            }
            val androidTitleId = resources.getIdentifier("alertTitle", "id", "android")
            val dialogTitle = if (androidTitleId != 0) dialog.findViewById<TextView>(androidTitleId)
                else dialog.findViewById<TextView>(android.R.id.title)
            dialogTitle?.apply {
                setTextColor(primaryText())
                textSize = 20f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(secondaryText())
            val listId = resources.getIdentifier("select_dialog_listview", "id", "android")
            dialog.findViewById<ListView>(listId)?.let { list ->
                for (index in 0 until list.childCount) {
                    (list.getChildAt(index) as? TextView)?.setTextColor(primaryText())
                }
            }
            listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
                .forEach { which -> dialog.getButton(which)?.setTextColor(accentColor) }
            onShown?.invoke()
        }
        dialog.show()
    }

    private fun navigateTo(tab: Tab) {
        rememberHomeForm()
        if (tab != selectedTab) performAppHaptic()
        if (tab != selectedTab) {
            if (sessionSelectionMode) {
                selectedSessionUris.clear()
                setSessionSelectionMode(false)
            }
            if (historySelectionMode) {
                selectedHistoryUris.clear()
                setHistorySelectionMode(false)
            }
        }
        selectedTab = tab
        renderScreen()
    }

    private fun adjacentTab(direction: Int): Tab? {
        val tabs = listOf(Tab.HOME, Tab.HISTORY, Tab.SETTINGS)
        val current = tabs.indexOf(selectedTab)
        if (current < 0) return null
        return tabs.getOrNull(current + direction)
    }

    private fun isInsidePageHost(rawX: Float, rawY: Float): Boolean {
        if (!::pageHost.isInitialized) return false
        val location = IntArray(2)
        pageHost.getLocationOnScreen(location)
        return rawX >= location[0] && rawX <= location[0] + pageHost.width &&
            rawY >= location[1] && rawY <= location[1] + pageHost.height
    }

    private fun finishSwipe(commit: Boolean) {
        val incoming = nextPageView ?: run { swipeActive = false; return }
        val outgoing = currentPageView
        val host = pageHost
        val target = swipeTargetTab
        val width = host.width.toFloat().coerceAtLeast(1f)
        val distance = swipeDistanceX
        val duration = 190L
        val generation = ++pageTransitionGeneration
        pageTransitioning = true
        outgoing.animate().cancel()
        incoming.animate().cancel()
        if (commit && target != null) {
            val finalX = if (distance < 0) -width else width
            outgoing.animate().translationX(finalX).setDuration(duration)
                .setInterpolator(DecelerateInterpolator(1.4f)).withEndAction {
                    if (generation != pageTransitionGeneration || pageHost !== host) return@withEndAction
                    host.removeView(outgoing)
                    incoming.translationX = 0f
                    currentPageView = incoming
                    if (nextPageView === incoming) nextPageView = null
                    if (sessionSelectionMode) {
                        selectedSessionUris.clear()
                        setSessionSelectionMode(false)
                    }
                    if (historySelectionMode) {
                        selectedHistoryUris.clear()
                        setHistorySelectionMode(false)
                    }
                    selectedTab = target
                    performAppHaptic()
                    swipeTargetTab = null
                    swipeActive = false
                    pageTransitioning = false
                    refreshBottomNavigation()
                    if (selectedTab == Tab.HISTORY) {
                        populateHistoryList(visibleHistoryItems(), historyScrollView?.scrollY ?: 0, animate = false)
                    }
                    if (selectedTab == Tab.HOME) {
                        linkInput.setText(savedLink)
                        updateAudioQualityLabel()
                        if (isDownloading) setBusy(true)
                    }
                }.start()
            incoming.animate().translationX(0f).setDuration(duration)
                .setInterpolator(DecelerateInterpolator(1.4f)).start()
        } else {
            outgoing.animate().translationX(0f).setDuration(duration)
                .setInterpolator(DecelerateInterpolator(1.4f)).withEndAction {
                    if (generation != pageTransitionGeneration || pageHost !== host) return@withEndAction
                    host.removeView(incoming)
                    nextPageView = null
                    swipeTargetTab = null
                    swipeActive = false
                    pageTransitioning = false
                    renderScreen()
                }.start()
            val offscreen = if (distance < 0) width else -width
            incoming.animate().translationX(offscreen).setDuration(duration)
                .setInterpolator(DecelerateInterpolator(1.4f)).start()
        }
    }

    private fun refreshBottomNavigation() {
        if (!::navigationBar.isInitialized) return
        populateBottomNavigation(navigationBar)
        navigationBar.invalidate()
    }

    private fun rememberHomeForm() {
        if (::linkInput.isInitialized) savedLink = linkInput.text.toString()
        savedQualityIndex = savedQualityIndex.coerceIn(0, AUDIO_QUALITY_KBPS.lastIndex)
    }

    private fun setBusy(busy: Boolean) {
        if (!::downloadButton.isInitialized) return
        downloadButton.isEnabled = !busy
        videoButton.isEnabled = !busy
        downloadButton.alpha = if (busy) 0.55f else 1f
        videoButton.alpha = if (busy) 0.55f else 1f
        if (::linkInput.isInitialized) linkInput.isEnabled = !busy
        if (::qualityPicker.isInitialized) qualityPicker.isEnabled = !busy
        if (::cancelButton.isInitialized) cancelButton.visibility = if (busy) View.VISIBLE else View.GONE
        if (::pauseButton.isInitialized) {
            pauseButton.visibility = if (busy) View.VISIBLE else View.GONE
            pauseButton.text = if (activeSession?.paused == true) "Resume" else "Pause"
            pauseButton.isEnabled = busy
        }
        if (::progressBar.isInitialized) {
            progressBar.visibility = if (busy) View.VISIBLE else View.GONE
            progressBar.isIndeterminate = busy && statusProgress == 0
        }
        if (::statusText.isInitialized) {
            statusText.visibility = if (busy || statusMessage.isNotBlank()) View.VISIBLE else View.GONE
        }
    }

    private fun confirmCancelDownload() {
        if (activeSession == null) return
        val dialog = AlertDialog.Builder(this)
            .setTitle("Cancel download?")
            .setMessage("This stops the current download and any queued songs. Files already saved will stay in History.")
            .setNegativeButton("Keep downloading", null)
            .setPositiveButton("Cancel download", null)
            .create()
        showStyledDialog(dialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(DELETE_RED)
                setOnClickListener {
                    dialog.dismiss()
                    cancelDownloadConfirmed()
                }
            }
        }
    }

    private fun cancelDownloadConfirmed() {
        val session = activeSession ?: return
        session.cancelled = true
        session.paused = false
        val processId = session.currentProcessId ?: activeProcessId
        stopYoutubeProcessAsync(processId) {
            cleanupSessionDirectories(session)
        }
        val summary = if (session.isPlaylist && session.downloadedCount > 0) {
            "Canceled · ${session.downloadedCount} of ${session.queue.size} songs downloaded"
        } else ""
        finishDownloadSession(session, summary, success = false, canceled = true)
    }

    private fun togglePauseDownload() {
        val session = activeSession ?: return
        if (session.paused) {
            session.paused = false
            activeProcessId = null
            isDownloading = true
            setBusy(true)
            updateStatus("Resuming download…", session.currentPercent)
            publishDownloadNotification(session, "Resuming", "Resuming")
            worker.execute { runDownloadSession(session) }
            return
        }
        session.paused = true
        val processId = session.currentProcessId
        if (processId != null) stopYoutubeProcessAsync(processId)
        updateStatus("Download paused", session.currentPercent)
        setBusy(true)
        renderHomeQueue()
        publishDownloadNotification(session, "Paused")
    }

    private fun stopYoutubeProcessAsync(processId: String?, whenNoProcess: (() -> Unit)? = null) {
        Thread({
            if (processId == null) {
                whenNoProcess?.invoke()
            } else {
                runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
            }
        }, "AudioDropProcessStop").apply {
            isDaemon = true
            start()
        }
    }

    private fun updateStatus(message: String, progress: Int) {
        statusMessage = message
        statusProgress = progress
        if (::statusText.isInitialized) {
            statusText.text = message
            statusText.visibility = if (isDownloading || message.isNotBlank()) View.VISIBLE else View.GONE
        }
        if (::progressBar.isInitialized) {
            progressBar.progress = progress
            progressBar.isIndeterminate = isDownloading && progress == 0
        }
    }

    private fun chooseAppearance(mode: String) {
        rememberHomeForm()
        appearanceMode = mode
        darkMode = isDarkAppearance()
        preferences.edit().putString(KEY_APPEARANCE, mode).apply()
        renderScreen()
    }

    private fun isDarkAppearance(): Boolean = when (appearanceMode) {
        MODE_BLACK -> true
        MODE_LIGHT -> false
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun mediaMimeType(extension: String): String = when (extension.lowercase(Locale.ROOT)) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "opus" -> "audio/opus"
        "wav" -> "audio/wav"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "ts" -> "video/mp2t"
        else -> "application/octet-stream"
    }

    private fun actionButton(caption: String, filled: Boolean, action: () -> Unit) = Button(this).apply {
        text = caption
        isAllCaps = false
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (filled) Color.WHITE else accentColor)
        background = rounded(if (filled) accentColor else accentTint(), dp(18))
        elevation = 0f
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(12), 0, dp(12), 0)
        setOnClickListener { action() }
    }

    private fun headerIconButton(icon: Int, description: String, action: () -> Unit) = ImageView(this).apply {
        setImageResource(icon)
        setColorFilter(primaryText())
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        background = rounded(if (darkMode) surfaceColor() else Color.TRANSPARENT, dp(16))
        contentDescription = description
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = rounded(surfaceColor(), dp(26))
        elevation = if (darkMode) 0f else dp(1).toFloat()
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
    }

    private fun label(caption: String) = text(caption, 11, secondaryText(), bold = true).apply {
        letterSpacing = 0.08f
        setPadding(0, 0, 0, dp(8))
    }

    private fun text(caption: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = caption
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun oval(color: Int, strokeWidth: Int, strokeColor: Int?) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        if (strokeColor != null) setStroke(strokeWidth, strokeColor)
    }

    private fun applyWindowTheme() {
        window.statusBarColor = pageColor()
        window.navigationBarColor = pageColor()
        window.decorView.systemUiVisibility = if (darkMode) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    private fun pageColor() = if (darkMode) Color.BLACK else 0xFFF3F4F7.toInt()
    private fun surfaceColor() = if (darkMode) 0xFF151517.toInt() else Color.WHITE
    private fun fieldColor() = if (darkMode) 0xFF202124.toInt() else 0xFFF8F9FB.toInt()
    private fun primaryText() = if (darkMode) 0xFFF3F4F7.toInt() else 0xFF15171C.toInt()
    private fun secondaryText() = if (darkMode) 0xFFA8ABB4.toInt() else 0xFF717681.toInt()
    private fun borderColor() = if (darkMode) 0xFF303135.toInt() else 0xFFE0E2E7.toInt()
    private fun accentTint() = Color.argb(28, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val EMPTY_SESSION_TAG = "empty_download_session"
        private const val REQUEST_POST_NOTIFICATIONS = 4103
        private const val REQUEST_SAVE_FOLDER = 4102
        private const val HISTORY_ROOT_TAG = "audiodrop_history_root"
        private const val PAGE_ROOT_TAG = "audiodrop_page_root"
        private const val PAGE_HEADER_TAG = "audiodrop_page_header"
        private const val HOME_HEADER_TAG = "audiodrop_home_header"
        private const val SETTINGS_HEADER_TAG = "audiodrop_settings_header"
        private const val HISTORY_HEADER_EXPANDED_DP = 144
        private const val HISTORY_HEADER_COLLAPSED_DP = 60
        private const val HISTORY_COLLAPSE_RANGE_DP = 88
        private const val HOME_HEADER_EXPANDED_DP = 184
        private const val SETTINGS_HEADER_EXPANDED_DP = 146
        private const val PAGE_HEADER_COLLAPSED_DP = 62
        private const val HOME_HEADER_COLLAPSE_RANGE_DP = 118
        private const val SETTINGS_HEADER_COLLAPSE_RANGE_DP = 82
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_APPEARANCE = "appearance_mode"
        private const val KEY_ACCENT = "accent_color"
        private const val KEY_SAVE_FOLDER = "save_folder"
        private const val KEY_ENGINE_UPDATE = "last_engine_update"
        private const val KEY_HISTORY = "download_history"
        private const val KEY_HISTORY_SORT = "history_sort_mode"
        private const val KEY_VIDEO_QUALITY = "video_quality_index"
        private const val KEY_DELETE_DEVICE_MEDIA = "delete_device_media"
        private const val KEY_AUDIO_QUALITY = "audio_quality_index"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        private const val KEY_HAPTIC_STRENGTH = "haptic_strength"
        private const val KEY_SWIPE_NAV_ENABLED = "swipe_navigation_enabled"
        private const val KEY_HEADER_BLUR_ENABLED = "header_blur_enabled"
        private const val KEY_HEADER_BLUR_ALPHA = "header_blur_alpha"
        private const val BUY_ME_A_COFFEE_URL = "https://www.buymeacoffee.com"
        private const val MODE_LIGHT = "light"
        private const val MODE_BLACK = "black"
        private const val MODE_SYSTEM = "system"
        private const val HISTORY_SORT_NEWEST = "newest"
        private const val HISTORY_SORT_OLDEST = "oldest"
        private const val HISTORY_SORT_TITLE = "title"
        private const val ACCENT_BLUE = 0xFF0381FE.toInt()
        private const val DELETE_RED = 0xFFE53935.toInt()
        private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "webm", "mkv", "mov", "avi", "ts")
        private val VIDEO_QUALITY_HEIGHTS = listOf<Int?>(1080, 720, 480, 360, null)
        private val AUDIO_QUALITY_KBPS = listOf(320, 256, 192, 128)
        private val ACCENT_OPTIONS = listOf(
            ACCENT_BLUE,
            0xFF7355D9.toInt(),
            0xFF168C83.toInt(),
            0xFFE46D59.toInt(),
            0xFFD14F87.toInt()
        )
    }
}
