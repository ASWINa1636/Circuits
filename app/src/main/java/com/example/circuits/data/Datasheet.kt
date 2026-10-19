package com.example.circuits.data

data class Datasheet(
    val id: Long,
    val componentName: String,
    val description: String,
    val filePath: String,
    val uploadTimestamp: Long,
    val uploaderId: Long
)

