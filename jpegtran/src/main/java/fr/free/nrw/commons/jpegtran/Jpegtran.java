package fr.free.nrw.commons.jpegtran;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;

import org.jspecify.annotations.NonNull;

import java.io.File;
import java.util.List;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class Jpegtran {

    private static final String TAG = "JPEGTRAN";

    private final Context context;
    private final ContentResolver resolver;
    private final ExecutorService executor;
    private final Handler handler;
    static final int JCS_BG_YCC = 7; /* big gamut Y/Cb/Cr, bg-sYCC */

    private static native String ajpegtran(int rfd, int wfd, String optionstr);

    private static native String ajpegtranhead(int fd, int[] retarry);

    // Load the library and initialize the native methods
    static {
        System.loadLibrary("ajpegtran");
    }

    // Initialize on constructor.
    public Jpegtran(Context context) {
        this.context = context;
        this.resolver = context.getContentResolver();
        this.executor = Executors.newSingleThreadExecutor();
        this.handler = new Handler(Looper.getMainLooper());
    }

    /**
     * Returns the properties of the provided image, Runs on a background thread.
     * Returns null on failure.
     * <p>
     * <p>
     * Properties returned are of type {@link Properties}
     * FileName,
     * FileSize,
     * Width,
     * Height,
     * MCU_Width,
     * MCU_Height,
     * Color_space
     *
     * @param fileUri content URI of the source JPEG
     * @param post    callback receiving the output file, or null on error / no-op
     */
    public void getProperties(@NonNull Uri fileUri, @NonNull Consumer<Properties> post) {

        executor.execute(() -> {
            final int[] retarray = new int[10];   // JPEG properties are returned to here.
            try (ParcelFileDescriptor parcelFd = resolver.openFileDescriptor(fileUri, "r")) {

                // Handle descriptors exceptions.
                if (parcelFd == null) {
                    Log.d(TAG, "Error while opening descriptors");
                    handler.post(() -> post.accept(null));
                    return;
                }

                // Get properties.
                final String result = ajpegtranhead(parcelFd.detachFd(), retarray);

                if (result.startsWith("OK")) {
                    //Get file name and file size
                    final Cursor returnCursor = resolver.query(fileUri, null, null, null, null);
                    final String filename;
                    final long filesize;

                    if (returnCursor != null) {
                        int index = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        returnCursor.moveToFirst();
                        filename = returnCursor.getString(index);
                        index = returnCursor.getColumnIndex(OpenableColumns.SIZE);
                        filesize = returnCursor.getLong(index);
                        returnCursor.close();
                    } else {
                        filename = null;
                        filesize = 0;
                    }

                    // Get properties from return array.
                    final int width = retarray[0];
                    final int height = retarray[1];
                    final int component_num = retarray[2];
                    final int mcu_width = retarray[3] * 8;
                    final int mcu_height = retarray[4] * 8;
                    final String[] color_space_str = {"Unknown", "Grayscale", "RGB", "YCbCr", "CMYK", "YCbCrK", "RGB", "YCbCr"};
                    final String color_space = (retarray[5] >= 0 && retarray[5] <= JCS_BG_YCC)
                            ? color_space_str[retarray[5]]
                            : color_space_str[0]; // Unknown
                    handler.post(() -> post.accept(new Properties(filename, filesize, width, height, mcu_width, mcu_height, color_space)));
                } else {
                    Log.d(TAG, "Error while getting properties");
                    handler.post(() -> post.accept(null));
                }

            } catch (IOException | SecurityException e) {
                Log.d(TAG, "getProperties error" + e.getMessage());
                handler.post(() -> post.accept(null));
            }
        });

    }

    /**
     * Losslessly rotates a JPEG image by the given angle.
     * Runs on a background thread;
     * the result {@link File} (or null on failure)
     *
     * @param fileUri  content URI of the source JPEG
     * @param rotation rotation angle {@link RotationDegree}
     * @param post     callback receiving the output file, or null on error / no-op
     */
    public void rotate(@NonNull Uri fileUri,
                       @NonNull RotationDegree rotation,
                       @NonNull Consumer<File> post) {

        // Build options from params.
        String options = "-rotate " + rotation.getDegrees() + " -optimize -copy all";
        ApplyTransformation(fileUri, options, post);
    }

    /**
     * Losslessly crops a JPEG image to the specified rectangle.
     * Runs on a background thread; the result {@link File} (or null on failure)
     *
     * @param fileUri content URI of the source JPEG
     * @param width   crop width in pixels  (must be &gt; 0, non-null)
     * @param height  crop height in pixels (must be &gt; 0, non-null)
     * @param x       x-offset of the crop origin (must be &ge; 0, non-null)
     * @param y       y-offset of the crop origin (must be &ge; 0, non-null)
     * @param post    callback receiving the output file, or null on error
     */
    public void crop(@NonNull Uri fileUri,
                     @NonNull Integer width, @NonNull Integer height,
                     @NonNull Integer x, @NonNull Integer y,
                     @NonNull Consumer<File> post) {
        // Check height and width to be positive.
        if (width <= 0 || height <= 0) {
            Log.e(TAG, "Crop dimensions must be positive: width=" + width
                    + ", height=" + height);
            handler.post(() -> post.accept(null));
            return;
        }
        // Check X and Y coordinates to be positive.
        if (x < 0 || y < 0) {
            Log.e(TAG, "Crop origin must be non-negative: x=" + x + ", y=" + y);
            handler.post(() -> post.accept(null));
            return;
        }

        // Build options from params.
        // jpegtran crop format: WxH+X+Y
        String cropSpec = width + "x" + height + "+" + x + "+" + y;
        String options = "-crop " + cropSpec + " -optimize -copy all";
        ApplyTransformation(fileUri, options, post);
    }

    /**
     * Pixelizes (blurs) one or more rectangular regions of a JPEG image.
     * <p>
     * Each {@link BlurRegion} is applied sequentially — the output of one
     * step becomes the input of the next. This is because the native jpegtran
     * engine only supports a single {@code -pixelize} per invocation.
     * <p>
     * Runs entirely on a background thread; the final result {@link File}
     * (or null on failure)
     *
     * @param fileUri content URI of the source JPEG
     * @param regions list of regions to pixelize (must not be null or empty)
     * @param post    callback receiving the output file, or null on error
     */
    public void blur(@NonNull Uri fileUri,
                     @NonNull List<BlurRegion> regions,
                     @NonNull Consumer<File> post) {

        // Regions list must not be empty.
        if (regions.isEmpty()) {
            Log.e(TAG, "Regions list must not be null or empty");
            handler.post(() -> post.accept(null));
            return;
        }

        // Iterate through regions list and check if any of them are null.
        for (int i = 0; i < regions.size(); i++) {
            BlurRegion region = regions.get(i);
            if (region == null) {
                Log.e(TAG, "BlurRegion at index " + i + " is null");
                handler.post(() -> post.accept(null));
                return;
            }

            // Validate each region value to be positive.
            if (!region.isValid()) {
                Log.e(TAG, "Invalid BlurRegion at index " + i + ": " + region);
                handler.post(() -> post.accept(null));
                return;
            }
        }

        // Run in background thread and apply the blur sequentially.
        executor.execute(() -> {
            // Initialize the temporary files
            File tempA = null;
            File tempB = null;
            try {
                // Load the temporary files from the Cache.
                tempA = File.createTempFile("pixelize_a_", ".jpg", context.getCacheDir());
                tempB = File.createTempFile("pixelize_b_", ".jpg", context.getCacheDir());
                Uri tempAUri = Uri.fromFile(tempA);
                Uri tempBUri = Uri.fromFile(tempB);

                // apply blur sequentially for each region in the list.
                for (int i = 0; i < regions.size(); i++) {
                    BlurRegion region = regions.get(i);
                    String options = "-pixelize " + region.toOptionStr()
                            + " -optimize -copy all";
                    boolean success;
                    if (i == 0) {
                        // First iteration: read from source URI, write to tempA.
                        success = executeTransform(fileUri, tempA, options);
                    } else {
                        // Following subsequent steps are alternate between tempA/tempB.
                        // so we never read and write the same file.
                        // Input is Uri and output is a File/
                        Uri inputUri = (i % 2 == 1) ? tempAUri : tempBUri;
                        File output = (i % 2 == 1) ? tempB : tempA;
                        success = executeTransform(inputUri, output, options);
                    }
                    // If process is unsuccessful delete the temp files and return.
                    if (!success) {
                        Log.d(TAG, "Pixelize failed at region " + i);
                        tempA.delete();
                        tempB.delete();
                        handler.post(() -> post.accept(null));
                        return;
                    }
                }

                // Result is in whichever file was last written to
                File resultFile = (regions.size() == 1) ? tempA
                        : ((regions.size() % 2 == 1) ? tempA : tempB);
                File otherFile = (resultFile == tempA) ? tempB : tempA;
                otherFile.delete();
                handler.post(() -> {
                    post.accept(resultFile);
                });

            } catch (Exception e) {
                Log.d(TAG, "pixelize error: " + e.getMessage());
                if (tempA != null) tempA.delete();
                if (tempB != null) tempB.delete();
                handler.post(() -> post.accept(null));
            }
        });
    }

    /**
     * Core synchronous transform: reads from a content {@link Uri}, writes to
     * {@code outputFile}. Must be called on a background thread.
     * Takes input file Uri as input and gives the transformed output file.
     *
     * @param inputUri   content URI to read from
     * @param outputFile file to write the result to
     * @param optionStr  jpegtran CLI-style options
     * @return {@code true} if the transform succeeded
     */
    // This just executes the transformation and returns if transformations was successful or not.
    private boolean executeTransform(@NonNull Uri inputUri,
                                     @NonNull File outputFile,
                                     @NonNull String optionStr) {

        ParcelFileDescriptor tempRFd = null;
        // Check the scheme of the Uri - content:// or file://
        try {
            if ("file".equals(inputUri.getScheme())) {
                tempRFd = ParcelFileDescriptor.open(new File(Objects.requireNonNull(inputUri.getPath())),
                        ParcelFileDescriptor.MODE_READ_ONLY);
            } else {
                tempRFd = resolver.openFileDescriptor(inputUri, "r");
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to open input file descriptor: " + e.getMessage());
            return false;
        }

        try (ParcelFileDescriptor rFd = tempRFd;
             ParcelFileDescriptor wFd = ParcelFileDescriptor.open(
                     outputFile,
                     ParcelFileDescriptor.MODE_WRITE_ONLY
                             | ParcelFileDescriptor.MODE_CREATE
                             | ParcelFileDescriptor.MODE_TRUNCATE)) {

            if (rFd == null || wFd == null) {
                Log.d(TAG, "Failed to open file descriptors for: " + inputUri);
                return false;
            }

            String result = ajpegtran(rFd.detachFd(), wFd.detachFd(), optionStr);

            if (result.startsWith("OK")) {
                return true;
            } else {
                Log.d(TAG, "Transform failed: " + result);
                return false;
            }

        } catch (Exception e) {
            Log.d(TAG, "executeTransform error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Core synchronous transform: reads from a content {@link Uri}, writes to
     * {@code outputFile}. Must be called on a background thread.
     *
     * @param fileUri   content URI to read from.
     * @param optionStr jpegtran CLI-style options.
     * @param post      callback received from the calling method.
     *
     */
    // Executes the synchronous transform function in background thread.
    // A helper method for Crop and rotate feature as they have this in common.
    private void ApplyTransformation(@NonNull Uri fileUri, @NonNull String optionStr, @NonNull Consumer<File> post) {

        executor.execute(() -> {
            // Create a temp file in the cache
            File tempFile = null;
            try {
                tempFile = File.createTempFile("jpegtran_op_", ".jpg", context.getCacheDir());
            } catch (IOException e) {
                Log.d(TAG, "Failed to create temp file: " + e.getMessage());
                handler.post(() -> post.accept(null));
                return;
            }
            // Call the synchronous transform function.
            boolean success = executeTransform(fileUri, tempFile, optionStr);

            if (success) {
                final File finalTempFile = tempFile;
                handler.post(() -> post.accept(finalTempFile));
            } else {
                tempFile.delete();
                handler.post(() -> post.accept(null));
            }
        });
    }
}


