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

package com.android.systemui.statusbar.pipeline.wifi.domain.interactor

import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.pipeline.shared.data.model.ConnectivitySlot
import com.android.systemui.statusbar.pipeline.shared.data.repository.FakeConnectivityRepository
import com.android.systemui.statusbar.pipeline.wifi.data.model.WifiActivityModel
import com.android.systemui.statusbar.pipeline.wifi.data.model.WifiNetworkModel
import com.android.systemui.statusbar.pipeline.wifi.data.repository.FakeWifiRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
class WifiInteractorTest : SysuiTestCase() {

    private lateinit var underTest: WifiInteractor

    private lateinit var connectivityRepository: FakeConnectivityRepository
    private lateinit var wifiRepository: FakeWifiRepository

    @Before
    fun setUp() {
        connectivityRepository = FakeConnectivityRepository()
        wifiRepository = FakeWifiRepository()
        underTest = WifiInteractor(connectivityRepository, wifiRepository)
    }

    @Test
    fun hasActivityIn_noInOrOut_outputsFalse() = runBlocking(IMMEDIATE) {
        wifiRepository.setWifiNetwork(VALID_WIFI_NETWORK_MODEL)
        wifiRepository.setWifiActivity(
            WifiActivityModel(hasActivityIn = false, hasActivityOut = false)
        )

        var latest: Boolean? = null
        val job = underTest
                .hasActivityIn
                .onEach { latest = it }
                .launchIn(this)

        assertThat(latest).isFalse()

        job.cancel()
    }

    @Test
    fun hasActivityIn_onlyOut_outputsFalse() = runBlocking(IMMEDIATE) {
        wifiRepository.setWifiNetwork(VALID_WIFI_NETWORK_MODEL)
        wifiRepository.setWifiActivity(
            WifiActivityModel(hasActivityIn = false, hasActivityOut = true)
        )

        var latest: Boolean? = null
        val job = underTest
                .hasActivityIn
                .onEach { latest = it }
                .launchIn(this)

        assertThat(latest).isFalse()

        job.cancel()
    }

    @Test
    fun hasActivityIn_onlyIn_outputsTrue() = runBlocking(IMMEDIATE) {
        wifiRepository.setWifiNetwork(VALID_WIFI_NETWORK_MODEL)
        wifiRepository.setWifiActivity(
            WifiActivityModel(hasActivityIn = true, hasActivityOut = false)
        )

        var latest: Boolean? = null
        val job = underTest
                .hasActivityIn
                .onEach { latest = it }
                .launchIn(this)

        assertThat(latest).isTrue()

        job.cancel()
    }

    @Test
    fun hasActivityIn_inAndOut_outputsTrue() = runBlocking(IMMEDIATE) {
        wifiRepository.setWifiNetwork(VALID_WIFI_NETWORK_MODEL)
        wifiRepository.setWifiActivity(
            WifiActivityModel(hasActivityIn = true, hasActivityOut = true)
        )

        var latest: Boolean? = null
        val job = underTest
                .hasActivityIn
                .onEach { latest = it }
                .launchIn(this)

        assertThat(latest).isTrue()

        job.cancel()
    }

    @Test
    fun hasActivityIn_ssidNull_outputsFalse() = runBlocking(IMMEDIATE) {
        wifiRepository.setWifiNetwork(WifiNetworkModel.Active(networkId = 1, ssid = null))
        wifiRepository.setWifiActivity(
            WifiActivityModel(hasActivityIn = true, hasActivityOut = true)
        )

        var latest: Boolean? = null
        val job = underTest
                .hasActivityIn
                .onEach { latest = it }
                .launchIn(this)

        assertThat(latest).isFalse()

        job.cancel()
    }

