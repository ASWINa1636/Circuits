package com.example.circuits

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.circuits.data.Datasheet
import com.example.circuits.db.DatabaseHelper
import java.io.File

class UploadActivity : AppCompatActivity() {

    private lateinit var etComponentName: EditText
    private lateinit var etDescription: EditText
    private lateinit var btnSelectFile: Button
    private lateinit var btnSave: Button
    private lateinit var tvSelectedFileName: TextView
    private lateinit var btnOpenFile: Button

    private var selectedFileUri: Uri? = null
    private var currentUserId: Long = -1

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->

        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedFileUri = uri
                btnSelectFile.text = getString(R.string.file_selected)

                try {
                    val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, flag)

                    val fileName = getFileNameFromUri(uri)
                    tvSelectedFileName.text = fileName ?: getString(R.string.file_selected)
                    Log.d("UploadActivity", "Selected file: $fileName")

                } catch (e: SecurityException) {
                    Log.e("UploadActivity", "URI permission error for $uri", e)
                    Toast.makeText(this, "Failed to get file access.", Toast.LENGTH_SHORT).show()
                    tvSelectedFileName.text = getString(R.string.error_selecting_file)
                } catch (e: Exception) {
                    Log.e("UploadActivity", "Error processing file: $uri", e)
                    Toast.makeText(this, "Error processing file.", Toast.LENGTH_SHORT).show()
                    tvSelectedFileName.text = getString(R.string.error_selecting_file)
                }
            } ?: run {
                Toast.makeText(this, "Could not get file URI.", Toast.LENGTH_SHORT).show()
                tvSelectedFileName.text = getString(R.string.error_selecting_file)
            }
        } else {
            Log.w("UploadActivity", "File picker cancelled. Code: ${result.resultCode}")
        }
    }

    @SuppressLint("Range")
    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    fileName = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (fileName == null && uri.scheme == "file") {
            fileName = File(uri.path ?: "").name
        }
        if (fileName == null) {
            Log.w("UploadActivity", "Could not determine file name for URI: $uri")
        }
        return fileName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)

        etComponentName = findViewById(R.id.etComponentName)
        etDescription = findViewById(R.id.etDescription)
        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnSave = findViewById(R.id.btnSave)
        tvSelectedFileName = findViewById(R.id.tvSelectedFileName)

        // ✅ Add a button in your layout (activity_upload.xml) with id "btnOpenFile"
        btnOpenFile = findViewById(R.id.btnOpenFile)

        currentUserId = intent.getLongExtra("USER_ID", -1L)
        if (currentUserId == -1L) {
            Toast.makeText(this, R.string.error_no_user_cannot_upload, Toast.LENGTH_LONG).show()
            Log.e("UploadActivity", "No USER_ID found in Intent extras.")
            finish()
            return
        }

        btnSelectFile.setOnClickListener { launchFilePicker() }
        btnSave.setOnClickListener { saveDatasheet() }
        btnOpenFile.setOnClickListener { openSelectedFile() }
    }

    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            filePickerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("UploadActivity", "Could not open file picker", e)
            Toast.makeText(this, "Could not open file picker.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveDatasheet() {
        val name = etComponentName.text.toString().trim()
        val description = etDescription.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, R.string.error_component_name_empty, Toast.LENGTH_SHORT).show()
            etComponentName.error = getString(R.string.error_component_name_empty)
            return
        }
        if (description.isEmpty()) {
            Toast.makeText(this, R.string.error_description_empty, Toast.LENGTH_SHORT).show()
            etDescription.error = getString(R.string.error_description_empty)
            return
        }
        if (selectedFileUri == null) {
            Toast.makeText(this, R.string.error_no_file_selected, Toast.LENGTH_SHORT).show()
            return
        }

        val filePath = selectedFileUri.toString()
        val currentTime = System.currentTimeMillis()

        val datasheet = Datasheet(
            id = 0,
            componentName = name,
            description = description,
            filePath = filePath,
            uploaderId = currentUserId,
            uploadTimestamp = currentTime
        )

        val db = DatabaseHelper(this)
        try {
            db.addDatasheet(datasheet)
            db.updateUserLastUploadTime(currentUserId, currentTime)
            Toast.makeText(this, R.string.datasheet_shared, Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK)
            finish()
        } catch (e: Exception) {
            Log.e("UploadActivity", "Error saving datasheet", e)
            Toast.makeText(this, "Error saving datasheet.", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ NEW FUNCTION — Open the uploaded file safely
    private fun openSelectedFile() {
        val uri = selectedFileUri
        if (uri == null) {
            Toast.makeText(this, "No file selected.", Toast.LENGTH_SHORT).show()
            return
        }
        openFile(uri)
    }

    private fun openFile(uri: Uri) {
        try {
            val mime = contentResolver.getType(uri) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "No app found to open this file type.", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            Log.e("UploadActivity", "Error opening file", e)
            Toast.makeText(this, "Error opening file.", Toast.LENGTH_SHORT).show()
        }
    }
}
