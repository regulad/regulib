package xyz.regulad.regulib.reflect

// map of boxed java types to kotlin types, for reflection

fun java.lang.Boolean.toKotlinBoolean(): Boolean {
    return this.booleanValue()
}

fun java.lang.Byte.toKotlinByte(): Byte {
    return this.toByte()
}

fun java.lang.Short.toKotlinShort(): Short {
    return this.toShort()
}

fun Integer.toKotlinInt(): Int {
    return this.toInt()
}

fun java.lang.Long.toKotlinLong(): Long {
    return this.toLong()
}

fun Character.toKotlinChar(): Char {
    return this.charValue()
}

fun java.lang.Float.toKotlinFloat(): Float {
    return this.toFloat()
}

fun java.lang.Double.toKotlinDouble(): Double {
    return this.toDouble()
}

fun java.lang.String.toKotlinString(): String {
    return this.toString()
}

