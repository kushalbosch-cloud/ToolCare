package com.example.toolcare

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    firebaseManager: FirebaseManager = (LocalContext.current.applicationContext as ToolCareApplication).firebaseManager,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    fun performLogin() {
        val cleanEmail = email.trim()
        val cleanPassword = password.trim()

        if (cleanEmail.isBlank() || cleanPassword.isBlank()) {
            Toast.makeText(context, "कृपया ईमेल आणि पासवर्ड टाका.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            Toast.makeText(context, "कृपया योग्य ईमेल आयडी टाका.", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true
        auth.signInWithEmailAndPassword(cleanEmail, cleanPassword)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: ""
                    firebaseManager.getUserProfileDetailed(uid) { profile, errorMsg ->
                        isLoading = false
                        if (profile != null) {
                            val statusClean = profile.approvalStatus.trim().uppercase(Locale.ROOT)
                            when (statusClean) {
                                "APPROVED" -> {
                                    onLoginSuccess()
                                }
                                "PENDING" -> {
                                    auth.signOut()
                                    Toast.makeText(context, "तुमचा अर्ज मंजुरीसाठी प्रलंबित आहे. ॲडमिन मंजुरीनंतर लॉगिन करा.", Toast.LENGTH_LONG).show()
                                }
                                "REJECTED" -> {
                                    auth.signOut()
                                    Toast.makeText(context, "तुमचा अर्ज नाकारला गेला आहे. ॲडमिन टीमशी संपर्क साधा.", Toast.LENGTH_LONG).show()
                                }
                                else -> {
                                    auth.signOut()
                                    Toast.makeText(context, "तुमचा अर्ज मंजुरीसाठी प्रलंबित आहे. ॲडमिन मंजुरीनंतर लॉगिन करा.", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            auth.signOut()
                            Toast.makeText(context, errorMsg ?: "प्रोफाईल मिळाला नाही, पुन्हा प्रयत्न करा.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    isLoading = false
                    Toast.makeText(context, "लॉगिन अयशस्वी: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ToolCare Login", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Industrial Maintenance Management", fontSize = 14.sp)
        
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password *") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "HIDE" else "SHOW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { performLogin() }),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = {
                if (email.isNotBlank()) {
                    auth.sendPasswordResetEmail(email.trim())
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(context, "Password reset link sent to your email", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    Toast.makeText(context, "Please enter your email first", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Forgot Password?")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { performLogin() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            else Text("Login")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateToRegister) {
            Text("Don't have an account? Register here")
        }
    }
}

@Composable
fun PendingApprovalScreen(
    userProfile: UserProfile,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFFFEF2F2), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Pending Approval",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "Account Pending Approval",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Your ${userProfile.role.uppercase()} account (${userProfile.email}) is currently awaiting authorization from the Main Admin for ${userProfile.company} (${userProfile.block}).",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Surface(
                    color = Color(0xFFFFFBEB),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Status: PENDING ADMIN APPROVAL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Back to Login", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
