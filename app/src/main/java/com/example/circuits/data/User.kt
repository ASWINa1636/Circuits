package com.example.circuits.data

data class User(
    val id: Long,
    val fullName: String,
    val username: String,
    val email: String, // FIX: Added email field
    val profileType: String,
    // These are updated by separate DB calls, so they are vars
    var uploadCount: Int = 0,
    var followerCount: Int = 0,
    var followingCount: Int = 0
)

