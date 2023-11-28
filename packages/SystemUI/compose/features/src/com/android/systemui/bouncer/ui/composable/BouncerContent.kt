/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.bouncer.ui.composable

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.android.compose.PlatformButton
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneScope
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.transitions
import com.android.compose.modifiers.thenIf
import com.android.systemui.bouncer.shared.model.BouncerActionButtonModel
import com.android.systemui.bouncer.ui.helper.BouncerSceneLayout
import com.android.systemui.bouncer.ui.viewmodel.AuthMethodBouncerViewModel
import com.android.systemui.bouncer.ui.viewmodel.BouncerViewModel
import com.android.systemui.bouncer.ui.viewmodel.PasswordBouncerViewModel
import com.android.systemui.bouncer.ui.viewmodel.PatternBouncerViewModel
import com.android.systemui.bouncer.ui.viewmodel.PinBouncerViewModel
import com.android.systemui.common.shared.model.Text.Companion.loadText
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.fold.ui.composable.foldPosture
import com.android.systemui.fold.ui.helper.FoldPosture
import com.android.systemui.res.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

@Composable
fun BouncerContent(
    viewModel: BouncerViewModel,
    dialogFactory: BouncerDialogFactory,
    modifier: Modifier = Modifier,
) {
    val isFullScreenUserSwitcherEnabled = viewModel.isUserSwitcherVisible
    val isSideBySideSupported by viewModel.isSideBySideSupported.collectAsState()
    val layout = calculateLayout(isSideBySideSupported = isSideBySideSupported)

    when (layout) {
        BouncerSceneLayout.STANDARD ->
            StandardLayout(
                viewModel = viewModel,
                dialogFactory = dialogFactory,
                modifier = modifier,
            )
        BouncerSceneLayout.SIDE_BY_SIDE ->
            SideBySideLayout(
                viewModel = viewModel,
                dialogFactory = dialogFactory,
                isUserSwitcherVisible = isFullScreenUserSwitcherEnabled,
                modifier = modifier,
            )
        BouncerSceneLayout.STACKED ->
            StackedLayout(
                viewModel = viewModel,
                dialogFactory = dialogFactory,
                isUserSwitcherVisible = isFullScreenUserSwitcherEnabled,
                modifier = modifier,
            )
        BouncerSceneLayout.SPLIT ->
            SplitLayout(
                viewModel = viewModel,
                dialogFactory = dialogFactory,
                modifier = modifier,
            )
    }
}

/**
 * Renders the contents of the actual bouncer UI, the area that takes user input to do an
 * authentication attempt, including all messaging UI (directives, reasoning, errors, etc.).
 */
@Composable
private fun StandardLayout(
    viewModel: BouncerViewModel,
    dialogFactory: BouncerDialogFactory,
    modifier: Modifier = Modifier,
    layout: BouncerSceneLayout = BouncerSceneLayout.STANDARD,
    outputOnly: Boolean = false,
) {
    val foldPosture: FoldPosture by foldPosture()
    val isSplitAroundTheFoldRequired by viewModel.isFoldSplitRequired.collectAsState()
    val isSplitAroundTheFold =
        foldPosture == FoldPosture.Tabletop && !outputOnly && isSplitAroundTheFoldRequired
    val currentSceneKey =
        if (isSplitAroundTheFold) SceneKeys.SplitSceneKey else SceneKeys.ContiguousSceneKey

    SceneTransitionLayout(
        currentScene = currentSceneKey,
        onChangeScene = {},
        transitions = SceneTransitions,
        modifier = modifier,
    ) {
        scene(SceneKeys.ContiguousSceneKey) {
            FoldSplittable(
                viewModel = viewModel,
                dialogFactory = dialogFactory,
                layout = layout,
                outputOnly = outputOnly,
                isSplit = false,
            )
        }

        scene(SceneKeys.SplitSceneKey) {
            FoldSplittable(
                viewModel = viewModel,
                dialogFactory = dialogFactory,
                layout = layout,
                outputOnly = outputOnly,
                isSplit = true,
            )
        }
    }
}

