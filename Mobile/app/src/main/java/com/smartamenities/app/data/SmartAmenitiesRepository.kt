package com.smartamenities.app.data

import android.util.Log
import com.smartamenities.app.api.RetrofitClient
import com.smartamenities.app.model.AmenityLiveStatusResponse
import com.smartamenities.app.model.AmenityReplacementResponse
import com.smartamenities.app.model.AmenityResponse
import com.smartamenities.app.model.InfrastructureRouteStatusRequest
import com.smartamenities.app.model.InfrastructureStatusResponse
import com.smartamenities.app.model.RouteRequest
import com.smartamenities.app.model.RouteProgressRequest
import com.smartamenities.app.model.RouteProgressResponse
import com.smartamenities.app.model.RouteResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SmartAmenitiesRepository {
    companion object {
        private const val TAG = "SmartAmenitiesRepo"
    }

    fun getAmenities(
        type: String?,
        currentLat: Double?,
        currentLon: Double?,
        sessionSeed: String?,
        onResult: (Result<List<AmenityResponse>>) -> Unit
    ) {
        RetrofitClient.apiService.getAmenities(type, currentLat, currentLon, sessionSeed)
            .enqueue(object : Callback<List<AmenityResponse>> {
                override fun onResponse(
                    call: Call<List<AmenityResponse>>,
                    response: Response<List<AmenityResponse>>
                ) {
                    if (response.isSuccessful) {
                        onResult(Result.success(response.body().orEmpty()))
                    } else {
                        val errorBody = response.errorBody()?.string().orEmpty()
                        Log.e(TAG, "GET /amenities failed code=${response.code()} body=$errorBody")
                        onResult(Result.failure(IllegalStateException("Amenity request failed (${response.code()})")))
                    }
                }

                override fun onFailure(call: Call<List<AmenityResponse>>, throwable: Throwable) {
                    Log.e(TAG, "GET /amenities failed", throwable)
                    onResult(Result.failure(throwable))
                }
            })
    }

    fun configureSession(
        sessionSeed: String,
        segment: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        RetrofitClient.apiService.configureSession(sessionSeed, segment)
            .enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (response.isSuccessful) {
                        onResult(Result.success(Unit))
                    } else {
                        val errorBody = response.errorBody()?.string().orEmpty()
                        Log.w(TAG, "POST /session/configure failed code=${response.code()} body=$errorBody")
                        onResult(Result.failure(IllegalStateException("Session configure failed (${response.code()})")))
                    }
                }

                override fun onFailure(call: Call<Void>, throwable: Throwable) {
                    Log.w(TAG, "POST /session/configure failed", throwable)
                    onResult(Result.failure(throwable))
                }
            })
    }

    fun getAmenityLiveStatus(
        amenityId: String,
        currentLat: Double,
        currentLon: Double,
        sessionSeed: String?,
        onResult: (Result<AmenityLiveStatusResponse>) -> Unit
    ) {
        RetrofitClient.apiService.getAmenityLiveStatus(amenityId, currentLat, currentLon, sessionSeed)
            .enqueue(object : Callback<AmenityLiveStatusResponse> {
                override fun onResponse(
                    call: Call<AmenityLiveStatusResponse>,
                    response: Response<AmenityLiveStatusResponse>
                ) {
                    val body = response.body()
                    if (response.isSuccessful && body != null) {
                        onResult(Result.success(body))
                    } else {
                        val errorBody = response.errorBody()?.string().orEmpty()
                        Log.e(TAG, "GET /amenities/live-status failed code=${response.code()} body=$errorBody")
                        onResult(Result.failure(IllegalStateException("Live status request failed (${response.code()})")))
                    }
                }

                override fun onFailure(call: Call<AmenityLiveStatusResponse>, throwable: Throwable) {
                    Log.e(TAG, "GET /amenities/live-status failed", throwable)
                    onResult(Result.failure(throwable))
                }
            })
    }

    fun getNearestOpenReplacement(
        closedAmenityId: String,
        currentLat: Double,
        currentLon: Double,
        accessibilityOn: Boolean,
        sessionSeed: String?,
        onResult: (Result<AmenityReplacementResponse>) -> Unit
    ) {
        RetrofitClient.apiService.getNearestOpenReplacement(
            closedAmenityId = closedAmenityId,
            currentLat = currentLat,
            currentLon = currentLon,
            accessibilityOn = accessibilityOn,
            sessionSeed = sessionSeed
        ).enqueue(object : Callback<AmenityReplacementResponse> {
            override fun onResponse(
                call: Call<AmenityReplacementResponse>,
                response: Response<AmenityReplacementResponse>
            ) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    onResult(Result.success(body))
                } else {
                    val errorBody = response.errorBody()?.string().orEmpty()
                    Log.e(TAG, "GET /amenities/replacement failed code=${response.code()} body=$errorBody")
                    onResult(Result.failure(IllegalStateException("Replacement request failed (${response.code()})")))
                }
            }

            override fun onFailure(call: Call<AmenityReplacementResponse>, throwable: Throwable) {
                Log.e(TAG, "GET /amenities/replacement failed", throwable)
                onResult(Result.failure(throwable))
            }
        })
    }

    fun createRoute(
        request: RouteRequest,
        sessionSeed: String?,
        onResult: (Result<RouteResponse>) -> Unit
    ) {
        RetrofitClient.apiService.createRoute(sessionSeed, request).enqueue(object : Callback<RouteResponse> {
            override fun onResponse(call: Call<RouteResponse>, response: Response<RouteResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    onResult(Result.success(body))
                } else {
                    val errorBody = response.errorBody()?.string().orEmpty()
                    Log.e(TAG, "POST /route failed code=${response.code()} body=$errorBody")
                    onResult(Result.failure(IllegalStateException("Route request failed (${response.code()})")))
                }
            }

            override fun onFailure(call: Call<RouteResponse>, throwable: Throwable) {
                Log.e(TAG, "POST /route failed", throwable)
                onResult(Result.failure(throwable))
            }
        })
    }

    fun checkInfrastructureStatus(
        segmentIds: List<String>,
        sessionSeed: String?,
        onResult: (Result<InfrastructureStatusResponse>) -> Unit
    ) {
        RetrofitClient.apiService.checkInfrastructureRouteStatus(
            sessionSeed = sessionSeed,
            request = InfrastructureRouteStatusRequest(segmentIds, sessionSeed)
        ).enqueue(object : Callback<InfrastructureStatusResponse> {
            override fun onResponse(
                call: Call<InfrastructureStatusResponse>,
                response: Response<InfrastructureStatusResponse>
            ) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    onResult(Result.success(body))
                } else {
                    val errorBody = response.errorBody()?.string().orEmpty()
                    Log.e(TAG, "POST /infrastructure/route-status failed code=${response.code()} body=$errorBody")
                    onResult(Result.failure(IllegalStateException("Infrastructure status request failed (${response.code()})")))
                }
            }

            override fun onFailure(call: Call<InfrastructureStatusResponse>, throwable: Throwable) {
                Log.e(TAG, "POST /infrastructure/route-status failed", throwable)
                onResult(Result.failure(throwable))
            }
        })
    }

    fun simulateRouteProgress(
        request: RouteProgressRequest,
        onResult: (Result<RouteProgressResponse>) -> Unit
    ) {
        RetrofitClient.apiService.progressFromGeometry(request).enqueue(object : Callback<RouteProgressResponse> {
            override fun onResponse(
                call: Call<RouteProgressResponse>,
                response: Response<RouteProgressResponse>
            ) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    onResult(Result.success(body))
                } else {
                    val errorBody = response.errorBody()?.string().orEmpty()
                    Log.e(TAG, "POST /route/progress-from-geometry failed code=${response.code()} body=$errorBody")
                    onResult(Result.failure(IllegalStateException("Route progress request failed (${response.code()})")))
                }
            }

            override fun onFailure(call: Call<RouteProgressResponse>, throwable: Throwable) {
                Log.e(TAG, "POST /route/progress-from-geometry failed", throwable)
                onResult(Result.failure(throwable))
            }
        })
    }

    fun resetSession(oldSeed: String, onResult: (Result<com.smartamenities.app.model.SessionResetResponse>) -> Unit) {
        try {
            RetrofitClient.apiService.resetSession(oldSeed).enqueue(object : Callback<com.smartamenities.app.model.SessionResetResponse> {
                override fun onResponse(
                    call: Call<com.smartamenities.app.model.SessionResetResponse>,
                    response: Response<com.smartamenities.app.model.SessionResetResponse>
                ) {
                    val body = response.body()
                    if (response.isSuccessful && body != null) {
                        onResult(Result.success(body))
                    } else {
                        val errorBody = response.errorBody()?.string().orEmpty()
                        Log.w(TAG, "POST /session/reset failed code=${response.code()} body=$errorBody")
                        onResult(Result.failure(IllegalStateException("Session reset failed (${response.code()})")))
                    }
                }

                override fun onFailure(call: Call<com.smartamenities.app.model.SessionResetResponse>, throwable: Throwable) {
                    Log.w(TAG, "POST /session/reset failed", throwable)
                    onResult(Result.failure(throwable))
                }
            })
        } catch (ex: Exception) {
            Log.w(TAG, "Exception while requesting session reset", ex)
            onResult(Result.failure(ex))
        }
    }
}
