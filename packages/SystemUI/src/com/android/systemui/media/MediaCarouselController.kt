package com.android.systemui.media

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.provider.Settings.ACTION_MEDIA_CONTROLS_SETTINGS
import android.util.Log
import android.util.MathUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.VisibleForTesting
import com.android.systemui.R
import com.android.systemui.classifier.FalsingCollector
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.qs.PageIndicator
import com.android.systemui.shared.system.SysUiStatsLog
import com.android.systemui.statusbar.notification.collection.legacy.VisualStabilityManager
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.util.Utils
import com.android.systemui.util.animation.UniqueObjectHostView
import com.android.systemui.util.animation.requiresRemeasuring
import com.android.systemui.util.concurrency.DelayableExecutor
import java.util.TreeMap
import javax.inject.Inject
import javax.inject.Provider

private const val TAG = "MediaCarouselController"
private val settingsIntent = Intent().setAction(ACTION_MEDIA_CONTROLS_SETTINGS)

/**
 * Class that is responsible for keeping the view carousel up to date.
 * This also handles changes in state and applies them to the media carousel like the expansion.
 */
@SysUISingleton
class MediaCarouselController @Inject constructor(
    private val context: Context,
    private val mediaControlPanelFactory: Provider<MediaControlPanel>,
    private val visualStabilityManager: VisualStabilityManager,
    private val mediaHostStatesManager: MediaHostStatesManager,
    private val activityStarter: ActivityStarter,
    @Main executor: DelayableExecutor,
    private val mediaManager: MediaDataManager,
    configurationController: ConfigurationController,
    falsingCollector: FalsingCollector,
    falsingManager: FalsingManager
) {
    /**
     * The current width of the carousel
     */
    private var currentCarouselWidth: Int = 0

    /**
     * The current height of the carousel
     */
    private var currentCarouselHeight: Int = 0

    /**
     * Are we currently showing only active players
     */
    private var currentlyShowingOnlyActive: Boolean = false

    /**
     * Is the player currently visible (at the end of the transformation
     */
    private var playersVisible: Boolean = false
    /**
     * The desired location where we'll be at the end of the transformation. Usually this matches
     * the end location, except when we're still waiting on a state update call.
     */
    @MediaLocation
    private var desiredLocation: Int = -1

    /**
     * The ending location of the view where it ends when all animations and transitions have
     * finished
     */
    @MediaLocation
    private var currentEndLocation: Int = -1

    /**
     * The ending location of the view where it ends when all animations and transitions have
     * finished
     */
    @MediaLocation
    private var currentStartLocation: Int = -1

    /**
     * The progress of the transition or 1.0 if there is no transition happening
     */
    private var currentTransitionProgress: Float = 1.0f

    /**
     * The measured width of the carousel
     */
    private var carouselMeasureWidth: Int = 0

    /**
     * The measured height of the carousel
     */
    private var carouselMeasureHeight: Int = 0
    private var desiredHostState: MediaHostState? = null
    private val mediaCarousel: MediaScrollView
    val mediaCarouselScrollHandler: MediaCarouselScrollHandler
    val mediaFrame: ViewGroup
    private lateinit var settingsButton: View
    private val mediaContent: ViewGroup
    private val pageIndicator: PageIndicator
    private val visualStabilityCallback: VisualStabilityManager.Callback
    private var needsReordering: Boolean = false
    private var keysNeedRemoval = mutableSetOf<String>()
    private var bgColor = getBackgroundColor()
    protected var shouldScrollToActivePlayer: Boolean = false
    private var isRtl: Boolean = false
        set(value) {
            if (value != field) {
                field = value
                mediaFrame.layoutDirection =
                        if (value) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                mediaCarouselScrollHandler.scrollToStart()
            }
        }
    private var currentlyExpanded = true
        set(value) {
            if (field != value) {
                field = value
                for (player in MediaPlayerData.players()) {
                    player.setListening(field)
                }
            }
        }
    private val configListener = object : ConfigurationController.ConfigurationListener {
        override fun onDensityOrFontScaleChanged() {
            recreatePlayers()
            inflateSettingsButton()
        }

        override fun onOverlayChanged() {
            recreatePlayers()
            inflateSettingsButton()
        }

        override fun onConfigChanged(newConfig: Configuration?) {
            if (newConfig == null) return
            isRtl = newConfig.layoutDirection == View.LAYOUT_DIRECTION_RTL
        }

        override fun onUiModeChanged() {
            recreatePlayers()
            inflateSettingsButton()
        }
    }

    init {
        mediaFrame = inflateMediaCarousel()
        mediaCarousel = mediaFrame.requireViewById(R.id.media_carousel_scroller)
        pageIndicator = mediaFrame.requireViewById(R.id.media_page_indicator)
        mediaCarouselScrollHandler = MediaCarouselScrollHandler(mediaCarousel, pageIndicator,
                executor, this::onSwipeToDismiss, this::updatePageIndicatorLocation,
                this::closeGuts, falsingCollector, falsingManager, this::logSmartspaceImpression)
        isRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        inflateSettingsButton()
        mediaContent = mediaCarousel.requireViewById(R.id.media_carousel)
        configurationController.addCallback(configListener)
        // TODO (b/162832756): remove visual stability manager when migrating to new pipeline
        visualStabilityCallback = VisualStabilityManager.Callback {
            if (needsReordering) {
                needsReordering = false
                reorderAllPlayers()
            }

            keysNeedRemoval.forEach { removePlayer(it) }
            keysNeedRemoval.clear()

            // Let's reset our scroll position
            mediaCarouselScrollHandler.scrollToStart()
        }
        visualStabilityManager.addReorderingAllowedCallback(visualStabilityCallback,
                true /* persistent */)
        mediaManager.addListener(object : MediaDataManager.Listener {
            override fun onMediaDataLoaded(
                key: String,
                oldKey: String?,
                data: MediaData,
                immediately: Boolean
            ) {
                if (addOrUpdatePlayer(key, oldKey, data)) {
                    MediaPlayerData.getMediaPlayer(key, null)?.let {
                        logSmartspaceCardReported(759, // SMARTSPACE_CARD_RECEIVED
                                it.mInstanceId,
                                /* isRecommendationCard */ false,
                                it.surfaceForSmartspaceLogging)
                    }
                }
                val canRemove = data.isPlaying?.let { !it } ?: data.isClearable && !data.active
                if (canRemove && !Utils.useMediaResumption(context)) {
                    // This view isn't playing, let's remove this! This happens e.g when
                    // dismissing/timing out a view. We still have the data around because
                    // resumption could be on, but we should save the resources and release this.
                    if (visualStabilityManager.isReorderingAllowed) {
                        onMediaDataRemoved(key)
                    } else {
                        keysNeedRemoval.add(key)
                    }
                } else {
                    keysNeedRemoval.remove(key)
                }
            }

            override fun onSmartspaceMediaDataLoaded(
                key: String,
                data: SmartspaceMediaData,
                shouldPrioritize: Boolean
            ) {
                Log.d(TAG, "My Smartspace media update is here")
                if (data.isActive) {
                    addSmartspaceMediaRecommendations(key, data, shouldPrioritize)
                    MediaPlayerData.getMediaPlayer(key, null)?.let {
                        logSmartspaceCardReported(759, // SMARTSPACE_CARD_RECEIVED
                                it.mInstanceId,
                                /* isRecommendationCard */ true,
                                it.surfaceForSmartspaceLogging)
                    }
                    if (mediaCarouselScrollHandler.visibleToUser) {
                        logSmartspaceImpression()
                    }
                } else {
                    onSmartspaceMediaDataRemoved(data.targetId, immediately = true)
                }
            }

            override fun onMediaDataRemoved(key: String) {
                removePlayer(key)
            }

            override fun onSmartspaceMediaDataRemoved(key: String, immediately: Boolean) {
                Log.d(TAG, "My Smartspace media removal request is received")
                if (immediately || visualStabilityManager.isReorderingAllowed) {
                    onMediaDataRemoved(key)
                } else {
                    keysNeedRemoval.add(key)
                }
            }
        })
        mediaFrame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            // The pageIndicator is not laid out yet when we get the current state update,
            // Lets make sure we have the right dimensions
            updatePageIndicatorLocation()
        }
        mediaHostStatesManager.addCallback(object : MediaHostStatesManager.Callback {
            override fun onHostStateChanged(location: Int, mediaHostState: MediaHostState) {
                if (location == desiredLocation) {
                    onDesiredLocationChanged(desiredLocation, mediaHostState, animate = false)
                }
            }
        })
    }

    private fun inflateSettingsButton() {
        val settings = LayoutInflater.from(context).inflate(R.layout.media_carousel_settings_button,
                mediaFrame, false) as View
        if (this::settingsButton.isInitialized) {
            mediaFrame.removeView(settingsButton)
        }
        settingsButton = settings
        mediaFrame.addView(settingsButton)
        mediaCarouselScrollHandler.onSettingsButtonUpdated(settings)
        settingsButton.setOnClickListener {
            activityStarter.startActivity(settingsIntent, true /* dismissShade */)
        }
    }

    private fun inflateMediaCarousel(): ViewGroup {
        val mediaCarousel = LayoutInflater.from(context).inflate(R.layout.media_carousel,
                UniqueObjectHostView(context), false) as ViewGroup
        // Because this is inflated when not attached to the true view hierarchy, it resolves some
        // potential issues to force that the layout direction is defined by the locale
        // (rather than inherited from the parent, which would resolve to LTR when unattached).
        mediaCarousel.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        return mediaCarousel
    }

    private fun reorderAllPlayers() {
        mediaContent.removeAllViews()
        for (mediaPlayer in MediaPlayerData.players()) {
            mediaPlayer.playerViewHolder?.let {
                mediaContent.addView(it.player)
            } ?: mediaPlayer.recommendationViewHolder?.let {
                mediaContent.addView(it.recommendations)
            }
        }
        mediaCarouselScrollHandler.onPlayersChanged()

        // Automatically scroll to the active player if needed
        if (shouldScrollToActivePlayer) {
            shouldScrollToActivePlayer = false
            val activeMediaIndex = MediaPlayerData.activeMediaIndex()
            if (activeMediaIndex != -1) {
                mediaCarouselScrollHandler.scrollToActivePlayer(activeMediaIndex)
            }
        }
    }

    // Returns true if new player is added
    private fun addOrUpdatePlayer(key: String, oldKey: String?, data: MediaData): Boolean {
        val dataCopy = data.copy(backgroundColor = bgColor)
        val existingPlayer = MediaPlayerData.getMediaPlayer(key, oldKey)
        if (existingPlayer == null) {
            var newPlayer = mediaControlPanelFactory.get()
            newPlayer.attachPlayer(
                PlayerViewHolder.create(LayoutInflater.from(context), mediaContent))
            newPlayer.mediaViewController.sizeChangedListener = this::updateCarouselDimensions
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            newPlayer.playerViewHolder?.player?.setLayoutParams(lp)
            newPlayer.bindPlayer(dataCopy, key)
            newPlayer.setListening(currentlyExpanded)
            MediaPlayerData.addMediaPlayer(key, dataCopy, newPlayer)
            updatePlayerToState(newPlayer, noAnimation = true)
            reorderAllPlayers()
        } else {
            existingPlayer.bindPlayer(dataCopy, key)
            MediaPlayerData.addMediaPlayer(key, dataCopy, existingPlayer)
            if (visualStabilityManager.isReorderingAllowed || shouldScrollToActivePlayer) {
                reorderAllPlayers()
            } else {
                needsReordering = true
            }
        }
        updatePageIndicator()
        mediaCarouselScrollHandler.onPlayersChanged()
        mediaCarousel.requiresRemeasuring = true
        // Check postcondition: mediaContent should have the same number of children as there are
        // elements in mediaPlayers.
        if (MediaPlayerData.players().size != mediaContent.childCount) {
            Log.wtf(TAG, "Size of players list and number of views in carousel are out of sync")
        }
        return existingPlayer == null
    }

    private fun addSmartspaceMediaRecommendations(
        key: String,
        data: SmartspaceMediaData,
        shouldPrioritize: Boolean
    ) {
        Log.d(TAG, "Updating smartspace target in carousel")
        if (MediaPlayerData.getMediaPlayer(key, null) != null) {
            Log.w(TAG, "Skip adding smartspace target in carousel")
            return
        }

        val existingSmartspaceMediaKey = MediaPlayerData.smartspaceMediaKey()
        existingSmartspaceMediaKey?.let {
            MediaPlayerData.removeMediaPlayer(existingSmartspaceMediaKey)
        }

        var newRecs = mediaControlPanelFactory.get()
        newRecs.attachRecommendation(
            RecommendationViewHolder.create(LayoutInflater.from(context), mediaContent))
        newRecs.mediaViewController.sizeChangedListener = this::updateCarouselDimensions
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT)
        newRecs.recommendationViewHolder?.recommendations?.setLayoutParams(lp)
        newRecs.bindRecommendation(data.copy(backgroundColor = bgColor))
        MediaPlayerData.addMediaRecommendation(key, data, newRecs, shouldPrioritize)
        updatePlayerToState(newRecs, noAnimation = true)
        reorderAllPlayers()
        updatePageIndicator()
        mediaCarousel.requiresRemeasuring = true
        // Check postcondition: mediaContent should have the same number of children as there are
        // elements in mediaPlayers.
        if (MediaPlayerData.players().size != mediaContent.childCount) {
            Log.wtf(TAG, "Size of players list and number of views in carousel are out of sync")
        }
    }

    private fun removePlayer(
        key: String,
        dismissMediaData: Boolean = true,
        dismissRecommendation: Boolean = true
    ) {
        val removed = MediaPlayerData.removeMediaPlayer(key)
        removed?.apply {
            mediaCarouselScrollHandler.onPrePlayerRemoved(removed)
            mediaContent.removeView(removed.playerViewHolder?.player)
            mediaContent.removeView(removed.recommendationViewHolder?.recommendations)
            removed.onDestroy()
            mediaCarouselScrollHandler.onPlayersChanged()
            updatePageIndicator()

            if (dismissMediaData) {
                // Inform the media manager of a potentially late dismissal
                mediaManager.dismissMediaData(key, delay = 0L)
            }
            if (dismissRecommendation) {
                // Inform the media manager of a potentially late dismissal
                mediaManager.dismissSmartspaceRecommendation(key, delay = 0L)
            }
        }
    }

    private fun recreatePlayers() {
        bgColor = getBackgroundColor()
        pageIndicator.tintList = ColorStateList.valueOf(getForegroundColor())

        MediaPlayerData.mediaData().forEach { (key, data, isSsMediaRec) ->
            if (isSsMediaRec) {
                val smartspaceMediaData = MediaPlayerData.smartspaceMediaData
                removePlayer(key, dismissMediaData = false, dismissRecommendation = false)
                smartspaceMediaData?.let {
                    addSmartspaceMediaRecommendations(
                        it.targetId, it, MediaPlayerData.shouldPrioritizeSs)
                }
            } else {
                removePlayer(key, dismissMediaData = false, dismissRecommendation = false)
                addOrUpdatePlayer(key = key, oldKey = null, data = data)
            }
        }
    }

    private fun getBackgroundColor(): Int {
        return context.getColor(android.R.color.system_accent2_50)
    }

    private fun getForegroundColor(): Int {
        return context.getColor(android.R.color.system_accent2_900)
    }

    private fun updatePageIndicator() {
        val numPages = mediaContent.getChildCount()
        pageIndicator.setNumPages(numPages)
        if (numPages == 1) {
            pageIndicator.setLocation(0f)
        }
        updatePageIndicatorAlpha()
    }

    /**
     * Set a new interpolated state for all players. This is a state that is usually controlled
     * by a finger movement where the user drags from one state to the next.
     *
     * @param startLocation the start location of our state or -1 if this is directly set
     * @param endLocation the ending location of our state.
     * @param progress the progress of the transition between startLocation and endlocation. If
     *                 this is not a guided transformation, this will be 1.0f
     * @param immediately should this state be applied immediately, canceling all animations?
     */
    fun setCurrentState(
        @MediaLocation startLocation: Int,
        @MediaLocation endLocation: Int,
        progress: Float,
        immediately: Boolean
    ) {
        if (startLocation != currentStartLocation ||
                endLocation != currentEndLocation ||
                progress != currentTransitionProgress ||
                immediately
        ) {
            currentStartLocation = startLocation
            currentEndLocation = endLocation
            currentTransitionProgress = progress
            for (mediaPlayer in MediaPlayerData.players()) {
                updatePlayerToState(mediaPlayer, immediately)
            }
            maybeResetSettingsCog()
            updatePageIndicatorAlpha()
        }
    }

    private fun updatePageIndicatorAlpha() {
        val hostStates = mediaHostStatesManager.mediaHostStates
        val endIsVisible = hostStates[currentEndLocation]?.visible ?: false
        val startIsVisible = hostStates[currentStartLocation]?.visible ?: false
        val startAlpha = if (startIsVisible) 1.0f else 0.0f
        val endAlpha = if (endIsVisible) 1.0f else 0.0f
        var alpha = 1.0f
        if (!endIsVisible || !startIsVisible) {
            var progress = currentTransitionProgress
            if (!endIsVisible) {
                progress = 1.0f - progress
            }
            // Let's fade in quickly at the end where the view is visible
            progress = MathUtils.constrain(
                    MathUtils.map(0.95f, 1.0f, 0.0f, 1.0f, progress),
                    0.0f,
                    1.0f)
            alpha = MathUtils.lerp(startAlpha, endAlpha, progress)
        }
        pageIndicator.alpha = alpha
    }

    private fun updatePageIndicatorLocation() {
        // Update the location of the page indicator, carousel clipping
        val translationX = if (isRtl) {
            (pageIndicator.width - currentCarouselWidth) / 2.0f
        } else {
            (currentCarouselWidth - pageIndicator.width) / 2.0f
        }
        pageIndicator.translationX = translationX + mediaCarouselScrollHandler.contentTranslation
        val layoutParams = pageIndicator.layoutParams as ViewGroup.MarginLayoutParams
        pageIndicator.translationY = (currentCarouselHeight - pageIndicator.height -
                layoutParams.bottomMargin).toFloat()
    }

    /**
     * Update the dimension of this carousel.
     */
    private fun updateCarouselDimensions() {
        var width = 0
        var height = 0
        for (mediaPlayer in MediaPlayerData.players()) {
            val controller = mediaPlayer.mediaViewController
            // When transitioning the view to gone, the view gets smaller, but the translation
            // Doesn't, let's add the translation
            width = Math.max(width, controller.currentWidth + controller.translationX.toInt())
            height = Math.max(height, controller.currentHeight + controller.translationY.toInt())
        }
        if (width != currentCarouselWidth || height != currentCarouselHeight) {
            currentCarouselWidth = width
            currentCarouselHeight = height
            mediaCarouselScrollHandler.setCarouselBounds(
                    currentCarouselWidth, currentCarouselHeight)
            updatePageIndicatorLocation()
        }
    }

    private fun maybeResetSettingsCog() {
        val hostStates = mediaHostStatesManager.mediaHostStates
        val endShowsActive = hostStates[currentEndLocation]?.showsOnlyActiveMedia
                ?: true
        val startShowsActive = hostStates[currentStartLocation]?.showsOnlyActiveMedia
                ?: endShowsActive
        if (currentlyShowingOnlyActive != endShowsActive ||
                ((currentTransitionProgress != 1.0f && currentTransitionProgress != 0.0f) &&
                            startShowsActive != endShowsActive)) {
            // Whenever we're transitioning from between differing states or the endstate differs
            // we reset the translation
            currentlyShowingOnlyActive = endShowsActive
            mediaCarouselScrollHandler.resetTranslation(animate = true)
        }
    }

    private fun updatePlayerToState(mediaPlayer: MediaControlPanel, noAnimation: Boolean) {
        mediaPlayer.mediaViewController.setCurrentState(
                startLocation = currentStartLocation,
                endLocation = currentEndLocation,
                transitionProgress = currentTransitionProgress,
                applyImmediately = noAnimation)
    }

    /**
     * The desired location of this view has changed. We should remeasure the view to match
     * the new bounds and kick off bounds animations if necessary.
     * If an animation is happening, an animation is kicked of externally, which sets a new
     * current state until we reach the targetState.
     *
     * @param desiredLocation the location we're going to
     * @param desiredHostState the target state we're transitioning to
     * @param animate should this be animated
     */
    fun onDesiredLocationChanged(
        desiredLocation: Int,
        desiredHostState: MediaHostState?,
        animate: Boolean,
        duration: Long = 200,
        startDelay: Long = 0
    ) {
        desiredHostState?.let {
            // This is a hosting view, let's remeasure our players
            this.desiredLocation = desiredLocation
            this.desiredHostState = it
            currentlyExpanded = it.expansion > 0

            val shouldCloseGuts = !currentlyExpanded && !mediaManager.hasActiveMedia() &&
                    desiredHostState.showsOnlyActiveMedia

            for (mediaPlayer in MediaPlayerData.players()) {
                if (animate) {
                    mediaPlayer.mediaViewController.animatePendingStateChange(
                            duration = duration,
                            delay = startDelay)
                }
                if (shouldCloseGuts && mediaPlayer.mediaViewController.isGutsVisible) {
                    mediaPlayer.closeGuts(!animate)
                }

                mediaPlayer.mediaViewController.onLocationPreChange(desiredLocation)
            }
            mediaCarouselScrollHandler.showsSettingsButton = !it.showsOnlyActiveMedia
            mediaCarouselScrollHandler.falsingProtectionNeeded = it.falsingProtectionNeeded
            val nowVisible = it.visible
            if (nowVisible != playersVisible) {
                playersVisible = nowVisible
                if (nowVisible) {
                    mediaCarouselScrollHandler.resetTranslation()
                }
            }
            updateCarouselSize()
        }
    }

    fun closeGuts(immediate: Boolean = true) {
        MediaPlayerData.players().forEach {
            it.closeGuts(immediate)
        }
    }

    /**
     * Update the size of the carousel, remeasuring it if necessary.
     */
    private fun updateCarouselSize() {
        val width = desiredHostState?.measurementInput?.width ?: 0
        val height = desiredHostState?.measurementInput?.height ?: 0
        if (width != carouselMeasureWidth && width != 0 ||
                height != carouselMeasureHeight && height != 0) {
            carouselMeasureWidth = width
            carouselMeasureHeight = height
            val playerWidthPlusPadding = carouselMeasureWidth +
                    context.resources.getDimensionPixelSize(R.dimen.qs_media_padding)
            // Let's remeasure the carousel
            val widthSpec = desiredHostState?.measurementInput?.widthMeasureSpec ?: 0
            val heightSpec = desiredHostState?.measurementInput?.heightMeasureSpec ?: 0
            mediaCarousel.measure(widthSpec, heightSpec)
            mediaCarousel.layout(0, 0, width, mediaCarousel.measuredHeight)
            // Update the padding after layout; view widths are used in RTL to calculate scrollX
            mediaCarouselScrollHandler.playerWidthPlusPadding = playerWidthPlusPadding
        }
    }

    /**
     * Log the user impression for media card.
     */
    fun logSmartspaceImpression() {
        val visibleMediaIndex = mediaCarouselScrollHandler.visibleMediaIndex
        if (MediaPlayerData.players().size > visibleMediaIndex) {
            val mediaControlPanel = MediaPlayerData.players().elementAt(visibleMediaIndex)
            val isMediaActive =
                    MediaPlayerData.playerKeys().elementAt(visibleMediaIndex).data?.active
            val isRecommendationCard = mediaControlPanel.recommendationViewHolder != null
            if (!isRecommendationCard && !isMediaActive) {
                // Media control card time out or swiped away
                return
            }
            logSmartspaceCardReported(800, // SMARTSPACE_CARD_SEEN
                    mediaControlPanel.mInstanceId,
                    isRecommendationCard,
                    mediaControlPanel.surfaceForSmartspaceLogging)
        }
    }

    @JvmOverloads
    fun logSmartspaceCardReported(
        eventId: Int,
        instanceId: Int,
        isRecommendationCard: Boolean,
        surface: Int,
        rank: Int = mediaCarouselScrollHandler.visibleMediaIndex
    ) {
        /* ktlint-disable max-line-length */
        SysUiStatsLog.write(SysUiStatsLog.SMARTSPACE_CARD_REPORTED,
                eventId,
                instanceId,
                if (isRecommendationCard)
                    SysUiStatsLog.SMART_SPACE_CARD_REPORTED__CARD_TYPE__HEADPHONE_MEDIA_RECOMMENDATIONS
                else
                    SysUiStatsLog.SMART_SPACE_CARD_REPORTED__CARD_TYPE__HEADPHONE_RESUME_MEDIA,
                surface,
                rank,
                mediaContent.getChildCount())
        /* ktlint-disable max-line-length */
    }

    private fun onSwipeToDismiss() {
        val recommendation = MediaPlayerData.players().filter {
            it.recommendationViewHolder != null
        }
        // Use -1 as rank value to indicate user swipe to dismiss the card
        if (!recommendation.isEmpty()) {
            logSmartspaceCardReported(761, // SMARTSPACE_CARD_DISMISS
                    recommendation.get(0).mInstanceId,
                    true,
                    recommendation.get(0).surfaceForSmartspaceLogging,
            /* rank */-1)
        } else {
            val visibleMediaIndex = mediaCarouselScrollHandler.visibleMediaIndex
            if (MediaPlayerData.players().size > visibleMediaIndex) {
                val player = MediaPlayerData.players().elementAt(visibleMediaIndex)
                logSmartspaceCardReported(761, // SMARTSPACE_CARD_DISMISS
                        player.mInstanceId,
                false,
                        player.surfaceForSmartspaceLogging,
                /* rank */-1)
            }
        }
        mediaManager.onSwipeToDismiss()
    }
}

