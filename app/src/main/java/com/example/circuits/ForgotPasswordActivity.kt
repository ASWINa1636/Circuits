package com.example.circuits

import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.circuits.db.DatabaseHelper

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var btnReset: Button
    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        etEmail = findViewById(R.id.etForgotPasswordEmail)
        btnReset = findViewById(R.id.btnResetPassword)
        db = DatabaseHelper(this)

        supportActionBar?.title = getString(R.string.forgot_password_title) // Set toolbar title
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Show back button

        btnReset.setOnClickListener {
            handlePasswordReset()
        }
    }

    private fun handlePasswordReset() {
        val email = etEmail.text.toString().trim()

        if (email.isEmpty()) {
            etEmail.error = getString(R.string.error_fill_field)
            Toast.makeText(this, R.string.error_fill_field, Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = getString(R.string.error_invalid_email)
            Toast.makeText(this, R.string.error_invalid_email, Toast.LENGTH_SHORT).show()
            return
        }

        // --- DANGER: Highly Insecure - For Local Demo Only ---
        // In a real app, NEVER retrieve and display the password.
        // Send a secure reset token/link via email instead.
        val password = db.getPasswordByEmail(email)

        if (password != null) {
            Log.i("ForgotPassword", "Password retrieved for $email (DEMO ONLY)")
            // Show the password in a Toast (VERY INSECURE - DEMO ONLY)
            Toast.makeText(this, "DEMO ONLY: Password is '$password'", Toast.LENGTH_LONG).show()
            // In a real app: Trigger email sending logic here.
            // Toast.makeText(this, R.string.password_reset_email_sent, Toast.LENGTH_LONG).show()
            finish() // Close the activity after showing the password/message
        } else {
            Log.w("ForgotPassword", "No account found for email: $email")
            Toast.makeText(this, R.string.error_email_not_found, Toast.LENGTH_SHORT).show()
        }
        // --- End Insecure Demo Code ---
    }

    // Handle the back button in the ActionBar
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

