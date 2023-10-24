/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.server.policy;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.KeyEvent.KEYCODE_ALT_LEFT;
import static android.view.KeyEvent.KEYCODE_ALT_RIGHT;
import static android.view.KeyEvent.KEYCODE_CTRL_LEFT;
import static android.view.KeyEvent.KEYCODE_CTRL_RIGHT;
import static android.view.KeyEvent.KEYCODE_META_LEFT;
import static android.view.KeyEvent.KEYCODE_META_RIGHT;
import static android.view.KeyEvent.KEYCODE_SHIFT_LEFT;
import static android.view.KeyEvent.KEYCODE_SHIFT_RIGHT;
import static android.view.KeyEvent.META_ALT_LEFT_ON;
import static android.view.KeyEvent.META_ALT_ON;
import static android.view.KeyEvent.META_ALT_RIGHT_ON;
import static android.view.KeyEvent.META_CTRL_LEFT_ON;
import static android.view.KeyEvent.META_CTRL_ON;
import static android.view.KeyEvent.META_CTRL_RIGHT_ON;
import static android.view.KeyEvent.META_META_LEFT_ON;
import static android.view.KeyEvent.META_META_ON;
import static android.view.KeyEvent.META_META_RIGHT_ON;
import static android.view.KeyEvent.META_SHIFT_LEFT_ON;
import static android.view.KeyEvent.META_SHIFT_ON;
import static android.view.KeyEvent.META_SHIFT_RIGHT_ON;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;
import static com.android.server.policy.WindowManagerPolicy.ACTION_PASS_TO_USER;

import static java.util.Collections.unmodifiableMap;

import android.content.Context;
import android.os.SystemClock;
import android.util.ArrayMap;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.ViewConfiguration;

import com.android.internal.util.test.FakeSettingsProvider;
import com.android.internal.util.test.FakeSettingsProviderRule;

import org.junit.After;
import org.junit.Rule;

import java.util.Map;

class ShortcutKeyTestBase {
    @Rule public FakeSettingsProviderRule mSettingsProviderRule = FakeSettingsProvider.rule();

    TestPhoneWindowManager mPhoneWindowManager;
    final Context mContext = spy(getInstrumentation().getTargetContext());

    /** Modifier key to meta state */
    protected static final Map<Integer, Integer> MODIFIER;
    static {
        final Map<Integer, Integer> map = new ArrayMap<>();
        map.put(KEYCODE_CTRL_LEFT, META_CTRL_LEFT_ON | META_CTRL_ON);
        map.put(KEYCODE_CTRL_RIGHT, META_CTRL_RIGHT_ON | META_CTRL_ON);
        map.put(KEYCODE_ALT_LEFT, META_ALT_LEFT_ON | META_ALT_ON);
        map.put(KEYCODE_ALT_RIGHT, META_ALT_RIGHT_ON | META_ALT_ON);
        map.put(KEYCODE_SHIFT_LEFT, META_SHIFT_LEFT_ON | META_SHIFT_ON);
        map.put(KEYCODE_SHIFT_RIGHT, META_SHIFT_RIGHT_ON | META_SHIFT_ON);
        map.put(KEYCODE_META_LEFT, META_META_LEFT_ON | META_META_ON);
        map.put(KEYCODE_META_RIGHT, META_META_RIGHT_ON | META_META_ON);

        MODIFIER = unmodifiableMap(map);
    }

    /** Same as {@link setUpPhoneWindowManager(boolean)}, without supporting settings update. */
    protected final void setUpPhoneWindowManager() {
        setUpPhoneWindowManager(/* supportSettingsUpdate= */ false);
    }

    /**
     * Creates and sets up a {@link TestPhoneWindowManager} instance.
     *
     * <p>Subclasses must call this at the start of the test if they intend to interact with phone
     * window manager.
     *
     * @param supportSettingsUpdate {@code true} if this test should read and listen to provider
     *      settings values.
     */
    protected final void setUpPhoneWindowManager(boolean supportSettingsUpdate) {
        doReturn(mSettingsProviderRule.mockContentResolver(mContext))
                .when(mContext).getContentResolver();
        mPhoneWindowManager = new TestPhoneWindowManager(mContext, supportSettingsUpdate);
    }

    @After
    public void tearDown() {
        if (mPhoneWindowManager != null) {
            mPhoneWindowManager.tearDown();
        }
    }

