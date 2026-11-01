package com.v1i2c.focusharbor.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object StrictModeGate {
    fun canDeactivate(
        requirements: StrictModeRequirements,
        context: StrictReleaseContext,
    ): Boolean {
        val timerReady = requirements.timerEndsAtMillis?.let { context.nowMillis >= it } ?: true
        val scheduleReady = !requirements.requireScheduleEnd || !context.scheduleActive
        val chargerReady = !requirements.requireCharger || context.chargerConnected
        val pinReady = !requirements.requirePin || context.pinAccepted
        val cooldownReady = requirements.cooldownUntilMillis?.let { context.nowMillis >= it } ?: true
        return timerReady && scheduleReady && chargerReady && pinReady && cooldownReady
    }

    fun canEmergencyRelease(
        emergencyStartedAtMillis: Long?,
        cooldownMinutes: Long,
        nowMillis: Long,
        commitmentMatches: Boolean,
    ): Boolean {
        val started = emergencyStartedAtMillis ?: return false
        val cooldownMillis = cooldownMinutes.coerceIn(1L, 1_440L) * 60_000L
        return commitmentMatches && nowMillis - started >= cooldownMillis
    }
}

data class StrictModeRequirements(
    val timerEndsAtMillis: Long? = null,
    val requireScheduleEnd: Boolean = false,
    val requireCharger: Boolean = false,
    val requirePin: Boolean = false,
    val cooldownUntilMillis: Long? = null,
)

data class StrictReleaseContext(
    val nowMillis: Long,
    val scheduleActive: Boolean,
    val chargerConnected: Boolean,
    val pinAccepted: Boolean,
)

object PinHasher {
    const val DEFAULT_ITERATIONS = 120_000
    private const val MAX_ITERATIONS = 2_000_000
    private const val KEY_LENGTH_BITS = 256
    private val random = SecureRandom()

    fun create(pin: CharArray, iterations: Int = DEFAULT_ITERATIONS): PinVerifier {
        require(pin.size in 4..12 && pin.all(Char::isDigit)) { "PIN must contain 4–12 digits" }
        require(iterations in 100_000..MAX_ITERATIONS) { "PIN iteration count is outside the supported range" }
        val salt = ByteArray(16).also(random::nextBytes)
        val digest = derive(pin, salt, iterations)
        return PinVerifier(
            saltBase64 = Base64.getEncoder().withoutPadding().encodeToString(salt),
            digestBase64 = Base64.getEncoder().withoutPadding().encodeToString(digest),
            iterations = iterations,
        )
    }

    fun verify(pin: CharArray, verifier: PinVerifier?): Boolean {
        verifier ?: return false
        if (pin.size !in 4..12 || !pin.all(Char::isDigit)) return false
        val salt = runCatching { Base64.getDecoder().decode(verifier.saltBase64) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(verifier.digestBase64) }.getOrNull() ?: return false
        if (verifier.iterations !in 100_000..MAX_ITERATIONS || salt.size < 16 || expected.size != KEY_LENGTH_BITS / 8) return false
        return MessageDigest.isEqual(expected, derive(pin, salt, verifier.iterations))
    }

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
