package com.example.toolcare

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoginScreen(
    firebaseManager: FirebaseManager,
    onLoginSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    var passwordInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var showForgotDialog by remember { mutableStateOf(false) }

    var savedPassword by remember { mutableStateOf<String?>(null) }
    var savedPin by remember { mutableStateOf<String?>(null) }

    BackHandler { onBackClick() }

    LaunchedEffect(Unit) {
        firebaseManager.getAdminSettings { pass, pin ->
            savedPassword = pass ?: "admin123" // Default if not set
            savedPin = pin ?: "19091999"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Login") },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Access Admin Panel", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Enter administrator password to continue", color = Color.Gray, fontSize = 14.sp)
            
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = passwordInput,
                onValueChange = { 
                    passwordInput = it
                    errorMessage = "" 
                },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage.isNotEmpty()
            )

            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (passwordInput == savedPassword) {
                        onLoginSuccess()
                    } else {
                        errorMessage = "Incorrect Password"
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Login")
            }

            TextButton(onClick = { showForgotDialog = true }) {
                Text("Forgot Password?")
            }
        }
    }

    if (showForgotDialog) {
        var pinInput by remember { mutableStateOf("") }
        var newPassInput by remember { mutableStateOf("") }
        var forgotError by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text("Reset Admin Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter Recovery PIN to reset password:")
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text("Recovery PIN") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = newPassInput,
                        onValueChange = { newPassInput = it },
                        label = { Text("New Password") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (forgotError.isNotEmpty()) {
                        Text(forgotError, color = Color.Red)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pinInput == savedPin) {
                        if (newPassInput.length >= 4) {
                            firebaseManager.updateAdminPassword(newPassInput)
                            savedPassword = newPassInput
                            showForgotDialog = false
                            errorMessage = "Password Reset Successful"
                        } else {
                            forgotError = "Min 4 characters required"
                        }
                    } else {
                        forgotError = "Incorrect Recovery PIN"
                    }
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
