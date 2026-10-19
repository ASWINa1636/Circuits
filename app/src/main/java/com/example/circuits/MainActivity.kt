package com.example.circuits

// --- Keep existing imports ---
import android.Manifest // Import Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException // Import ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager // Import PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build // Import Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings // Import Settings
import android.util.Log // Import Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog // Import AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar // Import Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider // Import FileProvider
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.circuits.data.Datasheet
import com.example.circuits.db.DatabaseHelper
import com.example.circuits.ui.theme.DatasheetAdapter // Correct import
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import java.io.File // Import File
import java.io.FileOutputStream // Import FileOutputStream
import java.io.InputStream // Import InputStream
import java.lang.Exception // Import base Exception

class MainActivity : AppCompatActivity() {

    // --- Keep existing variables ---
    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var datasheetAdapter: DatasheetAdapter
    private lateinit var db: DatabaseHelper
    private var currentUserId: Long = -1
    private var datasheets = ArrayList<Datasheet>()
    private var permissionRequested = false

    // --- Permission Launcher ---
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        permissionRequested = false // Reset flag after result
        if (isGranted) {
            Log.d("Permission", "Storage permission granted.")
            loadDatasheets() // Load data now
        } else {
            Log.w("Permission", "Storage permission denied.")
            // Check if rationale should be shown or if permanently denied
            // Check isGranted explicitly false here after denial
            if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) && !isGranted) {
                // User denied and checked "Don't ask again" or policy prevents asking
                showPermissionDeniedDialog()
            } else {
                // User denied but didn't check "Don't ask again"
                Toast.makeText(this, R.string.permission_needed_toast, Toast.LENGTH_LONG).show()
            }
        }
    }
    // --- End Permission Launcher ---

    private val uploadActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadDatasheets()
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbarMain)
        setSupportActionBar(toolbar)
        val toolbarTitle: TextView = findViewById(R.id.toolbar_title)
        toolbarTitle.text = getString(R.string.app_name)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val profileIcon = AppCompatResources.getDrawable(this, R.drawable.ic_profile)
        if (profileIcon != null) {
            val tintedIcon = DrawableCompat.wrap(profileIcon).mutate()
            DrawableCompat.setTint(tintedIcon, ContextCompat.getColor(this, R.color.white))
            supportActionBar?.setHomeAsUpIndicator(tintedIcon)
        }

        db = DatabaseHelper(this)
        currentUserId = intent.getLongExtra("USER_ID", -1L)

        if (currentUserId == -1L) {
            Toast.makeText(this, R.string.error_no_user_logged_in, Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        recyclerView = findViewById(R.id.rvDatasheets)
        fab = findViewById(R.id.fabAdd)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        datasheetAdapter = DatasheetAdapter(ArrayList()) { selectedDatasheet ->
            handleDatasheetClick(selectedDatasheet)
        }
        recyclerView.adapter = datasheetAdapter

        // Permission check in onResume

        fab.setOnClickListener {
            val intent = Intent(this, UploadActivity::class.java).apply {
                putExtra("USER_ID", currentUserId)
            }
            uploadActivityResultLauncher.launch(intent)
        }
    }

    // --- Move Permission Check to onResume ---
    override fun onResume() {
        super.onResume()
        checkAndRequestStoragePermission(requestIfNeeded = true)
    }
    // --- End onResume ---


    private fun checkAndRequestStoragePermission(requestIfNeeded: Boolean) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) { // Only for older SDKs
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d("Permission", "Permission already granted on check.")
                    loadDatasheets()
                }
                requestIfNeeded && shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                    showPermissionRationaleDialog()
                }
                requestIfNeeded && !permissionRequested -> {
                    Log.d("Permission", "Requesting storage permission...")
                    permissionRequested = true
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                else -> {
                    if (!permissionRequested) {
                        Log.w("Permission", "Permission not granted, not requesting now.")
                        // Consider showing an empty state view or a persistent message
                        // For example, show a TextView explaining permission is needed.
                    }
                }
            }
        } else {
            Log.d("Permission", "Android 13+ - relying on URI permissions.")
            loadDatasheets() // Proceed on newer Android
        }
    }


    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_needed_title))
            .setMessage(getString(R.string.storage_permission_rationale))
            .setPositiveButton(getString(R.string.grant_permission)) { _, _ ->
                permissionRequested = true
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, R.string.permission_needed_toast, Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_denied))
            .setMessage(getString(R.string.permission_denied_permanently))
            .setPositiveButton(getString(R.string.go_to_settings)) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uriValue = Uri.fromParts("package", packageName, null)
                intent.data = uriValue
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, R.string.file_access_limited, Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun loadDatasheets() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.w("LoadData", "Attempted to load datasheets without storage permission.")
            datasheets.clear()
            // Check if adapter is initialized before updating
            if (::datasheetAdapter.isInitialized) {
                datasheetAdapter.updateFullList(datasheets)
            }
            return
        }
        Log.d("LoadData", "Loading datasheets from DB...")
        datasheets = db.getAllDatasheets()
        // Check if adapter is initialized before updating
        if (::datasheetAdapter.isInitialized) {
            datasheetAdapter.updateFullList(datasheets)
        }
        Log.d("LoadData", "Loaded ${datasheets.size} datasheets.")
    }

    private fun handleDatasheetClick(datasheet: Datasheet) {
        // Permission Check before attempting to open
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.w("OpenFile", "[Main] Attempted to open file without storage permission.")
            checkAndRequestStoragePermission(requestIfNeeded = true) // Re-trigger permission request
            return
        }

        val lastUploadTime = db.getUserLastUploadTime(currentUserId)
        val currentTime = System.currentTimeMillis()
        val sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000L

        if (lastUploadTime == 0L || (currentTime - lastUploadTime) > sevenDaysInMillis) {
            Toast.makeText(this, R.string.upload_required_message, Toast.LENGTH_LONG).show()
        } else {
            try {
                if (!datasheet.filePath.isNullOrEmpty()) {
                    val fileUri = datasheet.filePath.toUri()
                    // Basic check for content or file scheme before trying to open
                    if (fileUri.scheme == "content" || fileUri.scheme == "file") {
                        openFile(fileUri) // Call the updated openFile
                    } else {
                        Log.e("MainActivity", "Invalid URI scheme or path: ${datasheet.filePath}")
                        Toast.makeText(this, R.string.error_invalid_file_path, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("MainActivity", "File path is null or empty for datasheet ID: ${datasheet.id}")
                    Toast.makeText(this, R.string.error_invalid_file_path, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error parsing or resolving URI: ${datasheet.filePath}", e)
                Toast.makeText(this, R.string.error_invalid_file_path, Toast.LENGTH_SHORT).show()
            }
        }
    }


    // --- FIX: Final openFile function with fallback ---
    private fun openFile(originalUri: Uri) {
        Log.d("OpenFile", "[Main] Attempting to open original URI: $originalUri")
        try {
            // Attempt 1: Open the original URI directly
            val mimeType = contentResolver.getType(originalUri) ?: "*/*" // Use generic if null
            Log.d("OpenFile", "[Main] Resolved MIME type for original URI: $mimeType")

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(originalUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
            Log.i("OpenFile", "[Main] Successfully launched intent for original URI.")

        } catch (e: ActivityNotFoundException) {
            Log.w("OpenFile", "[Main] ActivityNotFoundException for original URI ($e). Attempting fallback...")
            copyToCacheAndOpenFileProvider(originalUri) // Try copying and using FileProvider
        } catch (e: SecurityException) {
            // This might happen if the original URI permission expired or is invalid
            Log.w("OpenFile", "[Main] SecurityException for original URI ($e). Attempting fallback...")
            copyToCacheAndOpenFileProvider(originalUri) // Try copying and using FileProvider
        } catch (e: Exception) {
            Log.e("OpenFile", "[Main] Unexpected error opening original URI", e)
            Toast.makeText(this, R.string.error_opening_file, Toast.LENGTH_SHORT).show()
        }
    }

    // --- FIX: Final fallback function ---
    private fun copyToCacheAndOpenFileProvider(originalUri: Uri) {
        val fileName = getFileNameFromUri(originalUri) ?: "temp_datasheet_${System.currentTimeMillis()}"
        // Use a subdirectory in cache for better organization (optional)
        val cacheSubdir = File(cacheDir, "datasheets")
        if (!cacheSubdir.exists()) {
            cacheSubdir.mkdirs()
        }
        val cacheFile = File(cacheSubdir, fileName)
        Log.d("OpenFile", "[Main-Fallback] Attempting to copy to cache file: ${cacheFile.absolutePath}")

        var success = false
        try {
            // Copy the content using Input/Output Streams
            contentResolver.openInputStream(originalUri)?.use { inputStream ->
                FileOutputStream(cacheFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                    success = true // Mark copy as successful
                }
            } ?: run {
                Log.e("OpenFile", "[Main-Fallback] Failed to open input stream for $originalUri")
                throw Exception("Could not open input stream") // Throw exception to be caught below
            }

            // If copy succeeded, proceed to open with FileProvider
            // Check success explicitly
            if (success) { // Simpler check for true
                val providerUri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider", // Authority must match Manifest
                    cacheFile
                )
                Log.d("OpenFile", "[Main-Fallback] Copied successfully. Provider URI: $providerUri")

                // Determine MIME type (try provider URI first, fallback to original)
                val mimeType = contentResolver.getType(providerUri) ?: contentResolver.getType(originalUri) ?: "*/*"
                Log.d("OpenFile", "[Main-Fallback] MIME type for provider URI: $mimeType")

                // Create intent for FileProvider URI
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(providerUri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                // Verify and start activity
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    Log.i("OpenFile", "[Main-Fallback] Successfully launched intent for provider URI.")
                } else {
                    // This indicates no app can handle even the FileProvider URI + MIME type
                    Log.e("OpenFile", "[Main-Fallback] No application found for provider URI: $providerUri, MIME: $mimeType")
                    Toast.makeText(this, R.string.no_app_found_error, Toast.LENGTH_SHORT).show()
                }
            } else {
                // Handle case where copy might not have explicitly succeeded (though throw should prevent this)
                Log.e("OpenFile", "[Main-Fallback] Copy was marked as unsuccessful.")
                Toast.makeText(this, R.string.error_opening_file, Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) { // Catch errors during copy, provider URI generation, or starting activity
            Log.e("OpenFile", "[Main-Fallback] Error during copy or FileProvider opening", e)
            Toast.makeText(this, R.string.error_opening_file, Toast.LENGTH_SHORT).show()
        } finally {
            // Clean up the cache file ONLY if the process failed AFTER copying started
            // If startActivity succeeded, keep the file temporarily for the viewing app
            // If copy failed, 'success' is false, file might be incomplete or non-existent
            // Consider more sophisticated cache management if needed
            // if (!success && cacheFile.exists()) {
            //     cacheFile.delete()
            //     Log.d("OpenFile", "[Main-Fallback] Cleaned up cache file due to error.")
            // }
        }
    }

    // --- FIX: Final helper to get filename ---
    @SuppressLint("Range")
    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        if (uri.scheme == "content") {
            try {
                // Query the display name column
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        // Ensure the column index is valid before accessing
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        } else {
                            Log.w("GetFileName", "DISPLAY_NAME column not found for URI: $uri")
                        }
                    }
                }
            } catch (e: Exception) {
                // Catch potential SecurityException or other issues during query
                Log.w("GetFileName", "Error querying display name for content URI $uri", e)
            }
        }
        // Fallback for file scheme URIs
        if (fileName == null && uri.scheme == "file") {
            fileName = File(uri.path ?: "").name
        }
        // Last resort: try getting the last path segment
        if (fileName == null) {
            fileName = uri.lastPathSegment?.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            Log.d("GetFileName", "Using sanitized lastPathSegment as fallback filename: $fileName")
        }
        // If still null or blank, provide a generic default
        if (fileName.isNullOrBlank()){
            fileName = "downloaded_file_${System.currentTimeMillis()}"
            Log.w("GetFileName", "Could not determine filename, using default: $fileName")
        }
        Log.d("GetFileName", "Final filename determined for URI $uri: $fileName")
        return fileName // FIX: Ensure this function always returns the determined fileName
    }
    // --- End Helper ---


    // --- Keep Menu functions ---
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                datasheetAdapter.filter.filter(query)
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                datasheetAdapter.filter.filter(newText)
                return true
            }
        })
        return true // FIX: Ensure this function returns true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { // Handle click on the profile icon (home button)
                val intent = Intent(this, ProfileActivity::class.java).apply {
                    putExtra("USER_ID", currentUserId)
                }
                startActivity(intent)
                true // Return true as the event was handled
            }
            R.id.action_logout -> { // FIX: Handle Logout click
                // Navigate back to LoginActivity
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Clear back stack
                startActivity(intent)
                finish() // Close MainActivity
                true // Return true as the event was handled
            }
            // R.id.action_search is handled by the SearchView itself
            else -> super.onOptionsItemSelected(item) // Default case
        }
        // FIX: Remove redundant return statement if 'when' covers all cases or has 'else'
    }
} // End of MainActivity class

