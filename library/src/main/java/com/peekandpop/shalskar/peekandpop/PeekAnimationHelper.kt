package com.peekandpop.shalskar.peekandpop

import android.animation.Animator.AnimatorListener
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.max

/**
 * Created by Vincent on 21/01/2016.
 *
 *
 * Helper class for animating the PeekAndPop views
 */
class PeekAnimationHelper(private val context: Context, private val peekLayout: View, private val peekView: View) {
    /**
     * Occurs on on long hold.
     *
     *
     * Animates the peek view to fade in and scale to it's full size.
     * Also fades the peek background layout in.
     */
    fun animatePeek(duration: Int) {
        peekView.alpha = 1f

        val animatorLayoutAlpha = ObjectAnimator.ofFloat(peekLayout, "alpha", 1f).apply {
            interpolator = OvershootInterpolator(1.2f)
            this.duration = duration.toLong()
        }

        val animatorScaleX = ObjectAnimator.ofFloat(peekView, "scaleX", 1f).apply {
            this.duration = duration.toLong()
        }

        val animatorScaleY = ObjectAnimator.ofFloat(peekView, "scaleY", 1f).apply {
            this.duration = duration.toLong()
        }

        AnimatorSet().run {
            interpolator = OvershootInterpolator(1.2f)
            play(animatorScaleX).with(animatorScaleY)
            start()
        }
        animatorLayoutAlpha.start()
    }

    /**
     * Occurs on touch up.
     *
     *
     * Animates the peek view to return to it's original position and shrink.
     * Also animate the peek background layout to fade out.
     */
    fun animatePop(animatorListener: AnimatorListener?, duration: Int) {
        ObjectAnimator.ofFloat(peekLayout, "alpha", 0f).run {
            this.duration = duration.toLong()
            addListener(animatorListener)
            interpolator = DecelerateInterpolator(1.5f)
            start()
        }
        animateReturn(duration)
    }

    /**
     * Occurs when the peek view is dragged but not flung.
     *
     *
     * Animate the peek view back to it's original position and shrink it.
     */
    fun animateReturn(duration: Int) {
        val animatorTranslate = if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            ObjectAnimator.ofFloat(peekView, "translationY", 0f)
        } else {
            ObjectAnimator.ofFloat(peekView, "translationX", 0f)
        }.apply {
            interpolator = DecelerateInterpolator()
            this.duration = duration.toLong()
        }

        val animatorShrinkY = ObjectAnimator.ofFloat(peekView, "scaleY", 0.75f).apply {
            interpolator = DecelerateInterpolator()
            this.duration = duration.toLong()
        }


        val animatorShrinkX = ObjectAnimator.ofFloat(peekView, "scaleX", 0.75f).apply {
            interpolator = DecelerateInterpolator()
            this.duration = duration.toLong()
        }

        animatorShrinkX.start()
        animatorShrinkY.start()
        animatorTranslate.start()
    }

    /**
     * Occurs when the peek view is flung.
     *
     *
     * Animate the peek view to expand slightly.
     */
    fun animateExpand(duration: Int, popTime: Long) {
        val timeDifference = System.currentTimeMillis() - popTime
        val animatorExpandY = ObjectAnimator.ofFloat(peekView, "scaleY", 1.025f).apply {
            interpolator = DecelerateInterpolator()
            this.duration = max(0, duration - timeDifference)
        }
        val animatorExpandX = ObjectAnimator.ofFloat(peekView, "scaleX", 1.025f).apply {
            interpolator = DecelerateInterpolator()
            this.duration = max(0, duration - timeDifference)
        }

        animatorExpandX.start()
        animatorExpandY.start()
    }

    /**
     * Occurs when the peek view is flung.
     *
     *
     * Animate the peek view up towards the top of the screen.
     * The duration of the animation is the same as the pop animate, minus
     * the time since the pop occurred.
     */
    fun animateFling(velocityX: Float, velocityY: Float, duration: Int, popTime: Long, flingVelocityMax: Float) {
        val timeDifference = System.currentTimeMillis() - popTime

        if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            val translationAmount = max(velocityY / 8, flingVelocityMax)
            ObjectAnimator.ofFloat(peekView, "translationY", translationAmount).apply {
                interpolator = DecelerateInterpolator()
                this.duration = max(0, duration - timeDifference)
                start()
            }
        } else if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val translationAmount = max(velocityX / 8, flingVelocityMax)
            ObjectAnimator.ofFloat(peekView, "translationX", translationAmount).apply {
                interpolator = DecelerateInterpolator()
                this.duration = max(0, duration - timeDifference)
                start()
            }
        }
    }
}