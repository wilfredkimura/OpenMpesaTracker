package com.openmpesa.tracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver that listens for incoming SMS text messages broadcasted by Android.
 *
 * When a new text message arrives on the device, the Android operating system wakes up
 * this receiver. In Phase 4, this component will inspect the sender to ensure it is strictly
 * "MPESA", parse the message body, and save the transaction into the local Room database.
 */
class MpesaReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        // Will be connected to the M-Pesa parsing engine and Room database in Phase 4
    }
}
