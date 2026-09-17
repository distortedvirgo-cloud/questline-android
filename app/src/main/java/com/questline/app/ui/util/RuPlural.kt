package com.questline.app.ui.util

/** Русские формы: plural(2, "монета", "монеты", "монет") == "монеты" */
fun ruPlural(n: Int, one: String, few: String, many: String): String {
    val abs = kotlin.math.abs(n) % 100
    val last = abs % 10
    return when {
        abs in 11..14 -> many
        last == 1 -> one
        last in 2..4 -> few
        else -> many
    }
}
