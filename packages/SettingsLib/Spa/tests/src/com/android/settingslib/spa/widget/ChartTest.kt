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

package com.android.settingslib.spa.widget

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.spa.widget.chart.BarChart
import com.android.settingslib.spa.widget.chart.BarChartData
import com.android.settingslib.spa.widget.chart.LineChart
import com.android.settingslib.spa.widget.chart.LineChartData
import com.android.settingslib.spa.widget.chart.PieChart
import com.android.settingslib.spa.widget.chart.PieChartData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChartTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val Chart = SemanticsPropertyKey<String>("Chart")
    private var SemanticsPropertyReceiver.chart by Chart
    private fun hasChart(chart: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(Chart, chart)

    @Test
    fun line_chart_displayed() {
        composeTestRule.setContent {
            LineChart(
                chartDataList = listOf(
                    LineChartData(x = 0f, y = 0f),
                    LineChartData(x = 1f, y = 0.1f),
                    LineChartData(x = 2f, y = 0.2f),
                    LineChartData(x = 3f, y = 0.7f),
                    LineChartData(x = 4f, y = 0.9f),
                    LineChartData(x = 5f, y = 1.0f),
                    LineChartData(x = 6f, y = 0.8f),
                ),
                modifier = Modifier.semantics { chart = "line" }
            )
        }

        composeTestRule.onNode(hasChart("line")).assertIsDisplayed()
    }

    @Test
    fun bar_chart_displayed() {
        composeTestRule.setContent {
            BarChart(
                chartDataList = listOf(
                    BarChartData(x = 0f, y = 12f),
                    BarChartData(x = 1f, y = 5f),
                    BarChartData(x = 2f, y = 21f),
                    BarChartData(x = 3f, y = 5f),
                    BarChartData(x = 4f, y = 10f),
                    BarChartData(x = 5f, y = 9f),
                    BarChartData(x = 6f, y = 1f),
                ),
                yAxisMaxValue = 30f,
                modifier = Modifier.semantics { chart = "bar" }
            )
        }

        composeTestRule.onNode(hasChart("bar")).assertIsDisplayed()
    }

    @Test
    fun pie_chart_displayed() {
        composeTestRule.setContent {
            PieChart(
                chartDataList = listOf(
                    PieChartData(label = "Settings", value = 20f),
                    PieChartData(label = "Chrome", value = 5f),
                    PieChartData(label = "Gmail", value = 3f),
                ),
                centerText = "Today",
                modifier = Modifier.semantics { chart = "pie" }
            )
        }

        composeTestRule.onNode(hasChart("pie")).assertIsDisplayed()
    }
}
