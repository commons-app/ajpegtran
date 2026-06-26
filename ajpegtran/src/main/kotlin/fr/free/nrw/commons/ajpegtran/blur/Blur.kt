package fr.free.nrw.commons.ajpegtran.blur

import android.content.Context
import android.net.Uri
import fr.free.nrw.commons.ajpegtran.Jpegtran
import fr.free.nrw.commons.ajpegtran.Transform

class Blur internal constructor(
    context: Context,
    inputUri: Uri,
    outputUri: Uri,
    private val regions: List<BlurRegion>
) : Transform(context, inputUri, outputUri) {

    override fun transform() {
        require(regions.isNotEmpty()) { "Regions list must not be empty" }
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
