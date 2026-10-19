package com.example.circuits

import android.content.Intent
import android.os.Bundle
import android.util.Patterns // Import Patterns for email validation
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.circuits.db.DatabaseHelper

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etLoginEmail)
        val etPassword = findViewById<EditText>(R.id.etLoginPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvSignup = findViewById<TextView>(R.id.tvGoToSignup)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword) // Find Forgot Password TextView

        val db = DatabaseHelper(this)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            // Basic validation
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, R.string.error_fill_all_fields, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Email format validation
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, R.string.error_invalid_email, Toast.LENGTH_SHORT).show()
                etEmail.error = getString(R.string.error_invalid_email)
                return@setOnClickListener
            }

            val userId = db.checkUserByEmail(email, password)
            if (userId != -1L) {
                Toast.makeText(this, R.string.login_successful, Toast.LENGTH_SHORT).show()

                // Go to MainActivity
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("USER_ID", userId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                // finish() is implicit
            } else {
                Toast.makeText(this, R.string.invalid_credentials, Toast.LENGTH_SHORT).show()
            }
        }

        tvSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        // FIX: Add OnClickListener for Forgot Password
        tvForgotPassword.setOnClickListener {
            // Show placeholder message using a string resource
            Toast.makeText(this, R.string.forgot_password_not_implemented, Toast.LENGTH_LONG).show()
            // In a real app, you would start a password reset activity/fragment here
        }
    }
}

