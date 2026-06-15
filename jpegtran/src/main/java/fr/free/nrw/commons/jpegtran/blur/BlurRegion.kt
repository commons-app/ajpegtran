package fr.free.nrw.commons.jpegtran.blur

/**
 * Defines a rectangular region to be pixelized.
 */
class BlurRegion
/**
 * Creates a new blur region with custom block size.
 * 
 * @param width       area width in pixels (must be &gt; 0)
 * @param height      area height in pixels (must be &gt; 0)
 * @param cornerX     upper-left corner X (must be ≥ 0)
 * @param cornerY     upper-left corner Y (must be ≥ 0)
 * @param blockWidth  pixelized block width
 * @param blockHeight pixelized block height
 * @param aligned     whether the block is aligned
 */(
    /**
     * Width of the area to pixelize, in pixels.
     */
    val width: Int,
    /**
     * Height of the area to pixelize, in pixels.
     */
    val height: Int,
    /**
     * X coordinate of the upper-left corner of the region.
     */
    val cornerX: Int,
    /**
     * Y coordinate of the upper-left corner of the region.
     */
    val cornerY: Int,
    /**
     * Width of the pixelized block.
     */
    val blockWidth: Int,
    /**
     * Height of the pixelized block.
     */
    val blockHeight: Int,
    /**
     * Aligned flag.
     */
    val aligned: Boolean
) {
    val isValid: Boolean
        /**
         * Validates that this region has positive dimensions and coordinates.
         * 
         * @return `true` if width/height are positive and cornerX/cornerY
         * are non-negative
         */
        get() = width > 0 && height > 0 && cornerX >= 0 && cornerY >= 0 && blockWidth > 0 && blockHeight > 0
}
