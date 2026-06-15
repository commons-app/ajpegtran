package fr.free.nrw.commons.jpegtran.rotate

import android.content.Context
import android.net.Uri
import fr.free.nrw.commons.jpegtran.Jpegtran
import fr.free.nrw.commons.jpegtran.Transform

class Rotate internal constructor(
    context: Context,
    inputUri: Uri,
    outputUri: Uri,
    private val rotation: Int
) : Transform(context, inputUri, outputUri) {

    override fun transform() {
        val result = Jpegtran.nativeRotate(rFd, wFd, rotation)
        if (result == null || !result.startsWith("OK")) {
            throw RuntimeException("Native rotate failed: $result")
        }
    }
}