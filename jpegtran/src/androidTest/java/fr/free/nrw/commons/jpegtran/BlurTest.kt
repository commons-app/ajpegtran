package fr.free.nrw.commons.jpegtran

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.free.nrw.commons.jpegtran.blur.BlurRegion
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BlurTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var sourceFile: File
    private lateinit var sourceUri: Uri

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sourceFile = TestHelper.getTestAssetFile("test.jpg", tempFolder)
        sourceUri = Uri.fromFile(sourceFile)
    }

    @Test
    fun testBlurValidation_emptyRegions() {
        val jpegtran = Jpegtran(context, sourceUri)
        try {
            jpegtran.blur(emptyList())
            fail("Expected IllegalArgumentException for empty regions list")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Regions list must not be empty"))
        }
    }

    @Test
    fun testBlurValidation_invalidWidth() {
        val jpegtran = Jpegtran(context, sourceUri)
        val invalidRegion = BlurRegion(
            width = 0,
            height = 32,
            cornerX = 16,
            cornerY = 16,
            blockWidth = 8,
            blockHeight = 8,
            aligned = true
        )
        try {
            jpegtran.blur(listOf(invalidRegion))
            fail("Expected IllegalArgumentException for zero region width")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Invalid BlurRegion"))
        }
    }

    @Test
    fun testBlurValidation_invalidCornerX() {
        val jpegtran = Jpegtran(context, sourceUri)
        val invalidRegion = BlurRegion(
            width = 32,
            height = 32,
            cornerX = -1,
            cornerY = 16,
            blockWidth = 8,
            blockHeight = 8,
            aligned = true
        )
        try {
            jpegtran.blur(listOf(invalidRegion))
            fail("Expected IllegalArgumentException for negative cornerX")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Invalid BlurRegion"))
        }
    }

    @Test
    fun testBlurValidation_invalidBlockWidth() {
        val jpegtran = Jpegtran(context, sourceUri)
        val invalidRegion = BlurRegion(
            width = 32,
            height = 32,
            cornerX = 16,
            cornerY = 16,
            blockWidth = -2,
            blockHeight = 8,
            aligned = true
        )
        try {
            jpegtran.blur(listOf(invalidRegion))
            fail("Expected IllegalArgumentException for negative blockWidth")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Invalid BlurRegion"))
        }
    }

    @Test
    fun testBlurOutOfBounds() {
        val jpegtran = Jpegtran(context, sourceUri)
        try {
            val outOfBoundsRegion = BlurRegion(
                width = 64,
                height = 64,
                cornerX = 180, // out of bounds
                cornerY = 180, // out of bounds
                blockWidth = 8,
                blockHeight = 8,
                aligned = true
            )
            try {
                jpegtran.blur(listOf(outOfBoundsRegion))
            } catch (e: RuntimeException) {
                assertTrue((e.message ?: "").contains("Native pixelize failed"))
            }
        } finally {
            jpegtran.cleanup()
            val cacheFiles = context.cacheDir.listFiles() ?: emptyArray()
            val tempFiles = cacheFiles.filter { it.name.startsWith("jpegtran") }
            assertTrue("clean up temp files", tempFiles.isEmpty())
        }
    }

    @Test
    fun testBlurSuccess() {
        val jpegtran = Jpegtran(context, sourceUri)

        val regions = BlurRegion(
            width = 64,
            height = 64,
            cornerX = 16,
            cornerY = 16,
            blockWidth = 8,
            blockHeight = 8,
            aligned = true
        )

        val blurredFile = jpegtran.blur(listOf(regions))
        assertTrue("Blurred file should exist", blurredFile.exists())

        val blurredBitmap = BitmapFactory.decodeFile(blurredFile.absolutePath)
        assertNotNull("Blurred image should be decodable", blurredBitmap)

        val expectedBitmap = TestHelper.decodeAssetBitmap("expected_blur.jpg")
        TestHelper.assertBitmapsEqual(expectedBitmap, blurredBitmap)

        jpegtran.cleanup()
        val cacheFiles = context.cacheDir.listFiles() ?: emptyArray()
        val tempFiles = cacheFiles.filter { it.name.startsWith("jpegtran") }
        assertTrue("clean up temp files", tempFiles.isEmpty())
    }

    @Test
    fun testBlurMultipleRegions() {
        val jpegtran = Jpegtran(context, sourceUri)

        val region1 = BlurRegion(
            width = 32,
            height = 32,
            cornerX = 16,
            cornerY = 16,
            blockWidth = 8,
            blockHeight = 8,
            aligned = true
        )
        val region2 = BlurRegion(
            width = 16,
            height = 16,
            cornerX = 64,
            cornerY = 64,
            blockWidth = 8,
            blockHeight = 8,
            aligned = false
        )
        val blurredFile = jpegtran.blur(listOf(region1, region2))
        assertTrue("Blurred file should exist", blurredFile.exists())
        assertTrue("Blurred file should not be empty", blurredFile.length() > 0)
        // Verify Cleanup
        jpegtran.cleanup()
        val cacheFiles = context.cacheDir.listFiles() ?: emptyArray()
        val tempFiles = cacheFiles.filter { it.name.startsWith("jpegtran") }
        assertTrue("clean up temp files", tempFiles.isEmpty())

    }

}
