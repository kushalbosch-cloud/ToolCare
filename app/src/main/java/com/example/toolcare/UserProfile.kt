package com.example.toolcare

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "technician", // roles: technician, admin, store, operator
    val company: String = "",
    val block: String = "",
    val employeeId: String = "",
    val department: String = "",
    val designation: String = "",
    val mobile: String = "",
    val profilePhotoUrl: String = "",
    val fcmToken: String = "",
    val approvalStatus: String = "PENDING"
)
