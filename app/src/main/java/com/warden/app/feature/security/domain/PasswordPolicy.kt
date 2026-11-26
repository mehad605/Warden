package com.warden.app.feature.security.domain

import java.security.MessageDigest

/**
 * Pure domain rules for password strength and SHA-256 hex hashing.
 *
 * Preserves the exact SHA-256 lowercase hex format used by `baseline-v1` so existing
 * stored password hashes remain valid across upgrades.
 */
object PasswordPolicy {
    private const val MIN_PASSWORD_LENGTH = 6

    /** Returns `true` when [password] is at least 6 chars with at least one letter and one digit. */
    fun isStrong(password: String): Boolean =
        password.length >= MIN_PASSWORD_LENGTH &&
            password.any { it.isLetter() } &&
            password.any { it.isDigit() }

    /** Computes the lowercase SHA-256 hex digest of [password] in UTF-8. */
    fun hashSha256(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies [candidate] against [expectedHash].
     * When [expectedHash] is `null` (no password configured), verification succeeds.
     */
    fun verify(
        candidate: String,
        expectedHash: String?,
    ): Boolean {
        if (expectedHash == null) return true
        return MessageDigest.isEqual(
            hashSha256(candidate).toByteArray(Charsets.US_ASCII),
            expectedHash.toByteArray(Charsets.US_ASCII),
        )
    }
}
