package org.avmedia.mirrormirror.utils

import android.view.View
import android.widget.ProgressBar

open class ProgressBarContainer(_progressBar: ProgressBar) {
    var progressBar: ProgressBar = _progressBar
    var showProgress: (Int) -> Unit = {
        this.progressBar.setProgress(it, true)
    }

    open fun show() {
        progressBar.visibility = View.VISIBLE
    }

    open fun hide() {
        progressBar.visibility = View.GONE
    }
}