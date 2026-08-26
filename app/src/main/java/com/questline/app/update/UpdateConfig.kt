package com.questline.app.update

/**
 * Куда смотреть за обновлениями. Слаг фиксирован — приложение личное,
 * список релизов ведём в GitHub Releases этого репозитория.
 */
object UpdateConfig {
    const val REPO = "distortedvirgo-cloud/questline-android"
}

data class ReleaseInfo(val version: String, val apkUrl: String, val notes: String)
