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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var smsSync: SmsSyncService? = null
    private val PERMISSION_REQUEST_CODE = 101

    private val smsRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        startSmsSyncIfReady()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // use applicationContext to avoid leaks / nulls
        smsSync = SmsSyncService(applicationContext)

        if (!hasSmsPermissions()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS),
                PERMISSION_REQUEST_CODE
            )
        } else {
            ensureDefaultSmsApp()
        }
    }

    private fun hasSmsPermissions(): Boolean {
        val permissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun ensureDefaultSmsApp() {
        val myPackageName = packageName
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    smsRoleLauncher.launch(intent)
                } else {
                    startSmsSyncIfReady()
                }
            } else {
                val defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(this)
                if (defaultSmsApp != myPackageName) {
                    val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, myPackageName)
                    startActivity(intent)
                } else {
                    startSmsSyncIfReady()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error setting default SMS: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startSmsSyncIfReady() {
        val sync = smsSync
        if (sync == null) {
            Toast.makeText(this, "SmsSync not initialized", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            Toast.makeText(this, "SMS Sync Started", Toast.LENGTH_SHORT).show()
            sync.uploadInitialSms(20)
            sync.listenForFirebaseDeletion()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Sync error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            ensureDefaultSmsApp()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}
