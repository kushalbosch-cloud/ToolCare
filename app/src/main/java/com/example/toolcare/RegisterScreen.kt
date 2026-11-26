package com.example.toolcare

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    firebaseManager: FirebaseManager,
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf("technician") }
    var isLoading by remember { mutableStateOf(false) }

    var companyList by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var selectedCompany by remember { mutableStateOf("") }
    var selectedBlock by remember { mutableStateOf("") }
    var companyExpanded by remember { mutableStateOf(false) }
    var blockExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    // One-shot load of company and block hierarchy from Realtime Database
    LaunchedEffect(Unit) {
        firebaseManager.getMasterCompanyList { list ->
            companyList = list
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Create Account", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full Name *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password *") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "HIDE" else "SHOW", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password *") },
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                    Text(if (confirmPasswordVisible) "HIDE" else "SHOW", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Company Dropdown (Mandatory Explicit Selection) ---
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedCompany,
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Company *") },
                placeholder = { Text("Tap to choose company...") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Select Company",
                        Modifier.clickable { companyExpanded = true }
                    )
                },
                singleLine = true
            )
            DropdownMenu(
                expanded = companyExpanded,
                onDismissRequest = { companyExpanded = false },
                modifier = Modifier.width(280.dp)
            ) {
                if (companyList.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Loading companies...", fontSize = 12.sp) },
                        onClick = { companyExpanded = false }
                    )
                } else {
                    companyList.keys.forEach { company ->
                        DropdownMenuItem(
                            text = { Text(company, fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                selectedCompany = company
                                selectedBlock = ""
                                companyExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- Block / Shop Dropdown (Mandatory Explicit Selection) ---
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedBlock,
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Shop / Block *") },
                placeholder = { Text(if (selectedCompany.isBlank()) "Select company first..." else "Tap to choose shop/block...") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Select Block",
                        Modifier.clickable {
                            if (selectedCompany.isBlank()) {
                                Toast.makeText(context, "Please select a Company first", Toast.LENGTH_SHORT).show()
                            } else {
                                blockExpanded = true
                            }
                        }
                    )
                },
                singleLine = true
            )
            DropdownMenu(
                expanded = blockExpanded,
                onDismissRequest = { blockExpanded = false },
                modifier = Modifier.width(280.dp)
            ) {
                val availableBlocks = companyList[selectedCompany] ?: emptyList()
                if (availableBlocks.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No blocks available for $selectedCompany", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) },
                        onClick = { blockExpanded = false }
                    )
                } else {
                    availableBlocks.forEach { block ->
                        DropdownMenuItem(
                            text = { Text(block) },
                            onClick = {
                                selectedBlock = block
                                blockExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Role Selection (Technician and Operator ONLY)
        Text("Select Role *", modifier = Modifier.align(Alignment.Start), fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedRole == "technician", onClick = { selectedRole = "technician" })
                Text("Technician", fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.width(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedRole == "operator", onClick = { selectedRole = "operator" })
                Text("Operator", fontSize = 13.sp)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "All new accounts require Main Admin approval.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val cleanName = name.trim()
                val cleanEmail = email.trim()
                val cleanPassword = password.trim()
                val cleanConfirmPassword = confirmPassword.trim()
                val cleanCompany = selectedCompany.trim()
                val cleanBlock = selectedBlock.trim()

                // Form Validation Checks
                if (cleanName.isBlank()) {
                    Toast.makeText(context, "Please enter your Full Name", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (cleanEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                    Toast.makeText(context, "Please enter a valid Email address", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (cleanPassword.isBlank() || cleanPassword.length < 6) {
                    Toast.makeText(context, "Password should be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (cleanPassword != cleanConfirmPassword) {
                    Toast.makeText(context, "Password and Confirm Password do not match", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (cleanCompany.isBlank()) {
                    Toast.makeText(context, "Please select your Company", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (cleanBlock.isBlank()) {
                    Toast.makeText(context, "Please select your Shop / Block", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isLoading = true
                auth.createUserWithEmailAndPassword(cleanEmail, cleanPassword)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val uid = auth.currentUser?.uid ?: ""
                            val profile = UserProfile(
                                uid = uid,
                                name = cleanName,
                                email = cleanEmail,
                                role = selectedRole,
                                company = cleanCompany,
                                block = cleanBlock,
                                approvalStatus = "PENDING"
                            )
                            firebaseManager.saveUserProfile(profile) { success ->
                                isLoading = false
                                if (success) {
                                    Toast.makeText(context, "Registration submitted! Your account is pending approval from Main Admin.", Toast.LENGTH_LONG).show()
                                    onRegisterSuccess()
                                } else {
                                    auth.currentUser?.delete()
                                    Toast.makeText(context, "Registration failed while saving user profile. Please try again.", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            isLoading = false
                            Toast.makeText(context, "Registration Failed: ${task.exception?.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            else Text("Register")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateToLogin) {
            Text("Already have an account? Login here")
        }
    }
}
