package com.questline.app.ui.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** N-03: чистая логика «показывать ли онбординг» — от флага prefs и пустоты БД. */
class OnboardingGateTest {

    @Test
    fun freshInstallEmptyDbShowsOnboarding() {
        assertTrue(OnboardingGate.shouldShow(done = false, dbEmpty = true))
    }

    @Test
    fun doneFlagHidesOnboarding() {
        assertFalse(OnboardingGate.shouldShow(done = true, dbEmpty = true))
    }

    @Test
    fun nonEmptyDbHidesOnboardingEvenWithoutFlag() {
        // Апгрейд с v2.8/v3.0: флага ещё нет, но данные есть
        assertFalse(OnboardingGate.shouldShow(done = false, dbEmpty = false))
    }

    @Test
    fun doneFlagWithDataHidesOnboarding() {
        assertFalse(OnboardingGate.shouldShow(done = true, dbEmpty = false))
    }
}
