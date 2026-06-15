package fr.free.nrw.commons.jpegtran.crop

import android.content.Context
import android.net.Uri
import fr.free.nrw.commons.jpegtran.Jpegtran
import fr.free.nrw.commons.jpegtran.Tranform

class Crop internal constructor(
    context: Context,
    inputUri: Uri,
    outputUri: Uri,
    private val width: Int,
    private val height: Int,
    private val x: Int,
    private val y: Int
) : Tranform(context, inputUri, outputUri) {

    override fun transform() {
        val result = Jpegtran.nativeCrop(rFd, wFd, x, y, width, height)
        if (result == null || !result.startsWith("OK")) {
            throw RuntimeException("Native crop failed: $result")
        }
    }
}