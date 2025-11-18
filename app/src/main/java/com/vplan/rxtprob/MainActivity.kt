package com.vplan.rxtprob

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseApp
import com.vplan.rxtprob.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var smsSync: SmsSyncService

    private lateinit var googleSignInClient: GoogleSignInClient
    private var gmailSyncService: GmailSyncService? = null
    private val RC_GMAIL_SIGN_IN = 300

    private val REQUEST_NOTIFICATION_PERMISSION = 100
    private val REQUEST_SMS_PERMISSION = 101
    private val REQUEST_DEFAULT_SMS_APP = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        smsSync = SmsSyncService(this)

        requestNotificationPermission()

        // Gmail Login button
        binding.btnGmailLogin.setOnClickListener {
            startGmailSignIn()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            } else requestSmsPermission()
        } else requestSmsPermission()
    }

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
        } else ensureDefaultSmsApp()
    }

    private fun ensureDefaultSmsApp() {
        val myPackageName = packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                startActivityForResult(intent, REQUEST_DEFAULT_SMS_APP)
            } else startSmsSync()
        } else {
            val defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(this)
            if (defaultSmsApp != myPackageName) {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, myPackageName)
                startActivity(intent)
            } else startSmsSync()
        }
    }

    private fun startSmsSync() {
        Toast.makeText(this, "✅ SMS Sync Started", Toast.LENGTH_SHORT).show()
        smsSync.uploadInitialSms(20)
        smsSync.listenForFirebaseDeletion()
    }

    private fun startGmailSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/gmail.readonly"))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        startActivityForResult(googleSignInClient.signInIntent, RC_GMAIL_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_DEFAULT_SMS_APP) startSmsSync()

        if (requestCode == RC_GMAIL_SIGN_IN && resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.result
            if (account != null) {
                gmailSyncService = GmailSyncService(this, account)
                gmailSyncService?.start()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_NOTIFICATION_PERMISSION -> requestSmsPermission()
            REQUEST_SMS_PERMISSION -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    ensureDefaultSmsApp()
                } else showPermissionDialog("SMS permissions required.")
            }
        }
    }

    private fun showPermissionDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Permission Info")
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("Ignore") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
