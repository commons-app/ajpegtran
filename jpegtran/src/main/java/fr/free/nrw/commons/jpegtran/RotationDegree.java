package fr.free.nrw.commons.jpegtran;

/**
 * Represents valid JPEG lossless rotation angles.
 * <p>
 * Only 90°, 180°, and 270° produce an actual jpegtran transform.
 */
public enum RotationDegree {

    ROTATE_90(90),
    ROTATE_180(180),
    ROTATE_270(270);

    private final int degrees;

    RotationDegree(int degrees) {
        this.degrees = degrees;
    }

    /**
     * Returns the normalized degree value (90, 180, or 270).
     */
    int getDegrees() {
        return degrees;
    }

}
