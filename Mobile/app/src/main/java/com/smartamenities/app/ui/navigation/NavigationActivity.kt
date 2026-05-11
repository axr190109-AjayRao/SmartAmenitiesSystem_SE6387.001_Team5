package com.smartamenities.app.ui.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.smartamenities.app.MainActivity
import com.smartamenities.app.R
import com.smartamenities.app.data.SmartAmenitiesRepository
import com.smartamenities.app.model.AmenityLiveStatusResponse
import com.smartamenities.app.model.AmenityReplacementResponse
import com.smartamenities.app.model.InfrastructureStatusResponse
import com.smartamenities.app.model.RouteGeoPoint
import com.smartamenities.app.model.RouteRequest
import com.smartamenities.app.model.RouteProgressRequest
import com.smartamenities.app.model.RouteProgressResponse
import com.smartamenities.app.model.RouteResponse
import com.smartamenities.app.model.RouteNavigationModel
import com.smartamenities.app.model.toNavigationModel
import com.smartamenities.app.session.DemoSegment
import com.smartamenities.app.session.NavigationSessionState
import com.smartamenities.app.ui.amenities.AmenityListActivity
import java.util.Locale
import kotlin.random.Random
import kotlin.math.*

class NavigationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "NavigationActivity"
        private const val PROGRESS_TICK_MS = 1000L
        private const val PROGRESS_METERS_PER_TICK = 18.0
        private const val LIVE_STATUS_CHECK_INTERVAL_TICKS = 3
        // Deviation simulation: wrong turn fires at 25% of total route distance.
        private const val OFF_ROUTE_SIM_TRIGGER_FRACTION = 0.25
        private const val DEVIATION_STEPS = 3
        private const val DEVIATION_MIN_BEARING_DIFF_DEG = 30.0
        private const val DEVIATION_MAX_BEARING_DIFF_DEG = 150.0
        private const val DEVIATION_MAX_SEARCH_RADIUS_METERS = 60.0
        private const val DEVIATION_MIN_DIST_FROM_ROUTE_METERS = 3.0
    }

    private lateinit var loadingContainer: LinearLayout
    private lateinit var routeDetailsContainer: LinearLayout
    private lateinit var destinationView: TextView
    private lateinit var accessibilityView: TextView
    private lateinit var routeFromView: TextView
    private lateinit var routeToView: TextView
    private lateinit var estimatedTimeView: TextView
    private lateinit var routeStepsView: TextView
    private lateinit var indoorMapView: IndoorMapView
    private lateinit var retryRouteButton: Button
    private lateinit var tvWaitTime: TextView
    private lateinit var tvStallsAvailable: TextView
    private lateinit var tvOccupancyStatus: TextView

    private var selectedAmenityId: String? = null
    private lateinit var selectedAmenityRequestName: String
    private lateinit var selectedAmenityDisplayName: String
    private var selectedAmenityType: String = ""
    private var selectedAmenityLatitude: Double? = null
    private var selectedAmenityLongitude: Double? = null
    private var isStepFreeRouteEnabled: Boolean = false
    private var sessionSeed: String = ""
    private val repository = SmartAmenitiesRepository()
    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null
    private var currentRouteResponse: RouteResponse? = null
    private var currentRouteModel: RouteNavigationModel? = null
    private var simulatedProgressMeters: Double = 0.0
    private var elapsedTicks: Int = 0
    private var closureCheckDelayTicks: Int = 0
    private var rerouteMuted: Boolean = false
    private var pendingClosureDialogShown: Boolean = false
    private var lastKnownSnappedLat: Double? = null
    private var lastKnownSnappedLon: Double? = null
    private var closureSnapshotLat: Double? = null
    private var closureSnapshotLon: Double? = null
    private var isNavigationCompleted: Boolean = false
    private var hasShownArrivalDialog: Boolean = false
    private var indoorLevelMap: IndoorLevelMap? = null
    private var deviationPath: List<IndoorGeoPoint> = emptyList()
    private var deviationStep: Int = 0
    private var offRouteSimTriggerMeters: Double = 0.0
    private var simulatedActualLat: Double? = null
    private var simulatedActualLon: Double? = null
    private var liveStatusRequestInFlight: Boolean = false
    private var replacementRequestInFlight: Boolean = false
    private var rerouteDialogVisible: Boolean = false
    private var isOffRouteRerouteInProgress: Boolean = false
    private var hasRerouted: Boolean = false
    private var infraCheckDelayTicks: Int = 0
    private var infraRerouteMuted: Boolean = false
    private var infraStatusRequestInFlight: Boolean = false
    private var infraSnapshotLat: Double? = null
    private var infraSnapshotLon: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation)

        loadingContainer = findViewById(R.id.llLoadingContainer)
        routeDetailsContainer = findViewById(R.id.llRouteDetailsContainer)
        destinationView = findViewById(R.id.tvDestination)
        accessibilityView = findViewById(R.id.tvAccessibility)
        routeFromView = findViewById(R.id.tvRouteFrom)
        routeToView = findViewById(R.id.tvRouteTo)
        estimatedTimeView = findViewById(R.id.tvEstimatedTime)
        routeStepsView = findViewById(R.id.tvRouteSteps)
        indoorMapView = findViewById(R.id.indoorMapView)
        retryRouteButton = findViewById(R.id.btnRetryRoute)
        tvWaitTime = findViewById(R.id.tvWaitTime)
        tvStallsAvailable = findViewById(R.id.tvStallsAvailable)
        tvOccupancyStatus = findViewById(R.id.tvOccupancyStatus)

        val selectedAmenityName = intent.getStringExtra(NavigationExtras.EXTRA_AMENITY_NAME)
        if (selectedAmenityName.isNullOrBlank()) {
            Toast.makeText(this, R.string.missing_amenity_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        selectedAmenityId = intent.getStringExtra(NavigationExtras.EXTRA_AMENITY_ID)
        selectedAmenityRequestName = selectedAmenityName
        selectedAmenityDisplayName = intent.getStringExtra(NavigationExtras.EXTRA_AMENITY_DISPLAY_NAME)
            ?: selectedAmenityRequestName
        selectedAmenityType = intent.getStringExtra(NavigationExtras.EXTRA_AMENITY_TYPE).orEmpty()
        selectedAmenityLatitude = intent.getDoubleExtra(NavigationExtras.EXTRA_AMENITY_LATITUDE, Double.NaN)
            .takeIf { !it.isNaN() }
        selectedAmenityLongitude = intent.getDoubleExtra(NavigationExtras.EXTRA_AMENITY_LONGITUDE, Double.NaN)
            .takeIf { !it.isNaN() }
        isStepFreeRouteEnabled = intent.getBooleanExtra(NavigationExtras.EXTRA_STEP_FREE_ROUTE, false)
        sessionSeed = intent.getStringExtra(NavigationExtras.EXTRA_SESSION_SEED)
            ?: NavigationSessionState.currentSessionSeed()

        destinationView.text = getString(R.string.destination_label, selectedAmenityDisplayName)
        accessibilityView.text = getString(
            R.string.accessibility_label,
            if (isStepFreeRouteEnabled) getString(R.string.accessibility_on) else getString(R.string.accessibility_off)
        )
        routeToView.text = getString(R.string.route_to_label, selectedAmenityDisplayName)

        initializeIndoorMap()

        retryRouteButton.setOnClickListener {
            showLoading()
            loadRoute(selectedAmenityRequestName, isStepFreeRouteEnabled, resetClosureState = true)
        }

        showLoading()
        loadRoute(selectedAmenityRequestName, isStepFreeRouteEnabled, resetClosureState = true)

        findViewById<Button>(R.id.btnCancelNavigation).setOnClickListener {
            val backIntent = Intent(this, AmenityListActivity::class.java).apply {
                putExtra(NavigationExtras.EXTRA_SESSION_SEED, sessionSeed)
            }
            try {
                startActivity(backIntent)
            } catch (exception: ActivityNotFoundException) {
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        stopProgressSimulation()
    }

    private fun initializeIndoorMap() {
        val map = runCatching { GeoJsonIndoorMapLoader.loadFromAssets(this, "maps/Level3.geojson") }
            .onFailure { Log.w(TAG, "Unable to load GeoJSON asset: maps/Level3.geojson", it) }
            .getOrNull()

        if (map == null) {
            Log.w(TAG, "Level3 map asset was not loaded; map area will remain empty")
            return
        }
        indoorLevelMap = map
        indoorMapView.setIndoorMap(map)
        indoorMapView.resetTransform()
    }

    private fun loadRoute(
        destination: String,
        accessibilityOn: Boolean,
        currentLatitude: Double? = null,
        currentLongitude: Double? = null,
        currentLocationLabel: String = NavigationExtras.DEFAULT_START_LOCATION,
        resetClosureState: Boolean = false,
        suppressDeviation: Boolean = false
    ) {
        stopProgressSimulation()
        isNavigationCompleted = false
        hasShownArrivalDialog = false
        elapsedTicks = 0
        liveStatusRequestInFlight = false
        replacementRequestInFlight = false
        rerouteDialogVisible = false
        isOffRouteRerouteInProgress = false
        hasRerouted = false
        infraStatusRequestInFlight = false
        infraSnapshotLat = null
        infraSnapshotLon = null
        pendingClosureDialogShown = false
        closureSnapshotLat = null
        closureSnapshotLon = null
        if (resetClosureState) {
            rerouteMuted = false
            infraRerouteMuted = false
        }
        val request = RouteRequest(
            destination = destination,
            currentLocation = currentLocationLabel,
            accessibilityOn = accessibilityOn,
            destinationAmenityId = selectedAmenityId,
            sessionSeed = sessionSeed,
            currentLatitude = currentLatitude,
            currentLongitude = currentLongitude
        )

        val applyRoute: (RouteResponse) -> Unit = { route ->
            currentRouteResponse = route
            currentRouteModel = route.toNavigationModel()
            lastKnownSnappedLat = route.startLatitude
            lastKnownSnappedLon = route.startLongitude
            simulatedActualLat = null
            simulatedActualLon = null
            deviationStep = 0
            val model = currentRouteModel ?: route.toNavigationModel()
            val totalDist = model.totalDistanceMeters ?: 0.0
            offRouteSimTriggerMeters = totalDist * OFF_ROUTE_SIM_TRIGGER_FRACTION
            indoorLevelMap?.let { levelMap ->
                deviationPath = computeDeviationPath(model.routeGeoPoints, levelMap, offRouteSimTriggerMeters, totalDist)
            }
            if (resetClosureState) {
                // Both polls now use 1-tick interval after the delay, so popup fires at delay+1
                // (poll 2) or delay+2 (poll 3, worst case). Set delay so worst-case popup (delay+2)
                // lands in 20%-65% of route length, guaranteeing mid-route on any route.
                val actualTotalTicks = (totalDist / PROGRESS_METERS_PER_TICK).toInt().coerceAtLeast(5)
                val closureMin = ((actualTotalTicks * 0.20) - 1).toInt().coerceAtLeast(1)
                val closureMax = ((actualTotalTicks * 0.65) - 2).toInt().coerceAtLeast(closureMin + 1)
                closureCheckDelayTicks = Random.nextInt(closureMin, closureMax + 1)
                val infraMin = ((actualTotalTicks * 0.20) - 1).toInt().coerceAtLeast(1)
                val infraMax = ((actualTotalTicks * 0.65) - 2).toInt().coerceAtLeast(infraMin + 1)
                infraCheckDelayTicks = Random.nextInt(infraMin, infraMax + 1)
            }
            if (suppressDeviation) {
                deviationPath = emptyList()
                offRouteSimTriggerMeters = Double.MAX_VALUE
                hasRerouted = true
            }
            renderRouteDetails(route, currentRouteModel ?: route.toNavigationModel())
            showRouteDetails()
            startProgressSimulation(resetProgress = true)
        }

        repository.createRoute(request, sessionSeed) { result ->
            val route = result.getOrNull()
            if (route != null) {
                applyRoute(route)
                return@createRoute
            }
            Log.e(TAG, "Failed to load route from backend lat=$currentLatitude lon=$currentLongitude", result.exceptionOrNull())
            showRouteLoadError(result.exceptionOrNull())
        }
    }

    private fun renderRouteDetails(route: RouteResponse, routeModel: RouteNavigationModel) {
        val routeDestination = routeModel.destination.ifBlank { selectedAmenityDisplayName }
        val routePointsFromBackend = routeModel.routeGeoPoints
            .map { IndoorGeoPoint(longitude = it.longitude, latitude = it.latitude) }
        // routeSteps are ordered by backend; preserve order exactly, do not rewrite or synthesize.
        val routeStepInstructions = routeModel.orderedInstructions

        destinationView.text = getString(R.string.destination_label, routeDestination)
        accessibilityView.text = getString(
            R.string.accessibility_label,
            if (route.accessibilityOn) getString(R.string.accessibility_on) else getString(R.string.accessibility_off)
        )
        routeFromView.text = getString(
            R.string.route_from_label,
            routeModel.startLocationName.ifBlank { getString(R.string.start_location_default) }
        )
        routeToView.text = getString(R.string.route_to_label, routeDestination)
        estimatedTimeView.text = getString(R.string.estimated_time_label, routeModel.estimatedTime)

        // Format and display backend-provided route steps in exact order.
        val stepLines = RouteSummaryFormatter.formatSteps(routeStepInstructions)
        routeStepsView.text = if (stepLines.isNotBlank()) {
            getString(R.string.route_steps_title) + "\n" + stepLines
        } else if (routePointsFromBackend.size >= 2) {
            // Fallback: if steps are empty but geometry exists, offer generic guidance.
            getString(R.string.route_steps_title) + "\n" +
                getString(R.string.route_steps_fallback_use_path)
        } else {
            getString(R.string.route_steps_empty)
        }

        if (routePointsFromBackend.size < 2) {
            Log.w(TAG, "Backend routeGeoPoints missing/insufficient for indoor polyline (count=${routePointsFromBackend.size}); skipping route draw")
        }

        // Resolve markers using backend coordinates; fallback only if backend data is missing.
        val startPoint = resolveStartPoint(routeModel, routePointsFromBackend)
        val destinationPoint = resolveDestinationPoint(routeModel, routePointsFromBackend)
        val routePoints = routePointsFromBackend

        // Draw route overlay using backend-provided geometry and markers.
        indoorMapView.setRouteOverlay(
            start = startPoint,
            destination = destinationPoint,
            destinationMarkerType = resolveDestinationMarkerType(selectedAmenityType),
            routePoints = routePoints,
            destinationText = routeDestination
        )
    }

    private fun startProgressSimulation(resetProgress: Boolean) {
        val route = currentRouteResponse ?: return
        val routeModel = currentRouteModel ?: route.toNavigationModel()
        currentRouteModel = routeModel

        if (isNavigationCompleted) return
        if (!routeModel.hasProgressMetadataForSimulation()) return

        val routePoints = routeModel.routeGeoPoints
        val stepRanges = routeModel.stepProgressRanges
        val totalDistance = routeModel.totalDistanceMeters ?: return
        if (routePoints.isEmpty() || stepRanges.isEmpty() || totalDistance <= 0.0) return

        if (resetProgress) {
            simulatedProgressMeters = 0.0
        }
        progressRunnable?.let { progressHandler.removeCallbacks(it) }

        progressRunnable = object : Runnable {
            override fun run() {
                val activeRoute = currentRouteResponse ?: return
                val activeModel = currentRouteModel ?: activeRoute.toNavigationModel().also { currentRouteModel = it }
                elapsedTicks += 1
                val deviatedPoint = advanceDeviationPosition()
                val actualLatitude = deviatedPoint?.latitude
                    ?: lastKnownSnappedLat
                    ?: activeRoute.startLatitude
                    ?: NavigationExtras.DEFAULT_START_LATITUDE
                val actualLongitude = deviatedPoint?.longitude
                    ?: lastKnownSnappedLon
                    ?: activeRoute.startLongitude
                    ?: NavigationExtras.DEFAULT_START_LONGITUDE
                val progressRequest = RouteProgressRequest(
                    routeGeoPoints = activeModel.routeGeoPoints,
                    stepProgressRanges = activeModel.stepProgressRanges,
                    progressMeters = simulatedProgressMeters,
                    totalDistanceMeters = activeModel.totalDistanceMeters ?: return,
                    actualLatitude = actualLatitude,
                    actualLongitude = actualLongitude
                )

                repository.simulateRouteProgress(progressRequest) { result ->
                    val progress = result.getOrNull()
                    if (progress == null) {
                        Log.e(TAG, "Failed to simulate route progress", result.exceptionOrNull())
                        scheduleNextProgressTick()
                        return@simulateRouteProgress
                    }

                    renderRouteProgress(activeRoute, activeModel, progress)
                    pollLiveStatusAndCheckClosure(progress)
                    maybeCheckInfrastructureStatus(activeModel, progress)

                    if (progress.offRoute == true && simulatedActualLat != null
                        && !hasRerouted && !isOffRouteRerouteInProgress && !rerouteDialogVisible) {
                        if (!NavigationSessionState.isDeviationRerouteAllowed()) {
                            Log.i(TAG, "Deviation reroute blocked outside SEGMENT_4; continuing normal navigation")
                            scheduleNextProgressTick()
                            return@simulateRouteProgress
                        }
                        val rerouteLat = simulatedActualLat!!
                        val rerouteLon = simulatedActualLon!!
                        isOffRouteRerouteInProgress = true
                        stopProgressSimulation()
                        triggerOffRouteReroute(rerouteLat, rerouteLon)
                        return@simulateRouteProgress
                    }

                    if (rerouteDialogVisible || isOffRouteRerouteInProgress) {
                        return@simulateRouteProgress
                    }

                    val clampedProgress = progress.clampedProgressMeters ?: simulatedProgressMeters
                    val arrived = isArrivalReached(activeModel, progress)
                    if (arrived || clampedProgress >= totalDistance) {
                        isNavigationCompleted = true
                        stopProgressSimulation()
                        showArrivalDialogAndReturnHome(activeRoute.destination.ifBlank { selectedAmenityDisplayName })
                    } else {
                        simulatedProgressMeters = (clampedProgress + PROGRESS_METERS_PER_TICK)
                            .coerceAtMost(totalDistance)
                        scheduleNextProgressTick()
                    }
                }
            }
        }

        progressHandler.post(progressRunnable!!)
    }

    private fun scheduleNextProgressTick() {
        val runnable = progressRunnable ?: return
        progressHandler.removeCallbacks(runnable)
        progressHandler.postDelayed(runnable, PROGRESS_TICK_MS)
    }

    private fun stopProgressSimulation() {
        progressRunnable?.let { progressHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun renderRouteProgress(
        route: RouteResponse,
        routeModel: RouteNavigationModel,
        progress: RouteProgressResponse
    ) {
        val routePoints = routeModel.routeGeoPoints.map { IndoorGeoPoint(longitude = it.longitude, latitude = it.latitude) }
        val stepInstructions = routeModel.orderedInstructions
        val arrivalReached = isArrivalReached(routeModel, progress)
        val activeIndex = routeModel.resolveActiveStepIndex(progress, arrivalReached)
        val activeInstructionText = progress.activeInstruction.orEmpty().ifBlank {
            activeIndex?.let { idx -> stepInstructions.getOrNull(idx).orEmpty() }.orEmpty()
        }
        val remainingDistanceText = formatRemainingDistanceText(progress.remainingDistanceMeters)

        val stepLines = RouteSummaryFormatter.formatSteps(stepInstructions, activeIndex)
        routeStepsView.text = if (stepLines.isNotBlank()) {
            val activeLine = if (activeInstructionText.isNotBlank()) {
                "\n${getString(R.string.current_step_label, activeInstructionText)}"
            } else {
                ""
            }
            val remainingLine = if (remainingDistanceText != null) {
                "\n${getString(R.string.remaining_distance_label, remainingDistanceText)}"
            } else {
                ""
            }
            getString(R.string.route_steps_title) + "\n" + stepLines + activeLine + remainingLine
        } else {
            getString(R.string.route_steps_empty)
        }

        // During deviation show the actual off-route position; otherwise follow the snapped position.
        val currentPoint = if (simulatedActualLat != null && simulatedActualLon != null) {
            IndoorGeoPoint(longitude = simulatedActualLon!!, latitude = simulatedActualLat!!)
        } else {
            IndoorGeoPoint(
                longitude = progress.snappedLongitude ?: route.startLongitude ?: NavigationExtras.DEFAULT_START_LONGITUDE,
                latitude = progress.snappedLatitude ?: route.startLatitude ?: NavigationExtras.DEFAULT_START_LATITUDE
            )
        }
        lastKnownSnappedLat = progress.snappedLatitude ?: lastKnownSnappedLat
        lastKnownSnappedLon = progress.snappedLongitude ?: lastKnownSnappedLon
        val destinationPoint = resolveDestinationPoint(routeModel, routePoints)

        indoorMapView.setRouteOverlay(
            start = currentPoint,
            destination = destinationPoint,
            destinationMarkerType = resolveDestinationMarkerType(selectedAmenityType),
            routePoints = routePoints,
            destinationText = route.destination.ifBlank { selectedAmenityDisplayName }
        )
    }

    private fun pollLiveStatusAndCheckClosure(progress: RouteProgressResponse) {
        val amenityId = selectedAmenityId ?: return
        if (isNavigationCompleted) return
        if (liveStatusRequestInFlight) return

        val isClosureSegment = NavigationSessionState.currentSegment() == DemoSegment.SEGMENT_2
        // In Segment 3, hold off polling until after the initial delay so the backend's
        // closure trigger (poll 2-3) fires AFTER the delay, not before it.
        val pollStartTick = if (isClosureSegment) closureCheckDelayTicks else 0
        if (elapsedTicks < pollStartTick) return
        // Segment 3: poll every tick after the delay so the backend's CLOSED trigger (poll 2-3)
        // fires mid-route regardless of route length. Other segments keep the normal interval.
        val pollInterval = if (isClosureSegment) 1 else LIVE_STATUS_CHECK_INTERVAL_TICKS
        if ((elapsedTicks - pollStartTick) % pollInterval != 0) return

        val currentLat = progress.snappedLatitude
            ?: lastKnownSnappedLat
            ?: currentRouteResponse?.startLatitude
            ?: NavigationExtras.DEFAULT_START_LATITUDE
        val currentLon = progress.snappedLongitude
            ?: lastKnownSnappedLon
            ?: currentRouteResponse?.startLongitude
            ?: NavigationExtras.DEFAULT_START_LONGITUDE

        liveStatusRequestInFlight = true
        repository.getAmenityLiveStatus(
            amenityId = amenityId,
            currentLat = currentLat,
            currentLon = currentLon,
            sessionSeed = sessionSeed
        ) { result ->
            liveStatusRequestInFlight = false
            val liveStatus = result.getOrNull() ?: return@getAmenityLiveStatus
            runOnUiThread { updateLiveStatusDisplay(liveStatus) }

            if (isClosureSegment
                && !rerouteMuted && !pendingClosureDialogShown && !rerouteDialogVisible
                && !replacementRequestInFlight
                && isAmenityClosed(liveStatus.status, liveStatus.selectable)
            ) {
                closureSnapshotLat = currentLat
                closureSnapshotLon = currentLon
                rerouteMuted = true
                fetchReplacementAndShowDialog(liveStatus, currentLat, currentLon)
            }
        }
    }

    private fun fetchReplacementAndShowDialog(
        liveStatus: AmenityLiveStatusResponse,
        currentLat: Double,
        currentLon: Double
    ) {
        val closedAmenityId = selectedAmenityId ?: return
        if (replacementRequestInFlight || rerouteDialogVisible) return

        replacementRequestInFlight = true
        repository.getNearestOpenReplacement(
            closedAmenityId = closedAmenityId,
            currentLat = currentLat,
            currentLon = currentLon,
            accessibilityOn = isStepFreeRouteEnabled,
            sessionSeed = sessionSeed
        ) { result ->
            replacementRequestInFlight = false
            val replacement = result.getOrNull()
            if (replacement == null) {
                Log.w(TAG, "Replacement lookup failed; keeping current route", result.exceptionOrNull())
                pendingClosureDialogShown = false
                rerouteMuted = true
                return@getNearestOpenReplacement
            }

            showAmenityClosedDialog(liveStatus, replacement)
        }
    }

    private fun showAmenityClosedDialog(
        liveStatus: AmenityLiveStatusResponse,
        replacement: AmenityReplacementResponse
    ) {
        if (isFinishing || isDestroyed || rerouteDialogVisible) return
        rerouteDialogVisible = true
        pendingClosureDialogShown = true
        stopProgressSimulation()

        val reason = liveStatus.statusReason?.takeIf { it.isNotBlank() }
        val replacementDistance = replacement.distanceMeters?.roundToInt()?.toString()
            ?: getString(R.string.live_field_unavailable)
        val replacementWait = replacement.waitTimeMinutes?.let { "${it}m" }
            ?: getString(R.string.live_field_unavailable)
        val replacementOccupancy = replacement.occupancyStatus ?: getString(R.string.live_field_unavailable)

        val dialogMessage = buildString {
            append(getString(R.string.reroute_popup_intro, selectedAmenityDisplayName))
            if (reason != null) {
                append("\n")
                append(getString(R.string.reroute_popup_reason, reason))
            }
            append("\n\n")
            append(getString(R.string.reroute_popup_suggestion, replacement.amenityName))
            append("\n")
            append(getString(R.string.reroute_popup_details, replacementDistance, replacementWait, replacementOccupancy))
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.reroute_popup_title))
            .setMessage(dialogMessage)
            .setCancelable(true)
            .setPositiveButton(getString(R.string.reroute_popup_accept)) { _, _ ->
                rerouteDialogVisible = false
                pendingClosureDialogShown = false
                rerouteMuted = true
                selectedAmenityId = replacement.amenityId
                selectedAmenityRequestName = replacement.amenityName
                selectedAmenityDisplayName = replacement.amenityName
                selectedAmenityType = replacement.amenityType
                showLoading()
                loadRoute(
                    destination = selectedAmenityRequestName,
                    accessibilityOn = isStepFreeRouteEnabled,
                    currentLatitude = closureSnapshotLat ?: lastKnownSnappedLat,
                    currentLongitude = closureSnapshotLon ?: lastKnownSnappedLon,
                    currentLocationLabel = "Terminal D Passenger Current Location",
                    resetClosureState = false
                )
            }
            .setNegativeButton(getString(R.string.reroute_popup_ignore)) { _, _ ->
                rerouteDialogVisible = false
                pendingClosureDialogShown = false
                rerouteMuted = true
                startProgressSimulation(resetProgress = false)
            }
            .setOnCancelListener {
                rerouteDialogVisible = false
                pendingClosureDialogShown = false
                rerouteMuted = true
                startProgressSimulation(resetProgress = false)
            }
            .show()
    }

    private fun isAmenityClosed(status: String?, selectable: Boolean?): Boolean {
        val statusUpper = status.orEmpty().uppercase(Locale.US)
        if (statusUpper == "CLOSED") return true
        if (statusUpper == "OPEN") return selectable == false
        return selectable == false
    }

    private fun isArrivalReached(routeModel: RouteNavigationModel, progress: RouteProgressResponse): Boolean {
        return RouteArrivalDecider.isArrived(
            progress = progress,
            totalDistanceMeters = routeModel.totalDistanceMeters,
            lastStepIndex = routeModel.terminalStepIndex
        )
    }

    private fun showArrivalDialogAndReturnHome(destinationName: String) {
        if (hasShownArrivalDialog || isFinishing || isDestroyed) return
        hasShownArrivalDialog = true

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.arrival_title))
            .setMessage(getString(R.string.arrival_message, destinationName))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.arrival_ok_button)) { _, _ ->
                navigateToHomeScreen()
            }
            .show()
    }

    private fun navigateToHomeScreen() {
        val homeIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val oldSeed = sessionSeed
        repository.resetSession(oldSeed) { result ->
            if (result.isFailure) {
                Log.w(TAG, "Session reset request failed; continuing with local segment rotation", result.exceptionOrNull())
            }

            val nextSegment = NavigationSessionState.advanceToNextSegment()
            val newSeed = NavigationSessionState.startNewSession()
            Log.i(TAG, "Rotated sessionSeed from $oldSeed to $newSeed for ${nextSegment.apiValue}")

            clearLocalNavigationState()
            startActivity(homeIntent)
            finish()
        }
    }

    private fun clearLocalNavigationState() {
        // Reset flags and state used during a navigation run so next run starts fresh.
        isNavigationCompleted = false
        hasShownArrivalDialog = false
        simulatedProgressMeters = 0.0
        elapsedTicks = 0
        closureCheckDelayTicks = 0
        rerouteMuted = false
        pendingClosureDialogShown = false
        lastKnownSnappedLat = null
        lastKnownSnappedLon = null
        closureSnapshotLat = null
        closureSnapshotLon = null
        progressRunnable?.let { progressHandler.removeCallbacks(it) }
        progressRunnable = null
        liveStatusRequestInFlight = false
        replacementRequestInFlight = false
        rerouteDialogVisible = false
        isOffRouteRerouteInProgress = false
        hasRerouted = false
        infraStatusRequestInFlight = false
        infraRerouteMuted = false
        infraSnapshotLat = null
        infraSnapshotLon = null
    }

    private fun formatRemainingDistanceText(remainingDistanceMeters: Double?): String? {
        val meters = remainingDistanceMeters ?: return null
        val normalized = meters.coerceAtLeast(0.0)
        return "${normalized.roundToInt()} m"
    }

    private fun resolveStartPoint(routeModel: RouteNavigationModel, routePoints: List<IndoorGeoPoint>): IndoorGeoPoint {
        routeModel.startGeoPoint?.let { backendStart ->
            return IndoorGeoPoint(longitude = backendStart.longitude, latitude = backendStart.latitude)
        }

        routePoints.firstOrNull()?.let {
            Log.w(TAG, "Backend start coordinates missing; using first backend route point for start marker")
            return it
        }

        Log.w(
            TAG,
            "Backend start coordinates missing; falling back to frontend default start location"
        )
        return IndoorGeoPoint(
            longitude = NavigationExtras.DEFAULT_START_LONGITUDE,
            latitude = NavigationExtras.DEFAULT_START_LATITUDE
        )
    }

    private fun resolveDestinationPoint(
        routeModel: RouteNavigationModel,
        routePoints: List<IndoorGeoPoint>
    ): IndoorGeoPoint? {
        routeModel.destinationGeoPoint?.let { backendDestination ->
            return IndoorGeoPoint(longitude = backendDestination.longitude, latitude = backendDestination.latitude)
        }

        routePoints.lastOrNull()?.let {
            Log.w(TAG, "Backend destination coordinates missing; using last backend route point for destination marker")
            return it
        }

        val fallbackLatitude = selectedAmenityLatitude
        val fallbackLongitude = selectedAmenityLongitude
        if (fallbackLatitude == null || fallbackLongitude == null) return null
        Log.w(TAG, "Backend destination coordinates missing; using selected amenity coordinates as fallback")
        return IndoorGeoPoint(longitude = fallbackLongitude, latitude = fallbackLatitude)
    }

    private fun resolveDestinationMarkerType(amenityType: String): IndoorMapView.MarkerType {
        return when (amenityType.uppercase()) {
            "MEN_RESTROOM" -> IndoorMapView.MarkerType.MEN_RESTROOM
            "WOMEN_RESTROOM" -> IndoorMapView.MarkerType.WOMEN_RESTROOM
            "ACCESSIBLE_RESTROOM" -> IndoorMapView.MarkerType.ACCESSIBLE_RESTROOM
            else -> IndoorMapView.MarkerType.DESTINATION_HIGHLIGHT
        }
    }

    private fun showRouteLoadError(throwable: Throwable?) {
        stopProgressSimulation()
        loadingContainer.visibility = View.GONE
        routeDetailsContainer.visibility = View.VISIBLE
        estimatedTimeView.text = getString(R.string.estimated_time_unavailable)
        val detail = throwable?.message?.takeIf { it.isNotBlank() }
        routeStepsView.text = if (detail == null) {
            getString(R.string.route_load_error_with_hint)
        } else {
            getString(R.string.route_load_error_with_reason, detail)
        }
        retryRouteButton.visibility = View.VISIBLE
        Toast.makeText(this, R.string.route_load_error, Toast.LENGTH_SHORT).show()
    }

    private fun showLoading() {
        loadingContainer.visibility = View.VISIBLE
        routeDetailsContainer.visibility = View.GONE
        retryRouteButton.visibility = View.GONE
    }

    private fun showRouteDetails() {
        loadingContainer.visibility = View.GONE
        routeDetailsContainer.visibility = View.VISIBLE
        retryRouteButton.visibility = View.GONE
    }

    // --- Infrastructure closure ---

    private fun maybeCheckInfrastructureStatus(
        routeModel: RouteNavigationModel,
        progress: RouteProgressResponse
    ) {
        if (NavigationSessionState.currentSegment() != DemoSegment.SEGMENT_3) return
        if (isNavigationCompleted || rerouteDialogVisible || infraRerouteMuted) return
        if (infraStatusRequestInFlight || isOffRouteRerouteInProgress) return
        if (elapsedTicks < infraCheckDelayTicks) return
        // Poll every tick after the delay so the backend's blocked trigger (poll 2-3) fires
        // mid-route on any route length; the in-flight guard prevents overlapping requests.

        val segmentIds = routeModel.routeSegments
            .map { "${it.segmentIndex}" }
            .ifEmpty { listOf("0") }

        val rawLat = progress.snappedLatitude
            ?: lastKnownSnappedLat
            ?: currentRouteResponse?.startLatitude
            ?: NavigationExtras.DEFAULT_START_LATITUDE
        val rawLon = progress.snappedLongitude
            ?: lastKnownSnappedLon
            ?: currentRouteResponse?.startLongitude
            ?: NavigationExtras.DEFAULT_START_LONGITUDE

        // Snap to the nearest actual route waypoint. Interpolated positions can fail
        // routing; waypoints from the Dijkstra path are guaranteed on the walkable graph.
        val nearest = nearestRouteGeoPoint(rawLat, rawLon, routeModel.routeGeoPoints)
        val currentLat = nearest?.latitude ?: rawLat
        val currentLon = nearest?.longitude ?: rawLon

        infraSnapshotLat = currentLat
        infraSnapshotLon = currentLon
        infraStatusRequestInFlight = true

        repository.checkInfrastructureStatus(segmentIds, sessionSeed) { result ->
            infraStatusRequestInFlight = false
            val status = result.getOrNull()
            if (status == null) {
                Log.w(TAG, "Infrastructure status check failed; navigation continues", result.exceptionOrNull())
                return@checkInfrastructureStatus
            }
            // Only mute when blocked — a non-blocked response at poll 1 must not silence poll 2.
            if (!status.corridorBlocked) return@checkInfrastructureStatus
            infraRerouteMuted = true
            fetchReplacementForInfraClosure(status, currentLat, currentLon)
        }
    }

    private fun fetchReplacementForInfraClosure(
        status: InfrastructureStatusResponse,
        currentLat: Double,
        currentLon: Double
    ) {
        val amenityId = selectedAmenityId ?: return
        repository.getNearestOpenReplacement(
            closedAmenityId = amenityId,
            currentLat = currentLat,
            currentLon = currentLon,
            accessibilityOn = isStepFreeRouteEnabled,
            sessionSeed = sessionSeed
        ) { result ->
            val replacement = result.getOrNull()
            if (replacement == null) {
                Log.w(TAG, "Infra closure replacement lookup failed; keeping current route", result.exceptionOrNull())
                return@getNearestOpenReplacement
            }
            showInfrastructureClosureDialog(status, replacement)
        }
    }

    private fun showInfrastructureClosureDialog(
        status: InfrastructureStatusResponse,
        replacement: AmenityReplacementResponse
    ) {
        if (isFinishing || isDestroyed || rerouteDialogVisible) return
        rerouteDialogVisible = true
        stopProgressSimulation()

        val dialogMessage = buildString {
            status.blockedSegmentDescription?.takeIf { it.isNotBlank() }?.let {
                append(getString(R.string.infra_closure_popup_location, it))
                append("\n")
            }
            status.reason?.takeIf { it.isNotBlank() }?.let {
                append(getString(R.string.infra_closure_popup_reason, it))
                append("\n")
            }
            status.estimatedClearanceMinutes?.let {
                append(getString(R.string.infra_closure_popup_clearance, it))
                append("\n")
            }
            append("\n")
            append(getString(R.string.infra_closure_popup_rerouting, replacement.amenityName))
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.infra_closure_popup_title))
            .setMessage(dialogMessage)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.infra_closure_popup_ok)) { _, _ ->
                rerouteDialogVisible = false
                selectedAmenityId = replacement.amenityId
                selectedAmenityRequestName = replacement.amenityName
                selectedAmenityDisplayName = replacement.amenityName
                selectedAmenityType = replacement.amenityType
                showLoading()
                loadRoute(
                    destination = selectedAmenityRequestName,
                    accessibilityOn = isStepFreeRouteEnabled,
                    currentLatitude = infraSnapshotLat ?: lastKnownSnappedLat,
                    currentLongitude = infraSnapshotLon ?: lastKnownSnappedLon,
                    currentLocationLabel = "Terminal D Passenger Current Location",
                    resetClosureState = false,
                    suppressDeviation = true
                )
            }
            .show()
    }

    // --- Off-route reroute ---

    private fun triggerOffRouteReroute(fromLat: Double, fromLon: Double) {
        if (!NavigationSessionState.isDeviationRerouteAllowed()) {
            Log.i(TAG, "Skipping deviation reroute because current segment does not allow it")
            isOffRouteRerouteInProgress = false
            startProgressSimulation(resetProgress = false)
            return
        }
        val request = RouteRequest(
            destination = selectedAmenityRequestName,
            currentLocation = "Terminal D Passenger Current Location",
            accessibilityOn = isStepFreeRouteEnabled,
            destinationAmenityId = selectedAmenityId,
            sessionSeed = sessionSeed,
            currentLatitude = fromLat,
            currentLongitude = fromLon
        )
        repository.createRoute(request, sessionSeed) { result ->
            val route = result.getOrNull()
            if (route == null) {
                val error = result.exceptionOrNull()
                if (isBlockedRerouteFailure(error)) {
                    Log.i(TAG, "Deviation reroute blocked by backend; continuing current navigation flow")
                    isOffRouteRerouteInProgress = false
                    startProgressSimulation(resetProgress = false)
                    return@createRoute
                }
                // Deviated coords couldn't be routed; retry from last backend-snapped position.
                Log.w(TAG, "off-route reroute failed from deviated coords; falling back to last snapped position", result.exceptionOrNull())
                val fallbackLat = lastKnownSnappedLat
                val fallbackLon = lastKnownSnappedLon
                if (fallbackLat == null || fallbackLon == null || (fallbackLat == fromLat && fallbackLon == fromLon)) {
                    Log.e(TAG, "off-route reroute: no valid fallback position; resuming original route")
                    isOffRouteRerouteInProgress = false
                    startProgressSimulation(resetProgress = false)
                    return@createRoute
                }
                triggerOffRouteReroute(fallbackLat, fallbackLon)
                return@createRoute
            }
            applyReroutedRoute(route)
        }
    }

    private fun isBlockedRerouteFailure(throwable: Throwable?): Boolean {
        val message = throwable?.message.orEmpty()
        return message.contains("(400)") || message.contains("blocked", ignoreCase = true)
    }

    private fun applyReroutedRoute(route: RouteResponse) {
        hasRerouted = true
        isOffRouteRerouteInProgress = false
        currentRouteResponse = route
        val newModel = route.toNavigationModel()
        currentRouteModel = newModel
        lastKnownSnappedLat = route.startLatitude
        lastKnownSnappedLon = route.startLongitude
        simulatedActualLat = null
        simulatedActualLon = null
        deviationStep = 0
        deviationPath = emptyList()
        // Deviation is permanently muted after a reroute — green dot stays on the new blue line.
        offRouteSimTriggerMeters = Double.MAX_VALUE
        elapsedTicks = 0
        renderRouteDetails(route, newModel)
        showRouteDetails()
        startProgressSimulation(resetProgress = true)
    }

    // --- Deviation simulation ---

    // Returns the current deviated position when the wrong-turn simulation is active,
    // or null when the passenger is still on the intended route.
    private fun advanceDeviationPosition(): IndoorGeoPoint? {
        if (!NavigationSessionState.isDeviationRerouteAllowed()) return null
        if (simulatedProgressMeters < offRouteSimTriggerMeters || deviationPath.isEmpty()) return null
        if (deviationStep < deviationPath.size) {
            val point = deviationPath[deviationStep]
            simulatedActualLat = point.latitude
            simulatedActualLon = point.longitude
            deviationStep++
        }
        // Freeze at the last deviation point once all steps are consumed.
        return simulatedActualLat?.let { lat ->
            simulatedActualLon?.let { lon -> IndoorGeoPoint(longitude = lon, latitude = lat) }
        }
    }

    // Finds a GeoJSON corridor that branches off the intended route near the trigger point,
    // then walks DEVIATION_STEPS steps along it. All returned points are on the grid.
    private fun computeDeviationPath(
        routePoints: List<RouteGeoPoint>,
        levelMap: IndoorLevelMap,
        triggerMeters: Double,
        totalDist: Double
    ): List<IndoorGeoPoint> {
        if (routePoints.size < 2 || levelMap.routeSegments.isEmpty()) return emptyList()

        val triggerPoint = interpolateRoute(routePoints, triggerMeters, totalDist) ?: return emptyList()
        val intendedHeading = routeHeadingAt(routePoints, triggerMeters)

        var bestEntryLat = 0.0
        var bestEntryLon = 0.0
        var bestExitLat = 0.0
        var bestExitLon = 0.0
        var bestDist = Double.MAX_VALUE

        for (corridor in levelMap.routeSegments) {
            for (i in 0 until corridor.size - 1) {
                val a = corridor[i]
                val b = corridor[i + 1]
                val (nearLat, nearLon) = nearestPointOnSegment(
                    triggerPoint.latitude, triggerPoint.longitude, a, b
                )
                val dist = haversineMeters(triggerPoint.latitude, triggerPoint.longitude, nearLat, nearLon)
                if (dist < DEVIATION_MIN_DIST_FROM_ROUTE_METERS || dist > DEVIATION_MAX_SEARCH_RADIUS_METERS) continue

                val segBearing = bearingDeg(nearLat, nearLon, b.latitude, b.longitude)
                val diff = angleBetween(intendedHeading, segBearing)
                if (diff < DEVIATION_MIN_BEARING_DIFF_DEG || diff > DEVIATION_MAX_BEARING_DIFF_DEG) continue

                if (dist < bestDist) {
                    bestDist = dist
                    bestEntryLat = nearLat; bestEntryLon = nearLon
                    bestExitLat = b.latitude; bestExitLon = b.longitude
                }
            }
        }

        if (bestDist == Double.MAX_VALUE) {
            Log.w(TAG, "deviation path: no suitable branching corridor found near trigger point")
            return emptyList()
        }

        val walkBearing = bearingDeg(bestEntryLat, bestEntryLon, bestExitLat, bestExitLon)
        val segLen = haversineMeters(bestEntryLat, bestEntryLon, bestExitLat, bestExitLon)
        val stepSize = if (segLen > 0.0) minOf(PROGRESS_METERS_PER_TICK, segLen / DEVIATION_STEPS) else PROGRESS_METERS_PER_TICK

        val path = mutableListOf<IndoorGeoPoint>()
        var lat = bestEntryLat
        var lon = bestEntryLon
        repeat(DEVIATION_STEPS) {
            val (newLat, newLon) = advanceLatLon(lat, lon, walkBearing, stepSize)
            lat = newLat; lon = newLon
            path.add(IndoorGeoPoint(longitude = lon, latitude = lat))
        }
        return path
    }

    private fun interpolateRoute(points: List<RouteGeoPoint>, targetMeters: Double, totalDist: Double): IndoorGeoPoint? {
        if (points.isEmpty()) return null
        if (targetMeters <= 0.0) return IndoorGeoPoint(longitude = points.first().longitude, latitude = points.first().latitude)
        if (targetMeters >= totalDist) return IndoorGeoPoint(longitude = points.last().longitude, latitude = points.last().latitude)
        var accumulated = 0.0
        for (i in 1 until points.size) {
            val prev = points[i - 1]; val curr = points[i]
            val seg = haversineMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
            if (accumulated + seg >= targetMeters) {
                val ratio = (targetMeters - accumulated) / seg.coerceAtLeast(1e-9)
                return IndoorGeoPoint(
                    longitude = prev.longitude + (curr.longitude - prev.longitude) * ratio,
                    latitude = prev.latitude + (curr.latitude - prev.latitude) * ratio
                )
            }
            accumulated += seg
        }
        return IndoorGeoPoint(longitude = points.last().longitude, latitude = points.last().latitude)
    }

    private fun routeHeadingAt(points: List<RouteGeoPoint>, targetMeters: Double): Double {
        var accumulated = 0.0
        for (i in 1 until points.size) {
            val prev = points[i - 1]; val curr = points[i]
            val seg = haversineMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
            if (accumulated + seg >= targetMeters) return bearingDeg(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
            accumulated += seg
        }
        val last = points.last(); val secondLast = points[points.size - 2]
        return bearingDeg(secondLast.latitude, secondLast.longitude, last.latitude, last.longitude)
    }

    private fun nearestPointOnSegment(lat: Double, lon: Double, a: IndoorGeoPoint, b: IndoorGeoPoint): Pair<Double, Double> {
        val abLat = b.latitude - a.latitude; val abLon = b.longitude - a.longitude
        val lenSq = abLat * abLat + abLon * abLon
        if (lenSq <= 0.0) return Pair(a.latitude, a.longitude)
        val t = ((lat - a.latitude) * abLat + (lon - a.longitude) * abLon) / lenSq
        val tc = t.coerceIn(0.0, 1.0)
        return Pair(a.latitude + tc * abLat, a.longitude + tc * abLon)
    }

    private fun nearestRouteGeoPoint(lat: Double, lon: Double, points: List<RouteGeoPoint>): RouteGeoPoint? {
        var bestDist = Double.MAX_VALUE
        var best: RouteGeoPoint? = null
        for (point in points) {
            val dist = haversineMeters(lat, lon, point.latitude, point.longitude)
            if (dist < bestDist) {
                bestDist = dist
                best = point
            }
        }
        return best
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * R * asin(sqrt(a).coerceAtMost(1.0))
    }

    private fun bearingDeg(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val dLon = Math.toRadians(toLon - fromLon)
        val y = sin(dLon) * cos(Math.toRadians(toLat))
        val x = cos(Math.toRadians(fromLat)) * sin(Math.toRadians(toLat)) -
            sin(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) * cos(dLon)
        return Math.toDegrees(atan2(y, x))
    }

    private fun angleBetween(a: Double, b: Double): Double {
        var diff = abs(a - b) % 360.0
        if (diff > 180.0) diff = 360.0 - diff
        return diff
    }

    private fun advanceLatLon(lat: Double, lon: Double, bearingDeg: Double, distMeters: Double): Pair<Double, Double> {
        val R = 6_371_000.0; val d = distMeters / R; val b = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(lat); val lon1 = Math.toRadians(lon)
        val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(b))
        val lon2 = lon1 + atan2(sin(b) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        return Pair(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    // --- Live status display ---

    private fun updateLiveStatusDisplay(liveStatus: AmenityLiveStatusResponse) {
        tvWaitTime.text = liveStatus.waitTimeMinutes
            ?.let { if (it == 0) "< 1 min" else "$it min" } ?: "—"
        tvStallsAvailable.text = liveStatus.stallsAvailable?.toString() ?: "—"
        tvOccupancyStatus.text = when (liveStatus.occupancyStatus?.uppercase()) {
            "LOW" -> "Low"
            "MEDIUM" -> "Medium"
            "HIGH" -> "High"
            "FULL" -> "Full / No stalls"
            else -> liveStatus.occupancyStatus ?: "—"
        }
    }
}