    void sendKeyCombination(int[] keyCodes, long duration, boolean longPress) {
        final long downTime = SystemClock.uptimeMillis();
        final int count = keyCodes.length;
        int metaState = 0;

        for (int i = 0; i < count; i++) {
            final int keyCode = keyCodes[i];
            final KeyEvent event = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode,
                    0 /*repeat*/, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0 /*scancode*/,
                    0 /*flags*/, InputDevice.SOURCE_KEYBOARD);
            event.setDisplayId(DEFAULT_DISPLAY);
            interceptKey(event);
            // The order is important here, metaState could be updated and applied to the next key.
            metaState |= MODIFIER.getOrDefault(keyCode, 0);
        }

        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (longPress) {
            final long nextDownTime = SystemClock.uptimeMillis();
            for (int i = 0; i < count; i++) {
                final int keyCode = keyCodes[i];
                final KeyEvent nextDownEvent = new KeyEvent(downTime, nextDownTime,
                        KeyEvent.ACTION_DOWN, keyCode, 1 /*repeat*/, metaState,
                        KeyCharacterMap.VIRTUAL_KEYBOARD, 0 /*scancode*/,
                        KeyEvent.FLAG_LONG_PRESS /*flags*/, InputDevice.SOURCE_KEYBOARD);
                nextDownEvent.setDisplayId(DEFAULT_DISPLAY);
                interceptKey(nextDownEvent);
            }
        }

        final long eventTime = SystemClock.uptimeMillis();
        for (int i = count - 1; i >= 0; i--) {
            final int keyCode = keyCodes[i];
            final KeyEvent upEvent = new KeyEvent(downTime, eventTime, KeyEvent.ACTION_UP, keyCode,
                    0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0 /*scancode*/, 0 /*flags*/,
                    InputDevice.SOURCE_KEYBOARD);
            upEvent.setDisplayId(DEFAULT_DISPLAY);
            interceptKey(upEvent);
            metaState &= ~MODIFIER.getOrDefault(keyCode, 0);
        }
    }

    void sendKeyCombination(int[] keyCodes, long duration) {
        sendKeyCombination(keyCodes, duration, false /* longPress */);
    }

    void sendLongPressKeyCombination(int[] keyCodes) {
        sendKeyCombination(keyCodes, ViewConfiguration.getLongPressTimeout(), true /* longPress */);
    }

    void sendKey(int keyCode) {
        sendKey(keyCode, false);
    }

    void sendKey(int keyCode, boolean longPress) {
        final long downTime = SystemClock.uptimeMillis();
        final KeyEvent event = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode,
                0 /*repeat*/, 0 /*metaState*/, KeyCharacterMap.VIRTUAL_KEYBOARD, 0 /*scancode*/,
                0 /*flags*/, InputDevice.SOURCE_KEYBOARD);
        event.setDisplayId(DEFAULT_DISPLAY);
        interceptKey(event);

        if (longPress) {
            final long nextDownTime = downTime + ViewConfiguration.getLongPressTimeout();
            final KeyEvent nextDownevent = new KeyEvent(downTime, nextDownTime,
                    KeyEvent.ACTION_DOWN, keyCode, 1 /*repeat*/, 0 /*metaState*/,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0 /*scancode*/,
                    KeyEvent.FLAG_LONG_PRESS /*flags*/, InputDevice.SOURCE_KEYBOARD);
            interceptKey(nextDownevent);
        }

        final long eventTime = longPress
                ? SystemClock.uptimeMillis() + ViewConfiguration.getLongPressTimeout()
                : SystemClock.uptimeMillis();
        final KeyEvent upEvent = new KeyEvent(downTime, eventTime, KeyEvent.ACTION_UP, keyCode,
                0 /*repeat*/, 0 /*metaState*/, KeyCharacterMap.VIRTUAL_KEYBOARD, 0 /*scancode*/,
                0 /*flags*/, InputDevice.SOURCE_KEYBOARD);
        upEvent.setDisplayId(DEFAULT_DISPLAY);
        interceptKey(upEvent);
    }

    private void interceptKey(KeyEvent keyEvent) {
        int actions = mPhoneWindowManager.interceptKeyBeforeQueueing(keyEvent);
        if ((actions & ACTION_PASS_TO_USER) != 0) {
            if (0 == mPhoneWindowManager.interceptKeyBeforeDispatching(keyEvent)) {
                mPhoneWindowManager.dispatchUnhandledKey(keyEvent);
            }
        }
        mPhoneWindowManager.dispatchAllPendingEvents();
    }
}
