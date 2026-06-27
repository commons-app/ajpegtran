package fr.free.nrw.commons.ajpegtran

internal object JpegtranNative {

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

    // Load the library and initialize the native methods
    init {
        System.loadLibrary("ajpegtran")
    }
}