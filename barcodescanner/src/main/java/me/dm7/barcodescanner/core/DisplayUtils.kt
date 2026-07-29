package me.dm7.barcodescanner.core

import android.content.Context
import android.content.res.Configuration
import android.view.WindowManager

object DisplayUtils {

    @JvmStatic
    fun getScreenOrientation(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val orientation: Int = if (display.width == display.height) {
            Configuration.ORIENTATION_SQUARE
        } else {
            if (display.width < display.height) {
                Configuration.ORIENTATION_PORTRAIT
            } else {
                Configuration.ORIENTATION_LANDSCAPE
            }
        }
        return orientation
    }
}
