package fr.free.nrw.commons.ajpegtran.crop

import android.content.Context
import android.net.Uri
import fr.free.nrw.commons.ajpegtran.JpegtranNative
import fr.free.nrw.commons.ajpegtran.Properties
import fr.free.nrw.commons.ajpegtran.Transform

class Crop internal constructor(
    context: Context,
    inputUri: Uri,
    outputUri: Uri,
    width: Int,
    height: Int,
    x: Int,
    y: Int
) : Transform(context, inputUri, outputUri) {

    private var alignedWidth: Int = 0
    private var alignedHeight: Int = 0
    private var alignedX: Int = 0
    private var alignedY: Int = 0

    init {
        alignCropCoordinates(context, inputUri, width, height, x, y)
    }

    /**
     * Aligns the crop coordinates to nearest MCU boundary.
    */
    private fun alignCropCoordinates(
        context: Context,
        inputUri: Uri,
        width: Int,
        height: Int,
        x: Int,
        y: Int
    ) {
        val properties = Properties.of(context, inputUri)
        val actualFileWidth = properties.width
        val actualFileHeight = properties.height
        val mcuWidth = properties.MCU_Width
        val mcuHeight = properties.MCU_Height

        val cropLeft = x
        val cropRight = x + width
        val cropTop = y
        val cropBottom = y + height

        // Handle width & horizontal crop bounds
        if (actualFileWidth <= mcuWidth) {
            alignedX = 0
            alignedWidth = actualFileWidth
        } else {
            val maxRight = (actualFileWidth / mcuWidth) * mcuWidth
            val alignedLeft = mcuAlign(cropLeft, mcuWidth, actualFileWidth).coerceIn(0, maxRight - mcuWidth)
            val alignedRight = mcuAlign(cropRight, mcuWidth, actualFileWidth).coerceIn(alignedLeft + mcuWidth, maxRight)
            alignedX = alignedLeft
            alignedWidth = alignedRight - alignedLeft
        }

        // Handle height & vertical crop bounds
        if (actualFileHeight <= mcuHeight) {
            alignedY = 0
            alignedHeight = actualFileHeight
        } else {
            val maxBottom = (actualFileHeight / mcuHeight) * mcuHeight
            val alignedTop = mcuAlign(cropTop, mcuHeight, actualFileHeight).coerceIn(0, maxBottom - mcuHeight)
            val alignedBottom = mcuAlign(cropBottom, mcuHeight, actualFileHeight).coerceIn(alignedTop + mcuHeight, maxBottom)
            alignedY = alignedTop
            alignedHeight = alignedBottom - alignedTop
        }
    }

    /**
     * Helper method which aligns given coordinate to the nearest MCU boundary.
     */
    private fun mcuAlign(value: Int, mcuSize: Int, maxLimit: Int): Int {
        val aligned = ((value + mcuSize / 2) / mcuSize) * mcuSize
        return aligned.coerceIn(0, maxLimit)
    }

    override fun transform() {
        val result = JpegtranNative.runOnJniThread {
            JpegtranNative.nativeCrop(rFd, wFd, alignedX, alignedY, alignedWidth, alignedHeight)
        }
        if (result == null || !result.startsWith("OK")) {
            throw RuntimeException("Native crop failed: $result")
        }
    }
}