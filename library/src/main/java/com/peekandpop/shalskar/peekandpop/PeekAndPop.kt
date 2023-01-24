package com.peekandpop.shalskar.peekandpop

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.View.OnTouchListener
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.annotation.IntDef
import androidx.annotation.LayoutRes
import com.peekandpop.shalskar.peekandpop.model.HoldAndReleaseView
import com.peekandpop.shalskar.peekandpop.model.LongHoldView
import java.util.*

class PeekAndPop(builder: Builder) {
    @IntDef(value = [FLING_UPWARDS, FLING_DOWNWARDS])
    annotation class FlingDirections

    lateinit var peekView: View
        private set
    private lateinit var contentView: ViewGroup
    private lateinit var peekLayout: ViewGroup
    private lateinit var peekAnimationHelper: PeekAnimationHelper
    var isBlurBackground = false
    var isAnimateFling = false
    private var allowUpwardsFling = false
    private var allowDownwardsFling = false
    private var customLongHoldDuration = -1
    var isEnabled = true
    private var longHoldViews: ArrayList<LongHoldView> = ArrayList()
    private var holdAndReleaseViews: ArrayList<HoldAndReleaseView> = ArrayList()
    var currentHoldAndReleaseView: HoldAndReleaseView? = null
    private var onFlingToActionListener: OnFlingToActionListener? = null
    private var onGeneralActionListener: OnGeneralActionListener? = null
    private var onLongHoldListener: OnLongHoldListener? = null
    private var onHoldAndReleaseListener: OnHoldAndReleaseListener? = null
    private var gestureListener = GestureListener()
    private var gestureDetector: GestureDetector
    private var longHoldTimer = Timer()
    private var orientation = 0
    private var peekViewOriginalPosition: FloatArray? = null
    private var peekViewMargin = 0
    private var downX = 0
    private var downY = 0
    private var popTime: Long = 0
    private var usedBuilder: Builder? = null

    init {
        onFlingToActionListener = builder.onFlingToActionListener
        onGeneralActionListener = builder.onGeneralActionListener
        onLongHoldListener = builder.onLongHoldListener
        onHoldAndReleaseListener = builder.onHoldAndReleaseListener
        gestureDetector = GestureDetector(builder.activity, gestureListener)
        initialiseGestureListeners(builder)
        isBlurBackground = builder.blurBackground
        isAnimateFling = builder.animateFling
        allowUpwardsFling = builder.allowUpwardsFling
        allowDownwardsFling = builder.allowDownwardsFling
        orientation = builder.activity.resources.configuration.orientation
        peekViewMargin = DimensionUtil.convertDpToPx(builder.activity.applicationContext, PEEK_VIEW_MARGIN)
        initialisePeekView(builder)
    }

    /**
     * Inflate the peekView, add it to the peekLayout with a shaded/blurred background,
     * bring it to the front and set the peekLayout to have an alpha of 0. Get the peekView's
     * original Y position for use when dragging.
     *
     *
     * If a flingToActionViewLayoutId is supplied, inflate the flingToActionViewLayoutId.
     */
    private fun initialisePeekView(builder: Builder) {
        val inflater = LayoutInflater.from(builder.activity)
        contentView = builder.activity.findViewById<View>(android.R.id.content).rootView as ViewGroup

        // Center onPeek view in the onPeek layout and add to the container view group
        peekLayout = inflater.inflate(R.layout.peek_background, contentView, false) as FrameLayout
        peekView = inflater.inflate(builder.peekLayoutId, peekLayout, false)
        peekView.id = R.id.peek_view

        val layoutParams = peekView.layoutParams as FrameLayout.LayoutParams
        layoutParams.gravity = Gravity.CENTER

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            layoutParams.topMargin = peekViewMargin
        }

