/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.keyguard;

import static com.google.common.truth.Truth.assertThat;

import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper.RunWithLooper;
import android.view.View;

import com.android.systemui.SysuiTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@RunWithLooper
public class KeyguardMessageAreaTest extends SysuiTestCase {
    private KeyguardMessageArea mKeyguardMessageArea;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mKeyguardMessageArea = new KeyguardMessageArea(mContext, null);
        mKeyguardMessageArea.setBouncerVisible(true);
    }

    @Test
    public void testShowsTextField() {
        mKeyguardMessageArea.setVisibility(View.INVISIBLE);
        mKeyguardMessageArea.setMessage("oobleck");
        assertThat(mKeyguardMessageArea.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mKeyguardMessageArea.getText()).isEqualTo("oobleck");
    }

    @Test
    public void testHiddenWhenBouncerHidden() {
        mKeyguardMessageArea.setBouncerVisible(false);
        mKeyguardMessageArea.setVisibility(View.INVISIBLE);
        mKeyguardMessageArea.setMessage("oobleck");
        assertThat(mKeyguardMessageArea.getVisibility()).isEqualTo(View.INVISIBLE);
        assertThat(mKeyguardMessageArea.getText()).isEqualTo("oobleck");
    }

    @Test
    public void testClearsTextField() {
        mKeyguardMessageArea.setVisibility(View.VISIBLE);
        mKeyguardMessageArea.setMessage("");
        assertThat(mKeyguardMessageArea.getVisibility()).isEqualTo(View.INVISIBLE);
        assertThat(mKeyguardMessageArea.getText()).isEqualTo("");
    }
}
