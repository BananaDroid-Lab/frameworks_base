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

package com.android.systemui.statusbar.notification.dagger;

import android.content.Context;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.systemui.CoreStartable;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.UiBackground;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.res.R;
import com.android.systemui.scene.domain.interactor.WindowRootViewVisibilityInteractor;
import com.android.systemui.shade.ShadeEventsModule;
import com.android.systemui.statusbar.NotificationListener;
import com.android.systemui.statusbar.notification.NotificationActivityStarter;
import com.android.systemui.statusbar.notification.NotificationLaunchAnimatorControllerProvider;
import com.android.systemui.statusbar.notification.VisibilityLocationProvider;
import com.android.systemui.statusbar.notification.collection.NotifInflaterImpl;
import com.android.systemui.statusbar.notification.collection.NotifLiveDataStore;
import com.android.systemui.statusbar.notification.collection.NotifLiveDataStoreImpl;
import com.android.systemui.statusbar.notification.collection.NotifPipeline;
import com.android.systemui.statusbar.notification.collection.NotifPipelineChoreographerModule;
import com.android.systemui.statusbar.notification.collection.coordinator.ShadeEventCoordinator;
import com.android.systemui.statusbar.notification.collection.coordinator.dagger.CoordinatorsModule;
import com.android.systemui.statusbar.notification.collection.inflation.BindEventManager;
import com.android.systemui.statusbar.notification.collection.inflation.BindEventManagerImpl;
import com.android.systemui.statusbar.notification.collection.inflation.NotifInflater;
import com.android.systemui.statusbar.notification.collection.inflation.OnUserInteractionCallbackImpl;
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection;
import com.android.systemui.statusbar.notification.collection.provider.NotificationDismissibilityProvider;
import com.android.systemui.statusbar.notification.collection.provider.NotificationDismissibilityProviderImpl;
import com.android.systemui.statusbar.notification.collection.provider.NotificationVisibilityProviderImpl;
import com.android.systemui.statusbar.notification.collection.provider.VisibilityLocationProviderDelegator;
import com.android.systemui.statusbar.notification.collection.render.GroupExpansionManager;
import com.android.systemui.statusbar.notification.collection.render.GroupExpansionManagerImpl;
import com.android.systemui.statusbar.notification.collection.render.GroupMembershipManager;
import com.android.systemui.statusbar.notification.collection.render.GroupMembershipManagerImpl;
import com.android.systemui.statusbar.notification.collection.render.NotifGutsViewManager;
import com.android.systemui.statusbar.notification.collection.render.NotifShadeEventSource;
import com.android.systemui.statusbar.notification.collection.render.NotificationVisibilityProvider;
import com.android.systemui.statusbar.notification.data.NotificationDataLayerModule;
import com.android.systemui.statusbar.notification.data.repository.NotificationExpansionRepository;
import com.android.systemui.statusbar.notification.icon.ConversationIconManager;
import com.android.systemui.statusbar.notification.icon.IconManager;
import com.android.systemui.statusbar.notification.init.NotificationsController;
import com.android.systemui.statusbar.notification.init.NotificationsControllerImpl;
import com.android.systemui.statusbar.notification.init.NotificationsControllerStub;
import com.android.systemui.statusbar.notification.interruption.KeyguardNotificationVisibilityProviderModule;
import com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProvider;
import com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProviderImpl;
import com.android.systemui.statusbar.notification.logging.NotificationLogger;
import com.android.systemui.statusbar.notification.logging.NotificationPanelLogger;
import com.android.systemui.statusbar.notification.logging.NotificationPanelLoggerImpl;
import com.android.systemui.statusbar.notification.row.NotificationGutsManager;
import com.android.systemui.statusbar.notification.row.OnUserInteractionCallback;
import com.android.systemui.statusbar.notification.row.ui.viewmodel.ActivatableNotificationViewModelModule;
import com.android.systemui.statusbar.notification.stack.NotificationListContainer;
import com.android.systemui.statusbar.notification.stack.NotificationSectionsManager;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController;
import com.android.systemui.statusbar.notification.stack.StackScrollAlgorithm;
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationListViewModelModule;
import com.android.systemui.statusbar.phone.KeyguardBypassController;
import com.android.systemui.statusbar.phone.StatusBarNotificationActivityStarter;
import com.android.systemui.statusbar.policy.HeadsUpManager;
import com.android.systemui.util.kotlin.JavaAdapter;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

import java.util.concurrent.Executor;

import javax.inject.Provider;

/**
 * Dagger Module for classes found within the com.android.systemui.statusbar.notification package.
 */
@Module(includes = {
        CoordinatorsModule.class,
        KeyguardNotificationVisibilityProviderModule.class,
        ShadeEventsModule.class,
        NotificationDataLayerModule.class,
        NotifPipelineChoreographerModule.class,
        NotificationSectionHeadersModule.class,
        NotificationListViewModelModule.class,
        ActivatableNotificationViewModelModule.class,
        NotificationMemoryModule.class,
})
public interface NotificationsModule {
    @Binds
    StackScrollAlgorithm.SectionProvider bindSectionProvider(NotificationSectionsManager impl);

    @Binds
    StackScrollAlgorithm.BypassController bindBypassController(KeyguardBypassController impl);

    /** Provides an instance of {@link NotifGutsViewManager} */
    @Binds
    NotifGutsViewManager bindNotifGutsViewManager(NotificationGutsManager notificationGutsManager);

