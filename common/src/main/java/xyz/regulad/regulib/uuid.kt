package xyz.regulad.regulib

import java.nio.ByteBuffer
import java.util.*

/**
 * Converts a UUID to a byte array.
 */
fun UUID.asBytes(): ByteArray {
    val b = ByteBuffer.allocate(16)
    b.putLong(mostSignificantBits)
    b.putLong(leastSignificantBits)
    return b.array()
}

/**
 * Converts a byte array to a UUID.
 */
fun ByteArray.asUUID(): UUID {
    val b = ByteBuffer.wrap(this)
    return UUID(b.long, b.long)
}
