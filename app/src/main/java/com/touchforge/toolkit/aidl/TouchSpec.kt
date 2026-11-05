package com.touchforge.toolkit.aidl

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** One synthetic touch event to inject. Screen-pixel coordinates. */
@Parcelize
data class TouchSpec(
    val x: Float,
    val y: Float,
    /** MotionEvent ACTION_DOWN / ACTION_MOVE / ACTION_UP. */
    val action: Int,
    val pointerId: Int,
    val downTimeMs: Long,
    val eventTimeMs: Long,
) : Parcelable
