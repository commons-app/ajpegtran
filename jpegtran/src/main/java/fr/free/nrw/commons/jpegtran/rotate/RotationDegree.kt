package fr.free.nrw.commons.jpegtran.rotate

/**
 * Represents valid JPEG rotation angles.
 */
enum class RotationDegree(
    /**
     * Returns the normalized degree value (90, 180, or 270).
     */
    val degrees: Int
) {
    ROTATE_90(90),
    ROTATE_180(180),
    ROTATE_270(270)
}