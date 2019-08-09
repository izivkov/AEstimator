package org.avmedia.ageestimator.utils

import android.os.Build
import android.view.View
import android.widget.ProgressBar

open class ProgressBarContainer(_progressBar: ProgressBar) {
    var progressBar: ProgressBar = _progressBar
    var showProgress: (Int) -> Unit = {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            progressBar.setProgress(it)
        } else {
            progressBar.setProgress(it, true)
        }
    }

    open fun show() {
        progressBar.visibility = View.VISIBLE
    }

    open fun hide() {
        progressBar.visibility = View.GONE
    }
}