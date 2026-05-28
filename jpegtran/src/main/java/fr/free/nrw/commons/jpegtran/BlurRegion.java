package fr.free.nrw.commons.jpegtran;

import org.jspecify.annotations.NonNull;

/**
 * Defines a rectangular region to be pixelized.
 */
public class BlurRegion {

    /**
     * Width of the area to pixelize, in pixels.
     */
    public final int width;

    /**
     * Height of the area to pixelize, in pixels.
     */
    public final int height;

    /**
     * X coordinate of the upper-left corner of the region.
     */
    public final int cornerX;

    /**
     * Y coordinate of the upper-left corner of the region.
     */
    public final int cornerY;

    /**
     * Width of the pixelized block.
     */
    public final int blockWidth;

    /**
     * Height of the pixelized block.
     */
    public final int blockHeight;

    /**
     * Aligned flag.
     */
    public final boolean aligned;

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
     */
    public BlurRegion(int width, int height,
                      int cornerX, int cornerY,
                      int blockWidth, int blockHeight,
                      boolean aligned) {
        this.width = width;
        this.height = height;
        this.cornerX = cornerX;
        this.cornerY = cornerY;
        this.blockWidth = blockWidth;
        this.blockHeight = blockHeight;
        this.aligned = aligned;
    }

    /**
     * Validates that this region has positive dimensions and coordinates.
     *
     * @return {@code true} if width/height are positive and cornerX/cornerY
     * are non-negative
     */
    protected boolean isValid() {
        return width > 0 && height > 0 && cornerX >= 0 && cornerY >= 0 && blockWidth > 0 && blockHeight > 0;
    }

    /**
     * Builds the jpegtran area spec ({@code WxH+X+Y@bW@bH[A]}) for this region.
     * <p>
     * Raw pixel values are passed directly — the C engine handles all
     * alignment to block / iMCU boundaries internally.
     */
    @NonNull
    protected String toOptionStr() {
        return width + "x" + height + "+" + cornerX + "+" + cornerY
                + "@" + blockWidth + "@" + blockHeight + (aligned ? "A" : "");
    }
}
