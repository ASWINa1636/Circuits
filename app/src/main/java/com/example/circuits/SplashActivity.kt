package com.example.circuits

import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // References to the views
        val logo = findViewById<ImageView>(R.id.logoImage)
        val appName = findViewById<TextView>(R.id.appNameText)

        // Fade-in animation for logo
        val logoFade = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f).apply {
            duration = 1200
        }

        // Slide-up + fade-in for text
        val textFade = ObjectAnimator.ofFloat(appName, "alpha", 0f, 1f).apply {
            duration = 1000
            startDelay = 500
        }
        val textMove = ObjectAnimator.ofFloat(appName, "translationY", 50f, 0f).apply {
            duration = 1000
            startDelay = 500
        }

        // Combine animations
        AnimatorSet().apply {
            playTogether(logoFade, textFade, textMove)
            start()
        }

        // Move to MainActivity after animation
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 2500)
    }
}
