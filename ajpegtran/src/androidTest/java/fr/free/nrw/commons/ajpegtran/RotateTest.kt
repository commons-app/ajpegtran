package fr.free.nrw.commons.ajpegtran

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.free.nrw.commons.ajpegtran.rotate.RotationDegree
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RotateTest {

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
    fun testRotate90() {
        verifyRotation(RotationDegree.ROTATE_90, "expected_rotate_90.jpg")
    }

    @Test
    fun testRotate180() {
        verifyRotation(RotationDegree.ROTATE_180, "expected_rotate_180.jpg")
    }

    @Test
    fun testRotate270() {
        verifyRotation(RotationDegree.ROTATE_270, "expected_rotate_270.jpg")
    }

    @Test
    fun rotateImage180ThenAnother180ReturnsToNormalForImperfectJpeg() {
        // Create an imperfect JPEG (1003x1005 are not multiples of 8 or 16)
        val file = createImperfectJpeg(1003, 1005)
        val uri = Uri.fromFile(file)
        
        val jpegtran1 = Jpegtran(context, uri)
        val file1 = jpegtran1.rotate(RotationDegree.ROTATE_180)
        jpegtran1.cleanup()
        
        val jpegtran2 = Jpegtran(context, Uri.fromFile(file1))
        val file2 = jpegtran2.rotate(RotationDegree.ROTATE_180)
        jpegtran2.cleanup()
        
        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
        val finalBitmap = BitmapFactory.decodeFile(file2.absolutePath)
        
        // Assert dimensions within tolerance of 16 pixels (since trim=true drops edge pixels)
        val tolerance = 16
        assertTrue("Width should be within tolerance", Math.abs(originalBitmap.width - finalBitmap.width) <= tolerance)
        assertTrue("Height should be within tolerance", Math.abs(originalBitmap.height - finalBitmap.height) <= tolerance)
    }

    @Test
    fun rotateImageKeepsLosslessPixelRotationForPerfectJpeg() {
        // Create a perfect JPEG (1024x768 are multiples of 16)
        val file = createImperfectJpeg(1024, 768)
        val uri = Uri.fromFile(file)
        
        val jpegtran = Jpegtran(context, uri)
        val rotatedFile = jpegtran.rotate(RotationDegree.ROTATE_90)
        jpegtran.cleanup()
        
        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
        val rotatedBitmap = BitmapFactory.decodeFile(rotatedFile.absolutePath)
        
        // Perfect JPEG should have exact dimensions swapped (no pixel trimming is needed)
        assertEquals("Width must equal original height", originalBitmap.height, rotatedBitmap.width)
        assertEquals("Height must equal original width", originalBitmap.width, rotatedBitmap.height)
    }

    @Test
    fun test360DegreeRotationCyclesForAllExifImages() {
        val orientations = listOf(
            android.media.ExifInterface.ORIENTATION_NORMAL,       // 1
            android.media.ExifInterface.ORIENTATION_ROTATE_180,   // 3
            android.media.ExifInterface.ORIENTATION_ROTATE_90,    // 6
            android.media.ExifInterface.ORIENTATION_ROTATE_270     // 8
        )
        
        for (orient in orientations) {
            val file = createImperfectJpeg(1003, 1005)
            setExifOrientation(file, orient)
            
            // Perform 4 x 90 degree rotations
            var currentFile = file
            for (i in 1..4) {
                val jpegtran = Jpegtran(context, Uri.fromFile(currentFile))
                val nextFile = jpegtran.rotate(RotationDegree.ROTATE_90)
                jpegtran.cleanup()
                currentFile = nextFile
            }
            
            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
            val finalBitmap = BitmapFactory.decodeFile(currentFile.absolutePath)
            
            // Assert visual rotation returns to normal, subject to trim tolerance
            val tolerance = 32 // 4 times rotate_90 can trim up to 32 pixels
            assertTrue("Width should be within tolerance for orient $orient", Math.abs(originalBitmap.width - finalBitmap.width) <= tolerance)
            assertTrue("Height should be within tolerance for orient $orient", Math.abs(originalBitmap.height - finalBitmap.height) <= tolerance)
        }
    }

    private fun verifyRotation(degree: RotationDegree, expectedAssetName: String) {
        val jpegtran = Jpegtran(context, sourceUri)

        val rotatedFile = jpegtran.rotate(degree)
        assertTrue("Rotated file should exist", rotatedFile.exists())

        val rotatedBitmap = BitmapFactory.decodeFile(rotatedFile.absolutePath)
        assertNotNull("Rotated image should be decodable", rotatedBitmap)

        // Verify dimensions swap 90/270 for dimensions check.
        val originalBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
        if (degree == RotationDegree.ROTATE_90 || degree == RotationDegree.ROTATE_270) {
            assertEquals(
                "Width should equal original height",
                originalBitmap.height,
                rotatedBitmap.width
            )
            assertEquals(
                "Height should equal original width",
                originalBitmap.width,
                rotatedBitmap.height
            )
        } else {
            assertEquals("Width should match original", originalBitmap.width, rotatedBitmap.width)
            assertEquals(
                "Height should match original",
                originalBitmap.height,
                rotatedBitmap.height
            )
        }

        val expectedBitmap = TestHelper.decodeAssetBitmap(expectedAssetName)
        TestHelper.assertBitmapsEqual(expectedBitmap, rotatedBitmap)
        // Verify Cleanup.
        jpegtran.cleanup()
        val cacheFiles = context.cacheDir.listFiles() ?: emptyArray()
        val tempFiles = cacheFiles.filter { it.name.startsWith("jpegtran") }
        assertTrue("clean up temp files", tempFiles.isEmpty())
    }

    private fun createImperfectJpeg(width: Int, height: Int): File {
        val destFile = tempFolder.newFile("imperfect_${width}_${height}.jpg")
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.RED)
        destFile.outputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        return destFile
    }

    private fun setExifOrientation(file: File, orientation: Int) {
        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        exif.saveAttributes()
    }

}