@VisibleForTesting
internal object MediaPlayerData {
    private val EMPTY = MediaData(-1, false, 0, null, null, null, null, null,
        emptyList(), emptyList(), "INVALID", null, null, null, true, null)
    // Whether should prioritize Smartspace card.
    internal var shouldPrioritizeSs: Boolean = false
        private set
    internal var smartspaceMediaData: SmartspaceMediaData? = null
        private set

    data class MediaSortKey(
        // Whether the item represents a Smartspace media recommendation.
        val isSsMediaRec: Boolean,
        val data: MediaData,
        val updateTime: Long = 0
    )

    private val comparator =
        compareByDescending<MediaSortKey>
            { if (shouldPrioritizeSs) it.isSsMediaRec else !it.isSsMediaRec }
            .thenByDescending { it.data.isPlaying }
            .thenByDescending { it.data.isLocalSession }
            .thenByDescending { !it.data.resumption }
            .thenByDescending { it.updateTime }

    private val mediaPlayers = TreeMap<MediaSortKey, MediaControlPanel>(comparator)
    private val mediaData: MutableMap<String, MediaSortKey> = mutableMapOf()

    fun addMediaPlayer(key: String, data: MediaData, player: MediaControlPanel) {
        removeMediaPlayer(key)
        val sortKey = MediaSortKey(isSsMediaRec = false, data, System.currentTimeMillis())
        mediaData.put(key, sortKey)
        mediaPlayers.put(sortKey, player)
    }

