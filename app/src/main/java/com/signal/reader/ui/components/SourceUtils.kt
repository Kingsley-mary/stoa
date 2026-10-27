package com.signal.reader.ui.components

fun getSourceInitials(name: String): String {
    val clean = name.replace(Regex("[^A-Za-z*]"), "")
    return clean.take(2).uppercase()
}
