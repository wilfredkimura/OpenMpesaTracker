package com.openmpesa.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import com.openmpesa.tracker.MpesaApplication
import com.openmpesa.tracker.data.entity.MpesaTransactionEntity
import com.openmpesa.tracker.data.repository.TransactionRepository
import com.openmpesa.tracker.engine.MpesaEngineParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that intercepts incoming SMS text messages in real-time.
 *
 * When Safaricom transmits an M-Pesa transaction confirmation SMS to the user's phone,
 * Android broadcasts the `android.provider.Telephony.SMS_RECEIVED` action.
 *
 * Privacy & Performance Guarantees:
 * 1. Strictly filters by sender address "MPESA": Any personal texts or messages from friends,
 *    banks, or other senders are discarded immediately without inspection.
 * 2. Uses Android's [goAsync] API combined with Kotlin Coroutines on [Dispatchers.IO]:
 *    Ensures database writes never block the Android main thread or trigger Application
 *    Not Responding (ANR) warnings.
 * 3. Handles multi-part SMS messages: Long confirmation messages split across multiple PDUs
 *    are concatenated together before regex evaluation.
 */
class MpesaReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") {
            return
        }

        // Get the application-wide repository
        val repository = (context.applicationContext as? MpesaApplication)?.container?.transactionRepository

        // Inform the system that this receiver needs to do asynchronous work in the background
        val pendingResult = goAsync()

        // Launch in a background coroutine
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                if (repository != null) {
                    processIntent(intent, repository)
                }
            } catch (e: Exception) {
                // Log or gracefully absorb any exceptions to prevent app crashes on incoming texts
                e.printStackTrace()
            } finally {
                // Must always finish the pendingResult to release system receiver locks
                pendingResult.finish()
            }
        }
    }

    /**
     * Extracts and parses M-Pesa messages from the received SMS intent and saves them to the repository.
     * Exposed with package visibility to enable fast, clean unit testing.
     */
    suspend fun processIntent(
        intent: Intent,
        repository: TransactionRepository
    ): List<MpesaTransactionEntity> {
        val bundle = intent.extras ?: return emptyList()
        val pdus = bundle.get("pdus") as? Array<*> ?: return emptyList()
        val format = bundle.getString("format")

        // Map each sender to the concatenated text of their message parts
        val messagesBySender = mutableMapOf<String, StringBuilder>()
        var messageTimestamp = System.currentTimeMillis()

        for (pdu in pdus) {
            val pduBytes = pdu as? ByteArray ?: continue
            val smsMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SmsMessage.createFromPdu(pduBytes, format)
            } else {
                @Suppress("DEPRECATION")
                SmsMessage.createFromPdu(pduBytes)
            } ?: continue

            val sender = smsMessage.originatingAddress.orEmpty().trim()
            val body = smsMessage.messageBody.orEmpty()
            messageTimestamp = smsMessage.timestampMillis

            val current = messagesBySender.getOrPut(sender) { StringBuilder() }
            current.append(body)
        }

        val savedTransactions = mutableListOf<MpesaTransactionEntity>()

        // Strictly target Safaricom's official sender ID
        for ((sender, bodyBuilder) in messagesBySender) {
            val fullBody = bodyBuilder.toString()
            val parsed = processMessage(sender, fullBody, messageTimestamp, repository)
            if (parsed != null) {
                savedTransactions.add(parsed)
            }
        }

        return savedTransactions
    }

    /**
     * Inspects a single sender and message body, verifying that sender is strictly "MPESA",
     * parsing the text, and storing it into the repository.
     */
    suspend fun processMessage(
        sender: String,
        body: String,
        timestamp: Long,
        repository: TransactionRepository
    ): MpesaTransactionEntity? {
        if (!sender.equals("MPESA", ignoreCase = true)) {
            // Discard any non-Mpesa messages immediately to protect privacy
            return null
        }

        val parsed = MpesaEngineParser.parse(body, timestamp) ?: return null
        val wasInserted = repository.insertTransaction(parsed)
        return if (wasInserted) parsed else null
    }
}
