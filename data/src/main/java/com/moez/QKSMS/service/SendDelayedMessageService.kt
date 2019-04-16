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
package com.moez.QKSMS.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moez.QKSMS.data.R
import com.moez.QKSMS.interactor.RetrySending
import com.moez.QKSMS.manager.NotificationManager
import com.moez.QKSMS.repository.ConversationRepository
import com.moez.QKSMS.repository.MessageRepository
import dagger.android.AndroidInjection
import io.reactivex.Flowable
import io.reactivex.disposables.CompositeDisposable
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SendDelayedMessageService : Service() {

    companion object {
        private const val ACTION_START = "com.moez.QKSMS.ACTION_START"
        private const val ACTION_STOP = "com.moez.QKSMS.ACTION_STOP"
        private const val EXTRA_MESSAGE_ID = "com.moez.QKSMS.MESSAGE_ID"

        fun start(context: Context, messageId: Long) {
            val intent = Intent(context, SendDelayedMessageService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_MESSAGE_ID, messageId)

            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context, messageId: Long) {
            val intent = Intent(context, SendDelayedMessageService::class.java)
                    .setAction(ACTION_STOP)
                    .putExtra(EXTRA_MESSAGE_ID, messageId)

            ContextCompat.startForegroundService(context, intent)
        }
    }

    @Inject lateinit var conversationRepo: ConversationRepository
    @Inject lateinit var messageRepo: MessageRepository
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var retrySending: RetrySending

    private val disposables = CompositeDisposable()

    override fun onCreate() = AndroidInjection.inject(this)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent.action) {
            ACTION_START -> start(intent)
            ACTION_STOP -> stop()
        }

        return START_STICKY
    }

    @SuppressLint("CheckResult")
    private fun start(intent: Intent) {
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, 0L)
        val message = messageRepo.getMessage(messageId) ?: return // TODO

        val notification = notificationManager.getNotificationForDelayedMessage(message)
        val notificationManager = NotificationManagerCompat.from(this)

        startForeground(-messageId.toInt(), notification.build())

        val conversation = conversationRepo.getConversation(message.threadId) ?: return // TODO

        val cancelIntent = Intent(this, SendDelayedMessageService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_MESSAGE_ID, messageId)

        val cancelPendingIntent = PendingIntent.getService(this, 4318904, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT) // TODO requestcode

        notification.setContentTitle("Sending message to ${conversation.getTitle()}") // TODO
                .setStyle(NotificationCompat.BigTextStyle().bigText(message.getText()))
                .addAction(R.drawable.abc_ic_clear_material, "Cancel", cancelPendingIntent) // TODO

        val startTime = System.currentTimeMillis()
        val delay = Math.max(0, message.date - startTime)

        Flowable.interval(100, TimeUnit.MILLISECONDS)
                .doOnNext {
                    notification
                            .setProgress(delay.toInt(), (System.currentTimeMillis() - startTime).toInt(), false)
                            .let { builder -> notificationManager.notify(-messageId.toInt(), builder.build()) }
                }
                .takeUntil(Flowable.timer(delay, TimeUnit.MILLISECONDS))
                .ignoreElements()
                .andThen(retrySending.buildObservable(message))
                .ignoreElements()
                .subscribe { stop() }
    }

    private fun stop() {
        disposables.clear()
        stopForeground(true)
        stopSelf()
    }

}
