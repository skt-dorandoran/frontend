package org.duckdns.dorandoran.callaiassistant.util

fun extractPhoneDigits(input: String): String {
    return input.replace(Regex("[^0-9]"), "")
}

fun formatPhoneNumberByRule(input: String): String {
    val digits = extractPhoneDigits(input)
    if (digits.isBlank()) return input

    if (digits.startsWith("82")) {
        val match = Regex("(^82)(2|\\d{2})(\\d+)?(\\d{4})$").matchEntire(digits) ?: return digits
        val middle = match.groups[3]?.value.orEmpty()
        return if (middle.isEmpty()) {
            "+${match.groupValues[1]}-${match.groupValues[2]}-${match.groupValues[4]}"
        } else {
            "+${match.groupValues[1]}-${match.groupValues[2]}-$middle-${match.groupValues[4]}"
        }
    }

    if (digits.startsWith("1")) {
        return Regex("(^1\\d{3})(\\d{4})$")
            .replace(digits, "$1-$2")
    }

    val match = Regex("(^02|^0504|^0505|^0\\d{2})(\\d+)?(\\d{4})$").matchEntire(digits) ?: return digits
    val middle = match.groups[2]?.value.orEmpty()
    return if (middle.isEmpty()) {
        "${match.groupValues[1]}-${match.groupValues[3]}"
    } else {
        "${match.groupValues[1]}-$middle-${match.groupValues[3]}"
    }
}
