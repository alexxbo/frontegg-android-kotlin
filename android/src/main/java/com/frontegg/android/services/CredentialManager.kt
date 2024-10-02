package com.frontegg.android.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.frontegg.android.utils.CredentialKeys
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey


open class CredentialManager(val context: Context) {
    companion object {
        private const val SHARED_PREFERENCES_NAME: String =
            "com.frontegg.services.CredentialManager"
        private val TAG = CredentialManager::class.java.simpleName
    }

    private var sp: SharedPreferences;

    private fun createKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        return keyStore
    }

    fun clearSharedPreference(context: Context) {
        context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear()
            .apply()
    }

    private fun createSecretKey(alias: String): SecretKey {
        val keyGenerator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")

        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )

        return keyGenerator.generateKey()
    }

    private fun getSecretKey(keyStore: KeyStore, alias: String): SecretKey {
        return if (keyStore.containsAlias(alias)) {
            (keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            this.createSecretKey(alias)
        }
    }

    init {

        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            sp = EncryptedSharedPreferences.create(
                context,
                SHARED_PREFERENCES_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            val cause: Throwable = e.cause!!
            if (cause.message!!.contains("Signature/MAC verification failed")) {
                Log.w(TAG, "Master key is corrupted. Recreating the master key")
                // Recreate the Master Key
                val masterKey = reinitializeMasterKey()

                // Recreate EncryptedSharedPreferences
                sp = EncryptedSharedPreferences.create(
                    context,
                    SHARED_PREFERENCES_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } else {
                // Handle other exceptions
                throw e
            }
        }
    }

    private fun reinitializeMasterKey(): MasterKey {
        val keyStore = this.createKeyStore()
        getSecretKey(keyStore, SHARED_PREFERENCES_NAME)
        clearSharedPreference(this.context)
        return MasterKey.Builder(context)
            .setKeyGenParameterSpec(
                KeyGenParameterSpec.Builder(
                    MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            .build()
    }

    /**
     * Save value for key into the shared preference
     */
    fun save(key: CredentialKeys, value: String): Boolean {
        Log.d(TAG, "Saving Frontegg $key in shared preference")

        with(sp.edit()) {
            putString(key.toString(), value)
            apply()
            return commit()
        }
    }


    /**
     * Get value by key from the shared preference
     */
    fun get(key: CredentialKeys): String? {
        Log.d(TAG, "get Frontegg $key in shared preference ")
        with(sp) {
            return getString(key.toString(), null)
        }
    }


    /**
     * Remove all keys from shared preferences
     */
    @SuppressLint("ApplySharedPref")
    fun clear() {
        Log.d(TAG, "clear Frontegg shared preference ")

        val selectedRegion: String? = getSelectedRegion()

        with(sp.edit()) {
            remove(CredentialKeys.CODE_VERIFIER.toString())
            remove(CredentialKeys.ACCESS_TOKEN.toString())
            remove(CredentialKeys.REFRESH_TOKEN.toString())
            if (selectedRegion != null) {
                putString(CredentialKeys.SELECTED_REGION.toString(), selectedRegion)
            }
            apply()
            commit()
        }
    }

    fun getCodeVerifier(): String? {
        return this.get(CredentialKeys.CODE_VERIFIER)
    }

    fun saveCodeVerifier(codeVerifier: String): Boolean {
        return this.save(CredentialKeys.CODE_VERIFIER, codeVerifier)
    }


    fun getSelectedRegion(): String? {
        return this.get(CredentialKeys.SELECTED_REGION)
    }

    fun saveSelectedRegion(selectedRegion: String): Boolean {
        return this.save(CredentialKeys.SELECTED_REGION, selectedRegion)
    }
}