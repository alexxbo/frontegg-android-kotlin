package com.frontegg.android.services

import android.util.Log
import com.frontegg.android.models.AuthResponse
import com.frontegg.android.models.User
import com.frontegg.android.utils.ApiConstants
import com.frontegg.android.utils.CredentialKeys
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.Call
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

open class Api(
    private var baseUrl: String,
    private var clientId: String,
    private var credentialManager: CredentialManager,
    private var httpClient: OkHttpClient = OkHttpClient()
) {
    companion object {
        private val TAG = Api::class.java.simpleName
    }

    private fun prepareHeaders(additionalHeaders: Map<String, String> = mapOf()): Headers {

        val headers: MutableMap<String, String> = mutableMapOf(
            Pair("Content-Type", "application/json"),
            Pair("Accept", "application/json"),
            Pair("Origin", this.baseUrl)
        )

        additionalHeaders.forEach {
            headers[it.key] = it.value
        }

        val accessToken = this.credentialManager.getOrNull(CredentialKeys.ACCESS_TOKEN)
        if (accessToken != null) {
            headers["Authorization"] = "Bearer $accessToken"
        }
        return headers.toHeaders()
    }

    private fun buildPostRequest(
        path: String,
        body: JsonObject,
        additionalHeaders: Map<String, String> = mapOf()
    ): Call {
        val url = "${this.baseUrl}/$path".toHttpUrl()
        val requestBuilder = Request.Builder()
        val bodyRequest =
            body.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
        val headers = this.prepareHeaders(additionalHeaders);

        requestBuilder.method("POST", bodyRequest)
        requestBuilder.headers(headers);
        requestBuilder.url(url)

        val request = requestBuilder.build()
        return this.httpClient.newCall(request)
    }

    private fun buildGetRequest(path: String): Call {
        val url = "$baseUrl/$path".toHttpUrl()
        val requestBuilder = Request.Builder()
        val headers = prepareHeaders();

        requestBuilder.method("GET", null)
        requestBuilder.headers(headers);
        requestBuilder.url(url)

        val request = requestBuilder.build()
        return this.httpClient.newCall(request)
    }

    @Throws(IllegalArgumentException::class)
    public fun me(): User? {
        try {
            val call = buildGetRequest(ApiConstants.me)
            val response = call.execute()
            if (response.isSuccessful) {
                return Gson().fromJson(response.body!!.string(), User::class.java)
            }

        } catch (e: IOException) {
            Log.e(TAG, "me() ${e.message}", e)
        }
        return null
    }

    @Throws(IllegalArgumentException::class)
    public fun refreshToken(refreshToken: String): AuthResponse? {
        val body = JsonObject()
        body.addProperty("grant_type", "refresh_token")
        body.addProperty("refresh_token", refreshToken)
        try {
            val call = buildPostRequest(ApiConstants.refreshToken, body)
            val response = call.execute()
            if (response.isSuccessful) {
                return Gson().fromJson(response.body!!.string(), AuthResponse::class.java)
            }
        } catch (e: IOException) {
            Log.e(TAG, "refreshToken() ${e.message}", e)
        }
        return null
    }


    @Throws(IllegalArgumentException::class)
    public fun exchangeToken(
        code: String,
        redirectUrl: String,
        codeVerifier: String
    ): AuthResponse? {

        val body = JsonObject()
        body.addProperty("code", code)
        body.addProperty("redirect_uri", redirectUrl)
        body.addProperty("code_verifier", codeVerifier)
        body.addProperty("grant_type", "authorization_code")

        try {
            val call = buildPostRequest(ApiConstants.exchangeToken, body)
            val response = call.execute()
            if (response.isSuccessful) {
                return Gson().fromJson(response.body!!.string(), AuthResponse::class.java)
            }
        } catch (e: IOException) {
            Log.e(TAG, "exchangeToken() ${e.message}", e)
        }
        return null
    }

    fun logout() {
        val refreshToken = this.credentialManager.getOrNull(CredentialKeys.ACCESS_TOKEN)

        if (refreshToken != null) {
            try {
                val call = buildPostRequest(
                    ApiConstants.logout, JsonObject(), mapOf(
                        Pair("fe_refresh_$clientId", refreshToken)
                    )
                )
                call.execute()
            } catch (e: IOException) {
                Log.e(TAG, "logout() ${e.message}", e)
            }
        }
    }
}