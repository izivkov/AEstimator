package org.avmedia.mirrormirror.utils

import android.content.ContentValues.TAG
import android.util.Log
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FileDataPart
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.coroutines.awaitObjectResult
import kotlinx.coroutines.runBlocking
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
        runBlocking {
            val fileSize = file.length()
            Fuel.upload("/model/predict")
                    .add {
                        FileDataPart(file, "image", "testimage.jpg")
                    }

                    .progress { writtenBytes, totalBytes ->
                        Log.v(TAG, "Upload: ${writtenBytes.toFloat() / totalBytes.toFloat()}")
                    }
                    .also { Log.d(TAG, it.toString()) }

                    .awaitObjectResult(AgeDeserializer).fold(
                            { data -> successCallback.invoke(JSONObject(data))},
                            { error -> failureCallback.invoke(JSONObject("""{"msg": "Something went wrong!"}""")) }
                    )
        }

    }

    object AgeDeserializer : ResponseDeserializable<String> {
        override fun deserialize(content: String) =
                content
    }
}
