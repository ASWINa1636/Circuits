package com.example.circuits.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.circuits.data.Datasheet
import com.example.circuits.data.User
import java.lang.Exception // Ensure Exception is imported

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 2
        private const val DATABASE_NAME = "DatasheetDB"

        // User Table
        private const val TABLE_USERS = "Users"
        private const val KEY_USER_ID = "id"
        private const val KEY_USER_FULL_NAME = "fullName"
        private const val KEY_USER_NAME = "username"
        private const val KEY_USER_EMAIL = "email"
        private const val KEY_USER_PASSWORD = "password" // Store hashed in production
        private const val KEY_USER_PROFILE_TYPE = "profileType"
        private const val KEY_USER_LAST_UPLOAD = "lastUploadTimestamp"

        // Datasheet Table
        private const val TABLE_DATASHEETS = "Datasheets"
        private const val KEY_DATASHEET_ID = "id"
        private const val KEY_DATASHEET_NAME = "name"
        private const val KEY_DATASHEET_DESCRIPTION = "description"
        private const val KEY_DATASHEET_FILE_PATH = "file_path"
        private const val KEY_DATASHEET_UPLOADER_ID = "uploaderId"
        private const val KEY_DATASHEET_UPLOAD_TIMESTAMP = "uploadTimestamp"

        // Follow Table
        private const val TABLE_FOLLOW = "Follow"
        private const val KEY_FOLLOW_ID = "id"
        private const val KEY_FOLLOWER_ID = "followerId"
        private const val KEY_FOLLOWING_ID = "followingId"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        // --- Create Tables ---
        val createUserTable = ("CREATE TABLE " + TABLE_USERS + "("
                + KEY_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_USER_FULL_NAME + " TEXT,"
                + KEY_USER_NAME + " TEXT UNIQUE,"
                + KEY_USER_EMAIL + " TEXT UNIQUE,"
                + KEY_USER_PASSWORD + " TEXT,"
                + KEY_USER_PROFILE_TYPE + " TEXT,"
                + KEY_USER_LAST_UPLOAD + " INTEGER DEFAULT 0" + ")")

        val createDatasheetTable = ("CREATE TABLE " + TABLE_DATASHEETS + "("
                + KEY_DATASHEET_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_DATASHEET_NAME + " TEXT,"
                + KEY_DATASHEET_DESCRIPTION + " TEXT,"
                + KEY_DATASHEET_FILE_PATH + " TEXT,"
                + KEY_DATASHEET_UPLOADER_ID + " INTEGER,"
                + KEY_DATASHEET_UPLOAD_TIMESTAMP + " INTEGER,"
                + "FOREIGN KEY($KEY_DATASHEET_UPLOADER_ID) REFERENCES $TABLE_USERS($KEY_USER_ID)" + ")")

        val createFollowTable = ("CREATE TABLE " + TABLE_FOLLOW + "("
                + KEY_FOLLOW_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_FOLLOWER_ID + " INTEGER,"
                + KEY_FOLLOWING_ID + " INTEGER,"
                + "FOREIGN KEY($KEY_FOLLOWER_ID) REFERENCES $TABLE_USERS($KEY_USER_ID),"
                + "FOREIGN KEY($KEY_FOLLOWING_ID) REFERENCES $TABLE_USERS($KEY_USER_ID),"
                + "UNIQUE ($KEY_FOLLOWER_ID, $KEY_FOLLOWING_ID)"
                + ")")

        try {
            db?.execSQL(createUserTable)
            db?.execSQL(createDatasheetTable)
            db?.execSQL(createFollowTable)
            Log.d("DatabaseHelper", "Database tables created successfully.")
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error creating database tables", e)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // --- Handle DB Upgrades ---
        Log.w("DatabaseHelper", "Upgrading database from version $oldVersion to $newVersion")
        if (oldVersion < 2) {
            try {
                // Check if column exists before adding
                val cursor = db?.rawQuery("PRAGMA table_info($TABLE_USERS)", null)
                var columnExists = false
                cursor?.use {
                    while(it.moveToNext()) {
                        val nameIndex = it.getColumnIndex("name")
                        if (nameIndex != -1 && it.getString(nameIndex) == KEY_USER_EMAIL) {
                            columnExists = true
                            break
                        }
                    }
                }
                if (!columnExists) {
                    db?.execSQL("ALTER TABLE $TABLE_USERS ADD COLUMN $KEY_USER_EMAIL TEXT UNIQUE DEFAULT ''")
                    Log.d("DatabaseHelper", "Added email column to Users table.")
                } else {
                    Log.d("DatabaseHelper", "Email column already exists.")
                }
            } catch (e: Exception) {
                Log.e("DatabaseHelper", "Error adding email column during upgrade. Falling back to drop/recreate.", e)
                db?.execSQL("DROP TABLE IF EXISTS $TABLE_FOLLOW")
                db?.execSQL("DROP TABLE IF EXISTS $TABLE_DATASHEETS")
                db?.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
                onCreate(db)
                return // Exit after recreating
            }
        }
        // Add more upgrade steps here for future versions if (oldVersion < 3) { ... }
    }


    // --- User Functions ---

    fun addUser(user: User, password: String): Boolean {
        // Use separate readableDB instances for checks to allow closing them
        if (isUserExists(user.username) || isEmailExists(user.email)) {
            Log.w("DatabaseHelper", "Attempted to add user failed: username or email exists.")
            return false // Username or Email already exists
        }

        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_USER_FULL_NAME, user.fullName)
            put(KEY_USER_NAME, user.username)
            put(KEY_USER_EMAIL, user.email)
            put(KEY_USER_PASSWORD, password) // WARNING: Store hashed password in production
            put(KEY_USER_PROFILE_TYPE, user.profileType)
            put(KEY_USER_LAST_UPLOAD, 0) // Default last upload time
        }
        var result = -1L
        try {
            // Use insertWithOnConflict to handle potential unique constraint violations gracefully
            result = db.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            if (result == -1L) {
                Log.w("DatabaseHelper", "Insert failed, likely due to UNIQUE constraint (user/email exists) despite check.")
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error inserting user", e)
            result = -1L // Ensure result indicates failure on exception
        } finally {
            db.close()
        }
        Log.d("DatabaseHelper", "addUser result: $result (User: ${user.username})")
        return result != -1L // Return true if insert succeeded
    }


    @SuppressLint("Range")
    fun checkUserByEmail(email: String, password: String): Long {
        val db = this.readableDatabase
        val query = "SELECT $KEY_USER_ID FROM $TABLE_USERS WHERE $KEY_USER_EMAIL = ? AND $KEY_USER_PASSWORD = ?"
        var userId: Long = -1
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(query, arrayOf(email, password))
            // Check cursor is not null before moving
            if (cursor != null && cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(KEY_USER_ID)
                if (idIndex != -1) { // Check column index validity
                    userId = cursor.getLong(idIndex)
                } else {
                    Log.e("DatabaseHelper", "Column $KEY_USER_ID not found in checkUserByEmail query result.")
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error in checkUserByEmail", e)
            userId = -1L // Ensure failure return on exception
        } finally {
            cursor?.close()
            db.close()
        }
        Log.d("DatabaseHelper", "checkUserByEmail for '$email': $userId")
        return userId
    }

    @SuppressLint("Range")
    fun getPasswordByEmail(email: String): String? {
        val db = this.readableDatabase
        val query = "SELECT $KEY_USER_PASSWORD FROM $TABLE_USERS WHERE $KEY_USER_EMAIL = ?"
        var password: String? = null
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(query, arrayOf(email))
            if (cursor != null && cursor.moveToFirst()) {
                val passwordIndex = cursor.getColumnIndex(KEY_USER_PASSWORD)
                if (passwordIndex != -1) {
                    password = cursor.getString(passwordIndex)
                } else {
                    Log.e("DatabaseHelper", "Column $KEY_USER_PASSWORD not found.")
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error getting password for email $email", e)
            password = null // Ensure null return on exception
        } finally {
            cursor?.close()
            db.close()
        }
        Log.d("DatabaseHelper", "getPasswordByEmail for '$email': ${if (password != null) "Found" else "Not Found"}")
        return password
    }


    @SuppressLint("Range")
    fun getUser(userId: Long): User? {
        val db = this.readableDatabase
        val query = "SELECT * FROM $TABLE_USERS WHERE $KEY_USER_ID = ?"
        var user: User? = null
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(query, arrayOf(userId.toString()))
            if (cursor != null && cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(KEY_USER_ID)
                val nameIndex = cursor.getColumnIndex(KEY_USER_FULL_NAME)
                val usernameIndex = cursor.getColumnIndex(KEY_USER_NAME)
                val emailIndex = cursor.getColumnIndex(KEY_USER_EMAIL)
                val profileTypeIndex = cursor.getColumnIndex(KEY_USER_PROFILE_TYPE)

                if (idIndex != -1 && nameIndex != -1 && usernameIndex != -1 && emailIndex != -1 && profileTypeIndex != -1) {
                    user = User(
                        id = cursor.getLong(idIndex),
                        fullName = cursor.getString(nameIndex),
                        username = cursor.getString(usernameIndex),
                        email = cursor.getString(emailIndex),
                        profileType = cursor.getString(profileTypeIndex)
                    )
                } else {
                    Log.e("DatabaseHelper", "One or more columns not found in getUser query result.")
                    user = null // Explicitly null if columns missing
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error getting user with ID $userId", e)
            user = null // Ensure null return on exception
        } finally {
            cursor?.close()
            db.close()
        }
        return user
    }

    @SuppressLint("Range")
    fun getUserLastUploadTime(userId: Long): Long {
        val db = this.readableDatabase
        val query = "SELECT $KEY_USER_LAST_UPLOAD FROM $TABLE_USERS WHERE $KEY_USER_ID = ?"
        var timestamp: Long = 0
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(query, arrayOf(userId.toString()))
            if (cursor != null && cursor.moveToFirst()) {
                val tsIndex = cursor.getColumnIndex(KEY_USER_LAST_UPLOAD)
                if (tsIndex != -1) {
                    timestamp = cursor.getLong(tsIndex)
                } else {
                    Log.e("DatabaseHelper", "Column $KEY_USER_LAST_UPLOAD not found.")
                    timestamp = 0L // Default if column missing
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error getting user last upload time for ID $userId", e)
            timestamp = 0L // Default on exception
        } finally {
            cursor?.close()
            db.close()
        }
        return timestamp
    }

    fun updateUserLastUploadTime(userId: Long, timestamp: Long) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_USER_LAST_UPLOAD, timestamp)
        }
        try {
            val rowsAffected = db.update(TABLE_USERS, values, "$KEY_USER_ID = ?", arrayOf(userId.toString()))
            Log.d("DatabaseHelper", "updateUserLastUploadTime for user $userId: $rowsAffected rows affected.")
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error updating last upload time for user $userId", e)
        } finally {
            db.close()
        }
    }


    private fun isUserExists(username: String): Boolean {
        val db = this.readableDatabase
        val query = "SELECT $KEY_USER_ID FROM $TABLE_USERS WHERE $KEY_USER_NAME = ?"
        var exists = false
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(query, arrayOf(username))
            exists = (cursor != null && cursor.count > 0)
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error checking if user exists: $username", e)
            exists = false // Assume not exists on error
        } finally {
            cursor?.close()
            db.close() // Close here as it's a separate check
        }
        return exists
    }


    private fun isEmailExists(email: String): Boolean {
        val db = this.readableDatabase
        val query = "SELECT $KEY_USER_ID FROM $TABLE_USERS WHERE $KEY_USER_EMAIL = ?"
        var exists = false
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(query, arrayOf(email))
            exists = (cursor != null && cursor.count > 0)
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error checking if email exists: $email", e)
            exists = false // Assume not exists on error
        } finally {
            cursor?.close()
            db.close() // Close here as it's a separate check
        }
        return exists
    }


    // --- Datasheet Functions ---

    fun addDatasheet(datasheet: Datasheet) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_DATASHEET_NAME, datasheet.componentName)
            put(KEY_DATASHEET_DESCRIPTION, datasheet.description)
            put(KEY_DATASHEET_FILE_PATH, datasheet.filePath)
            put(KEY_DATASHEET_UPLOADER_ID, datasheet.uploaderId)
            put(KEY_DATASHEET_UPLOAD_TIMESTAMP, datasheet.uploadTimestamp)
        }
        try {
            db.insertOrThrow(TABLE_DATASHEETS, null, values) // Use insertOrThrow for better error reporting
            Log.d("DatabaseHelper", "Datasheet added: ${datasheet.componentName}")
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error adding datasheet", e)
        } finally {
            db.close()
        }
    }

    @SuppressLint("Range")
    fun getAllDatasheets(): ArrayList<Datasheet> {
        val datasheetList = ArrayList<Datasheet>()
        val selectQuery = "SELECT * FROM $TABLE_DATASHEETS ORDER BY $KEY_DATASHEET_UPLOAD_TIMESTAMP DESC" // Optional: Order by newest first
        val db = this.readableDatabase
        var cursor: Cursor? = null

        try {
            cursor = db.rawQuery(selectQuery, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndexOrThrow(KEY_DATASHEET_ID) // Use OrThrow for mandatory columns
                val nameIndex = cursor.getColumnIndexOrThrow(KEY_DATASHEET_NAME)
                val descIndex = cursor.getColumnIndexOrThrow(KEY_DATASHEET_DESCRIPTION)
                val pathIndex = cursor.getColumnIndexOrThrow(KEY_DATASHEET_FILE_PATH)
                val uploaderIndex = cursor.getColumnIndexOrThrow(KEY_DATASHEET_UPLOADER_ID)
                val timestampIndex = cursor.getColumnIndexOrThrow(KEY_DATASHEET_UPLOAD_TIMESTAMP)

                do {
                    val datasheet = Datasheet(
                        id = cursor.getLong(idIndex),
                        componentName = cursor.getString(nameIndex),
                        description = cursor.getString(descIndex),
                        filePath = cursor.getString(pathIndex),
                        uploaderId = cursor.getLong(uploaderIndex),
                        uploadTimestamp = cursor.getLong(timestampIndex)
                    )
                    datasheetList.add(datasheet)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error getting all datasheets", e)
            // Return empty list on error
        } finally {
            cursor?.close()
            db.close()
        }
        return datasheetList // Always return the list (possibly empty)
    }

    @SuppressLint("Range")
    fun getDatasheetsByUploader(userId: Long): ArrayList<Datasheet> {
        val datasheetList = ArrayList<Datasheet>()
        val selectQuery = "SELECT * FROM $TABLE_DATASHEETS WHERE $KEY_DATASHEET_UPLOADER_ID = ? ORDER BY $KEY_DATASHEET_UPLOAD_TIMESTAMP DESC"
        val db = this.readableDatabase
        var cursor: Cursor? = null

        try {
            cursor = db.rawQuery(selectQuery, arrayOf(userId.toString()))
            if (cursor != null && cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndexOrThrow(KEY_DATASHEET_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(KEY_DATASHEET_NAME)
                val descIndex = cursor.getColumnIndexOrThrow(KEY_DATASHEET_DESCRIPTION)
                val pathIndex = cursor.getColumnIndexOrThrow(KEY_DATASHEET_FILE_PATH)
                val uploaderIndex = cursor.getColumnIndexOrThrow(KEY_DATASHEET_UPLOADER_ID)
                val timestampIndex = cursor.getColumnIndexOrThrow(KEY_DATASHEET_UPLOAD_TIMESTAMP)

                do {
                    val datasheet = Datasheet(
                        id = cursor.getLong(idIndex),
                        componentName = cursor.getString(nameIndex),
                        description = cursor.getString(descIndex),
                        filePath = cursor.getString(pathIndex),
                        uploaderId = cursor.getLong(uploaderIndex),
                        uploadTimestamp = cursor.getLong(timestampIndex)
                    )
                    datasheetList.add(datasheet)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error getting datasheets for uploader $userId", e)
            // Return empty list on error
        } finally {
            cursor?.close()
            db.close()
        }
        return datasheetList // Always return the list (possibly empty)
    }

    fun getUploadCount(userId: Long): Int {
        val db = this.readableDatabase
        val query = "SELECT COUNT(*) FROM $TABLE_DATASHEETS WHERE $KEY_DATASHEET_UPLOADER_ID = ?"
        var count = 0
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(query, arrayOf(userId.toString()))
            if (cursor != null && cursor.moveToFirst()) {
                count = cursor.getInt(0)
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error getting upload count for user $userId", e)
            count = 0 // Default to 0 on error
        } finally {
            cursor?.close()
            db.close()
        }
        return count // Always return an Int
    }

    // --- Follow Functions ---

    fun getFollowerCount(userId: Long): Int {
        val db = this.readableDatabase
        val query = "SELECT COUNT(*) FROM $TABLE_FOLLOW WHERE $KEY_FOLLOWING_ID = ?"
        var count = 0
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(query, arrayOf(userId.toString()))
            if (cursor != null && cursor.moveToFirst()) {
                count = cursor.getInt(0)
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error getting follower count for user $userId", e)
            count = 0 // Default to 0 on error
        } finally {
            cursor?.close()
            db.close()
        }
        return count // Always return an Int
    }

    fun getFollowingCount(userId: Long): Int {
        val db = this.readableDatabase
        val query = "SELECT COUNT(*) FROM $TABLE_FOLLOW WHERE $KEY_FOLLOWER_ID = ?"
        var count = 0
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(query, arrayOf(userId.toString()))
            if (cursor != null && cursor.moveToFirst()) {
                count = cursor.getInt(0)
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error getting following count for user $userId", e)
            count = 0 // Default to 0 on error
        } finally {
            cursor?.close()
            db.close()
        }
        return count // Always return an Int
    }

    fun isFollowing(followerId: Long, followingId: Long): Boolean {
        if (followerId == followingId) return false
        val db = this.readableDatabase
        val query = "SELECT $KEY_FOLLOW_ID FROM $TABLE_FOLLOW WHERE $KEY_FOLLOWER_ID = ? AND $KEY_FOLLOWING_ID = ?"
        var exists = false
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(query, arrayOf(followerId.toString(), followingId.toString()))
            exists = (cursor != null && cursor.count > 0)
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error checking isFollowing: $followerId -> $followingId", e)
            exists = false // Assume not following on error
        } finally {
            cursor?.close()
            db.close()
        }
        Log.d("DatabaseHelper", "isFollowing check: $followerId -> $followingId = $exists")
        return exists // Always return a Boolean
    }

    fun followUser(followerId: Long, followingId: Long): Boolean {
        if (followerId == followingId) return false
        // Re-checking isFollowing is redundant due to UNIQUE constraint handled by insertWithOnConflict

        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_FOLLOWER_ID, followerId)
            put(KEY_FOLLOWING_ID, followingId)
        }
        var result = -1L
        try {
            result = db.insertWithOnConflict(TABLE_FOLLOW, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            if (result == -1L) {
                Log.w("DatabaseHelper", "Follow failed, likely already exists: $followerId -> $followingId")
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error following user: $followerId -> $followingId", e)
            result = -1L // Ensure failure indication
        } finally {
            db.close()
        }
        Log.d("DatabaseHelper", "followUser: $followerId -> $followingId | Result: $result")
        return result != -1L // Return true if insert occurred (result > -1)
    }

    fun unfollowUser(followerId: Long, followingId: Long): Boolean {
        if (followerId == followingId) return false
        val db = this.writableDatabase
        var result = 0
        try {
            result = db.delete(
                TABLE_FOLLOW,
                "$KEY_FOLLOWER_ID = ? AND $KEY_FOLLOWING_ID = ?",
                arrayOf(followerId.toString(), followingId.toString())
            )
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error unfollowing user: $followerId -> $followingId", e)
            result = 0 // Indicate failure
        } finally {
            db.close()
        }
        Log.d("DatabaseHelper", "unfollowUser: $followerId -> $followingId | Rows affected: $result")
        return result > 0 // Return true if any rows were deleted
    }

} // End of DatabaseHelper class