    @Test
    fun hasActivityIn_inactiveNetwork_outputsFalse() = runBlocking(IMMEDIATE) {
        wifiRepository.setWifiNetwork(WifiNetworkModel.Inactive)
        wifiRepository.setWifiActivity(
            WifiActivityModel(hasActivityIn = true, hasActivityOut = true)
        )

        var latest: Boolean? = null
        val job = underTest
            .hasActivityIn
            .onEach { latest = it }
            .launchIn(this)

        assertThat(latest).isFalse()

        job.cancel()
    }

    @Test
    fun hasActivityIn_carrierMergedNetwork_outputsFalse() = runBlocking(IMMEDIATE) {
        wifiRepository.setWifiNetwork(WifiNetworkModel.CarrierMerged)
        wifiRepository.setWifiActivity(
            WifiActivityModel(hasActivityIn = true, hasActivityOut = true)
        )

        var latest: Boolean? = null
        val job = underTest
            .hasActivityIn
            .onEach { latest = it }
            .launchIn(this)

        assertThat(latest).isFalse()

        job.cancel()
    }

    @Test
    fun hasActivityIn_multipleChanges_multipleOutputChanges() = runBlocking(IMMEDIATE) {
        wifiRepository.setWifiNetwork(VALID_WIFI_NETWORK_MODEL)

        var latest: Boolean? = null
        val job = underTest
                .hasActivityIn
                .onEach { latest = it }
                .launchIn(this)

        // Conduct a series of changes and verify we catch each of them in succession
        wifiRepository.setWifiActivity(
            WifiActivityModel(hasActivityIn = true, hasActivityOut = false)
        )
        yield()
        assertThat(latest).isTrue()

        wifiRepository.setWifiActivity(
            WifiActivityModel(hasActivityIn = false, hasActivityOut = true)
        )
        yield()
        assertThat(latest).isFalse()

        wifiRepository.setWifiActivity(
            WifiActivityModel(hasActivityIn = true, hasActivityOut = true)
        )
        yield()
        assertThat(latest).isTrue()

        wifiRepository.setWifiActivity(
            WifiActivityModel(hasActivityIn = true, hasActivityOut = false)
        )
        yield()
        assertThat(latest).isTrue()

        wifiRepository.setWifiActivity(
            WifiActivityModel(hasActivityIn = false, hasActivityOut = false)
        )
        yield()
        assertThat(latest).isFalse()

        job.cancel()
    }

    @Test
    fun wifiNetwork_matchesRepoWifiNetwork() = runBlocking(IMMEDIATE) {
        val wifiNetwork = WifiNetworkModel.Active(
            networkId = 45,
            isValidated = true,
            level = 3,
            ssid = "AB",
            passpointProviderFriendlyName = "friendly"
        )
        wifiRepository.setWifiNetwork(wifiNetwork)

        var latest: WifiNetworkModel? = null
        val job = underTest
            .wifiNetwork
            .onEach { latest = it }
            .launchIn(this)

        assertThat(latest).isEqualTo(wifiNetwork)

        job.cancel()
    }

    @Test
    fun isForceHidden_repoHasWifiHidden_outputsTrue() = runBlocking(IMMEDIATE) {
        connectivityRepository.setForceHiddenIcons(setOf(ConnectivitySlot.WIFI))

        var latest: Boolean? = null
        val job = underTest
            .isForceHidden
            .onEach { latest = it }
            .launchIn(this)

        assertThat(latest).isTrue()

        job.cancel()
    }

    @Test
    fun isForceHidden_repoDoesNotHaveWifiHidden_outputsFalse() = runBlocking(IMMEDIATE) {
        connectivityRepository.setForceHiddenIcons(setOf())

        var latest: Boolean? = null
        val job = underTest
            .isForceHidden
            .onEach { latest = it }
            .launchIn(this)

        assertThat(latest).isFalse()

        job.cancel()
    }

    companion object {
        val VALID_WIFI_NETWORK_MODEL = WifiNetworkModel.Active(networkId = 1, ssid = "AB")
    }
}

private val IMMEDIATE = Dispatchers.Main.immediate