    /** Binds {@link NotificationGutsManager} as a {@link CoreStartable}. */
    @Binds
    @IntoMap
    @ClassKey(NotificationGutsManager.class)
    CoreStartable bindsNotificationGutsManager(NotificationGutsManager notificationGutsManager);


    /** Provides an instance of {@link VisibilityLocationProvider} */
    @Binds
    VisibilityLocationProvider bindVisibilityLocationProvider(
            VisibilityLocationProviderDelegator visibilityLocationProviderDelegator);

    /** Provides an instance of {@link NotificationLogger} */
    @SysUISingleton
    @Provides
    static NotificationLogger provideNotificationLogger(
            NotificationListener notificationListener,
            @UiBackground Executor uiBgExecutor,
            NotifLiveDataStore notifLiveDataStore,
            NotificationVisibilityProvider visibilityProvider,
            NotifPipeline notifPipeline,
            StatusBarStateController statusBarStateController,
            WindowRootViewVisibilityInteractor windowRootViewVisibilityInteractor,
            JavaAdapter javaAdapter,
            NotificationLogger.ExpansionStateLogger expansionStateLogger,
            NotificationPanelLogger notificationPanelLogger) {
        return new NotificationLogger(
                notificationListener,
                uiBgExecutor,
                notifLiveDataStore,
                visibilityProvider,
                notifPipeline,
                statusBarStateController,
                windowRootViewVisibilityInteractor,
                javaAdapter,
                expansionStateLogger,
                notificationPanelLogger);
    }

    /** Binds {@link NotificationLogger} as a {@link CoreStartable}. */
    @Binds
    @IntoMap
    @ClassKey(NotificationLogger.class)
    CoreStartable bindsNotificationLogger(NotificationLogger notificationLogger);

    /** Provides an instance of {@link NotificationPanelLogger} */
    @SysUISingleton
    @Provides
    static NotificationPanelLogger provideNotificationPanelLogger() {
        return new NotificationPanelLoggerImpl();
    }

    /** Provides an instance of {@link GroupMembershipManager} */
    @Binds
    GroupMembershipManager provideGroupMembershipManager(GroupMembershipManagerImpl impl);

    /** Provides an instance of {@link GroupExpansionManager} */
    @Binds
    GroupExpansionManager provideGroupExpansionManager(GroupExpansionManagerImpl impl);

    /** Provides an instance of {@link NotificationActivityStarter}. */
    @Binds
    NotificationActivityStarter bindActivityStarter(StatusBarNotificationActivityStarter impl);

    /** Initializes the notification data pipeline (can be disabled via config). */
    @SysUISingleton
    @Provides
    static NotificationsController provideNotificationsController(
            Context context,
            Provider<NotificationsControllerImpl> realController,
            Provider<NotificationsControllerStub> stubController) {
        if (context.getResources().getBoolean(R.bool.config_renderNotifications)) {
            return realController.get();
        } else {
            return stubController.get();
        }
    }

    /** Provides the container for the notification list. */
    @Provides
    @SysUISingleton
    static NotificationListContainer provideListContainer(
            NotificationStackScrollLayoutController nsslController) {
        return nsslController.getNotificationListContainer();
    }

    /** Provides notification launch animator. */
    @Provides
    @SysUISingleton
    static NotificationLaunchAnimatorControllerProvider provideNotifLaunchAnimControllerProvider(
            NotificationExpansionRepository notificationExpansionRepository,
            NotificationListContainer notificationListContainer,
            HeadsUpManager headsUpManager,
            InteractionJankMonitor jankMonitor) {
        return new NotificationLaunchAnimatorControllerProvider(
                notificationExpansionRepository,
                notificationListContainer,
                headsUpManager,
                jankMonitor);
    }

    /**
     * Provide the active notification collection managing the notifications to render.
     */
    @Binds
    CommonNotifCollection provideCommonNotifCollection(NotifPipeline pipeline);

    /**
     * Provide the object which can be used to obtain dismissibility of a Notification.
     */
    @Binds
    NotificationDismissibilityProvider provideNotificationDismissibilityProvider(
            NotificationDismissibilityProviderImpl impl);

    /**
     * Provide the object which can be used to obtain NotificationVisibility objects.
     */
    @Binds
    NotificationVisibilityProvider provideNotificationVisibilityProvider(
            NotificationVisibilityProviderImpl impl);

    /**
     * Provide the active implementation for presenting notifications.
     */
    @Binds
    NotifShadeEventSource provideNotifShadeEventSource(ShadeEventCoordinator shadeEventCoordinator);

    /**
     * Provide a dismissal callback that's triggered when a user manually dismissed a notification
     * from the notification shade or it gets auto-cancelled by click.
     */
    @Binds
    OnUserInteractionCallback provideOnUserInteractionCallback(OnUserInteractionCallbackImpl impl);

    /** */
    @Binds
    NotificationInterruptStateProvider bindNotificationInterruptStateProvider(
            NotificationInterruptStateProviderImpl notificationInterruptStateProviderImpl);

    /** */
    @Binds
    NotifInflater bindNotifInflater(NotifInflaterImpl notifInflaterImpl);

    /** */
    @Binds
    ConversationIconManager bindConversationIconManager(IconManager iconManager);

    /** */
    @Binds
    BindEventManager bindBindEventManagerImpl(BindEventManagerImpl bindEventManagerImpl);

    /** */
    @Binds
    NotifLiveDataStore bindNotifLiveDataStore(NotifLiveDataStoreImpl notifLiveDataStoreImpl);
}
