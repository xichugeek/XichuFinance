package com.xichugeek.finance.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.xichugeek.finance.BuildConfig
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.sessionDataStore by preferencesDataStore(name = "secure_session")

@Serializable
data class UserSession(val id: Long, val email: String, val token: String, val server: String = BuildConfig.API_BASE_URL) {
    val authorization: String get() = "Bearer $token"
    override fun toString() = "UserSession(id=$id, token=[redacted])"
}

/** Only ciphertext is persisted. Passwords are never stored. Backup is disabled in the manifest. */
class SessionStore(context: Context) {
    private val store = context.applicationContext.sessionDataStore
    private val encryptedSession = stringPreferencesKey("session_ciphertext_v1")
    private val localMode = booleanPreferencesKey("local_mode")
    private val alias = "xichu_finance_session_v1"
    private val associatedData = "Xichu Finance session v1".toByteArray(Charsets.UTF_8)

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    suspend fun load(): UserSession? = withContext(Dispatchers.IO) {
        try {
            val encoded = store.data.first()[encryptedSession] ?: return@withContext null
            val parts = encoded.split(":", limit = 2)
            if (parts.size != 2) throw GeneralSecurityException("Invalid ciphertext")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            cipher.updateAAD(associatedData)
            val decoded = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
            ApiClient.json.decodeFromString<UserSession>(decoded).also {
                require(it.id > 0 && it.token.isNotBlank() && it.server == BuildConfig.API_BASE_URL)
            }
        } catch (_: GeneralSecurityException) {
            clear(); null
        } catch (_: SerializationException) {
            clear(); null
        } catch (_: IllegalArgumentException) {
            clear(); null
        } catch (_: IOException) {
            null
        }
    }

    suspend fun isLocalMode(): Boolean = store.data.first()[localMode] ?: false

    suspend fun save(session: UserSession): Unit = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(associatedData)
        val bytes = cipher.doFinal(ApiClient.json.encodeToString(session).toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(bytes, Base64.NO_WRAP)
        store.edit { it[encryptedSession] = encoded; it[localMode] = false }
    }

    suspend fun useLocalMode() { store.edit { it.remove(encryptedSession); it[localMode] = true } }
    suspend fun clear() { store.edit { it.remove(encryptedSession); it[localMode] = false } }
}
