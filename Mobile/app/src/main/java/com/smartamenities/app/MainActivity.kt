package com.smartamenities.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.smartamenities.app.ui.amenities.AmenityListActivity
import com.smartamenities.app.session.NavigationSessionState
import com.smartamenities.app.ui.navigation.NavigationExtras

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NavigationSessionState.init(this)
        setContentView(R.layout.activity_main)

        val startButton = findViewById<Button>(R.id.btnStart)
        startButton.setOnClickListener {
            val sessionSeed = NavigationSessionState.currentSessionSeed()
            val amenityIntent = Intent(this, AmenityListActivity::class.java).apply {
                putExtra(NavigationExtras.EXTRA_SESSION_SEED, sessionSeed)
            }
            startActivity(amenityIntent)
        }
    }
}
