package com.smartamenities.app.ui.amenities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.smartamenities.app.R
import com.smartamenities.app.data.SmartAmenitiesRepository
import com.smartamenities.app.model.AmenityResponse
import com.smartamenities.app.session.NavigationSessionState
import com.smartamenities.app.ui.navigation.NavigationExtras
import com.smartamenities.app.ui.navigation.StartNavigationActivity
import java.util.Locale

class AmenityListActivity : AppCompatActivity() {
    private lateinit var amenitiesContainer: LinearLayout
    private lateinit var amenityStateText: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var retryButton: Button
    private lateinit var amenityTypeSpinner: Spinner
    private val repository = SmartAmenitiesRepository()
    private lateinit var sessionSeed: String
    private var sessionConfigured: Boolean = false
    private var sessionConfigurationInFlight: Boolean = false

    private val amenityTypes = AmenityTypeOption.entries

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_amenity_list)

        sessionSeed = intent.getStringExtra(NavigationExtras.EXTRA_SESSION_SEED)
            ?: NavigationSessionState.currentSessionSeed()

        amenitiesContainer = findViewById(R.id.llAmenitiesContainer)
        amenityStateText = findViewById(R.id.tvAmenityLoading)
        loadingIndicator = findViewById(R.id.pbAmenityLoading)
        retryButton = findViewById(R.id.btnRetryAmenities)
        amenityTypeSpinner = findViewById(R.id.spAmenityType)

        amenityTypeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            amenityTypes.map { it.displayLabel }
        )
        amenityTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadAmenities()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        retryButton.setOnClickListener { loadAmenities() }
    }

    private fun loadAmenities() {
        showLoadingState()

        if (!sessionConfigured) {
            if (sessionConfigurationInFlight) return
            sessionConfigurationInFlight = true
            val segment = NavigationSessionState.currentSegment()
            repository.configureSession(sessionSeed, segment.apiValue) { result ->
                sessionConfigurationInFlight = false
                sessionConfigured = true
                result.exceptionOrNull()?.let {
                    android.util.Log.w("AmenityListActivity", "Session configure failed; continuing with local flow", it)
                }
                loadAmenitiesInternal()
            }
            return
        }

        loadAmenitiesInternal()
    }

    private fun loadAmenitiesInternal() {
        showLoadingState()

        val selectedType = amenityTypes.getOrElse(amenityTypeSpinner.selectedItemPosition) {
            AmenityTypeOption.MEN_RESTROOM
        }

        repository.getAmenities(
            type = selectedType.apiValue,
            currentLat = null,
            currentLon = null,
            sessionSeed = sessionSeed
        ) { result ->
            val amenities = result.getOrNull()
            if (amenities == null) {
                showAmenityLoadError(R.string.amenity_load_error)
                return@getAmenities
            }

            val filteredAmenities = amenities.filter {
                it.amenityType.equals(selectedType.apiValue, ignoreCase = true)
            }

            if (filteredAmenities.isEmpty()) {
                showAmenityLoadError(R.string.amenity_empty_state)
                return@getAmenities
            }

            // Preserve backend ranking order exactly; backend is source of truth.
            bindAmenities(filteredAmenities)
        }
    }

    private fun bindAmenities(amenities: List<AmenityResponse>) {
        amenitiesContainer.removeAllViews()

        for (amenity in amenities) {
            val amenityView = layoutInflater.inflate(
                R.layout.item_amenity,
                amenitiesContainer,
                false
            )

            val amenityType = AmenityTypeOption.fromApiValue(amenity.amenityType)
            val displayTitle = amenityType?.displayLabel ?: amenity.name
            amenityView.findViewById<TextView>(R.id.tvAmenityTitle).text = displayTitle
            amenityView.findViewById<TextView>(R.id.tvAmenitySubtitle).apply {
                text = buildSubtitle(amenity)
                visibility = View.VISIBLE
            }

            val amenitySelectable = isSelectable(amenity)
            amenityView.isEnabled = amenitySelectable
            amenityView.isClickable = amenitySelectable
            amenityView.alpha = if (amenitySelectable) 1.0f else 0.6f

            amenityView.setOnClickListener {
                if (!amenitySelectable) {
                    Toast.makeText(this, R.string.amenity_unavailable_short, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val startNavigationIntent = Intent(this, StartNavigationActivity::class.java)
                startNavigationIntent.putExtra(NavigationExtras.EXTRA_AMENITY_ID, amenity.id)
                startNavigationIntent.putExtra(NavigationExtras.EXTRA_AMENITY_NAME, amenity.name)
                startNavigationIntent.putExtra(
                    NavigationExtras.EXTRA_AMENITY_DISPLAY_NAME,
                    "$displayTitle - ${amenity.level}"
                )
                startNavigationIntent.putExtra(NavigationExtras.EXTRA_AMENITY_TYPE, amenity.amenityType)
                startNavigationIntent.putExtra(NavigationExtras.EXTRA_AMENITY_LEVEL, amenity.level)
                startNavigationIntent.putExtra(NavigationExtras.EXTRA_AMENITY_LATITUDE, amenity.latitude)
                startNavigationIntent.putExtra(NavigationExtras.EXTRA_AMENITY_LONGITUDE, amenity.longitude)
                startNavigationIntent.putExtra(NavigationExtras.EXTRA_SESSION_SEED, sessionSeed)
                startActivity(startNavigationIntent)
            }

            amenitiesContainer.addView(amenityView)
        }

        showContentState()
    }

    private fun buildSubtitle(amenity: AmenityResponse): String {
        val distanceText = amenity.distanceMeters?.let { d ->
            String.format(Locale.US, "~%.0f m", d)
        } ?: getString(R.string.distance_unavailable)

        val statusText = amenity.status ?: getString(R.string.status_unavailable)
        val waitText = amenity.waitTimeMinutes?.let { "${it}m" } ?: getString(R.string.live_field_unavailable)
        val stallsText = amenity.stallsAvailable?.toString() ?: getString(R.string.live_field_unavailable)
        val occupancyText = amenity.occupancyStatus ?: getString(R.string.live_field_unavailable)
        val reasonText = amenity.statusReason?.takeIf { it.isNotBlank() }

        val lineOne = "${amenity.level}  |  $distanceText  |  $statusText"
        val lineTwo = "Wait: $waitText  |  Stalls: $stallsText  |  Occupancy: $occupancyText"
        val lineThree = if (reasonText != null) "\nReason: $reasonText" else ""
        return "$lineOne\n$lineTwo$lineThree"
    }

    private fun isSelectable(amenity: AmenityResponse): Boolean {
        val status = amenity.status.orEmpty().uppercase(Locale.US)
        val statusOpen = status.isBlank() || status == "OPEN"
        val backendSelectable = amenity.selectable ?: true
        return statusOpen && backendSelectable
    }

    private fun showLoadingState() {
        loadingIndicator.visibility = View.VISIBLE
        amenityStateText.visibility = View.VISIBLE
        amenityStateText.text = getString(R.string.loading_amenities)
        retryButton.visibility = View.GONE
        amenitiesContainer.visibility = View.GONE
    }

    private fun showContentState() {
        loadingIndicator.visibility = View.GONE
        amenityStateText.visibility = View.GONE
        retryButton.visibility = View.GONE
        amenitiesContainer.visibility = View.VISIBLE
    }

    private fun showAmenityLoadError(messageRes: Int) {
        loadingIndicator.visibility = View.GONE
        amenityStateText.visibility = View.VISIBLE
        amenityStateText.text = getString(messageRes)
        retryButton.visibility = View.VISIBLE
        amenitiesContainer.visibility = View.GONE
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
}
