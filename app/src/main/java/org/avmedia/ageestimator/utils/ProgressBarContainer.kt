package org.avmedia.ageestimator.utils

import android.os.Build
import android.view.View
import android.widget.ProgressBar
import io.reactivex.Observer
import io.reactivex.disposables.Disposable

open class ProgressBarContainer(var progressBar: ProgressBar) {

    private fun showProgress (progress: Int): Unit {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            progressBar.setProgress(progress)
        } else {
            progressBar.setProgress(progress, true)
        }
    }

    open fun show() {
        progressBar.visibility = View.VISIBLE
    }

    open fun hide() {
        progressBar.visibility = View.GONE
    }

    open fun getProgressObserver(): Observer<Int> {
        return object : Observer<Int> {
            override fun onSubscribe(d: Disposable) {
                println ("onSubscribe")
            }

            override fun onNext(progress: Int) {
                showProgress(progress)
            }

            override fun onError(e: Throwable) {
                println ("onError")
            }

            override fun onComplete() {
                println ("onComplete")
            }
        }
    }

}