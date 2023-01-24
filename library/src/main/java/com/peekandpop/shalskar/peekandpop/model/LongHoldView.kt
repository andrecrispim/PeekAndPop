package com.peekandpop.shalskar.peekandpop.model

import android.view.View
import com.peekandpop.shalskar.peekandpop.PeekAndPop
import java.util.*

/**
 * Created by Vincent on 9/01/2016.
 */
class LongHoldView(var view: View, var isReceiveMultipleEvents: Boolean) {
    var longHoldTimer: Timer? = null

    /**
     * Sets a timer on the long hold view that will send a long hold event after the duration
     * If receiveMultipleEvents is true, it will set another timer directly after for the duration * 1.5
     *
     * @param position
     * @param duration
     */
    fun startLongHoldViewTimer(peekAndPop: PeekAndPop, position: Int, duration: Long) {
        val longHoldTimer = Timer()
        longHoldTimer.schedule(object : TimerTask() {
            override fun run() {
                peekAndPop.sendOnLongHoldEvent(view, position)
                if (isReceiveMultipleEvents) {
                    startLongHoldViewTimer(peekAndPop, position, duration)
                }
            }
        }, duration)
        this.longHoldTimer = longHoldTimer
    }
}