    fun addMediaRecommendation(
        key: String,
        data: SmartspaceMediaData,
        player: MediaControlPanel,
        shouldPrioritize: Boolean
    ) {
        shouldPrioritizeSs = shouldPrioritize
        removeMediaPlayer(key)
        val sortKey = MediaSortKey(isSsMediaRec = true, EMPTY, System.currentTimeMillis())
        mediaData.put(key, sortKey)
        mediaPlayers.put(sortKey, player)
        smartspaceMediaData = data
    }

    fun getMediaPlayer(key: String, oldKey: String?): MediaControlPanel? {
        // If the key was changed, update entry
        oldKey?.let {
            if (it != key) {
                mediaData.remove(it)?.let { sortKey -> mediaData.put(key, sortKey) }
            }
        }
        return mediaData.get(key)?.let { mediaPlayers.get(it) }
    }

    fun removeMediaPlayer(key: String) = mediaData.remove(key)?.let {
        if (it.isSsMediaRec) {
            smartspaceMediaData = null
        }
        mediaPlayers.remove(it)
    }

    fun mediaData() = mediaData.entries.map { e -> Triple(e.key, e.value.data, e.value.isSsMediaRec) }

    fun players() = mediaPlayers.values

    /** Returns the index of the first non-timeout media. */
    fun activeMediaIndex(): Int {
        mediaPlayers.entries.forEachIndexed { index, e ->
            if (!e.key.isSsMediaRec && e.key.data.active) {
                return index
            }
        }
        return -1
    }

    /** Returns the existing Smartspace target id. */
    fun smartspaceMediaKey(): String? {
        mediaData.entries.forEach { e ->
            if (e.value.isSsMediaRec) {
                return e.key
            }
        }
        return null
    }

    fun playerKeys() = mediaPlayers.keys

    @VisibleForTesting
    fun clear() {
        mediaData.clear()
        mediaPlayers.clear()
    }
}
