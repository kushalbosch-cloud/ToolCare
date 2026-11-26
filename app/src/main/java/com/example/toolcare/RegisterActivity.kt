package com.example.toolcare

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.example.toolcare.R

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        val etCompanyName = findViewById<EditText>(R.id.etCompanyName)
        val etShopName = findViewById<EditText>(R.id.etShopName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        btnRegister.setOnClickListener {
            val companyName = etCompanyName.text.toString().trim()
            val shopName = etShopName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (companyName.isEmpty() || shopName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "सगळे फिल्ड भरणे आवश्यक आहे!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid ?: ""
                        val databaseRef = FirebaseDatabase.getInstance().getReference("Companies")

                        val companyData = mapOf(
                            "companyName" to companyName,
                            "shopName" to shopName,
                            "email" to email,
                            "adminId" to userId
                        )

                        databaseRef.child(userId).setValue(companyData)
                            .addOnSuccessListener {
                                Toast.makeText(this, "रजिस्ट्रेशन यशस्वी!", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "डेटा सेव्ह करताना एरर: ${e.message}", Toast.LENGTH_SHORT).show()
                            }

                    } else {
                        Toast.makeText(this, "रजिस्ट्रेशन फेल: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
}
