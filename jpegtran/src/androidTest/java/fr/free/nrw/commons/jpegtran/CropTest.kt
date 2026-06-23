package fr.free.nrw.commons.jpegtran

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CropTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var sourceFile: File
    private lateinit var sourceUri: Uri

    // Crop parameters used for success tests.
    private val cropX = 16
    private val cropY = 16
    private val cropWidth = 64
    private val cropHeight = 64

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sourceFile = TestHelper.getTestAssetFile("test.jpg", tempFolder)
        sourceUri = Uri.fromFile(sourceFile)
    }


    @Test
    fun testCropOutOfBounds_throwsException() {
        val jpegtran = Jpegtran(context, sourceUri)

        // Find dimensions of sourceFile
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, options)
        val imageWidth = options.outWidth

        // Trying to crop a region that goes out of bounds: x + width > imageWidth
        try {
            jpegtran.crop(cropWidth, cropHeight, imageWidth - cropWidth + 10, cropY)
            fail("Expected RuntimeException for out-of-bounds crop")
        } catch (e: RuntimeException) {
            assertTrue(e.message!!.contains("Native crop failed"))
        }

        // Verify cleanup
        val cacheFiles = context.cacheDir.listFiles() ?: emptyArray()
        val tempFiles = cacheFiles.filter { it.name.startsWith("jpegtran_") }
        jpegtran.cleanup()
        assertTrue("clean up temp files", tempFiles.isEmpty())
    }

    @Test
    fun testCropValidation_zeroWidth() {
        val jpegtran = Jpegtran(context, sourceUri)
        try {
            jpegtran.crop(0, cropHeight, cropX, cropY)
            fail("Expected IllegalArgumentException for zero width")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Crop dimensions must be positive"))
        }
    }

    @Test
    fun testCropValidation_negativeHeight() {
        val jpegtran = Jpegtran(context, sourceUri)
        try {
            jpegtran.crop(cropWidth, -50, cropX, cropY)
            fail("Expected IllegalArgumentException for negative height")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Crop dimensions must be positive"))
        }
    }

    @Test
    fun testCropValidation_negativeX() {
        val jpegtran = Jpegtran(context, sourceUri)
        try {
            jpegtran.crop(cropWidth, cropHeight, -10, cropY)
            fail("Expected IllegalArgumentException for negative x")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Crop origin must be non-negative"))
        }
    }

    @Test
    fun testCropValidation_negativeY() {
        val jpegtran = Jpegtran(context, sourceUri)
        try {
            jpegtran.crop(cropWidth, cropHeight, cropX, -5)
            fail("Expected IllegalArgumentException for negative y")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Crop origin must be non-negative"))
        }
    }

    @Test
    fun testCropSuccess() {
        val jpegtran = Jpegtran(context, sourceUri)

        val croppedFile = jpegtran.crop(cropWidth, cropHeight, cropX, cropY)
        assertTrue("Cropped file should exist", croppedFile.exists())

        val croppedBitmap = BitmapFactory.decodeFile(croppedFile.absolutePath)
        assertNotNull("Cropped image should be decodable", croppedBitmap)
        assertEquals("Cropped width mismatch", cropWidth, croppedBitmap.width)
        assertEquals("Cropped height mismatch", cropHeight, croppedBitmap.height)

        val expectedBitmap = TestHelper.decodeAssetBitmap("expected_crop.jpg")
        TestHelper.assertBitmapsEqual(expectedBitmap, croppedBitmap)
    }

}
