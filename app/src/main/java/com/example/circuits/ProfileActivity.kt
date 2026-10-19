package com.example.circuits

// --- Keep existing imports ---
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException // Import ActivityNotFoundException
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider // Import FileProvider
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.circuits.data.Datasheet
import com.example.circuits.db.DatabaseHelper
import com.example.circuits.ui.theme.DatasheetAdapter // Correct import
import java.io.File // Import File
import java.io.FileOutputStream // Import FileOutputStream
import java.io.InputStream // Import InputStream
import java.lang.Exception // Import base Exception


class ProfileActivity : AppCompatActivity() {

    // --- Keep existing variables ---
    private lateinit var db: DatabaseHelper
    private var userId: Long = -1
    private lateinit var datasheetAdapter: DatasheetAdapter
    private val userUploads = ArrayList<Datasheet>()
    private lateinit var rvUserUploads: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        // --- Keep existing onCreate logic ---
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        db = DatabaseHelper(this)
        userId = intent.getLongExtra("USER_ID", -1)

        if (userId == -1L) {
            Toast.makeText(this, R.string.error_profile_load, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // --- Setup RecyclerView ---
        rvUserUploads = findViewById(R.id.rvUserUploads) // Use correct ID
        rvUserUploads.layoutManager = LinearLayoutManager(this) // Remove named arg
        datasheetAdapter = DatasheetAdapter(ArrayList()) { selectedDatasheet -> // Remove named arg
            // --- Updated click listener ---
            try {
                if (!selectedDatasheet.filePath.isNullOrEmpty()) {
                    val fileUri = selectedDatasheet.filePath.toUri()
                    // Check scheme before attempting to open
                    if (fileUri.scheme == "content" || fileUri.scheme == "file") {
                        Log.d("ProfileClick", "Attempting to open datasheet: ${selectedDatasheet.componentName}, URI: $fileUri")
                        openFile(fileUri) // Call the updated openFile
                    } else {
                        Log.e("ProfileClick", "Invalid URI scheme or path: ${selectedDatasheet.filePath}")
                        Toast.makeText(this, R.string.error_invalid_file_path, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("ProfileClick", "File path is null or empty for datasheet ID: ${selectedDatasheet.id}")
                    Toast.makeText(this, R.string.error_invalid_file_path, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ProfileClick", "Error parsing or resolving URI: ${selectedDatasheet.filePath}", e)
                Toast.makeText(this, R.string.error_invalid_file_path, Toast.LENGTH_SHORT).show()
            }
            // --- End updated click listener ---
        }
        rvUserUploads.adapter = datasheetAdapter
        // --- End RecyclerView Setup ---

        loadProfileData()
        loadUserUploads()
    }

    // --- Keep existing load functions ---
    private fun loadProfileData() {
        // Load User Details
        val user = db.getUser(userId)
        if (user != null) {
            findViewById<TextView>(R.id.tvProfileName).text = user.fullName
            // Use String resource with placeholder for formatting
            findViewById<TextView>(R.id.tvProfileUsername).text = getString(R.string.username_format, user.username)
            findViewById<TextView>(R.id.tvProfileEmail).text = user.email
            findViewById<TextView>(R.id.tvProfileType).text = user.profileType

            // Load User Stats
            findViewById<TextView>(R.id.tvUploadsCount).text = db.getUploadCount(userId).toString()

        } else {
            Toast.makeText(this, R.string.error_user_not_found, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    private fun loadUserUploads() {
        userUploads.clear()
        userUploads.addAll(db.getDatasheetsByUploader(userId))
        datasheetAdapter.updateFullList(userUploads)
        Log.d("ProfileActivity", "Loaded ${userUploads.size} uploads for user $userId")
    }

    // --- EXACT COPIES of file opening functions from MainActivity ---
    private fun openFile(originalUri: Uri) {
        Log.d("OpenFile", "[Profile] Attempting to open original URI: $originalUri")
        try {
            // Attempt 1: Open the original URI directly
            val mimeType = contentResolver.getType(originalUri) ?: "*/*"
            Log.d("OpenFile", "[Profile] Resolved MIME type: $mimeType")

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(originalUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
            Log.i("OpenFile", "[Profile] Successfully launched intent for original URI.")

        } catch (e: ActivityNotFoundException) {
            Log.w("OpenFile", "[Profile] ActivityNotFoundException for original URI ($e). Attempting fallback...")
            copyToCacheAndOpenFileProvider(originalUri)
        } catch (e: SecurityException) {
            Log.w("OpenFile", "[Profile] SecurityException for original URI ($e). Attempting fallback...")
            copyToCacheAndOpenFileProvider(originalUri)
        } catch (e: Exception) {
            Log.e("OpenFile", "[Profile] Unexpected error opening original URI", e)
            Toast.makeText(this, R.string.error_opening_file, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToCacheAndOpenFileProvider(originalUri: Uri) {
        val fileName = getFileNameFromUri(originalUri) ?: "temp_datasheet_profile_${System.currentTimeMillis()}"
        val cacheSubdir = File(cacheDir, "datasheets")
        if (!cacheSubdir.exists()) { cacheSubdir.mkdirs() }
        val cacheFile = File(cacheSubdir, fileName)
        Log.d("OpenFile", "[Profile-Fallback] Attempting to copy to cache file: ${cacheFile.absolutePath}")

        var success = false
        try {
            contentResolver.openInputStream(originalUri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                    success = true
                }
            } ?: run {
                Log.e("OpenFile", "[Profile-Fallback] Failed to open input stream for $originalUri")
                throw Exception("Could not open input stream")
            }

            if (success) {
                val providerUri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    cacheFile
                )
                Log.d("OpenFile", "[Profile-Fallback] Copied successfully. Provider URI: $providerUri")

                val mimeType = contentResolver.getType(providerUri) ?: contentResolver.getType(originalUri) ?: "*/*"
                Log.d("OpenFile", "[Profile-Fallback] MIME type for provider URI: $mimeType")

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(providerUri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    Log.i("OpenFile", "[Profile-Fallback] Successfully launched intent for provider URI.")
                } else {
                    Log.e("OpenFile", "[Profile-Fallback] No application found for provider URI: $providerUri, MIME: $mimeType")
                    Toast.makeText(this, R.string.no_app_found_error, Toast.LENGTH_SHORT).show()
                    // Optionally delete cache file if opening failed
                    // if (cacheFile.exists()) { cacheFile.delete() }
                }
            } else {
                Log.e("OpenFile", "[Profile-Fallback] Copy was marked unsuccessful.")
                Toast.makeText(this, R.string.error_opening_file, Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e("OpenFile", "[Profile-Fallback] Error during copy or FileProvider opening", e)
            Toast.makeText(this, R.string.error_opening_file, Toast.LENGTH_SHORT).show()
            // Clean up cache file on error
            // if (cacheFile.exists()) { cacheFile.delete() }
        }
    }

    @SuppressLint("Range")
    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        } else {
                            Log.w("GetFileName", "DISPLAY_NAME column not found for URI: $uri")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("GetFileName", "Error querying display name for content URI $uri", e)
            }
        }
        if (fileName == null && uri.scheme == "file") {
            fileName = File(uri.path ?: "").name
        }
        if (fileName == null) {
            fileName = uri.lastPathSegment?.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            Log.d("GetFileName", "Using sanitized lastPathSegment as fallback filename: $fileName")
        }
        if (fileName.isNullOrBlank()){
            fileName = "downloaded_file_${System.currentTimeMillis()}"
            Log.w("GetFileName", "Could not determine filename, using default: $fileName")
        }
        Log.d("GetFileName", "Final filename determined for URI $uri: $fileName")
        return fileName
    }
    // --- End Copied Functions ---


    // --- Keep onSupportNavigateUp ---
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

