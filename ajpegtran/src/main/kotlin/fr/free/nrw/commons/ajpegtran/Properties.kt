package fr.free.nrw.commons.ajpegtran

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.IOException

class Properties internal constructor(
    /**
     * File name of the JPEG.
     */
    @JvmField var fileName: String?,
    /**
     * File size of the JPEG.
     */
    @JvmField var fileSize: Long,
    /**
     * Width of the JPEG.
     */
    @JvmField var width: Int,
    /**
     * Height of the JPEG.
     */
    @JvmField var height: Int,
    /**
     * Width of one MCU block of the JPEG.
     */
    @JvmField var MCU_Width: Int,
    /**
     * Height of one MCU block of the JPEG.
     */
    @JvmField var MCU_Height: Int,
    /**
     * Color space of the JPEG
     *
     * Unknown, Grayscale, RGB, YCbCr, CMYK, YCbCrK, RGB, YCbCr.
     */
    @JvmField var Color_space: String?
) {
    companion object {
        /**
         * Synchronously retrieves the properties of the JPEG image.
         *
         * @param context the Android Context.
         * @param fileUri content URI of the source JPEG.
         * @return the [Properties] of the image.
         * @throws Exception if opening/reading the file descriptor or parsing fails.
         */
        @JvmStatic
        @Throws(Exception::class)
        fun of(context: Context, fileUri: Uri): Properties {
            val retarray = IntArray(10)
            val resolver = context.contentResolver

            var parcelFd: ParcelFileDescriptor? = null
            try {
                parcelFd = resolver.openFileDescriptor(fileUri, "r")
                if (parcelFd == null) {
                    throw IOException("Failed to open input file descriptor")
                }

                val result: String = Jpegtran.ajpegtranhead(parcelFd.detachFd(), retarray)
                if (!result.startsWith("OK")) {
                    throw RuntimeException("Native ajpegtranhead failed: $result")
                }

                // Get file name and file size
                var filename: String? = null
                var filesize: Long = 0
                resolver.query(fileUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        var index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            filename = cursor.getString(index)
                        }
                        index = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (index != -1) {
                            filesize = cursor.getLong(index)
                        }
                    }
                }

                val width = retarray[0]
                val height = retarray[1]
                val mcu_width = retarray[3] * 8
                val mcu_height = retarray[4] * 8
                val color_space_str = arrayOf(
                    "Unknown",
                    "Grayscale",
                    "RGB",
                    "YCbCr",
                    "CMYK",
                    "YCbCrK",
                    "RGB",
                    "YCbCr"
                )
                val colorSpaceInt = retarray[5]
                val colorSpace = if (colorSpaceInt in 0..7) { /* JCS_BG_YCC= 7 */
                    color_space_str[colorSpaceInt]
                } else {
                    color_space_str[0]
                }

                return Properties(
                    filename,
                    filesize,
                    width,
                    height,
                    mcu_width,
                    mcu_height,
                    colorSpace
                )
            } finally {
                try {
                    parcelFd?.close()
                } catch (e: Exception) {
                    // Ignore close exception
                }
            }
        }
    }
}
