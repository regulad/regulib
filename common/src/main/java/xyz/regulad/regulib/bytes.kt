package xyz.regulad.regulib

import java.security.MessageDigest

fun ByteArray.digest(algorithm: String = "SHA-256"): ByteArray {
    val digest = MessageDigest.getInstance(algorithm)
    return digest.digest(this)
}

/**
 * Finds the starting position of the specified subarray within the array.
 *
 * @param subArray the subarray to search for
 * @param startIndex the index to start the search from
 * @return the index of the first occurrence of the subarray in the array,
 *         or -1 if the subarray is not found
 */
fun ByteArray.indexOf(subArray: ByteArray, startIndex: Int = 0): Int {
    if (subArray.isEmpty()) return -1
    if (startIndex < 0) return -1

    for (i in startIndex..this.size - subArray.size) {
        var found = true
        for (j in subArray.indices) {
            if (this[i + j] != subArray[j]) {
                found = false
                break
            }
        }
        if (found) return i
    }
    return -1
}
