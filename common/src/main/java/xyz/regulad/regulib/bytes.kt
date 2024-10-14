package xyz.regulad.regulib

import java.security.MessageDigest

fun ByteArray.digest(algorithm: String = "SHA-256"): ByteArray {
    val digest = MessageDigest.getInstance(algorithm)
    return digest.digest(this)
}
