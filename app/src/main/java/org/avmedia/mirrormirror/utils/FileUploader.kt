package org.avmedia.mirrormirror.utils

import android.content.ContentValues.TAG
import android.util.Log
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FileDataPart
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.json.responseJson
import com.github.kittinunf.result.Result
import org.json.JSONObject
import java.io.File
import java.net.URL

open class FileUploader(baseUrl: URL, val successCallback: (msg: JSONObject) -> Unit, val failureCallback: (msg: JSONObject) -> Unit) {

    init {
        FuelManager.instance.apply {
            basePath = baseUrl.toString()
            baseHeaders = mapOf("Content-Type" to "image/jpeg")
        }
    }

    open fun upload(file: File) {
        val fileSize = file.length()
        Fuel.upload("/model/predict")
                .add {
                    FileDataPart(file, "image", "testimage.jpg")
                }

                .progress { writtenBytes, totalBytes ->
                    Log.v(TAG, "Upload: ${writtenBytes.toFloat() / totalBytes.toFloat()}")
                }
                .also { Log.d(TAG, it.toString()) }

                .header(mutableMapOf(
                        "Content-Length" to fileSize,
                        "Accept-Encoding" to "gzip, deflate",
                        "Accept" to "application/json"
                ))

                .responseJson { _, _, result ->

                    when (result) {
                        is Result.Success -> {
                            successCallback.invoke(JSONObject(result.get().content))
                        }
                        is Result.Failure -> {
                            failureCallback.invoke(JSONObject("""{"msg": "Error occured"}"""))
                        }
                    }
                }
    }
}
