/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.gitakraft.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's semantic tints.
 *
 * These are not decoration and they are not a palette in the Material sense. Each one
 * classifies a row in Settings: this is about reading, this is about script, this is
 * dangerous. The icon tint is how that classification is carried, and it has to be the same
 * tint for every row of the same kind or the grouping stops reading.
 *
 * Declared here rather than inline because `colour.per-app-declared` is right that a colour
 * literal repeated at its point of use is a colour that will eventually drift — the reader
 * sets the Settings screen and the Licenses row stops matching the Sources row, and nothing
 * notices. Seven literals in one screen became four named values.
 *
 * These are the **dark** steps of the system palette, not the light ones. GitaKraft is
 * dark-only: MainActivity passes no `darkTheme` argument and the app has no light scheme,
 * because the text is read for long stretches and the author's intent was a single
 * deliberately dark surface. Picking the light steps here would have looked correct in a
 * screenshot and been wrong on the device.
 *
 * The accent itself is not here — that lives in the theme, where the scheme is assembled.
 */
object GitaTints {

    /**
     * Reading, and anything informational rather than actionable.
     *
     * Used for text size, and for the recension note. Blue is the "this is information"
     * default, so it carries no alarm and no invitation.
     */
    val Info = Color(0xFF0A84FF)

    /**
     * On, active, and safe to leave alone.
     *
     * Transliteration and Privacy. Both are settings whose current state is "on", and green
     * here means present rather than good — the privacy row is green whether or not the user
     * has understood it.
     */
    val Active = Color(0xFF30D158)

    /**
     * Reference and provenance.
     *
     * Sources and licenses. Amber rather than blue to keep the reference material visually
     * separate from the settings that change how the text reads; a reader who lands on
     * Sources should be able to tell at a glance that it is about where the text came from,
     * not about how it is displayed.
     */
    val Reference = Color(0xFFFF9F0A)

    /**
     * Destructive and irreversible.
     *
     * Delete all data, and nothing else. Used once in the whole app, and it is the only place
     * the word "delete" appears in Settings without an adjacent explanation of what is lost.
     */
    val Destructive = Color(0xFFFF453A)
}
