package com.smartamenities.app.api

import com.smartamenities.app.model.AmenityResponse
import com.smartamenities.app.model.AmenityLiveStatusResponse
import com.smartamenities.app.model.AmenityReplacementResponse
import com.smartamenities.app.model.InfrastructureRouteStatusRequest
import com.smartamenities.app.model.InfrastructureStatusResponse
import com.smartamenities.app.model.RouteRequest
import com.smartamenities.app.model.RouteProgressRequest
import com.smartamenities.app.model.RouteProgressResponse
import com.smartamenities.app.model.RouteResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SmartAmenitiesApiService {
    @GET("amenities")
    fun getAmenities(
        @Query("type") type: String? = null,
        @Query("currentLat") currentLat: Double? = null,
        @Query("currentLon") currentLon: Double? = null,
        @Query("sessionSeed") sessionSeed: String? = null
    ): Call<List<AmenityResponse>>

    @GET("amenities/live-status")
    fun getAmenityLiveStatus(
        @Query("amenityId") amenityId: String,
        @Query("currentLat") currentLat: Double,
        @Query("currentLon") currentLon: Double,
        @Query("sessionSeed") sessionSeed: String? = null
    ): Call<AmenityLiveStatusResponse>

    @GET("amenities/replacement")
    fun getNearestOpenReplacement(
        @Query("closedAmenityId") closedAmenityId: String,
        @Query("currentLat") currentLat: Double,
        @Query("currentLon") currentLon: Double,
        @Query("accessibilityOn") accessibilityOn: Boolean,
        @Query("sessionSeed") sessionSeed: String? = null
    ): Call<AmenityReplacementResponse>

    @POST("route")
    fun createRoute(
        @Query("sessionSeed") sessionSeed: String? = null,
        @Body routeRequest: RouteRequest
    ): Call<RouteResponse>

    @POST("route/progress-from-geometry")
    fun progressFromGeometry(@Body request: RouteProgressRequest): Call<RouteProgressResponse>

    @POST("session/configure")
    fun configureSession(
        @Query("sessionSeed") sessionSeed: String,
        @Query("segment") segment: String
    ): Call<Void>

    @POST("infrastructure/route-status")
    fun checkInfrastructureRouteStatus(
        @Query("sessionSeed") sessionSeed: String? = null,
        @Body request: InfrastructureRouteStatusRequest
    ): Call<InfrastructureStatusResponse>

    @POST("session/reset")
    fun resetSession(
        @Query("sessionSeed") sessionSeed: String
    ): Call<com.smartamenities.app.model.SessionResetResponse>
}

