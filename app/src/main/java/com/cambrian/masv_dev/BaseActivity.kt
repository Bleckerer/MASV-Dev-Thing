package com.cambrian.masv_dev

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider

abstract class BaseActivity : AppCompatActivity() {

    protected val transitionViewModel: ThemeTransitionViewModel by lazy {
        ViewModelProvider(this)[ThemeTransitionViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        transitionViewModel.screenshotBitmap?.let { bitmap ->
            showScreenshotOverlayAndFade(bitmap)
            transitionViewModel.screenshotBitmap = null
        }
    }

    protected fun takeScreenshot(): Bitmap {
        val rootView = window.decorView.rootView
        val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        rootView.draw(canvas)
        return bitmap
    }

    protected fun showScreenshotOverlayAndFade(bitmap: Bitmap) {
        val decorView = window.decorView as ViewGroup

        decorView.findViewWithTag<ImageView>("theme_overlay")?.let {
            decorView.removeView(it)
        }

        val overlay = ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            tag = "theme_overlay"
        }

        decorView.addView(overlay)

        overlay.animate()
            .alpha(0f)
            .setDuration(1000)
            .withEndAction {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
                bitmap.recycle()
            }
            .start()
    }

    protected fun switchThemeWithFade() {
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        val newMode = if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }

        val screenshot = takeScreenshot()
        transitionViewModel.screenshotBitmap = screenshot
        AppCompatDelegate.setDefaultNightMode(newMode)
        recreate()
    }
}