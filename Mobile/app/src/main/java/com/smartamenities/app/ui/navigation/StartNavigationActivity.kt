package com.smartamenities.app.ui.navigation

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.smartamenities.app.R
import com.smartamenities.app.session.NavigationSessionState

class StartNavigationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start_navigation)

        val selectedAmenity = intent.getStringExtra(NavigationExtras.EXTRA_AMENITY_NAME)
        if (selectedAmenity.isNullOrBlank()) {
            Toast.makeText(this, R.string.missing_amenity_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val selectedAmenityDisplayName = intent.getStringExtra(NavigationExtras.EXTRA_AMENITY_DISPLAY_NAME)
            ?: selectedAmenity
        val sessionSeed = intent.getStringExtra(NavigationExtras.EXTRA_SESSION_SEED)
            ?: NavigationSessionState.currentSessionSeed()

        val destinationText = getString(R.string.destination_label, selectedAmenityDisplayName)
        findViewById<TextView>(R.id.tvStartNavDestination).text = destinationText
        findViewById<TextView>(R.id.tvCurrentLocation).text = getString(
            R.string.current_location_placeholder,
            getString(R.string.start_location_default)
        )

        val stepFreeCheckBox = findViewById<CheckBox>(R.id.cbStepFreeRoute)
        val startNavigationButton = findViewById<Button>(R.id.btnStartNavigation)
        startNavigationButton.setOnClickListener {
            val navigationIntent = Intent(this, NavigationActivity::class.java)
            navigationIntent.putExtra(
                NavigationExtras.EXTRA_AMENITY_ID,
                intent.getStringExtra(NavigationExtras.EXTRA_AMENITY_ID)
            )
            navigationIntent.putExtra(NavigationExtras.EXTRA_AMENITY_NAME, selectedAmenity)
            navigationIntent.putExtra(NavigationExtras.EXTRA_AMENITY_DISPLAY_NAME, selectedAmenityDisplayName)
            navigationIntent.putExtra(
                NavigationExtras.EXTRA_AMENITY_TYPE,
                intent.getStringExtra(NavigationExtras.EXTRA_AMENITY_TYPE)
            )
            navigationIntent.putExtra(
                NavigationExtras.EXTRA_AMENITY_LOCATION_NAME,
                intent.getStringExtra(NavigationExtras.EXTRA_AMENITY_LOCATION_NAME)
            )
            navigationIntent.putExtra(
                NavigationExtras.EXTRA_AMENITY_LEVEL,
                intent.getStringExtra(NavigationExtras.EXTRA_AMENITY_LEVEL)
            )
            navigationIntent.putExtra(
                NavigationExtras.EXTRA_AMENITY_LATITUDE,
                intent.getDoubleExtra(NavigationExtras.EXTRA_AMENITY_LATITUDE, Double.NaN)
            )
            navigationIntent.putExtra(
                NavigationExtras.EXTRA_AMENITY_LONGITUDE,
                intent.getDoubleExtra(NavigationExtras.EXTRA_AMENITY_LONGITUDE, Double.NaN)
            )
            navigationIntent.putExtra(NavigationExtras.EXTRA_STEP_FREE_ROUTE, stepFreeCheckBox.isChecked)
            navigationIntent.putExtra(NavigationExtras.EXTRA_SESSION_SEED, sessionSeed)
            startActivity(navigationIntent)
        }
    }
}

