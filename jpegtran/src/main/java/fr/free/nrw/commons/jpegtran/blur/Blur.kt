package fr.free.nrw.commons.jpegtran.blur

import android.content.Context
import android.net.Uri
import fr.free.nrw.commons.jpegtran.Jpegtran
import fr.free.nrw.commons.jpegtran.Tranform

class Blur internal constructor(
    context: Context,
    inputUri: Uri,
    outputUri: Uri,
    private val regions: List<BlurRegion>
) : Tranform(context, inputUri, outputUri) {

    override fun transform() {
        if (regions.isEmpty()) {
            throw IllegalArgumentException("Regions list must not be empty")
        }

        // Flatten the regions into an IntArray (7 integers per region)
        val regionsArray = IntArray(regions.size * 7)
        for (i in regions.indices) {
            val r = regions[i]
            require(r.isValid) { "Invalid BlurRegion at index $i: $r" }
            regionsArray[i * 7] = r.width
            regionsArray[i * 7 + 1] = r.height
            regionsArray[i * 7 + 2] = r.cornerX
            regionsArray[i * 7 + 3] = r.cornerY
            regionsArray[i * 7 + 4] = r.blockWidth
            regionsArray[i * 7 + 5] = r.blockHeight
            regionsArray[i * 7 + 6] = if (r.aligned) 1 else 0
        }

        val result = Jpegtran.nativePixelize(rFd, wFd, regionsArray)
        if (result == null || !result.startsWith("OK")) {
            throw RuntimeException("Native pixelize failed: $result")
        }
    }
}