/**
 * Renders the "standard" layout of the bouncer, where the bouncer is rendered on its own (no user
 * switcher UI) and laid out vertically, centered horizontally.
 *
 * If [isSplit] is `true`, the top and bottom parts of the bouncer are split such that they don't
 * render across the location of the fold hardware when the device is fully or part-way unfolded
 * with the fold hinge in a horizontal position.
 *
 * If [outputOnly] is `true`, only the "output" part of the UI is shown (where the entered PIN
 * "shapes" appear), if `false`, the entire UI is shown, including the area where the user can enter
 * their PIN or pattern.
 */
@Composable
private fun SceneScope.FoldSplittable(
    viewModel: BouncerViewModel,
    dialogFactory: BouncerDialogFactory,
    layout: BouncerSceneLayout,
    outputOnly: Boolean,
    isSplit: Boolean,
    modifier: Modifier = Modifier,
) {
    val message: BouncerViewModel.MessageViewModel by viewModel.message.collectAsState()
    val dialogMessage: String? by viewModel.throttlingDialogMessage.collectAsState()
    var dialog: Dialog? by remember { mutableStateOf(null) }
    val actionButton: BouncerActionButtonModel? by viewModel.actionButton.collectAsState()
    val splitRatio =
        LocalContext.current.resources.getFloat(
            R.dimen.motion_layout_half_fold_bouncer_height_ratio
        )

    Column(modifier = modifier.padding(horizontal = 32.dp)) {
        // Content above the fold, when split on a foldable device in a "table top" posture:
        Box(
            modifier =
                Modifier.element(SceneElements.AboveFold)
                    .fillMaxWidth()
                    .then(
                        if (isSplit) {
                            Modifier.weight(splitRatio)
                        } else if (outputOnly) {
                            Modifier.fillMaxHeight()
                        } else {
                            Modifier
                        }
                    ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = layout.topPadding),
            ) {
                Crossfade(
                    targetState = message,
                    label = "Bouncer message",
                    animationSpec = if (message.isUpdateAnimated) tween() else snap(),
                ) { message ->
                    Text(
                        text = message.text,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                if (!outputOnly) {
                    Spacer(Modifier.height(layout.spacingBetweenMessageAndEnteredInput))

                    UserInputArea(
                        viewModel = viewModel,
                        visibility = UserInputAreaVisibility.OUTPUT_ONLY,
                        layout = layout,
                    )
                }
            }

            if (outputOnly) {
                UserInputArea(
                    viewModel = viewModel,
                    visibility = UserInputAreaVisibility.OUTPUT_ONLY,
                    layout = layout,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        // Content below the fold, when split on a foldable device in a "table top" posture:
        Box(
            modifier =
                Modifier.element(SceneElements.BelowFold)
                    .fillMaxWidth()
                    .weight(
                        if (isSplit) {
                            1 - splitRatio
                        } else {
                            1f
                        }
                    ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                if (!outputOnly) {
                    Box(Modifier.weight(1f)) {
                        UserInputArea(
                            viewModel = viewModel,
                            visibility = UserInputAreaVisibility.INPUT_ONLY,
                            layout = layout,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }

                Spacer(Modifier.height(48.dp))

                val actionButtonModifier = Modifier.height(56.dp)

                actionButton.let { actionButtonViewModel ->
                    if (actionButtonViewModel != null) {
                        BouncerActionButton(
                            viewModel = actionButtonViewModel,
                            modifier = actionButtonModifier,
                        )
                    } else {
                        Spacer(modifier = actionButtonModifier)
                    }
                }

                Spacer(Modifier.height(layout.bottomPadding))
            }
        }

        if (dialogMessage != null) {
            if (dialog == null) {
                dialog =
                    dialogFactory().apply {
                        setMessage(dialogMessage)
                        setButton(
                            DialogInterface.BUTTON_NEUTRAL,
                            context.getString(R.string.ok),
                        ) { _, _ ->
                            viewModel.onThrottlingDialogDismissed()
                        }
                        setCancelable(false)
                        setCanceledOnTouchOutside(false)
                        show()
                    }
            }
        } else {
            dialog?.dismiss()
            dialog = null
        }
    }
}

/**
 * Renders the user input area, where the user interacts with the UI to enter their credentials.
 *
 * For example, this can be the pattern input area, the password text box, or pin pad.
 */
@Composable
private fun UserInputArea(
    viewModel: BouncerViewModel,
    visibility: UserInputAreaVisibility,
    layout: BouncerSceneLayout,
    modifier: Modifier = Modifier,
) {
    val authMethodViewModel: AuthMethodBouncerViewModel? by
        viewModel.authMethodViewModel.collectAsState()

    when (val nonNullViewModel = authMethodViewModel) {
        is PinBouncerViewModel ->
            when (visibility) {
                UserInputAreaVisibility.OUTPUT_ONLY ->
                    PinInputDisplay(
                        viewModel = nonNullViewModel,
                        modifier = modifier,
                    )
                UserInputAreaVisibility.INPUT_ONLY ->
                    PinPad(
                        viewModel = nonNullViewModel,
                        layout = layout,
                        modifier = modifier,
                    )
            }
        is PasswordBouncerViewModel ->
            if (visibility == UserInputAreaVisibility.INPUT_ONLY) {
                PasswordBouncer(
                    viewModel = nonNullViewModel,
                    modifier = modifier,
                )
            }
        is PatternBouncerViewModel ->
            if (visibility == UserInputAreaVisibility.INPUT_ONLY) {
                PatternBouncer(
                    viewModel = nonNullViewModel,
                    layout = layout,
                    modifier = modifier.aspectRatio(1f, matchHeightConstraintsFirst = false),
                )
            }
        else -> Unit
    }
}

/**
 * Renders the action button on the bouncer, which triggers either Return to Call or Emergency Call.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BouncerActionButton(
    viewModel: BouncerActionButtonModel,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = viewModel.onClick,
        modifier =
            modifier.thenIf(viewModel.onLongClick != null) {
                Modifier.combinedClickable(
                    onClick = viewModel.onClick,
                    onLongClick = viewModel.onLongClick,
                )
            },
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
    ) {
        Text(
            text = viewModel.label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Renders the UI of the user switcher that's displayed on large screens next to the bouncer UI. */
@Composable
private fun UserSwitcher(
    viewModel: BouncerViewModel,
    modifier: Modifier = Modifier,
) {
    val selectedUserImage by viewModel.selectedUserImage.collectAsState(null)
    val dropdownItems by viewModel.userSwitcherDropdown.collectAsState(emptyList())

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier,
    ) {
        selectedUserImage?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(SelectedUserImageSize),
            )
        }

        val (isDropdownExpanded, setDropdownExpanded) = remember { mutableStateOf(false) }

        dropdownItems.firstOrNull()?.let { firstDropdownItem ->
            Spacer(modifier = Modifier.height(40.dp))

            Box {
                PlatformButton(
                    modifier =
                        Modifier
                            // Remove the built-in padding applied inside PlatformButton:
                            .padding(vertical = 0.dp)
                            .width(UserSwitcherDropdownWidth)
                            .height(UserSwitcherDropdownHeight),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    onClick = { setDropdownExpanded(!isDropdownExpanded) },
                ) {
                    val context = LocalContext.current
                    Text(
                        text = checkNotNull(firstDropdownItem.text.loadText(context)),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                }

                UserSwitcherDropdownMenu(
                    isExpanded = isDropdownExpanded,
                    items = dropdownItems,
                    onDismissed = { setDropdownExpanded(false) },
                )
            }
        }
    }
}

/**
 * Renders the dropdowm menu that displays the actual users and/or user actions that can be
 * selected.
 */
@Composable
private fun UserSwitcherDropdownMenu(
    isExpanded: Boolean,
    items: List<BouncerViewModel.UserSwitcherDropdownItemViewModel>,
    onDismissed: () -> Unit,
) {
    val context = LocalContext.current

    // TODO(b/303071855): once the FR is fixed, remove this composition local override.
    MaterialTheme(
        colorScheme =
            MaterialTheme.colorScheme.copy(
                surface = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(28.dp)),
    ) {
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = onDismissed,
            offset =
                DpOffset(
                    x = 0.dp,
                    y = -UserSwitcherDropdownHeight,
                ),
            modifier = Modifier.width(UserSwitcherDropdownWidth),
        ) {
            items.forEach { userSwitcherDropdownItem ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            icon = userSwitcherDropdownItem.icon,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    },
                    text = {
                        Text(
                            text = checkNotNull(userSwitcherDropdownItem.text.loadText(context)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onDismissed()
                        userSwitcherDropdownItem.onClick()
                    },
                )
            }
        }
    }
}

/**
 * Renders the bouncer UI in split mode, with half on one side and half on the other side, swappable
 * by double-tapping on the side.
 */
@Composable
private fun SplitLayout(
    viewModel: BouncerViewModel,
    dialogFactory: BouncerDialogFactory,
    modifier: Modifier = Modifier,
) {
    SwappableLayout(
        startContent = { startContentModifier ->
            StandardLayout(
                viewModel = viewModel,
                dialogFactory = dialogFactory,
                layout = BouncerSceneLayout.SPLIT,
                outputOnly = true,
                modifier = startContentModifier,
            )
        },
        endContent = { endContentModifier ->
            UserInputArea(
                viewModel = viewModel,
                visibility = UserInputAreaVisibility.INPUT_ONLY,
                layout = BouncerSceneLayout.SPLIT,
                modifier = endContentModifier,
            )
        },
        layout = BouncerSceneLayout.SPLIT,
        modifier = modifier,
    )
}

/**
 * Arranges the given two contents side-by-side, supporting a double tap anywhere on the background
 * to flip their positions.
 */
@Composable
private fun SwappableLayout(
    startContent: @Composable (Modifier) -> Unit,
    endContent: @Composable (Modifier) -> Unit,
    layout: BouncerSceneLayout,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val isLeftToRight = layoutDirection == LayoutDirection.Ltr
    val (isSwapped, setSwapped) = rememberSaveable(isLeftToRight) { mutableStateOf(!isLeftToRight) }

    Row(
        modifier =
            modifier.pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        // Depending on where the user double tapped, switch the elements such that
                        // the endContent is closer to the side that was double tapped.
                        setSwapped(offset.x < size.width / 2)
                    }
                )
            },
    ) {
        val animatedOffset by
            animateFloatAsState(
                targetValue =
                    if (!isSwapped) {
                        // When startContent is first, both elements have their natural placement so
                        // they are not offset in any way.
                        0f
                    } else if (isLeftToRight) {
                        // Since startContent is not first, the elements have to be swapped
                        // horizontally. In the case of LTR locales, this means pushing startContent
                        // to the right, hence the positive number.
                        1f
                    } else {
                        // Since startContent is not first, the elements have to be swapped
                        // horizontally. In the case of RTL locales, this means pushing startContent
                        // to the left, hence the negative number.
                        -1f
                    },
                label = "offset",
            )

        startContent(
            Modifier.fillMaxHeight().weight(1f).graphicsLayer {
                translationX = size.width * animatedOffset
                alpha = animatedAlpha(animatedOffset)
            }
        )

        Box(
            modifier =
                Modifier.fillMaxHeight().weight(1f).graphicsLayer {
                    // A negative sign is used to make sure this is offset in the direction that's
                    // opposite of the direction that the user switcher is pushed in.
                    translationX = -size.width * animatedOffset
                    alpha = animatedAlpha(animatedOffset)
                }
        ) {
            endContent(Modifier.align(layout.swappableEndContentAlignment).widthIn(max = 400.dp))
        }
    }
}

