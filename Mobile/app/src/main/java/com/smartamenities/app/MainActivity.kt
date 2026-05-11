package com.smartamenities.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.smartamenities.app.ui.amenities.AmenityListActivity
import com.smartamenities.app.session.NavigationSessionState
import com.smartamenities.app.ui.navigation.NavigationExtras

class MainActivity : AppCompatActivity() {
    private lateinit var currentSegmentView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        currentSegmentView = findViewById(R.id.tvCurrentSegment)
        renderCurrentSegment()

        val startButton = findViewById<Button>(R.id.btnStart)
        startButton.setOnClickListener {
            val sessionSeed = NavigationSessionState.currentSessionSeed()
            val amenityIntent = Intent(this, AmenityListActivity::class.java).apply {
                putExtra(NavigationExtras.EXTRA_SESSION_SEED, sessionSeed)
            }
            startActivity(amenityIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        renderCurrentSegment()
    }

    private fun renderCurrentSegment() {
        currentSegmentView.text = getString(
            R.string.current_demo_segment_label,
            NavigationSessionState.currentSegment().apiValue
        )
    }
}
