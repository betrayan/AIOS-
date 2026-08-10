package com.buddy.aios.core.security

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-GCM encryption service using Android Keystore keys.
 * Used at the Mapper layer before persisting sensitive data to Room.
 */
@Singleton
class EncryptionService @Inject constructor(
    private val keystoreManager: KeystoreManager,
) {

    private val transformation = "AES/GCM/NoPadding"
    private val gcmTagLength = 128
    private val ivSize = 12

    /**
     * Encrypts a plaintext string.
     * Returns Base64 encoded string containing [IV (12 bytes) + Ciphertext].
     */
    fun encrypt(plainText: String, keyAlias: String = KeystoreManager.DEFAULT_KEY_ALIAS): String {
        if (plainText.isEmpty()) return ""

        val secretKey = keystoreManager.getOrCreateSecretKey(keyAlias)
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts a Base64 encoded string containing [IV + Ciphertext].
     */
    fun decrypt(encryptedBase64: String, keyAlias: String = KeystoreManager.DEFAULT_KEY_ALIAS): String {
        if (encryptedBase64.isEmpty()) return ""

        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        if (combined.size <= ivSize) return encryptedBase64

        val iv = ByteArray(ivSize)
        val ciphertext = ByteArray(combined.size - ivSize)

        System.arraycopy(combined, 0, iv, 0, ivSize)
        System.arraycopy(combined, ivSize, ciphertext, 0, ciphertext.size)

        val secretKey = keystoreManager.getOrCreateSecretKey(keyAlias)
        val cipher = Cipher.getInstance(transformation)
        val spec = GCMParameterSpec(gcmTagLength, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
