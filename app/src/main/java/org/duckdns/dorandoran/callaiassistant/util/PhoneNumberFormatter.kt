package org.duckdns.dorandoran.callaiassistant.util

fun formatPhoneNumberByRule(input: String): String {
    val digits = input.replace(Regex("[^0-9]"), "")
    if (digits.isBlank()) return input

    if (digits.startsWith("82")) {
        return Regex("(^82)(2|\\d{2})(\\d+)?(\\d{4})$")
            .replace(digits, "+$1-$2-$3-$4")
    }

    if (digits.startsWith("1")) {
        return Regex("(^1\\d{3})(\\d{4})$")
            .replace(digits, "$1-$2")
    }

    return Regex("(^02|^0504|^0505|^0\\d{2})(\\d+)?(\\d{4})$")
        .replace(digits, "$1-$2-$3")
}
