package fr.free.nrw.commons.jpegtran;

import github.kamemak.ajpegtran_example.MainActivity;

/**
 * Public API Bridge for AJpegTran library.
 */
public final class AJpegTran {
    private static final MainActivity bridge = new MainActivity();

    private AJpegTran() {}

    /**
     * Executes jpegtran transformations.
     *
     * @param rfd        Read File Descriptor
     * @param wfd        Write File Descriptor
     * @param optionstr  Jpegtran options string
     * @return "OK" on success, or error message on failure
     */
    public static String ajpegtran(int rfd, int wfd, String optionstr) {
        return bridge.ajpegtran(rfd, wfd, optionstr);
    }

    /**
     * Reads JPEG header properties.
     *
     * @param fd        File Descriptor
     * @param retarray  Output array for properties
     * @return "OK" on success, or error message on failure
     */
    public static String ajpegtranhead(int fd, int[] retarray) {
        return bridge.ajpegtranhead(fd, retarray);
    }
}
