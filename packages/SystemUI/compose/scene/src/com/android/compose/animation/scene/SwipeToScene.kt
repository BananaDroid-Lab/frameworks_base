/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.compose.animation.scene

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.android.compose.nestedscroll.PriorityPostNestedScrollConnection
import kotlin.math.absoluteValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Configures the swipeable behavior of a [SceneTransitionLayout] depending on the current state.
 */
@Composable
internal fun Modifier.swipeToScene(
    layoutImpl: SceneTransitionLayoutImpl,
    orientation: Orientation,
): Modifier {
    val gestureHandler = rememberSceneGestureHandler(layoutImpl, orientation)

    /** Whether swipe should be enabled in the given [orientation]. */
    fun Scene.shouldEnableSwipes(orientation: Orientation): Boolean =
        upOrLeft(orientation) != null || downOrRight(orientation) != null

    val currentScene = gestureHandler.currentScene
    val canSwipe = currentScene.shouldEnableSwipes(orientation)
    val canOppositeSwipe =
        currentScene.shouldEnableSwipes(
            when (orientation) {
                Orientation.Vertical -> Orientation.Horizontal
                Orientation.Horizontal -> Orientation.Vertical
            }
        )

    return nestedScroll(connection = gestureHandler.nestedScroll.connection)
        .draggable(
            state = rememberDraggableState(onDelta = gestureHandler.draggable::onDelta),
            orientation = orientation,
            enabled = gestureHandler.isDrivingTransition || canSwipe,
            // Immediately start the drag if this our [transition] is currently animating to a scene
            // (i.e. the user released their input pointer after swiping in this orientation) and
            // the user can't swipe in the other direction.
            startDragImmediately =
                gestureHandler.isDrivingTransition &&
                    gestureHandler.isAnimatingOffset &&
                    !canOppositeSwipe,
            onDragStarted = gestureHandler.draggable::onDragStarted,
            onDragStopped = gestureHandler.draggable::onDragStopped,
        )
}

@Composable
private fun rememberSceneGestureHandler(
    layoutImpl: SceneTransitionLayoutImpl,
    orientation: Orientation,
): SceneGestureHandler {
    val coroutineScope = rememberCoroutineScope()

    val gestureHandler =
        remember(layoutImpl, orientation, coroutineScope) {
            SceneGestureHandler(layoutImpl, orientation, coroutineScope)
        }

    // Make sure we reset the scroll connection when this handler is removed from composition
    val connection = gestureHandler.nestedScroll.connection
    DisposableEffect(connection) { onDispose { connection.reset() } }

    return gestureHandler
}