        peekLayout.addView(peekView, layoutParams)
        contentView.addView(peekLayout)
        peekLayout.visibility = View.GONE
        peekLayout.alpha = 0f
        peekLayout.requestLayout()
        peekAnimationHelper = PeekAnimationHelper(builder.activity.applicationContext, peekLayout, peekView)
        bringViewsToFront()
        initialiseViewTreeObserver()
        resetViews()
    }

    /**
     * If lollipop or above, use elevation to bring peek views to the front
     */
    private fun bringViewsToFront() {
        peekLayout.elevation = 10f
        peekView.elevation = 10f
    }

    /**
     * Once the onPeek view has inflated fully, this will also update if the view changes in size change
     */
    private fun initialiseViewTreeObserver() {
        peekView.viewTreeObserver.addOnGlobalLayoutListener { initialisePeekViewOriginalPosition() }
    }

    /**
     * Set an onClick and onTouch listener for each long click view.
     */
    private fun initialiseGestureListeners(builder: Builder) {
        for (i in builder.longClickViews.indices) {
            initialiseGestureListener(builder.longClickViews[i], -1)
        }
        gestureDetector.setIsLongpressEnabled(false)
    }

    private fun initialiseGestureListener(view: View, position: Int) {
        view.setOnTouchListener(PeekAndPopOnTouchListener(position))

        // onTouchListener will not work correctly if the view doesn't have an
        // onClickListener set, hence adding one if none has been added.
        if (view.hasOnClickListeners().not()) {
            view.setOnClickListener { }
        }
    }

    /**
     * Check if user has moved or lifted their finger.
     *
     *
     * If lifted, onPop the view and check if their is a drag to action listener, check
     * if it had been dragged enough and send an event if so.
     *
     *
     * If moved, check if the user has entered the bounds of the onPeek view.
     * If the user is within the bounds, and is at the edges of the view, then
     * move it appropriately.
     */
    private fun handleTouch(view: View, event: MotionEvent, position: Int) {
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            pop(view, position)
        } else if (event.action == MotionEvent.ACTION_MOVE) {
            downX = event.rawX.toInt()
            downY = event.rawY.toInt()
            onLongHoldListener?.run {
                checkLongHoldViews(position)
            }
            onHoldAndReleaseListener?.run {
                checkHoldAndReleaseViews(position)
            }
        }

        gestureDetector.onTouchEvent(event)
    }

    /**
     * Check all the long hold views to see if they are being held and if so for how long
     * they have been held and send a long hold event if over the long hold duration.
     *
     * @param position
     */
    private fun checkLongHoldViews(position: Int) {
        for (i in longHoldViews.indices) {
            val longHoldView = longHoldViews[i]
            val viewInBounds = DimensionUtil.pointInViewBounds(longHoldView.view, downX, downY)

            if (viewInBounds && longHoldView.longHoldTimer == null) {
                val duration = if (customLongHoldDuration != -1) customLongHoldDuration.toLong() else LONG_HOLD_DURATION
                longHoldView.startLongHoldViewTimer(this, position, duration)
                onLongHoldListener?.onEnter(longHoldView.view, position)
            } else if (viewInBounds.not() && longHoldView.longHoldTimer != null) {
                longHoldView.longHoldTimer?.cancel()
                longHoldView.longHoldTimer = null
            }
        }
    }

    /**
     * Check all the HoldAndRelease views to see if they are being held and if so for how long
     * they have been held. If > 100ms then set that HoldAndReleaseView as the current.
     *
     * @param position
     */
    private fun checkHoldAndReleaseViews(position: Int) {
        for (i in holdAndReleaseViews.indices) {
            val holdAndReleaseView = holdAndReleaseViews[i]
            val viewInBounds = DimensionUtil.pointInViewBounds(holdAndReleaseView.view, downX, downY)

            if (viewInBounds && holdAndReleaseView.holdAndReleaseTimer == null) {
                holdAndReleaseView.startHoldAndReleaseTimer(this, position, HOLD_AND_RELEASE_DURATION)
            } else if (viewInBounds.not() && holdAndReleaseView.holdAndReleaseTimer != null) {
                holdAndReleaseView.holdAndReleaseTimer?.cancel()
                holdAndReleaseView.holdAndReleaseTimer = null

                if (holdAndReleaseView === currentHoldAndReleaseView) {
                    triggerOnLeaveEvent(holdAndReleaseView.view, holdAndReleaseView.position)
                    holdAndReleaseView.position = -1
                    currentHoldAndReleaseView = null
                }
            }
        }
    }

    fun sendOnLongHoldEvent(view: View?, position: Int) {
        usedBuilder?.activity?.runOnUiThread { onLongHoldListener?.onLongHold(view, position) }
    }

    /**
     * Initialise the peek view original position to be centred in the middle of the screen.
     */
    private fun initialisePeekViewOriginalPosition() {
        peekViewOriginalPosition = floatArrayOf(
                (peekLayout.width / 2 - peekView.width / 2).toFloat(),
                (peekLayout.height / 2 - peekView.height / 2 + peekViewMargin).toFloat()
        )
    }

    /**
     * Animate the peek view in and send an on peek event
     *
     * @param longClickView the view that was long clicked
     * @param index         the view that long clicked
     */
    private fun peek(longClickView: View, index: Int) {
        onGeneralActionListener?.onPeek(longClickView, index)
        peekLayout.visibility = View.VISIBLE
        cancelClick(longClickView)

        if (isBlurBackground) {
            blurBackground()
        }

        peekAnimationHelper.animatePeek(ANIMATION_PEEK_DURATION)

        usedBuilder?.parentViewGroup?.requestDisallowInterceptTouchEvent(true)

        // Reset the touch coordinates to prevent accidental long hold actions on long hold views
        downX = 0
        downY = 0
        gestureListener.setView(longClickView)
        gestureListener.setPosition(index)
    }

    /**
     * Once the peek view has been shown, send a cancel motion event to the long hold view so that
     * it isn't left in a pressed state
     *
     * @param longClickView the view that was long clicked
     */
    private fun cancelClick(longClickView: View) {
        val e = MotionEvent.obtain(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_CANCEL,
                0f, 0f, 0
        )
        longClickView.onTouchEvent(e)
        e.recycle()
    }

    private fun blurBackground() {
        peekLayout.background = null

        usedBuilder?.let {
            peekLayout.background = BitmapDrawable(it.activity.resources, BlurBuilder.blur(contentView))
        }
    }

    /**
     * Animate the peek view in and send a on pop event.
     * Reset all the views and after the peek view has animated out, reset it's position.
     *
     * @param longClickView the view that was long clicked
     * @param index         the view that long clicked
     */
    private fun pop(longClickView: View, index: Int) {
        onGeneralActionListener?.onPop(longClickView, index)
        currentHoldAndReleaseView?.let {
            onHoldAndReleaseListener?.onRelease(it.view, it.position)
        }

        resetTimers()
        peekAnimationHelper.animatePop(object : AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                resetViews()
                animation.cancel()
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        }, ANIMATION_POP_DURATION)
        popTime = System.currentTimeMillis()
    }

    /**
     * Reset all views back to their initial values, this done after the onPeek has popped.
     */
    private fun resetViews() {
        peekLayout.visibility = View.GONE
        downX = 0
        downY = 0

        for (i in longHoldViews.indices) {
            val longHoldTimer = longHoldViews[i].longHoldTimer

            if (longHoldTimer != null) {
                longHoldTimer.cancel()
                longHoldViews[i].longHoldTimer = null
            }
        }

        if (peekViewOriginalPosition != null) {
            peekView.x = peekViewOriginalPosition!![0]
            peekView.y = peekViewOriginalPosition!![1]
        }

        peekView.scaleX = 0.85f
        peekView.scaleY = 0.85f
    }

    private fun resetTimers() {
        currentHoldAndReleaseView = null
        for (holdAndReleaseView in holdAndReleaseViews) {
            holdAndReleaseView.holdAndReleaseTimer?.cancel()
        }

        for (longHoldView in longHoldViews) {
            longHoldView.longHoldTimer?.cancel()
        }
    }

    fun destroy() {
        if (currentHoldAndReleaseView != null && onHoldAndReleaseListener != null) {
            currentHoldAndReleaseView?.holdAndReleaseTimer?.cancel()
            currentHoldAndReleaseView = null
        }

        for (i in longHoldViews.indices) {
            val longHoldTimer = longHoldViews[i].longHoldTimer

            if (longHoldTimer != null) {
                longHoldTimer.cancel()
                longHoldViews[i].longHoldTimer = null
            }
        }

        usedBuilder = null
    }

    fun setFlingTypes(allowUpwardsFling: Boolean, allowDownwardsFling: Boolean) {
        this.allowUpwardsFling = allowUpwardsFling
        this.allowDownwardsFling = allowDownwardsFling
    }

    /**
     * Adds a view to receive long click and touch events
     *
     * @param view     view to receive events
     * @param position add position of view if in a list, this will be returned in the general action listener
     * and drag to action listener.
     */
    fun addLongClickView(view: View, position: Int) {
        initialiseGestureListener(view, position)
    }

    /**
     * Specify id of view WITHIN the peek layout, this view will trigger on long hold events.
     * You can add multiple on long hold views
     *
     * @param longHoldViewId id of the view to receive on long hold events
     * @return
     */
    fun addLongHoldView(@IdRes longHoldViewId: Int, receiveMultipleEvents: Boolean) {
        longHoldViews.add(LongHoldView(peekView.findViewById(longHoldViewId), receiveMultipleEvents))
    }

    /**
     * Specify id of view WITHIN the peek layout, this view will trigger the following events:
     * onHold() - when the view is held for a small amount of time
     * onLeave() - when the view is no longer held but the user is is still touching the screen
     * onRelease() - when the user releases after holding the view
     *
     *
     * You can add multiple HoldAndRelease views
     *
     * @param holdAndReleaseViewId id of the view to receive on long hold events
     * @return
     */
    fun addHoldAndReleaseView(@IdRes holdAndReleaseViewId: Int) {
        holdAndReleaseViews.add(HoldAndReleaseView(peekView.findViewById(holdAndReleaseViewId)))
    }

    fun triggerOnHoldEvent(view: View, position: Int) {
        Handler(Looper.getMainLooper()).post { onHoldAndReleaseListener!!.onHold(view, position) }
    }

    private fun triggerOnLeaveEvent(view: View, position: Int) {
        Handler(Looper.getMainLooper()).post { onHoldAndReleaseListener!!.onLeave(view, position) }
    }

    fun setLongHoldDuration(duration: Int) {
        customLongHoldDuration = duration
    }

    /**
     * Builder class used for creating the PeekAndPop view.
     */
    class Builder(val activity: Activity) {
        var peekLayoutId = -1

        // optional extras
        var parentViewGroup: ViewGroup? = null
        var longClickViews: ArrayList<View> = ArrayList()
        var onFlingToActionListener: OnFlingToActionListener? = null
        var onGeneralActionListener: OnGeneralActionListener? = null
        var onLongHoldListener: OnLongHoldListener? = null
        var onHoldAndReleaseListener: OnHoldAndReleaseListener? = null
        var blurBackground = true
        var animateFling = true
        var allowUpwardsFling = true
        var allowDownwardsFling = true

        /**
         * Peek layout resource id, which will be inflated into the onPeek view
         *
         * @param peekLayoutId id of the onPeek layout resource
         * @return
         */
        fun peekLayout(@LayoutRes peekLayoutId: Int): Builder {
            this.peekLayoutId = peekLayoutId
            return this
        }

        /**
         * Views which will show the peek view when long clicked
         *
         * @param longClickViews One or more views to handle on long click events
         * @return
         */
        fun longClickViews(vararg longClickViews: View): Builder {
            Collections.addAll(this.longClickViews, *longClickViews)
            return this
        }

        /**
         * A listener for when the onPeek view is dragged enough.
         *
         * @param onFlingToActionListener
         * @return
         */
        fun onFlingToActionListener(onFlingToActionListener: OnFlingToActionListener): Builder {
            this.onFlingToActionListener = onFlingToActionListener
            return this
        }

        /**
         * A listener for the onPeek and onPop actions.
         *
         * @param onGeneralActionListener
         * @return
         */
        fun onGeneralActionListener(onGeneralActionListener: OnGeneralActionListener): Builder {
            this.onGeneralActionListener = onGeneralActionListener
            return this
        }

        /**
         * A listener for the on long hold views to receive onLongHold actions.
         *
         * @param onLongHoldListener
         * @return
         */
        fun onLongHoldListener(onLongHoldListener: OnLongHoldListener): Builder {
            this.onLongHoldListener = onLongHoldListener
            return this
        }

        /**
         * A listener for the hold and release views to receive onRelease actions.
         *
         * @param onHoldAndReleaseListener
         * @return
         */
        fun onHoldAndReleaseListener(onHoldAndReleaseListener: OnHoldAndReleaseListener): Builder {
            this.onHoldAndReleaseListener = onHoldAndReleaseListener
            return this
        }

        /**
         * If the container view is situated within another view that receives touch events (like a scroll view),
         * the touch events required for the onPeek and onPop will not work correctly so use this method to disallow
         * touch events from the parent view.
         *
         * @param parentViewGroup The parentView that you wish to disallow touch events to (Usually a scroll view, recycler view etc.)
         * @return
         */
        fun parentViewGroupToDisallowTouchEvents(parentViewGroup: ViewGroup): Builder {
            this.parentViewGroup = parentViewGroup
            return this
        }

        /**
         * Blur the background when showing the peek view, defaults to true.
         * Setting this to false may increase performance.
         *
         * @param blurBackground
         * @return
         */
        fun blurBackground(blurBackground: Boolean): Builder {
            this.blurBackground = blurBackground
            return this
        }

        /**
         * Animate the peek view upwards when a it is flung, defaults to true.
         *
         * @param animateFling
         * @return
         */
        fun animateFling(animateFling: Boolean): Builder {
            this.animateFling = animateFling
            return this
        }

        /**
         * Set the accepted fling types, defaults to both being true.
         */
        fun flingTypes(allowUpwardsFling: Boolean, allowDownwardsFling: Boolean): Builder {
            this.allowUpwardsFling = allowUpwardsFling
            this.allowDownwardsFling = allowDownwardsFling
            return this
        }

        /**
         * Create the PeekAndPop object
         *
         * @return the PeekAndPop object
         */
        fun build(): PeekAndPop {
            require(peekLayoutId != -1) { "No peekLayoutId specified." }
            return PeekAndPop(this)
        }
    }

    private inner class PeekAndPopOnTouchListener(private var position: Int) : OnTouchListener {
        private var longHoldRunnable: Runnable? = null
        private var peekShown = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (isEnabled.not()) {
                return false
            }

            if (event.action == MotionEvent.ACTION_DOWN) {
                peekShown = false
                cancelPendingTimer(view)
                startTimer(view)
            } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                cancelPendingTimer(view)
            }

            if (peekShown) {
                handleTouch(view, event, position)
            }

            return peekShown
        }

        /**
         * Cancel pending timer and if the timer has already activated, run another runnable to
         * pop the view.
         *
         * @param view
         */
        private fun cancelPendingTimer(view: View) {
            longHoldTimer.cancel()

            if (longHoldRunnable != null) {
                longHoldRunnable = Runnable {
                    peekShown = false
                    pop(view, position)
                    longHoldRunnable = null
                }
                usedBuilder?.activity?.runOnUiThread(longHoldRunnable)
            }
        }

        /**
         * Start the longHoldTimer, if it reaches the long hold duration, peek
         *
         * @param view
         */
        private fun startTimer(view: View) {
            longHoldTimer = Timer()
            longHoldTimer.schedule(object : TimerTask() {
                override fun run() {
                    peekShown = true
                    longHoldRunnable = Runnable {
                        if (peekShown) {
                            peek(view, position)
                            longHoldRunnable = null
                        }
                    }
                    usedBuilder?.activity?.runOnUiThread(longHoldRunnable)
                }
            }, LONG_CLICK_DURATION)
        }

        fun setPosition(position: Int) {
            this.position = position
        }
    }

    private inner class GestureListener : SimpleOnGestureListener() {
        private var position = 0
        private var view: View? = null

        fun setView(view: View?) {
            this.view = view
        }

        fun setPosition(position: Int) {
            this.position = position
        }

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onFling(firstEvent: MotionEvent, secondEvent: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            return if (onFlingToActionListener != null) handleFling(velocityX, velocityY) else true
        }

        private fun handleFling(velocityX: Float, velocityY: Float): Boolean {
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                if (velocityY < -FLING_VELOCITY_THRESHOLD && allowUpwardsFling) {
                    flingToAction(FLING_UPWARDS, velocityX, velocityY)
                    return false
                } else if (velocityY > FLING_VELOCITY_THRESHOLD && allowDownwardsFling) {
                    flingToAction(FLING_DOWNWARDS, velocityX, velocityY)
                    return false
                }
            } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (velocityX < -FLING_VELOCITY_THRESHOLD && allowUpwardsFling) {
                    flingToAction(FLING_UPWARDS, velocityX, velocityY)
                    return false
                } else if (velocityX > FLING_VELOCITY_THRESHOLD && allowDownwardsFling) {
                    flingToAction(FLING_DOWNWARDS, velocityX, velocityY)
                    return false
                }
            }

            return true
        }

        private fun flingToAction(@FlingDirections direction: Int, velocityX: Float, velocityY: Float) {
            onFlingToActionListener!!.onFlingToAction(view, position, direction)

            if (isAnimateFling) {
                if (direction == FLING_UPWARDS) {
                    peekAnimationHelper.animateExpand(ANIMATION_POP_DURATION, popTime)
                    peekAnimationHelper.animateFling(velocityX, velocityY, ANIMATION_POP_DURATION, popTime, -FLING_VELOCITY_MAX)
                } else {
                    peekAnimationHelper.animateFling(velocityX, velocityY, ANIMATION_POP_DURATION, popTime, FLING_VELOCITY_MAX)
                }
            }
        }
    }

    interface OnFlingToActionListener {
        fun onFlingToAction(longClickView: View?, position: Int, direction: Int)
    }

    interface OnGeneralActionListener {
        fun onPeek(longClickView: View?, position: Int)
        fun onPop(longClickView: View?, position: Int)
    }

    interface OnLongHoldListener {
        fun onEnter(view: View?, position: Int)
        fun onLongHold(view: View?, position: Int)
    }

    interface OnHoldAndReleaseListener {
        fun onHold(view: View?, position: Int)
        fun onLeave(view: View?, position: Int)
        fun onRelease(view: View?, position: Int)
    }

    companion object {
        const val FLING_UPWARDS = 0
        const val FLING_DOWNWARDS = 1
        private const val PEEK_VIEW_MARGIN = 12
        private const val LONG_CLICK_DURATION: Long = 200
        private const val LONG_HOLD_DURATION: Long = 850
        private const val HOLD_AND_RELEASE_DURATION: Long = 50
        private const val FLING_VELOCITY_THRESHOLD = 3000
        private const val FLING_VELOCITY_MAX = 1000f
        private const val ANIMATION_PEEK_DURATION = 275
        private const val ANIMATION_POP_DURATION = 250
    }
}