package com.vplan.rxtprob

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            // Enable disk persistence
            try {
                FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            } catch (_: Exception) { }
            Log.d("MyApp", "Firebase initialized")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("MyApp", "Firebase init failed: ${e.message}")
        }
    }
}
