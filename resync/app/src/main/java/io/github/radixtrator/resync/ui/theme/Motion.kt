package io.github.radixtrator.resync.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * Shared motion: short durations with a strong ease-out, so things move the moment they're asked to and
 * spend their remaining time settling rather than getting going.
 */
object Motion {
    /** Material 3's emphasized curve: a fast start and a long, soft landing. For page and sheet movement. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Material 3's emphasized decelerate, for things arriving on screen. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Material 3's emphasized accelerate, for things leaving the screen. */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Page pushes and pops. */
    const val PAGE = 280

    /** Sheets, panels and fades. */
    const val SHORT = 200

    /** Fades that only need to soften a change (media viewers, crossfades). */
    const val FADE = 150

    fun <T> page(): FiniteAnimationSpec<T> = tween(PAGE, easing = Emphasized)
    fun <T> enter(duration: Int = SHORT): FiniteAnimationSpec<T> = tween(duration, easing = EmphasizedDecelerate)
    fun <T> exit(duration: Int = FADE): FiniteAnimationSpec<T> = tween(duration, easing = EmphasizedAccelerate)
}
