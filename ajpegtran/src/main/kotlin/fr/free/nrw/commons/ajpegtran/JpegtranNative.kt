package fr.free.nrw.commons.ajpegtran

import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

internal object JpegtranNative {

    /**
     * Single dedicated thread that owns all JNI calls.
     * The underlying C library uses file-level global variables that are not thread-safe.
     * and eliminates the SIGSEGV race condition seen in multi-threaded apps.
     *
     * The thread is a daemon so it does not prevent the JVM from shutting down.
     */
    private val jniExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ajpegtran-jni").apply { isDaemon = true }
    }

    @JvmStatic
    external fun ajpegtranhead(fd: Int, retarry: IntArray?): String

    @JvmStatic
    external fun nativeCrop(
        rfd: Int,
        wfd: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): String?

    @JvmStatic
    external fun nativeRotate(rfd: Int, wfd: Int, degrees: Int): String?

    @JvmStatic
    external fun nativePixelize(rfd: Int, wfd: Int, regions: IntArray?): String?

    /**
     * Runs [block] on the single JNI thread and blocks the caller until it completes.
     * All native calls goes through here to guarantee thread safety.
     */
    fun <T> runOnJniThread(block: () -> T): T =
        try {
            jniExecutor.submit<T> { block() }.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }

    // Load the library and initialize the native methods
    init {
        System.loadLibrary("ajpegtran")
    }
}