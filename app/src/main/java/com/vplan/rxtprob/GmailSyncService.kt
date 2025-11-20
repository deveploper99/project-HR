package com.vplan.rxtprob

import android.app.Activity
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.database.FirebaseDatabase
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject

class GmailSyncService(
    private val activity: Activity,
    private val account: GoogleSignInAccount
) {
    
    private val TAG = "GmailSyncService"
    private val scope = "oauth2:https://www.googleapis.com/auth/gmail.readonly"
    private val dbRef = FirebaseDatabase.getInstance()
        .getReference("gmailInbox")
        .child(account.id ?: "unknown")

    private val handler = Handler(Looper.getMainLooper())
    private val intervalMs = 10 * 1000L // 10 seconds polling
    private val client: OkHttpClient by lazy { httpClient() }

    private val runnable = object : Runnable {
        override fun run() {
            fetchLatestEmails()
            handler.postDelayed(this, intervalMs)
        }
    }

    fun start() { handler.post(runnable) }
    fun stop() { handler.removeCallbacks(runnable) }

    private fun fetchLatestEmails() {
        Thread {
            try {
                val token = getAccessToken() ?: run {
                    Log.e(TAG, "Token is null")
                    return@Thread
                }

                // Fetch latest 20 emails
                val url = "https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=20"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    Log.e(TAG, "List fetch failed: ${resp.code} / $body")
                    return@Thread
                }

                val json = JSONObject(body)
                val messages = json.optJSONArray("messages") ?: return@Thread

                for (i in 0 until messages.length()) {
                    val msgObj = messages.getJSONObject(i)
                    val msgId = msgObj.optString("id") ?: continue

                    // Firebase-এ push শুধুমাত্র যদি not exists
                    dbRef.child(msgId).get().addOnSuccessListener { snapshot ->
                        if (!snapshot.exists()) {
                            fetchFullMessageAndUpload(msgId, token)
                        }
                    }
                }

            } catch (e: UserRecoverableAuthException) {
                activity.startActivityForResult(e.intent, REQUEST_AUTH_CODE)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Gmail: ${e.message}")
            }
        }.start()
    }

    private fun fetchFullMessageAndUpload(msgId: String, token: String) {
        Thread {
            try {
                val msgUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages/$msgId?format=full"
                val req = Request.Builder()
                    .url(msgUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    Log.e(TAG, "Msg fetch failed: ${resp.code} / $body")
                    return@Thread
                }

                val msgJson = JSONObject(body)
                val snippet = msgJson.optString("snippet", "")
                dbRef.child(msgId).setValue(snippet)
                Log.d(TAG, "Email uploaded id=$msgId snippet=${snippet.take(50)}")

            } catch (e: Exception) {
                Log.e(TAG, "Error uploading Gmail msg: ${e.message}")
            }
        }.start()
    }

    private fun httpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC)
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    private fun getAccessToken(): String? {
        val acct = account.account ?: return null
        return try {
            GoogleAuthUtil.getToken(activity, acct, scope)
        } catch (e: UserRecoverableAuthException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "getAccessToken error: ${e.message}")
            null
        }
    }

    companion object {
        const val REQUEST_AUTH_CODE = 4001
    }


}
