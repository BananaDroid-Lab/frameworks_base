/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.dagger;

import static com.android.systemui.Dependency.TIME_TICK_HANDLER_NAME;

import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.INotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.AmbientDisplayConfiguration;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.DisplayMetrics;
import android.view.Choreographer;
import android.view.IWindowManager;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.UiEventLogger;
import com.android.internal.logging.UiEventLoggerImpl;
import com.android.internal.util.NotificationMessagingUtil;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.ViewMediatorCallback;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.systemui.Prefs;
import com.android.systemui.accessibility.ModeSwitchesController;
import com.android.systemui.accessibility.SystemActions;
import com.android.systemui.assist.AssistManager;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.broadcast.logging.BroadcastDispatcherLogger;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.doze.AlwaysOnDisplayPolicy;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.model.SysUiState;
import com.android.systemui.navigationbar.NavigationBarController;
import com.android.systemui.navigationbar.NavigationModeController;
import com.android.systemui.plugins.PluginInitializerImpl;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.recents.OverviewProxyService;
import com.android.systemui.recents.Recents;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.shared.plugins.PluginManager;
import com.android.systemui.shared.plugins.PluginManagerImpl;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.DevicePolicyManagerWrapper;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.NotificationRemoteInputManager;
import com.android.systemui.statusbar.phone.AutoHideController;
import com.android.systemui.statusbar.phone.ConfigurationControllerImpl;
import com.android.systemui.statusbar.phone.ShadeController;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.statusbar.policy.AccessibilityManagerWrapper;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.DataSaverController;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.util.leak.LeakDetector;
import com.android.wm.shell.splitscreen.SplitScreen;

import java.util.Optional;
import java.util.concurrent.Executor;

import javax.inject.Named;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

/**
 * Provides dependencies for the root component of sysui injection.
 *
 * Only SystemUI owned classes and instances should go in here. Other, framework-owned classes
 * should go in {@link FrameworkServicesModule}.
 *
 * See SystemUI/docs/dagger.md
 */
@Module(includes = {NightDisplayListenerModule.class})
public class DependencyProvider {

    /** */
    @Provides
    @SysUISingleton
    @Named(TIME_TICK_HANDLER_NAME)
    public Handler provideTimeTickHandler() {
        HandlerThread thread = new HandlerThread("TimeTick");
        thread.start();
        return new Handler(thread.getLooper());
    }

    /** */
    @Provides
    @Main
    public SharedPreferences provideSharePreferences(Context context) {
        return Prefs.get(context);
    }

    /** */
    @Provides
    public AmbientDisplayConfiguration provideAmbientDisplayConfiguration(Context context) {
        return new AmbientDisplayConfiguration(context);
    }

    /** */
    @Provides
    public Handler provideHandler() {
        return new Handler();
    }

    /** */
    @Provides
    @SysUISingleton
    public DataSaverController provideDataSaverController(NetworkController networkController) {
        return networkController.getDataSaverController();
    }