/**
 * Arranges the bouncer contents and user switcher contents side-by-side, supporting a double tap
 * anywhere on the background to flip their positions.
 *
 * In situations when [isUserSwitcherVisible] is `false`, one of two things may happen: either the
 * UI for the bouncer will be shown on its own, taking up one side, with the other side just being
 * empty space or, if that kind of "stand-alone side-by-side" isn't supported, the standard
 * rendering of the bouncer will be used instead of the side-by-side layout.
 */
@Composable
private fun SideBySideLayout(
    viewModel: BouncerViewModel,
    dialogFactory: BouncerDialogFactory,
    isUserSwitcherVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    SwappableLayout(
        startContent = { startContentModifier ->
            if (isUserSwitcherVisible) {
                UserSwitcher(
                    viewModel = viewModel,
                    modifier = startContentModifier,
                )
            } else {
                Box(
                    modifier = startContentModifier,
                )
            }
        },
        endContent = { endContentModifier ->
            StandardLayout(
                viewModel = viewModel,
                dialogFactory = dialogFactory,
                layout = BouncerSceneLayout.SIDE_BY_SIDE,
                modifier = endContentModifier,
            )
        },
        layout = BouncerSceneLayout.SIDE_BY_SIDE,
        modifier = modifier,
    )
}

/** Arranges the bouncer contents and user switcher contents one on top of the other, vertically. */
@Composable
private fun StackedLayout(
    viewModel: BouncerViewModel,
    dialogFactory: BouncerDialogFactory,
    isUserSwitcherVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        if (isUserSwitcherVisible) {
            UserSwitcher(
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }

        StandardLayout(
            viewModel = viewModel,
            dialogFactory = dialogFactory,
            layout = BouncerSceneLayout.STACKED,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

interface BouncerDialogFactory {
    operator fun invoke(): AlertDialog
}

/** Enumerates all supported user-input area visibilities. */
private enum class UserInputAreaVisibility {
    /**
     * Only the area where the user enters the input is shown; the area where the input is reflected
     * back to the user is not shown.
     */
    INPUT_ONLY,
    /**
     * Only the area where the input is reflected back to the user is shown; the area where the
     * input is entered by the user is not shown.
     */
    OUTPUT_ONLY,
}

/**
 * Calculates an alpha for the user switcher and bouncer such that it's at `1` when the offset of
 * the two reaches a stopping point but `0` in the middle of the transition.
 */
private fun animatedAlpha(
    offset: Float,
): Float {
    // Describes a curve that is made of two parabolic U-shaped curves mirrored horizontally around
    // the y-axis. The U on the left runs between x = -1 and x = 0 while the U on the right runs
    // between x = 0 and x = 1.
    //
    // The minimum values of the curves are at -0.5 and +0.5.
    //
    // Both U curves are vertically scaled such that they reach the points (-1, 1) and (1, 1).
    //
    // Breaking it down, it's y = a×(|x|-m)²+b, where:
    // x: the offset
    // y: the alpha
    // m: x-axis center of the parabolic curves, where the minima are.
    // b: y-axis offset to apply to the entire curve so the animation spends more time with alpha =
    // 0.
    // a: amplitude to scale the parabolic curves to reach y = 1 at x = -1, x = 0, and x = +1.
    val m = 0.5f
    val b = -0.25
    val a = (1 - b) / m.pow(2)

    return max(0f, (a * (abs(offset) - m).pow(2) + b).toFloat())
}

private val SelectedUserImageSize = 190.dp
private val UserSwitcherDropdownWidth = SelectedUserImageSize + 2 * 29.dp
private val UserSwitcherDropdownHeight = 60.dp

private object SceneKeys {
    val ContiguousSceneKey = SceneKey("default")
    val SplitSceneKey = SceneKey("split")
}

private object SceneElements {
    val AboveFold = ElementKey("above_fold")
    val BelowFold = ElementKey("below_fold")
}

private val SceneTransitions = transitions {
    from(SceneKeys.ContiguousSceneKey, to = SceneKeys.SplitSceneKey) { spec = tween() }
}

/** Whether a more compact size should be used for various spacing dimensions. */
internal val BouncerSceneLayout.isUseCompactSize: Boolean
    get() =
        when (this) {
            BouncerSceneLayout.SIDE_BY_SIDE -> true
            BouncerSceneLayout.SPLIT -> true
            else -> false
        }

/** Amount of space to place between the message and the entered input UI elements, in dips. */
private val BouncerSceneLayout.spacingBetweenMessageAndEnteredInput: Dp
    get() =
        when {
            this == BouncerSceneLayout.STACKED -> 24.dp
            isUseCompactSize -> 96.dp
            else -> 128.dp
        }

/** Amount of space to place above the topmost UI element, in dips. */
private val BouncerSceneLayout.topPadding: Dp
    get() =
        if (this == BouncerSceneLayout.SPLIT) {
            40.dp
        } else {
            92.dp
        }

/** Amount of space to place below the bottommost UI element, in dips. */
private val BouncerSceneLayout.bottomPadding: Dp
    get() =
        if (this == BouncerSceneLayout.SPLIT) {
            40.dp
        } else {
            48.dp
        }

/** The in-a-box alignment for the content on the "end" side of a swappable layout. */
private val BouncerSceneLayout.swappableEndContentAlignment: Alignment
    get() =
        if (this == BouncerSceneLayout.SPLIT) {
            Alignment.Center
        } else {
            Alignment.BottomCenter
        }
