package com.frontegg.android.utils

import android.net.Uri
import android.util.Log
import com.frontegg.android.FronteggApp
import java.security.MessageDigest
import java.util.*

class AuthorizeUrlGenerator {
    companion object {
        private val TAG  = AuthorizeUrlGenerator::class.java.simpleName
    }

    private var clientId: String
    private var baseUrl: String

    init {
        this.baseUrl = FronteggApp.getInstance().baseUrl
        this.clientId = FronteggApp.getInstance().clientId
    }

    private fun createRandomString(length: Int = 16): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    private fun generateCodeChallenge(codeVerifier: String):String {
        val md = MessageDigest.getInstance("SHA-256")
        val input = codeVerifier.toByteArray()
        val bytes = md.digest(input)
        val digest =  Base64.getEncoder().encodeToString(bytes)

        return digest
            .replace("=", "")
            .replace("+", "-")
            .replace("/", "_")

    }


    fun generate(): Pair<String, String> {
        val nonce = createRandomString()
        val codeVerifier = createRandomString()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        val credentialManager = FronteggApp.getInstance().credentialManager
        credentialManager.save(CredentialKeys.CODE_VERIFIER, codeVerifier);
        val redirectUrl = Constants.oauthCallbackUrl(baseUrl)

        val authorizeUrlBuilder = Uri.Builder()
            .encodedPath(baseUrl)
            .appendEncodedPath("oauth/authorize")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("scope", "openid email profile")
            .appendQueryParameter("redirect_uri", redirectUrl)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("nonce", nonce)


        val url = authorizeUrlBuilder.build().toString()
        Log.d(TAG, "Generated url: $url")

        val authorizeUrl = Uri.Builder()
            .encodedPath(baseUrl)
            .appendEncodedPath("frontegg/oauth/logout")
            .appendQueryParameter("post_logout_redirect_uri", url)
            .build().toString()

        return Pair(authorizeUrl, codeVerifier)


    }
}


