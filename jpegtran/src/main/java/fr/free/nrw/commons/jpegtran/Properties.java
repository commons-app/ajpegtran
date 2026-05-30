package fr.free.nrw.commons.jpegtran;

public class Properties {

    /**
     * File name of the JPEG.
     *
     */
    public String fileName;
    /**
     * File size of the JPEG.
     *
     */
    public long fileSize;
    /**
     * Width of the JPEG.
     *
     */
    public int width;
    /**
     * Height of the JPEG.
     *
     */
    public int height;
    /**
     * Width of one MCU block of the JPEG.
     *
     */
    public int MCU_Width;
    /**
     * Height of one MCU block of the JPEG,
     *
     */
    public int MCU_Height;
    /**
     * Color space of the JPEG
     * <p>
     * Unknown, Grayscale, RGB, YCbCr, CMYK, YCbCrK, RGB, YCbCr.
     *
     */
    public String Color_space;

    Properties(String fileName, long fileSize, int width, int height, int MCU_Width, int MCU_Height, String Color_space) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.width = width;
        this.height = height;
        this.MCU_Width = MCU_Width;
        this.MCU_Height = MCU_Height;
        this.Color_space = Color_space;
    }
}
