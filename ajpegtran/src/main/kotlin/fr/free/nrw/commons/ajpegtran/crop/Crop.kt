package fr.free.nrw.commons.ajpegtran.crop

import android.content.Context
import android.net.Uri
import fr.free.nrw.commons.ajpegtran.Jpegtran
import fr.free.nrw.commons.ajpegtran.Transform

class Crop internal constructor(
    context: Context,
    inputUri: Uri,
    outputUri: Uri,
    private val width: Int,
    private val height: Int,
    private val x: Int,
    private val y: Int
) : Transform(context, inputUri, outputUri) {

    override fun transform() {
        val result = Jpegtran.nativeCrop(rFd, wFd, x, y, width, height)
        if (result == null || !result.startsWith("OK")) {
            throw RuntimeException("Native crop failed: $result")
        }
    }
}