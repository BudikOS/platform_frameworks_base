/*
 * Copyright (C) 2026 Budík OS
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

package com.android.systemui.notifications.ui.composable

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.plugins.activityStarter
import com.android.systemui.statusbar.notification.collection.notifPipeline
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.notification.row.onUserInteractionCallback
import com.android.systemui.statusbar.notification.stack.ui.view.notificationScrollView
import com.android.systemui.statusbar.notificationClickNotifier

val Kosmos.budikNotificationActions by
    Kosmos.Fixture {
        BudikNotificationActions(
            repository = activeNotificationListRepository,
            notifPipeline = { notifPipeline },
            onUserInteractionCallback = { onUserInteractionCallback },
            activityStarter = { activityStarter },
            clickNotifier = { notificationClickNotifier },
            stackScrollView = { notificationScrollView },
            bgDispatcher = testDispatcher,
        )
    }
