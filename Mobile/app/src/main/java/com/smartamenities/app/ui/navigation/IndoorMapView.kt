package com.smartamenities.app.ui.navigation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Iteration 1 indoor map renderer backed by Level 3 GeoJSON data.
 *
 * The base map and overlays use one shared transform from GeoJSON coordinates to view coordinates.
 */
class IndoorMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    init {
        // Ensure this view keeps touch events for drag/pinch interactions.
        isClickable = true
        isFocusable = true
    }

    enum class MarkerType {
        START,
        MEN_RESTROOM,
        WOMEN_RESTROOM,
        ACCESSIBLE_RESTROOM,
        DESTINATION_HIGHLIGHT
    }

    private var indoorLevelMap: IndoorLevelMap? = null
    private var startPoint: IndoorGeoPoint? = null
    private var destinationPoint: IndoorGeoPoint? = null
    private var destinationType: MarkerType = MarkerType.DESTINATION_HIGHLIGHT
    private var destinationLabel: String? = null
    private var routeOverlayPoints: List<IndoorGeoPoint> = emptyList()
    private var routePolylineViewPoints: List<PointF> = emptyList()
    private var gestureScale = 1f
    private var gestureTranslateX = 0f
    private var gestureTranslateY = 0f
    private var baseMapRect: RectF? = null

    private val minGestureScale = 1f
    private val maxGestureScale = 4f
    private val gestureMatrix = Matrix()

    private val density = resources.displayMetrics.density

    private val roomFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F4F6F8")
    }

    private val roomStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#CFD8DC")
        strokeWidth = 1.2f * density
    }

    private val pathNetworkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#B0BEC5")
        strokeWidth = 2.2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val routeOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 8f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        alpha = 220
    }

    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#1E88E5")
        strokeWidth = 5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#2E7D32")
    }

    private val destinationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#C62828")
    }

    private val menRestroomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#2563EB")
    }

    private val womenRestroomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#EC4899")
    }

    private val accessibleRestroomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#212121")
    }

    private val amenityLabelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(235, 255, 255, 255)
    }

    private val amenityLabelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#B0BEC5")
        strokeWidth = 1f * density
    }

    private val amenityLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#263238")
        textSize = 10f * density
        style = Paint.Style.FILL
    }

    private val markerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2f * density
    }

    private val destinationRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFC107")
        strokeWidth = 2.4f * density
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = gestureScale
                val newScale = (gestureScale * detector.scaleFactor).coerceIn(minGestureScale, maxGestureScale)
                if (newScale == oldScale) return false

                val focusX = detector.focusX
                val focusY = detector.focusY
                val scaleRatio = newScale / oldScale

                // Preserve the pinch focal point while zooming.
                gestureTranslateX = focusX - ((focusX - gestureTranslateX) * scaleRatio)
                gestureTranslateY = focusY - ((focusY - gestureTranslateY) * scaleRatio)
                gestureScale = newScale
                clampGestureTranslation()
                invalidate()
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                gestureTranslateX -= distanceX
                gestureTranslateY -= distanceY
                clampGestureTranslation()
                invalidate()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                resetTransform()
                return true
            }
        }
    )

    fun setIndoorMap(levelMap: IndoorLevelMap) {
        indoorLevelMap = levelMap
        invalidate()
    }

    fun resetTransform() {
        gestureScale = 1f
        gestureTranslateX = 0f
        gestureTranslateY = 0f
        invalidate()
    }

    fun setRouteOverlay(
        start: IndoorGeoPoint?,
        destination: IndoorGeoPoint?,
        destinationMarkerType: MarkerType,
        routePoints: List<IndoorGeoPoint>,
        destinationText: String? = null
    ) {
        startPoint = start
        destinationPoint = destination
        destinationType = destinationMarkerType
        routeOverlayPoints = routePoints
        destinationLabel = destinationText
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val levelMap = indoorLevelMap ?: return
        val drawTransform = computeDrawTransform(
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
            bounds = levelMap.bounds
        )
        baseMapRect = RectF(
            drawTransform.left,
            drawTransform.top,
            drawTransform.left + drawTransform.drawWidth,
            drawTransform.top + drawTransform.drawHeight
        )

        gestureMatrix.reset()
        gestureMatrix.setScale(gestureScale, gestureScale)
        gestureMatrix.postTranslate(gestureTranslateX, gestureTranslateY)

        canvas.save()
        canvas.concat(gestureMatrix)

        drawRooms(canvas, levelMap.roomPolygons, drawTransform)
        drawPathNetwork(canvas, levelMap.routeSegments, drawTransform)

        routePolylineViewPoints = routeOverlayPoints.map { toViewPoint(it, drawTransform) }

        if (routeOverlayPoints.size >= 2) {
            val path = Path()
            routePolylineViewPoints.forEachIndexed { index, mapped ->
                if (index == 0) {
                    path.moveTo(mapped.x, mapped.y)
                } else {
                    path.lineTo(mapped.x, mapped.y)
                }
            }
            canvas.drawPath(path, routeOutlinePaint)
            canvas.drawPath(path, routePaint)
        }

        drawAmenityAnchors(canvas, levelMap.amenities, drawTransform)

        destinationPoint?.let { destination ->
            val paint = when (destinationType) {
                MarkerType.MEN_RESTROOM -> menRestroomPaint
                MarkerType.WOMEN_RESTROOM -> womenRestroomPaint
                MarkerType.ACCESSIBLE_RESTROOM -> accessibleRestroomPaint
                else -> destinationPaint
            }
            drawDestinationMarker(canvas, toViewPoint(destination, drawTransform), paint, 5.0f * density)
        }

        startPoint?.let { drawCircleMarker(canvas, toViewPoint(it, drawTransform), startPaint, 5.8f * density) }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_MOVE -> parent?.requestDisallowInterceptTouchEvent(true)

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            performClick()
        }
        // Always consume once interaction starts so multi-touch gestures are stable.
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun drawRooms(
        canvas: Canvas,
        roomPolygons: List<List<IndoorGeoPoint>>,
        transform: DrawTransform
    ) {
        roomPolygons.forEach { polygon ->
            if (polygon.size < 3) return@forEach
            val path = Path()
            polygon.forEachIndexed { index, point ->
                val mapped = toViewPoint(point, transform)
                if (index == 0) {
                    path.moveTo(mapped.x, mapped.y)
                } else {
                    path.lineTo(mapped.x, mapped.y)
                }
            }
            path.close()
            canvas.drawPath(path, roomFillPaint)
            canvas.drawPath(path, roomStrokePaint)
        }
    }

    private fun drawPathNetwork(
        canvas: Canvas,
        routeSegments: List<List<IndoorGeoPoint>>,
        transform: DrawTransform
    ) {
        routeSegments.forEach { segment ->
            if (segment.size < 2) return@forEach
            val path = Path()
            segment.forEachIndexed { index, point ->
                val mapped = toViewPoint(point, transform)
                if (index == 0) {
                    path.moveTo(mapped.x, mapped.y)
                } else {
                    path.lineTo(mapped.x, mapped.y)
                }
            }
            canvas.drawPath(path, pathNetworkPaint)
        }
    }

    private fun drawAmenityAnchors(
        canvas: Canvas,
        anchors: List<IndoorAmenityAnchor>,
        transform: DrawTransform
    ) {
        anchors.forEach { amenity ->
            val amenityCenter = toViewPoint(amenity.point, transform)
            if (destinationPoint != null && pointsNearlyEqual(amenity.point, destinationPoint!!)) {
                return@forEach
            }

            val paint = when (amenity.type) {
                IndoorAmenityType.MEN_RESTROOM -> menRestroomPaint
                IndoorAmenityType.WOMEN_RESTROOM -> womenRestroomPaint
                IndoorAmenityType.ACCESSIBLE_RESTROOM -> accessibleRestroomPaint
                IndoorAmenityType.OTHER -> destinationPaint
            }
            drawAmenityMarker(canvas, amenityCenter, paint, 3.8f * density)
        }
    }

    private fun drawAmenityMarker(canvas: Canvas, center: PointF, fillPaint: Paint, radius: Float) {
        canvas.drawCircle(center.x, center.y, radius, fillPaint)
        canvas.drawCircle(center.x, center.y, radius, markerStrokePaint)
    }

    private fun drawDestinationMarker(canvas: Canvas, center: PointF, fillPaint: Paint, radius: Float) {
        val markerRadius = zoomAdjustedValue(radius, minValue = 3.6f * density)
        val ringGap = zoomAdjustedValue(2.2f * density, minValue = 1.2f * density)

        markerStrokePaint.strokeWidth = zoomAdjustedValue(1.6f * density, minValue = 0.9f * density)
        destinationRingPaint.strokeWidth = zoomAdjustedValue(1.8f * density, minValue = 1.0f * density)

        canvas.drawCircle(center.x, center.y, markerRadius, fillPaint)
        canvas.drawCircle(center.x, center.y, markerRadius, markerStrokePaint)
        canvas.drawCircle(center.x, center.y, markerRadius + ringGap, destinationRingPaint)

        destinationLabel?.takeIf { it.isNotBlank() }?.let {
            drawLabelBadge(canvas, center, it, amenityLabelPaint)
        }
    }

    private fun drawCircleMarker(canvas: Canvas, center: PointF, fillPaint: Paint, radius: Float = 6f * density) {
        val markerRadius = zoomAdjustedValue(radius, minValue = 3.8f * density)
        markerStrokePaint.strokeWidth = zoomAdjustedValue(1.6f * density, minValue = 0.9f * density)
        canvas.drawCircle(center.x, center.y, markerRadius, fillPaint)
        canvas.drawCircle(center.x, center.y, markerRadius, markerStrokePaint)
    }

    private fun drawLabelBadge(
        canvas: Canvas,
        anchor: PointF,
        label: String,
        paint: Paint
    ) {
        val cleanedLabel = label.trim().replace("\n", " ")
        val placement = computeLabelPlacement(anchor, cleanedLabel, paint) ?: return
        val cornerRadius = zoomAdjustedValue(4.8f * density, minValue = 3.2f * density)

        canvas.drawRoundRect(placement.rect, cornerRadius, cornerRadius, amenityLabelBackgroundPaint)
        canvas.drawRoundRect(placement.rect, cornerRadius, cornerRadius, amenityLabelBorderPaint)
        canvas.drawText(cleanedLabel, placement.textX, placement.baselineY, paint)
    }

    private fun computeLabelPlacement(
        center: PointF,
        label: String,
        paint: Paint
    ): LabelPlacement? {
        val mapRect = baseMapRect ?: return LabelPlacement(
            rect = RectF(center.x, center.y, center.x, center.y),
            textX = center.x,
            baselineY = center.y
        )
        val labelWidth = paint.measureText(label)
        val fontMetrics = paint.fontMetrics
        val offsetX = zoomAdjustedValue(7f * density, minValue = 4f * density)
        val offsetY = zoomAdjustedValue(7f * density, minValue = 4f * density)
        val edgePadding = zoomAdjustedValue(4f * density, minValue = 2f * density)
        val paddingX = zoomAdjustedValue(5f * density, minValue = 3.5f * density)
        val paddingY = zoomAdjustedValue(3.5f * density, minValue = 2.5f * density)
        val boxWidth = labelWidth + (paddingX * 2f)
        val boxHeight = (fontMetrics.descent - fontMetrics.ascent) + (paddingY * 2f)

        val candidates = mutableListOf<RectF>()
        val offsetMultipliers = listOf(1f, 1.6f, 2.2f)

        offsetMultipliers.forEach { multiplier ->
            val candidateOffsetX = offsetX * multiplier
            val candidateOffsetY = offsetY * multiplier
            candidates += RectF(
                center.x + candidateOffsetX,
                center.y - candidateOffsetY - boxHeight,
                center.x + candidateOffsetX + boxWidth,
                center.y - candidateOffsetY
            )
            candidates += RectF(
                center.x + candidateOffsetX,
                center.y + candidateOffsetY,
                center.x + candidateOffsetX + boxWidth,
                center.y + candidateOffsetY + boxHeight
            )
            candidates += RectF(
                center.x - candidateOffsetX - boxWidth,
                center.y - candidateOffsetY - boxHeight,
                center.x - candidateOffsetX,
                center.y - candidateOffsetY
            )
            candidates += RectF(
                center.x - candidateOffsetX - boxWidth,
                center.y + candidateOffsetY,
                center.x - candidateOffsetX,
                center.y + candidateOffsetY + boxHeight
            )
        }

        val safeRect = candidates
            .map { clampRectIntoMapBounds(it, mapRect, edgePadding, boxWidth, boxHeight) }
            .firstOrNull { candidate -> !rectIntersectsRoute(candidate, routePolylineViewPoints) }
            ?: return null

        val baseline = safeRect.top + paddingY - fontMetrics.top
        return LabelPlacement(
            rect = safeRect,
            textX = safeRect.left + paddingX,
            baselineY = baseline
        )
    }

    private fun clampRectIntoMapBounds(
        rect: RectF,
        mapRect: RectF,
        edgePadding: Float,
        boxWidth: Float,
        boxHeight: Float
    ): RectF {
        val left = rect.left.coerceIn(
            mapRect.left + edgePadding,
            (mapRect.right - edgePadding - boxWidth).coerceAtLeast(mapRect.left + edgePadding)
        )
        val top = rect.top.coerceIn(
            mapRect.top + edgePadding,
            (mapRect.bottom - edgePadding - boxHeight).coerceAtLeast(mapRect.top + edgePadding)
        )
        return RectF(left, top, left + boxWidth, top + boxHeight)
    }

    private fun rectIntersectsRoute(rect: RectF, routePoints: List<PointF>): Boolean {
        if (routePoints.size < 2) return false
        val padding = zoomAdjustedValue(4f * density, minValue = 2f * density)
        val paddedRect = RectF(rect)
        paddedRect.inset(-padding, -padding)

        for (index in 0 until (routePoints.size - 1)) {
            val start = routePoints[index]
            val end = routePoints[index + 1]
            if (segmentIntersectsRect(start, end, paddedRect)) {
                return true
            }
        }
        return false
    }

    private fun segmentIntersectsRect(start: PointF, end: PointF, rect: RectF): Boolean {
        if (rect.contains(start.x, start.y) || rect.contains(end.x, end.y)) return true

        val segmentBounds = RectF(
            minOf(start.x, end.x),
            minOf(start.y, end.y),
            maxOf(start.x, end.x),
            maxOf(start.y, end.y)
        )
        if (!RectF.intersects(segmentBounds, rect)) return false

        val topLeft = PointF(rect.left, rect.top)
        val topRight = PointF(rect.right, rect.top)
        val bottomRight = PointF(rect.right, rect.bottom)
        val bottomLeft = PointF(rect.left, rect.bottom)

        return segmentsIntersect(start, end, topLeft, topRight) ||
            segmentsIntersect(start, end, topRight, bottomRight) ||
            segmentsIntersect(start, end, bottomRight, bottomLeft) ||
            segmentsIntersect(start, end, bottomLeft, topLeft)
    }

    private fun segmentsIntersect(p1: PointF, p2: PointF, q1: PointF, q2: PointF): Boolean {
        val o1 = orientation(p1, p2, q1)
        val o2 = orientation(p1, p2, q2)
        val o3 = orientation(q1, q2, p1)
        val o4 = orientation(q1, q2, p2)

        if (o1 != o2 && o3 != o4) return true

        return (o1 == 0 && onSegment(p1, q1, p2)) ||
            (o2 == 0 && onSegment(p1, q2, p2)) ||
            (o3 == 0 && onSegment(q1, p1, q2)) ||
            (o4 == 0 && onSegment(q1, p2, q2))
    }

    private fun orientation(p: PointF, q: PointF, r: PointF): Int {
        val value = ((q.y - p.y) * (r.x - q.x)) - ((q.x - p.x) * (r.y - q.y))
        if (kotlin.math.abs(value) < 0.0001f) return 0
        return if (value > 0f) 1 else 2
    }

    private fun onSegment(p: PointF, q: PointF, r: PointF): Boolean {
        return q.x <= maxOf(p.x, r.x) + 0.0001f &&
            q.x + 0.0001f >= minOf(p.x, r.x) &&
            q.y <= maxOf(p.y, r.y) + 0.0001f &&
            q.y + 0.0001f >= minOf(p.y, r.y)
    }

    private fun pointsNearlyEqual(first: IndoorGeoPoint, second: IndoorGeoPoint): Boolean {
        return kotlin.math.abs(first.longitude - second.longitude) < 1e-6 &&
            kotlin.math.abs(first.latitude - second.latitude) < 1e-6
    }

    private data class LabelPlacement(
        val rect: RectF,
        val textX: Float,
        val baselineY: Float
    )

    private fun zoomAdjustedValue(baseValue: Float, minValue: Float): Float {
        return (baseValue / gestureScale).coerceAtLeast(minValue)
    }

    private fun toViewPoint(point: IndoorGeoPoint, transform: DrawTransform): PointF {
        val xRatio = ((point.longitude - transform.bounds.minLongitude) / transform.bounds.longitudeSpan)
            .coerceIn(0.0, 1.0)
        val yRatio = ((transform.bounds.maxLatitude - point.latitude) / transform.bounds.latitudeSpan)
            .coerceIn(0.0, 1.0)

        return PointF(
            transform.left + (xRatio.toFloat() * transform.drawWidth),
            transform.top + (yRatio.toFloat() * transform.drawHeight)
        )
    }

    private fun computeDrawTransform(
        viewWidth: Float,
        viewHeight: Float,
        bounds: IndoorGeoBounds
    ): DrawTransform {
        val contentWidth = (viewWidth - paddingLeft - paddingRight).coerceAtLeast(1f)
        val contentHeight = (viewHeight - paddingTop - paddingBottom).coerceAtLeast(1f)
        val mapWidth = bounds.longitudeSpan.toFloat()
        val mapHeight = bounds.latitudeSpan.toFloat()

        val scale = min(contentWidth / mapWidth, contentHeight / mapHeight)
        val drawWidth = mapWidth * scale
        val drawHeight = mapHeight * scale
        val left = paddingLeft.toFloat() + ((contentWidth - drawWidth) / 2f)
        val top = paddingTop.toFloat() + ((contentHeight - drawHeight) / 2f)

        return DrawTransform(
            left = left,
            top = top,
            drawWidth = drawWidth,
            drawHeight = drawHeight,
            bounds = bounds
        )
    }

    private fun clampGestureTranslation() {
        val mapRect = baseMapRect ?: return

        val scaledLeft = (mapRect.left * gestureScale) + gestureTranslateX
        val scaledTop = (mapRect.top * gestureScale) + gestureTranslateY
        val scaledRight = (mapRect.right * gestureScale) + gestureTranslateX
        val scaledBottom = (mapRect.bottom * gestureScale) + gestureTranslateY

        val scaledWidth = scaledRight - scaledLeft
        val scaledHeight = scaledBottom - scaledTop
        val viewWidth = width.toFloat().coerceAtLeast(1f)
        val viewHeight = height.toFloat().coerceAtLeast(1f)

        val minTranslateX = if (scaledWidth > viewWidth) {
            viewWidth - (mapRect.right * gestureScale)
        } else {
            ((viewWidth - scaledWidth) / 2f) - (mapRect.left * gestureScale)
        }
        val maxTranslateX = if (scaledWidth > viewWidth) {
            -(mapRect.left * gestureScale)
        } else {
            minTranslateX
        }

        val minTranslateY = if (scaledHeight > viewHeight) {
            viewHeight - (mapRect.bottom * gestureScale)
        } else {
            ((viewHeight - scaledHeight) / 2f) - (mapRect.top * gestureScale)
        }
        val maxTranslateY = if (scaledHeight > viewHeight) {
            -(mapRect.top * gestureScale)
        } else {
            minTranslateY
        }

        gestureTranslateX = gestureTranslateX.coerceIn(minTranslateX, max(maxTranslateX, minTranslateX))
        gestureTranslateY = gestureTranslateY.coerceIn(minTranslateY, max(maxTranslateY, minTranslateY))
    }

    private data class DrawTransform(
        val left: Float,
        val top: Float,
        val drawWidth: Float,
        val drawHeight: Float,
        val bounds: IndoorGeoBounds
    )
}
