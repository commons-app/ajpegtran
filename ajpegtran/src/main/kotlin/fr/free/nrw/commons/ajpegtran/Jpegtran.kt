package fr.free.nrw.commons.ajpegtran

import android.content.Context
import android.net.Uri
import fr.free.nrw.commons.ajpegtran.blur.Blur
import fr.free.nrw.commons.ajpegtran.blur.BlurRegion
import fr.free.nrw.commons.ajpegtran.crop.Crop
import fr.free.nrw.commons.ajpegtran.rotate.Rotate
import fr.free.nrw.commons.ajpegtran.rotate.RotationDegree
import java.io.File

class Jpegtran(
    context: Context,
    imageUri: Uri
){

    private val context: Context = context.applicationContext
    private var tempFileA: File? = null
    private var tempFileB: File? = null
    private var currentInputUri: Uri = imageUri

    /**
     * Retrieves the properties of the provided image Uri.
     *
     * @param fileUri content URI of the source JPEG
     * @return [Properties] on success
     * @throws Exception if fetching properties fails
     */
    fun getProperties(fileUri: Uri): Properties {
        return Properties.of(context, fileUri)
    }

    /**
     * Rotates the current JPEG image state by the given angle.
     *
     * @param rotation rotation angle [RotationDegree]
     * @return the output [File] containing the rotated JPEG
     * @throws RuntimeException if the native rotate operation fails
     */
    fun rotate(rotation: RotationDegree): File {
        val outputFile = getTempFile()
        try {
            val rotate = Rotate(context, currentInputUri, Uri.fromFile(outputFile), rotation.degrees)
            rotate.apply()
            currentInputUri = Uri.fromFile(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            throw e
        }
        return outputFile
    }

    /**
     * Crops the current JPEG image specified rectangle.
     *
     * @param width   crop width in pixels
     * @param height  crop height in pixels
     * @param x       x-offset of the crop origin
     * @param y       y-offset of the crop origin
     * @return the output [File] containing the cropped JPEG
     * @throws IllegalArgumentException if dimensions or offsets are invalid
     * @throws RuntimeException if the native crop operation fails
     */
    fun crop(width: Int, height: Int, x: Int, y: Int): File {
        require(width > 0 && height > 0) { "Crop dimensions must be positive: width=$width, height=$height" }
        require(x >= 0 && y >= 0) { "Crop origin must be non-negative: x=$x, y=$y" }

        val outputFile = getTempFile()
        try {
            val crop = Crop(context, currentInputUri, Uri.fromFile(outputFile), width, height, x, y)
            crop.apply()
            currentInputUri = Uri.fromFile(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            throw e
        }
        return outputFile
    }

    /**
     * Blurs one or more rectangular regions of the JPEG image.
     *
     * @param regions list of regions to pixelize (must not be null or empty)
     * @return the output [File] containing the blurred JPEG
     * @throws IllegalArgumentException if regions are empty or invalid
     * @throws RuntimeException if the native pixelize operation fails
     */
    fun blur(regions: List<BlurRegion>): File {
        val outputFile = getTempFile()
        try {
            val blur = Blur(context, currentInputUri, Uri.fromFile(outputFile), regions)
            blur.apply()
            currentInputUri = Uri.fromFile(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            throw e
        }
        return outputFile
    }

    /**
     * Saves the current JPEG image state directly to the destination Uri.
     *
     * @param destinationUri the target destination Uri
     * @throws Exception if saving fails
     */
    @Throws(java.io.IOException::class)
    fun save(destinationUri: Uri) {
        context.contentResolver.openInputStream(currentInputUri)?.use { input ->
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                input.copyTo(output)
            } ?: throw java.io.IOException("Failed to open output stream for destination: $destinationUri")
        } ?: throw java.io.IOException("Failed to open input stream for source: $currentInputUri")
    }

    /**
     *
     * @return the tempFile where one acts as a source which is previously edited.
     * The other one acts as the destination
     * */
    private fun getTempFile(): File {
        val currentIsA = tempFileA != null && Uri.fromFile(tempFileA) == currentInputUri
        return if (!currentIsA) {
            if (tempFileA == null) {
                tempFileA = File.createTempFile("jpegtran_a_", ".jpg", context.cacheDir)
            }
            tempFileA!!
        } else {
            if (tempFileB == null) {
                tempFileB = File.createTempFile("jpegtran_b_", ".jpg", context.cacheDir)
            }
            tempFileB!!
        }
    }

    /**
     * Cleans up all temporary files created on disk.
     */
    fun cleanup() {
        tempFileA?.takeIf { it.exists() }?.delete()
        tempFileB?.takeIf { it.exists() }?.delete()
        tempFileA = null
        tempFileB = null
    }
    
}
