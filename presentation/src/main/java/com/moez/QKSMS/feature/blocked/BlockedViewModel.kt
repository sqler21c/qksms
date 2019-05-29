/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.moez.QKSMS.feature.blocked

import android.content.Context
import com.moez.QKSMS.common.Navigator
import com.moez.QKSMS.common.androidxcompat.scope
import com.moez.QKSMS.common.base.QkViewModel
import com.moez.QKSMS.common.util.extensions.isInstalled
import com.moez.QKSMS.interactor.MarkUnblocked
import com.moez.QKSMS.manager.AnalyticsManager
import com.moez.QKSMS.repository.ConversationRepository
import com.moez.QKSMS.util.Preferences
import com.uber.autodispose.kotlin.autoDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class BlockedViewModel @Inject constructor(
    private val context: Context,
    private val analytics: AnalyticsManager,
    private val conversationRepo: ConversationRepository,
    private val markUnblocked: MarkUnblocked,
    private val navigator: Navigator,
    private val prefs: Preferences
) : QkViewModel<BlockedView, BlockedState>(BlockedState()) {

    init {
        newState { copy(data = conversationRepo.getBlockedConversations()) }

        disposables += prefs.callControl.asObservable()
                .subscribe { enabled -> newState { copy(ccEnabled = enabled) } }

        disposables += prefs.sia.asObservable()
                .subscribe { enabled -> newState { copy(siaEnabled = enabled) } }
    }

    override fun bindView(view: BlockedView) {
        super.bindView(view)

        view.ccClickedIntent
                .map { context.isInstalled("com.flexaspect.android.everycallcontrol") }
                .map { installed ->
                    if (!installed) {
                        navigator.showCallControl()
                    }

                    val enabled = prefs.callControl.get()
                    analytics.track("Clicked Call Control", Pair("enable", !enabled), Pair("installed", installed))
                    installed && !enabled
                }
                .autoDisposable(view.scope())
                .subscribe(prefs.callControl::set)

        view.siaClickedIntent
                .map {
                    listOf("org.mistergroup.shouldianswer",
                            "org.mistergroup.shouldianswerpersonal",
                            "org.mistergroup.muzutozvednout")
                            .any(context::isInstalled)
                }
                .map { installed ->
                    if (!installed) {
                        navigator.showSia()
                    }

                    val enabled = prefs.sia.get()
                    analytics.track("Clicked SIA", Pair("enable", !enabled), Pair("installed", installed))
                    installed && !enabled
                }
                .autoDisposable(view.scope())
                .subscribe(prefs.sia::set)

        // Show confirm unblock conversation dialog
        view.unblockIntent
                .autoDisposable(view.scope())
                .subscribe { threadId -> view.showUnblockDialog(threadId) }

        // Unblock conversation
        view.confirmUnblockIntent
                .autoDisposable(view.scope())
                .subscribe { threadId -> markUnblocked.execute(listOf(threadId)) }
    }

}