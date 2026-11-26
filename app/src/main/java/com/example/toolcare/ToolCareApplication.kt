package com.example.toolcare

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Logger
import com.google.firebase.database.ValueEventListener

class ToolCareApplication : Application() {

    val firebaseManager: FirebaseManager by lazy {
        FirebaseManager.getInstance()
    }

    override fun onCreate() {
        super.onCreate()
        try {
            val db = FirebaseDatabase.getInstance("https://toolcare-65581-default-rtdb.asia-southeast1.firebasedatabase.app")

            val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable) {
                db.setLogLevel(Logger.Level.DEBUG)
                Log.d("ToolCareApp", "Firebase Debug Logging Active (Debug Build)")
            }

            db.setPersistenceEnabled(true)
            Log.d("ToolCareApp", "Firebase Realtime Database Persistence Active!")

            db.reference.child(".info/connected").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    Log.d("FIREBASE_CONN", "Firebase Connection Status: Connected = $connected (t=${System.currentTimeMillis()})")
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("FIREBASE_CONN", "Connection listener cancelled: ${error.message}", error.toException())
                }
            })
        } catch (e: Exception) {
            Log.e("ToolCareApp", "Error initializing Firebase Application settings", e)
        }
    }
}