@VisibleForTesting
class SceneGestureHandler(
    private val layoutImpl: SceneTransitionLayoutImpl,
    internal val orientation: Orientation,
    private val coroutineScope: CoroutineScope,
) : GestureHandler {
    override val draggable: DraggableHandler = SceneDraggableHandler(this)

    override val nestedScroll: SceneNestedScrollHandler = SceneNestedScrollHandler(this)

    private var transitionState
        get() = layoutImpl.state.transitionState
        set(value) {
            layoutImpl.state.transitionState = value
        }

    /**
     * The transition controlled by this gesture handler. It will be set as the [transitionState] in
     * the [SceneTransitionLayoutImpl] whenever this handler is driving the current transition.
     *
     * Note: the initialScene here does not matter, it's only used for initializing the transition
     * and will be replaced when a drag event starts.
     */
    private val swipeTransition = SwipeTransition(initialScene = currentScene)

    internal val currentScene: Scene
        get() = layoutImpl.scene(transitionState.currentScene)

    internal val isDrivingTransition
        get() = transitionState == swipeTransition

    internal var isAnimatingOffset
        get() = swipeTransition.isAnimatingOffset
        private set(value) {
            swipeTransition.isAnimatingOffset = value
        }

    internal val swipeTransitionToScene
        get() = swipeTransition._toScene

    /**
     * The velocity threshold at which the intent of the user is to swipe up or down. It is the same
     * as SwipeableV2Defaults.VelocityThreshold.
     */
    @VisibleForTesting val velocityThreshold = with(layoutImpl.density) { 125.dp.toPx() }

    /**
     * The positional threshold at which the intent of the user is to swipe to the next scene. It is
     * the same as SwipeableV2Defaults.PositionalThreshold.
     */
    private val positionalThreshold = with(layoutImpl.density) { 56.dp.toPx() }

    internal fun onDragStarted() {
        if (isDrivingTransition) {
            // This [transition] was already driving the animation: simply take over it.
            if (isAnimatingOffset) {
                // Stop animating and start from where the current offset. Setting the animation job
                // to `null` will effectively cancel the animation.
                swipeTransition.stopOffsetAnimation()
                swipeTransition.dragOffset = swipeTransition.offsetAnimatable.value
            }

            return
        }

        // TODO(b/290184746): Better handle interruptions here if state != idle.

        val fromScene = currentScene

        swipeTransition._currentScene = fromScene
        swipeTransition._fromScene = fromScene

        // We don't know where we are transitioning to yet given that the drag just started, so set
        // it to fromScene, which will effectively be treated the same as Idle(fromScene).
        swipeTransition._toScene = fromScene

        swipeTransition.stopOffsetAnimation()
        swipeTransition.dragOffset = 0f

        // Use the layout size in the swipe orientation for swipe distance.
        // TODO(b/290184746): Also handle custom distances for transitions. With smaller distances,
        // we will also have to make sure that we correctly handle overscroll.
        swipeTransition.absoluteDistance =
            when (orientation) {
                Orientation.Horizontal -> layoutImpl.size.width
                Orientation.Vertical -> layoutImpl.size.height
            }.toFloat()

        if (swipeTransition.absoluteDistance > 0f) {
            transitionState = swipeTransition
        }
    }

    internal fun onDrag(delta: Float) {
        swipeTransition.dragOffset += delta

        // First check transition.fromScene should be changed for the case where the user quickly
        // swiped twice in a row to accelerate the transition and go from A => B then B => C really
        // fast.
        maybeHandleAcceleratedSwipe()

        val offset = swipeTransition.dragOffset
        val fromScene = swipeTransition._fromScene

        // Compute the target scene depending on the current offset.
        val target = fromScene.findTargetSceneAndDistance(offset)

        if (swipeTransition._toScene.key != target.sceneKey) {
            swipeTransition._toScene = layoutImpl.scenes.getValue(target.sceneKey)
        }

        if (swipeTransition._distance != target.distance) {
            swipeTransition._distance = target.distance
        }
    }

    /**
     * Change fromScene in the case where the user quickly swiped multiple times in the same
     * direction to accelerate the transition from A => B then B => C.
     */
    private fun maybeHandleAcceleratedSwipe() {
        val toScene = swipeTransition._toScene
        val fromScene = swipeTransition._fromScene

        // If the swipe was not committed, don't do anything.
        if (fromScene == toScene || swipeTransition._currentScene != toScene) {
            return
        }

        // If the offset is past the distance then let's change fromScene so that the user can swipe
        // to the next screen or go back to the previous one.
        val offset = swipeTransition.dragOffset
        val absoluteDistance = swipeTransition.absoluteDistance
        if (offset <= -absoluteDistance && fromScene.upOrLeft(orientation) == toScene.key) {
            swipeTransition.dragOffset += absoluteDistance
            swipeTransition._fromScene = toScene
        } else if (
            offset >= absoluteDistance && fromScene.downOrRight(orientation) == toScene.key
        ) {
            swipeTransition.dragOffset -= absoluteDistance
            swipeTransition._fromScene = toScene
        }

        // Important note: toScene and distance will be updated right after this function is called,
        // using fromScene and dragOffset.
    }

    private class TargetScene(
        val sceneKey: SceneKey,
        val distance: Float,
    )

    private fun Scene.findTargetSceneAndDistance(directionOffset: Float): TargetScene {
        val maxDistance =
            when (orientation) {
                Orientation.Horizontal -> layoutImpl.size.width
                Orientation.Vertical -> layoutImpl.size.height
            }.toFloat()

        val upOrLeft = upOrLeft(orientation)
        val downOrRight = downOrRight(orientation)

        // Compute the target scene depending on the current offset.
        return when {
            directionOffset < 0f && upOrLeft != null -> {
                TargetScene(
                    sceneKey = upOrLeft,
                    distance = -maxDistance,
                )
            }
            directionOffset > 0f && downOrRight != null -> {
                TargetScene(
                    sceneKey = downOrRight,
                    distance = maxDistance,
                )
            }
            else -> {
                TargetScene(
                    sceneKey = key,
                    distance = 0f,
                )
            }
        }
    }

    internal fun onDragStopped(velocity: Float, canChangeScene: Boolean) {
        // The state was changed since the drag started; don't do anything.
        if (!isDrivingTransition) {
            return
        }

        // We were not animating.
        if (swipeTransition._fromScene == swipeTransition._toScene) {
            transitionState = TransitionState.Idle(swipeTransition._fromScene.key)
            return
        }

        // Compute the destination scene (and therefore offset) to settle in.
        val targetOffset: Float
        val targetScene: Scene
        val offset = swipeTransition.dragOffset
        val distance = swipeTransition.distance
        if (
            canChangeScene &&
                shouldCommitSwipe(
                    offset,
                    distance,
                    velocity,
                    wasCommitted = swipeTransition._currentScene == swipeTransition._toScene,
                )
        ) {
            targetOffset = distance
            targetScene = swipeTransition._toScene
        } else {
            targetOffset = 0f
            targetScene = swipeTransition._fromScene
        }

        // If the effective current scene changed, it should be reflected right now in the current
        // scene state, even before the settle animation is ongoing. That way all the swipeables and
        // back handlers will be refreshed and the user can for instance quickly swipe vertically
        // from A => B then horizontally from B => C, or swipe from A => B then immediately go back
        // B => A.
        if (targetScene != swipeTransition._currentScene) {
            swipeTransition._currentScene = targetScene
            layoutImpl.onChangeScene(targetScene.key)
        }

        animateOffset(
            initialVelocity = velocity,
            targetOffset = targetOffset,
            targetScene = targetScene.key
        )
    }

    /**
     * Whether the swipe to the target scene should be committed or not. This is inspired by
     * SwipeableV2.computeTarget().
     */
    private fun shouldCommitSwipe(
        offset: Float,
        distance: Float,
        velocity: Float,
        wasCommitted: Boolean,
    ): Boolean {
        fun isCloserToTarget(): Boolean {
            return (offset - distance).absoluteValue < offset.absoluteValue
        }

        // Swiping up or left.
        if (distance < 0f) {
            return if (offset > 0f || velocity >= velocityThreshold) {
                false
            } else {
                velocity <= -velocityThreshold ||
                    (offset <= -positionalThreshold && !wasCommitted) ||
                    isCloserToTarget()
            }
        }

        // Swiping down or right.
        return if (offset < 0f || velocity <= -velocityThreshold) {
            false
        } else {
            velocity >= velocityThreshold ||
                (offset >= positionalThreshold && !wasCommitted) ||
                isCloserToTarget()
        }
    }

    private fun animateOffset(
        initialVelocity: Float,
        targetOffset: Float,
        targetScene: SceneKey,
    ) {
        swipeTransition.startOffsetAnimation {
            coroutineScope
                .launch {
                    if (!isAnimatingOffset) {
                        swipeTransition.offsetAnimatable.snapTo(swipeTransition.dragOffset)
                    }
                    isAnimatingOffset = true

                    swipeTransition.offsetAnimatable.animateTo(
                        targetOffset,
                        // TODO(b/290184746): Make this spring spec configurable.
                        spring(
                            stiffness = Spring.StiffnessMediumLow,
                            visibilityThreshold = OffsetVisibilityThreshold
                        ),
                        initialVelocity = initialVelocity,
                    )

                    // Now that the animation is done, the state should be idle. Note that if the
                    // state was changed since this animation started, some external code changed it
                    // and we shouldn't do anything here. Note also that this job will be cancelled
                    // in the case where the user intercepts this swipe.
                    if (isDrivingTransition) {
                        transitionState = TransitionState.Idle(targetScene)
                    }
                }
                .also { it.invokeOnCompletion { isAnimatingOffset = false } }
        }
    }

    internal fun animateOverscroll(velocity: Velocity): Velocity {
        val velocityAmount =
            when (orientation) {
                Orientation.Vertical -> velocity.y
                Orientation.Horizontal -> velocity.x
            }

        if (velocityAmount == 0f) {
            // There is no remaining velocity
            return Velocity.Zero
        }

        val fromScene = currentScene
        val target = fromScene.findTargetSceneAndDistance(velocityAmount)
        val isValidTarget = target.distance != 0f && target.sceneKey != fromScene.key

        if (!isValidTarget || isDrivingTransition) {
            // We have not found a valid target or we are already in a transition
            return Velocity.Zero
        }

        swipeTransition._currentScene = fromScene
        swipeTransition._fromScene = fromScene
        swipeTransition._toScene = layoutImpl.scene(target.sceneKey)
        swipeTransition._distance = target.distance
        swipeTransition.absoluteDistance = target.distance.absoluteValue
        swipeTransition.stopOffsetAnimation()
        swipeTransition.dragOffset = 0f

        transitionState = swipeTransition

        animateOffset(
            initialVelocity = velocityAmount,
            targetOffset = 0f,
            targetScene = fromScene.key
        )

        // The animateOffset animation consumes any remaining velocity.
        return velocity
    }

    private class SwipeTransition(initialScene: Scene) : TransitionState.Transition {
        var _currentScene by mutableStateOf(initialScene)
        override val currentScene: SceneKey
            get() = _currentScene.key

        var _fromScene by mutableStateOf(initialScene)
        override val fromScene: SceneKey
            get() = _fromScene.key

        var _toScene by mutableStateOf(initialScene)
        override val toScene: SceneKey
            get() = _toScene.key

        override val progress: Float
            get() {
                val offset = if (isAnimatingOffset) offsetAnimatable.value else dragOffset
                if (distance == 0f) {
                    // This can happen only if fromScene == toScene.
                    error(
                        "Transition.progress should be called only when Transition.fromScene != " +
                            "Transition.toScene"
                    )
                }
                return offset / distance
            }

        override val isUserInputDriven = true

        /** The current offset caused by the drag gesture. */
        var dragOffset by mutableFloatStateOf(0f)

        /**
         * Whether the offset is animated (the user lifted their finger) or if it is driven by
         * gesture.
         */
        var isAnimatingOffset by mutableStateOf(false)

        /** The animatable used to animate the offset once the user lifted its finger. */
        val offsetAnimatable = Animatable(0f, OffsetVisibilityThreshold)

        /** Job to check that there is at most one offset animation in progress. */
        private var offsetAnimationJob: Job? = null

        /** Ends any previous [offsetAnimationJob] and runs the new [job]. */
        fun startOffsetAnimation(job: () -> Job) {
            stopOffsetAnimation()
            offsetAnimationJob = job()
        }

        /** Stops any ongoing offset animation. */
        fun stopOffsetAnimation() {
            offsetAnimationJob?.cancel()
        }

        /** The absolute distance between [fromScene] and [toScene]. */
        var absoluteDistance = 0f

        /**
         * The signed distance between [fromScene] and [toScene]. It is negative if [fromScene] is
         * above or to the left of [toScene].
         */
        var _distance by mutableFloatStateOf(0f)
        val distance: Float
            get() = _distance
    }
}

