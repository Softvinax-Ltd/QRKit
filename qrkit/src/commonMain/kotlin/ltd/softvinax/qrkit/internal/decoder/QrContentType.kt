package ltd.softvinax.qrkit.internal.decoder

/**
 * Coarse classification of a decoded payload, shared by the built-in decoder and platform
 * scanners that use their own barcode engine.
 */
internal fun inferContentType(text: String): String = when {
    text.startsWith("http://", ignoreCase = true) ||
        text.startsWith("https://", ignoreCase = true) -> "URL"
    text.startsWith("wifi:", ignoreCase = true) -> "WIFI"
    text.startsWith("mailto:", ignoreCase = true) -> "EMAIL"
    text.startsWith("tel:", ignoreCase = true) -> "PHONE"
    text.startsWith("sms:", ignoreCase = true) -> "SMS"
    text.startsWith("geo:", ignoreCase = true) -> "LOCATION"
    text.startsWith("begin:vcard", ignoreCase = true) -> "CONTACT"
    text.startsWith("begin:vevent", ignoreCase = true) -> "EVENT"
    else -> "TEXT"
}
