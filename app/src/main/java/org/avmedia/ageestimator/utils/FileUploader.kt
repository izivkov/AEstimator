package org.avmedia.ageestimator.utils

import android.content.ContentValues.TAG
import android.util.Log
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FileDataPart
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.rx.rxObject
import com.github.kittinunf.result.Result
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.json.JSONObject
import java.io.File
import java.net.URL

open class FileUploader constructor(baseUrl: URL, val dataObserver: Observer<JSONObject>, val progressObserver: Observer<Int>) {


    init {
        FuelManager.instance.apply {
            basePath = baseUrl.toString()
            baseHeaders = mapOf("Content-Type" to "image/jpeg")
        }
    }

    open fun upload(file: File) {

        val dataSubject = PublishSubject.create<JSONObject>()
        dataSubject.subscribe(dataObserver)

        val progressSubject = PublishSubject.create<Int>()
        progressSubject.subscribe(progressObserver)

        doUpload(file, dataSubject, progressSubject)
    }

    private fun doUpload(file: File, dataSubject: PublishSubject<JSONObject>, progressSubject: PublishSubject<Int>): Unit {

        val fileSize = file.length()
        Fuel.upload("/model/predict")
                .add {
                    FileDataPart(file, "image", "testimage.jpg")
                }

                .progress { writtenBytes, totalBytes ->
                    val progress = writtenBytes.toFloat() / totalBytes.toFloat()
                    Log.v(TAG, "Upload: ${progress}")
                    progressSubject.onNext((100 * progress).toInt())
                }

                .also { Log.d(TAG, it.toString()) }

                .rxObject(AgeDeserializer)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { result ->

                    when (result) {
                        is Result.Success -> {
                            dataSubject.onNext(JSONObject(result.value))
                        }
                        is Result.Failure -> {
                            dataSubject.onError(Throwable("Something went wrong!"))
                        }
                    }

                    dataSubject.onComplete()
                    progressSubject.onComplete()
                }
    }

    object AgeDeserializer : ResponseDeserializable<String> {
        override fun deserialize(content: String) =
                content
    }
}
