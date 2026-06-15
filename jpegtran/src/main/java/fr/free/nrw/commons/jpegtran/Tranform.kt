package fr.free.nrw.commons.jpegtran

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.Objects

abstract class Tranform protected constructor(
    protected val context: Context,
    protected val inputUri: Uri,
    protected val outputUri: Uri
) {
    protected var rFd: Int = 0
    protected var wFd: Int = 0

    abstract fun transform()

    /**
     * Executes the transformation synchronously.
     * Opens file descriptors for reading (from [inputUri]) and writing (to [outputUri]),
     * then calls the abstract [transform] method which performs the actual JNI operation.
     *
     * @throws Exception if file descriptors cannot be opened or the transform fails.
     */
    fun apply() {
        var tempRFd: ParcelFileDescriptor? = null
        var tempWFd: ParcelFileDescriptor? = null
        try {
            tempRFd = if ("file" == inputUri.scheme) {
                ParcelFileDescriptor.open(
                    File(Objects.requireNonNull<String>(inputUri.path)),
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
            } else {
                context.contentResolver.openFileDescriptor(inputUri, "r")
            }

            tempWFd = if ("file" == outputUri.scheme) {
                ParcelFileDescriptor.open(
                    File(Objects.requireNonNull<String>(outputUri.path)),
                    (ParcelFileDescriptor.MODE_WRITE_ONLY
                            or ParcelFileDescriptor.MODE_CREATE
                            or ParcelFileDescriptor.MODE_TRUNCATE)
                )
            } else {
                context.contentResolver.openFileDescriptor(outputUri, "w")
            }

            if (tempRFd == null || tempWFd == null) {
                throw Exception("Failed to open file descriptors")
            }
            this@Tranform.rFd = tempRFd.detachFd()
            this@Tranform.wFd = tempWFd.detachFd()

            transform() // Runs the JNI operation
        } finally {
            try {
                tempRFd?.close()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                tempWFd?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
