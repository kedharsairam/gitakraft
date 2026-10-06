/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.gitakraft.app.ui.theme

import androidx.compose.ui.unit.dp
import com.gitakraft.app.ui.theme.GitaMetrics

/**
 * The metrics that belong to this app and to no other.
 *
 * Spacing, radius, type and touch targets come from kraft-foundation and are shared across the
 * portfolio — that is what makes nine apps look like one body of work. The values here are the
 * ones that would be wrong in any other app, which is the same test that decides an app's
 * accent belongs to the app and not to the foundation.
 *
 * `colour.per-app-declared` already works this way for colour, and this file is its twin. The
 * alternative for each of these was a `@kraft-lint-ignore` waiver at the point of use, and
 * five waivers for five numbers would have buried the mechanism that exists for exceptions
 * that are genuinely exceptional. These are not exceptions. They are this app's dimensions,
 * declared once, next to each other, with the reason each one has the value it has.
 *
 * This file sits under `ui/theme/` and is therefore exempt from `spacing.no-raw-dp`, on the
 * same footing as the foundation's own token file: a file whose content *is* the numbers is
 * not a file that should be asked to use them.
 */
object GitaMetrics {

    /**
     * The widest a notice pill may grow before its text wraps.
     *
     * On a phone this never binds. On a tablet or in landscape it is the difference between a
     * one-line notice and a paragraph-shaped block that looks like a dialog. 340dp is roughly
     * 72 characters at body size, which is a sentence; beyond that the message is long enough
     * that the user wants it dismissed rather than read in place.
     */
    val NoticePillMaxWidth = 340.dp

    /**
     * The icon in the glass tab bar.
     *
     * Deliberately one dp larger than `KraftIconSize.TabBar`. The tab bar draws its own glass
     * and its own indicator rather than using the foundation's, and at 25dp the icon reads
     * slightly small inside that taller bar. It is a one-dp optical adjustment to a component
     * this app draws itself, not a new step in a shared scale.
     */
    val GlassTabIcon = 26.dp

    /**
     * The fixed slot the update control occupies.
     *
     * End-aligned and constant, so the row does not change width as the state changes between
     * idle, checking, available and error. A spinner that is wider than a button makes the
     * whole row jump, and a layout that jumps is read as a bug.
     */
    val UpdateSlotWidth = 104.dp

    /**
     * The sponsor button's width, at the scale it renders at rest.
     *
     * The image is a bundled drawable with its own aspect ratio; this matches the width that
     * art was drawn for. The press animation scales it from here rather than re-laying it out,
     * so this is also the transform's origin.
     */
    val SponsorButtonWidth = 182.dp

    /**
     * Bottom padding that clears the glass tab bar.
     *
     * Larger than `KraftSpacing.GlassBarReserve` (120dp) because this tab bar is not the
     * foundation's: it is taller, and it draws a label under the icon as well as the icon
     * itself. Every scrollable screen ends its content at this inset so the last row is not
     * permanently under the glass.
     *
     * Declared once and used by four screens, which is the whole reason it exists — the four
     * literal 150s were four chances to type 150 slightly differently.
     */
    val AboveTabBar = 150.dp

    /**
     * The reading column's width on a wide screen.
     *
     * A verse is a line of text with a translation beneath it. Left to fill the width of a
     * tablet or an unfolded phone, a line of Sanskrit runs to well over a hundred characters,
     * which is unreadable. Capping the column keeps the measure near what the text was set
     * for.
     */
    val ReaderTextColumn = 120.dp
}
