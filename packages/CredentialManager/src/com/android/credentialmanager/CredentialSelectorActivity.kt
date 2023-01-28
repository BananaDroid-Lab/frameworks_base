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

package com.android.credentialmanager

import android.content.Intent
import android.credentials.ui.RequestInfo
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.credentialmanager.common.Constants
import com.android.credentialmanager.common.DialogState
import com.android.credentialmanager.common.ProviderActivityResult
import com.android.credentialmanager.createflow.CreateCredentialScreen
import com.android.credentialmanager.createflow.CreateCredentialViewModel
import com.android.credentialmanager.getflow.GetCredentialScreen
import com.android.credentialmanager.getflow.GetCredentialViewModel
import com.android.credentialmanager.ui.theme.CredentialSelectorTheme

@ExperimentalMaterialApi
class CredentialSelectorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val credManRepo = CredentialManagerRepo(this, intent)
        UserConfigRepo.setup(this)
        try {
            setContent {
                CredentialSelectorTheme {
                    CredentialManagerBottomSheet(credManRepo.requestInfo.type, credManRepo)
                }
            }
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG, "Failed to show the credential selector", e)
            reportInstantiationErrorAndFinishActivity(credManRepo)
        }
    }

    @ExperimentalMaterialApi
    @Composable
    fun CredentialManagerBottomSheet(requestType: String, credManRepo: CredentialManagerRepo) {
        val providerActivityResult = remember { mutableStateOf<ProviderActivityResult?>(null) }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) {
            providerActivityResult.value = ProviderActivityResult(it.resultCode, it.data)
        }
        when (requestType) {
            RequestInfo.TYPE_CREATE -> {
                val viewModel: CreateCredentialViewModel = viewModel {
                    val vm = CreateCredentialViewModel.newInstance(
                        credManRepo = credManRepo,
                        providerEnableListUiState =
                        credManRepo.getCreateProviderEnableListInitialUiState(),
                        providerDisableListUiState =
                        credManRepo.getCreateProviderDisableListInitialUiState(),
                        requestDisplayInfoUiState =
                        credManRepo.getCreateRequestDisplayInfoInitialUiState()
                    )
                    if (vm == null) {
                        // Input parsing failed. Close the activity.
                        reportInstantiationErrorAndFinishActivity(credManRepo)
                        throw IllegalStateException()
                    } else {
                        vm
                    }
                }
                LaunchedEffect(viewModel.uiState.dialogState) {
                    handleDialogState(viewModel.uiState.dialogState)
                }
                providerActivityResult.value?.let {
                    viewModel.onProviderActivityResult(it)
                    providerActivityResult.value = null
                }
                CreateCredentialScreen(
                    viewModel = viewModel,
                    providerActivityLauncher = launcher
                )
            }
            RequestInfo.TYPE_GET -> {
                val viewModel: GetCredentialViewModel = viewModel {
                    val initialUiState = credManRepo.getCredentialInitialUiState()
                    if (initialUiState == null) {
                        // Input parsing failed. Close the activity.
                        reportInstantiationErrorAndFinishActivity(credManRepo)
                        throw IllegalStateException()
                    } else {
                        GetCredentialViewModel(credManRepo, initialUiState)
                    }
                }
                LaunchedEffect(viewModel.uiState.dialogState) {
                    handleDialogState(viewModel.uiState.dialogState)
                }
                providerActivityResult.value?.let {
                    viewModel.onProviderActivityResult(it)
                    providerActivityResult.value = null
                }
                GetCredentialScreen(viewModel = viewModel, providerActivityLauncher = launcher)
            }
            else -> {
                Log.d(Constants.LOG_TAG, "Unknown type, not rendering any UI")
                reportInstantiationErrorAndFinishActivity(credManRepo)
            }
        }
    }

    private fun reportInstantiationErrorAndFinishActivity(credManRepo: CredentialManagerRepo) {
        Log.w(Constants.LOG_TAG, "Finishing the activity due to instantiation failure.")
        credManRepo.onParsingFailureCancel()
        this@CredentialSelectorActivity.finish()
    }

    private fun handleDialogState(dialogState: DialogState) {
        if (dialogState == DialogState.COMPLETE) {
            Log.d(Constants.LOG_TAG, "Received signal to finish the activity.")
            this@CredentialSelectorActivity.finish()
        } else if (dialogState == DialogState.CANCELED_FOR_SETTINGS) {
            Log.d(Constants.LOG_TAG, "Received signal to finish the activity and launch settings.")
            this@CredentialSelectorActivity.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
            this@CredentialSelectorActivity.finish()
        }
    }
}
