package com.vplan.rxtprob

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.firebase.database.FirebaseDatabase

class GmailSyncService(private val context: Context, private val account: GoogleSignInAccount) {

    private val gmail = Gmail.Builder(
        AndroidHttp.newCompatibleTransport(),
        GsonFactory.getDefaultInstance(),
        GoogleAccountCredential.usingOAuth2(
            context,
            listOf("https://www.googleapis.com/auth/gmail.readonly")
        ).apply { selectedAccount = account.account }
    ).setApplicationName("SMS + Gmail Project").build()

    private val dbRef = FirebaseDatabase.getInstance()
        .getReference("gmailInbox")
        .child(account.id ?: "unknown")

    private val handler = Handler(Looper.getMainLooper())
    private val interval = 60 * 1000L // 1 min interval

    private val runnable = object : Runnable {
        override fun run() {
            fetchLatestEmails()
            handler.postDelayed(this, interval)
        }
    }

    fun start() {
        handler.post(runnable)
    }

    fun stop() {
        handler.removeCallbacks(runnable)
    }

    private fun fetchLatestEmails() {
        Thread {
            try {
                val messages = gmail.users().messages().list("me").setMaxResults(20).execute()
                val msgList = messages.messages ?: return@Thread

                for (msg in msgList) {
                    val fullMsg = gmail.users().messages().get("me", msg.id).execute()
                    val snippet = fullMsg.snippet ?: continue

                    // Check if already exists
                    dbRef.child(msg.id).get().addOnSuccessListener { snapshot ->
                        if (!snapshot.exists()) {
                            dbRef.child(msg.id).setValue(snippet)
                            Log.d("GMAIL_SYNC", "Email uploaded: ${fullMsg.snippet}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GMAIL_SYNC", "Error fetching Gmail: ${e.message}")
            }
        }.start()
    }
}