    /** */
    @Provides
    @SysUISingleton
    public DisplayMetrics provideDisplayMetrics(Context context, WindowManager windowManager) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        context.getDisplay().getMetrics(displayMetrics);
        return displayMetrics;
    }

    /** */
    @Provides
    @SysUISingleton
    public INotificationManager provideINotificationManager() {
        return INotificationManager.Stub.asInterface(
                ServiceManager.getService(Context.NOTIFICATION_SERVICE));
    }

    /** */
    @Provides
    @SysUISingleton
    public LayoutInflater providerLayoutInflater(Context context) {
        return LayoutInflater.from(context);
    }

    /** */
    @Provides
    @SysUISingleton
    public LeakDetector provideLeakDetector() {
        return LeakDetector.create();

    }

    @SuppressLint("MissingPermission")
    @SysUISingleton
    @Provides
    @Nullable
    static LocalBluetoothManager provideLocalBluetoothController(Context context,
            @Background Handler bgHandler) {
        return LocalBluetoothManager.create(context, bgHandler, UserHandle.ALL);
    }

    /** */
    @Provides
    @SysUISingleton
    public MetricsLogger provideMetricsLogger() {
        return new MetricsLogger();
    }

    /** */
    @Provides
    @SysUISingleton
    public PluginManager providePluginManager(Context context) {
        return new PluginManagerImpl(context, new PluginInitializerImpl());
    }

    /** */
    @Provides
    @SysUISingleton
    public NavigationBarController provideNavigationBarController(Context context,
            WindowManager windowManager,
            Lazy<AssistManager> assistManagerLazy,
            AccessibilityManager accessibilityManager,
            AccessibilityManagerWrapper accessibilityManagerWrapper,
            DeviceProvisionedController deviceProvisionedController,
            MetricsLogger metricsLogger,
            OverviewProxyService overviewProxyService,
            NavigationModeController navigationModeController,
            StatusBarStateController statusBarStateController,
            SysUiState sysUiFlagsContainer,
            BroadcastDispatcher broadcastDispatcher,
            CommandQueue commandQueue,
            Optional<SplitScreen> splitScreenOptional,
            Optional<Recents> recentsOptional,
            Lazy<StatusBar> statusBarLazy,
            ShadeController shadeController,
            NotificationRemoteInputManager notificationRemoteInputManager,
            SystemActions systemActions,
            @Main Handler mainHandler,
            UiEventLogger uiEventLogger,
            ConfigurationController configurationController) {
        return new NavigationBarController(context,
                windowManager,
                assistManagerLazy,
                accessibilityManager,
                accessibilityManagerWrapper,
                deviceProvisionedController,
                metricsLogger,
                overviewProxyService,
                navigationModeController,
                statusBarStateController,
                sysUiFlagsContainer,
                broadcastDispatcher,
                commandQueue,
                splitScreenOptional,
                recentsOptional,
                statusBarLazy,
                shadeController,
                notificationRemoteInputManager,
                systemActions,
                mainHandler,
                uiEventLogger,
                configurationController);
    }

    /** */
    @Provides
    @SysUISingleton
    public ConfigurationController provideConfigurationController(Context context) {
        return new ConfigurationControllerImpl(context);
    }

    /** */
    @SysUISingleton
    @Provides
    public AutoHideController provideAutoHideController(Context context,
            @Main Handler mainHandler, IWindowManager iWindowManager) {
        return new AutoHideController(context, mainHandler, iWindowManager);
    }

    /** */
    @Provides
    @SysUISingleton
    public ActivityManagerWrapper provideActivityManagerWrapper() {
        return ActivityManagerWrapper.getInstance();
    }

    /** Provides and initializes the {#link BroadcastDispatcher} for SystemUI */
    @Provides
    @SysUISingleton
    public BroadcastDispatcher providesBroadcastDispatcher(
            Context context,
            @Background Looper backgroundLooper,
            @Background Executor backgroundExecutor,
            DumpManager dumpManager,
            BroadcastDispatcherLogger logger,
            UserTracker userTracker
    ) {
        BroadcastDispatcher bD = new BroadcastDispatcher(context, backgroundLooper,
                backgroundExecutor, dumpManager, logger, userTracker);
        bD.initialize();
        return bD;
    }

    /** */
    @Provides
    @SysUISingleton
    public DevicePolicyManagerWrapper provideDevicePolicyManagerWrapper() {
        return DevicePolicyManagerWrapper.getInstance();
    }

    /** */
    @Provides
    @SysUISingleton
    public LockPatternUtils provideLockPatternUtils(Context context) {
        return new LockPatternUtils(context);
    }

    /** */
    @Provides
    public AlwaysOnDisplayPolicy provideAlwaysOnDisplayPolicy(Context context) {
        return new AlwaysOnDisplayPolicy(context);
    }

    /***/
    @Provides
    public NotificationMessagingUtil provideNotificationMessagingUtil(Context context) {
        return new NotificationMessagingUtil(context);
    }

    /** */
    @Provides
    public ViewMediatorCallback providesViewMediatorCallback(KeyguardViewMediator viewMediator) {
        return viewMediator.getViewMediatorCallback();
    }

    /** */
    @Provides
    public SystemActions providesSystemActions(Context context) {
        return new SystemActions(context);
    }

    /** */
    @Provides
    @SysUISingleton
    public Choreographer providesChoreographer() {
        return Choreographer.getInstance();
    }

    /** Provides an instance of {@link com.android.internal.logging.UiEventLogger} */
    @Provides
    @SysUISingleton
    static UiEventLogger provideUiEventLogger() {
        return new UiEventLoggerImpl();
    }

    /** */
    @Provides
    @SysUISingleton
    public ModeSwitchesController providesModeSwitchesController(Context context) {
        return new ModeSwitchesController(context);
    }
}
