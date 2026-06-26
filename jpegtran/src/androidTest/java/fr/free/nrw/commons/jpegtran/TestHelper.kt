package fr.free.nrw.commons.jpegtran

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.rules.TemporaryFolder
import java.io.File

object TestHelper {

    /**
     * Adds the asset to the temp cache and returns the file object for its Uri.
     */
    fun getTestAssetFile(assetName: String, tempFolder: TemporaryFolder): File {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val destFile = tempFolder.newFile(assetName)
        testContext.assets.open(assetName).use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    /**
     * Directly decodes a Bitmap from the test APK assets.
     */
    fun decodeAssetBitmap(assetName: String): Bitmap {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        testContext.assets.open(assetName).use { input ->
            return BitmapFactory.decodeStream(input)
                ?: throw IllegalArgumentException("Could not decode asset: $assetName")
        }
    }

    /**
     * Asserts if the two bitmaps are identical pixel-by-pixel.
     */
    fun assertBitmapsEqual(expected: Bitmap, actual: Bitmap) {
        assertEquals("Bitmap width mismatch", expected.width, actual.width)
        assertEquals("Bitmap height mismatch", expected.height, actual.height)
        assertTrue("Bitmap pixels mismatch", expected.sameAs(actual))
    }
}
