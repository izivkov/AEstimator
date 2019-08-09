package org.avmedia.ageestimator.utils

import android.content.ContentValues.TAG
import android.util.Log
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FileDataPart
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.coroutines.awaitObjectResult
import com.github.kittinunf.fuel.json.responseJson
import com.github.kittinunf.result.Result
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.net.URL

open class FileUploader(baseUrl: URL, val successCallback: (msg: JSONObject) -> Unit, val failureCallback: (msg: JSONObject) -> Unit) {

    var progressCallback: (Int) -> Unit = {}

    init {
        FuelManager.instance.apply {
            basePath = baseUrl.toString()
            baseHeaders = mapOf("Content-Type" to "image/jpeg")
        }
    }

    open fun upload_XXX(file: File) {
        runBlocking {
            val fileSize = file.length()
            Fuel.upload("/model/predict")
                    .add {
                        FileDataPart(file, "image", "testimage.jpg")
                    }

                    .progress { writtenBytes, totalBytes ->
                        val progress = writtenBytes.toFloat() / totalBytes.toFloat()
                        Log.v(TAG, "Upload: ${progress}")
                        progressCallback.invoke((100 * progress).toInt())
                    }
                    .also { Log.d(TAG, it.toString()) }

                    .awaitObjectResult(AgeDeserializer).fold(
                            { data ->
                                successCallback.invoke(JSONObject(data))
                            },
                            { error ->
                                failureCallback.invoke(JSONObject("""{"msg": "Something went wrong!"}"""))
                            }
                    )
        }
    }

    open fun upload(file: File) {
        val fileSize = file.length()
        Fuel.upload("/model/predict")
                .add {
                    FileDataPart(file, "image", "testimage.jpg")
                }

                .progress { writtenBytes, totalBytes ->
                    val progress = writtenBytes.toFloat() / totalBytes.toFloat()
                    Log.v(TAG, "Upload: ${progress}")
                    progressCallback.invoke((100 * progress).toInt())
                }
                .also { Log.d(TAG, it.toString()) }

                .responseJson { _, _, result ->

                    when (result) {
                        is Result.Success -> {
                            successCallback.invoke(JSONObject(result.get().content))
                        }
                        is Result.Failure -> {
                            failureCallback.invoke(JSONObject("""{"msg": "Something went wrong!"}"""))
                        }
                    }
                }
    }


    object AgeDeserializer : ResponseDeserializable<String> {
        override fun deserialize(content: String) =
                content
    }

    open fun setProgressListener(_progressCallback: (progress: Int) -> Unit) {
        this.progressCallback = _progressCallback
    }
}
