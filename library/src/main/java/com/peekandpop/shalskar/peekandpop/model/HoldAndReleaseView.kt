package com.peekandpop.shalskar.peekandpop.model

import android.view.View
import com.peekandpop.shalskar.peekandpop.PeekAndPop
import java.util.*

/**
 * Created by Vincent on 9/01/2016.
 */
class HoldAndReleaseView(var view: View) {

    var position = -1
    var holdAndReleaseTimer: Timer? = Timer()

    fun startHoldAndReleaseTimer(peekAndPop: PeekAndPop, position: Int, duration: Long) {
        val holdAndReleaseTimer = Timer()
        this.position = position
        holdAndReleaseTimer.schedule(object : TimerTask() {
            override fun run() {
                peekAndPop.currentHoldAndReleaseView = this@HoldAndReleaseView
                peekAndPop.triggerOnHoldEvent(view, position)
            }
        }, duration)
        this.holdAndReleaseTimer = holdAndReleaseTimer
    }
}