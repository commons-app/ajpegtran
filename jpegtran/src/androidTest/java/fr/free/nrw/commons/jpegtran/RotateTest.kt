package fr.free.nrw.commons.jpegtran

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.free.nrw.commons.jpegtran.rotate.RotationDegree
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

}
