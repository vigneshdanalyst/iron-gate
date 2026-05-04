package com.irongate

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.KeyGenerator
import kotlin.random.Random

data class LockState(
    val active: Boolean,
    val endsAtMillis: Long,
    val sessionId: Long,
    val spartanEnabled: Boolean,
    val spartanUnlockCode: String
)

object LockStateStore {
    private const val PREFS = "iron_gate_prefs"
    private const val KEY_ACTIVE = "lock_active"
    private const val KEY_ENDS_AT = "lock_ends_at"
    private const val KEY_SESSION_ID = "lock_session_id"
    private const val KEY_SPARTAN = "spartan_enabled"
    private const val KEY_SPARTAN_CODE = "spartan_code"
    private const val KEY_SEED = "spartan_seed"
    private const val KEYSTORE_ALIAS = "iron_gate_keystore_key"

    private fun encryptedPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun read(context: Context): LockState {
        val prefs = encryptedPrefs(context)
        return LockState(
            active = prefs.getBoolean(KEY_ACTIVE, false),
            endsAtMillis = prefs.getLong(KEY_ENDS_AT, 0L),
            sessionId = prefs.getLong(KEY_SESSION_ID, -1L),
            spartanEnabled = prefs.getBoolean(KEY_SPARTAN, false),
            spartanUnlockCode = prefs.getString(KEY_SPARTAN_CODE, "") ?: ""
        )
    }

    fun start(context: Context, endsAtMillis: Long, sessionId: Long, spartanEnabled: Boolean): String {
        ensureKeyStoreKey()
        val code = randomCode(20)
        encryptedPrefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_ENDS_AT, endsAtMillis)
            .putLong(KEY_SESSION_ID, sessionId)
            .putBoolean(KEY_SPARTAN, spartanEnabled)
            .putString(KEY_SPARTAN_CODE, if (spartanEnabled) code else "")
            .putString(KEY_SEED, randomDigits(16))
            .apply()
        return code
    }

    fun stop(context: Context) {
        encryptedPrefs(context).edit()
            .putBoolean(KEY_ACTIVE, false)
            .putLong(KEY_ENDS_AT, 0L)
            .putLong(KEY_SESSION_ID, -1L)
            .putString(KEY_SPARTAN_CODE, "")
            .apply()
    }

    private fun ensureKeyStoreKey() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) return
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    private fun randomDigits(length: Int): String {
        return buildString(length) {
            repeat(length) { append(Random.nextInt(0, 10)) }
        }
    }

    private fun randomCode(length: Int): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#\$%&*"
        return buildString(length) {
            repeat(length) { append(chars[Random.nextInt(chars.length)]) }
        }
    }
}
