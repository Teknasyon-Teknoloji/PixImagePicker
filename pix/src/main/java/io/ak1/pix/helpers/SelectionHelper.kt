package io.ak1.pix.helpers

import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import androidx.core.view.isVisible
import io.ak1.pix.adapters.MainImageAdapter
import io.ak1.pix.databinding.LayoutCameraGridBinding

/**
 * Created By Akshay Sharma on 17,June,2021
 * https://ak1.io
 */

// TODO: 18/06/21 if possible include in fragment class
internal lateinit var mainImageAdapter: MainImageAdapter

fun Int.selection(b: Boolean) {
    mainImageAdapter.select(b, this)
}