private class SceneDraggableHandler(
    private val gestureHandler: SceneGestureHandler,
) : DraggableHandler {
    override suspend fun onDragStarted(coroutineScope: CoroutineScope, startedPosition: Offset) {
        gestureHandler.onDragStarted()
    }

    override fun onDelta(pixels: Float) {
        gestureHandler.onDrag(delta = pixels)
    }

    override suspend fun onDragStopped(coroutineScope: CoroutineScope, velocity: Float) {
        gestureHandler.onDragStopped(velocity = velocity, canChangeScene = true)
    }
}

@VisibleForTesting
class SceneNestedScrollHandler(
    private val gestureHandler: SceneGestureHandler,
) : NestedScrollHandler {
    override val connection: PriorityPostNestedScrollConnection = nestedScrollConnection()

    private fun Offset.toAmount() =
        when (gestureHandler.orientation) {
            Orientation.Horizontal -> x
            Orientation.Vertical -> y
        }

    private fun Velocity.toAmount() =
        when (gestureHandler.orientation) {
            Orientation.Horizontal -> x
            Orientation.Vertical -> y
        }

    private fun Float.toOffset() =
        when (gestureHandler.orientation) {
            Orientation.Horizontal -> Offset(x = this, y = 0f)
            Orientation.Vertical -> Offset(x = 0f, y = this)
        }

    private fun nestedScrollConnection(): PriorityPostNestedScrollConnection {
        // The next potential scene is calculated during the canStart
        var nextScene: SceneKey? = null

        // This is the scene on which we will have priority during the scroll gesture.
        var priorityScene: SceneKey? = null

        // If we performed a long gesture before entering priority mode, we would have to avoid
        // moving on to the next scene.
        var gestureStartedOnNestedChild = false

        return PriorityPostNestedScrollConnection(
            canStart = { offsetAvailable, offsetBeforeStart ->
                val amount = offsetAvailable.toAmount()
                if (amount == 0f) return@PriorityPostNestedScrollConnection false

                gestureStartedOnNestedChild = offsetBeforeStart != Offset.Zero

                val fromScene = gestureHandler.currentScene
                nextScene =
                    when {
                        amount < 0f -> fromScene.upOrLeft(gestureHandler.orientation)
                        amount > 0f -> fromScene.downOrRight(gestureHandler.orientation)
                        else -> null
                    }

                nextScene != null
            },
            canContinueScroll = { priorityScene == gestureHandler.swipeTransitionToScene.key },
            onStart = {
                priorityScene = nextScene
                gestureHandler.onDragStarted()
            },
            onScroll = { offsetAvailable ->
                val amount = offsetAvailable.toAmount()

                // TODO(b/297842071) We should handle the overscroll or slow drag if the gesture is
                // initiated in a nested child.
                gestureHandler.onDrag(amount)

                amount.toOffset()
            },
            onStop = { velocityAvailable ->
                priorityScene = null

                gestureHandler.onDragStopped(
                    velocity = velocityAvailable.toAmount(),
                    canChangeScene = !gestureStartedOnNestedChild
                )

                // The onDragStopped animation consumes any remaining velocity.
                velocityAvailable
            },
            onPostFling = { velocityAvailable ->
                // If there is any velocity left, we can try running an overscroll animation between
                // scenes.
                gestureHandler.animateOverscroll(velocity = velocityAvailable)
            },
        )
    }
}

/**
 * The number of pixels below which there won't be a visible difference in the transition and from
 * which the animation can stop.
 */
private const val OffsetVisibilityThreshold = 0.5f
