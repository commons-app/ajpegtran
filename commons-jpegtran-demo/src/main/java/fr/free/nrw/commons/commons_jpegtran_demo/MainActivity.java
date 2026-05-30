package fr.free.nrw.commons.commons_jpegtran_demo;

import android.Manifest;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import fr.free.nrw.commons.jpegtran.BlurRegion;
import fr.free.nrw.commons.jpegtran.Jpegtran;
import fr.free.nrw.commons.jpegtran.RotationDegree;

import android.graphics.RectF;


public class MainActivity extends AppCompatActivity {
    /* for jpeg_colorspace */
    static final int JCS_UNKNOWN = 0;        /* error/unspecified */
    static final int JCS_GRAYSCALE = 1;        /* monochrome */
    static final int JCS_RGB = 2;        /* red/green/blue, standard RGB (sRGB) */
    static final int JCS_YCbCr = 3;        /* Y/Cb/Cr (also known as YUV), standard YCC */
    static final int JCS_CMYK = 4;        /* C/M/Y/K */
    static final int JCS_YCCK = 5;        /* Y/Cb/Cr/K */
    static final int JCS_BG_RGB = 6;        /* big gamut red/green/blue, bg-sRGB */
    static final int JCS_BG_YCC = 7;        /* big gamut Y/Cb/Cr, bg-sYCC */

    static Uri loadUri = null;
    static String propertyStr = null;
    private ActivityResultLauncher<Intent> startForResultOpen, startForResultSave;

    private final int REQUEST_CODE_WRITE_PERMISSION = 0x01;
    private Jpegtran jpegtran;
    private ImageView imageView;
    private BlurOverlayView blurOverlayView;
    private CropOverlayView cropOverlayView;
    private final List<File> tempFiles = new ArrayList<>();
    private Matrix baseImageMatrix = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initializing the Jpegtran library.
        jpegtran = new Jpegtran(getApplicationContext());
        imageView = findViewById(R.id.imageView);
        blurOverlayView = findViewById(R.id.blurOverlayView);
        cropOverlayView = findViewById(R.id.cropOverlayView);
        blurOverlayView.setImageView(imageView);

        // Register handler for tapping [OPEN] button
        Button button_open = (Button) findViewById(R.id.button_open);
        button_open.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        getApplicationContext().checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    // Android 10~ : Request permission to access GEOTAG.
                    requestPermissions(new String[]{Manifest.permission.ACCESS_MEDIA_LOCATION}, REQUEST_CODE_WRITE_PERMISSION);
                } else {
                    // Android 4.4~9 : No request is needed.
                    //  or
                    // The permission is granted
                    //  -> Open file selector
                    callFileSelection();
                }
                // If you access file without SAF, you must get permission WRITE_EXTERNAL_STORAGE.
            }
        });

        // Register handler for tapping [SAVE] button
        //  -> Open file selector
        Button button_save = (Button) findViewById(R.id.button_save);
        button_save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (loadUri != null) {
                    callFileSelectionCreate();
                }
            }
        });

        if (loadUri == null) {
            // If file is not specified yet, disable [SAVE] button.
            button_save.setVisibility(View.INVISIBLE);
        } else if (propertyStr != null) {
            // If propertystr is exist, display it.
            TextView textView = (TextView) findViewById(R.id.text_view);
            textView.setText(propertyStr);
        }

        // Register handler for tapping [ABOUT] button
        //  -> Display app information dialog
        Button button_about = (Button) findViewById(R.id.button_about);
        button_about.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                FragmentManager manager = getSupportFragmentManager();
                AboutFragmentDialog abfdialog = new AboutFragmentDialog();
                abfdialog.show(manager, "dialog");
            }
        });
        // Blur feature.
        Button button_blur = (Button) findViewById(R.id.button_blur);
        button_blur.setOnClickListener(v -> {
            if (loadUri == null) {
                Toast.makeText(this, "Please open an image first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (blurOverlayView.getVisibility() != View.VISIBLE) {
                blurOverlayView.setVisibility(View.VISIBLE);
                cropOverlayView.setVisibility(View.GONE);
                // Enter zoom mode: save base matrix and sync ImageView
                baseImageMatrix = new Matrix(imageView.getImageMatrix());
                imageView.setScaleType(ImageView.ScaleType.MATRIX);
                blurOverlayView.setOnZoomChangedListener(vm -> {
                    Matrix combined = new Matrix(baseImageMatrix);
                    combined.postConcat(vm);
                    imageView.setImageMatrix(combined);
                });
                Toast.makeText(this, "Blur mode activated. Draw boxes on the image, then click Blur again to apply.", Toast.LENGTH_LONG).show();
            } else {
                List<BlurRegion> regions = blurOverlayView.getMappedBlurRegions(imageView, blurOverlayView);
                if (regions.isEmpty()) {
                    Toast.makeText(this, "Please draw at least one rectangle on the photo", Toast.LENGTH_SHORT).show();
                    return;
                }

                jpegtran.blur(loadUri, regions, p -> {
                    if (p != null) {
                        Bitmap bitmap = BitmapFactory.decodeFile(p.getAbsolutePath());
                        runOnUiThread(() -> {
                            updateLoadUri(p);
                            blurOverlayView.resetZoom();
                            blurOverlayView.setOnZoomChangedListener(null);
                            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            imageView.setImageBitmap(bitmap);
                            blurOverlayView.clearRegions();
                            blurOverlayView.setVisibility(View.GONE);
                        });
                        Toast.makeText(this, "Blurred successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Blur failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        // Crop feature.
        Button button_crop = (Button) findViewById(R.id.button_crop);
        button_crop.setOnClickListener((v) -> {
            if (loadUri == null) {
                Toast.makeText(this, "Please open an image first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (cropOverlayView.getVisibility() != View.VISIBLE) {
                // Reset blur zoom if active
                if (blurOverlayView.getVisibility() == View.VISIBLE) {
                    blurOverlayView.resetZoom();
                    blurOverlayView.setOnZoomChangedListener(null);
                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                }
                RectF bounds = getBitmapRect(imageView);
                cropOverlayView.setImageBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
                cropOverlayView.setVisibility(View.VISIBLE);
                blurOverlayView.setVisibility(View.GONE);
                Toast.makeText(this, "Crop mode activated. Drag handles to resize, then click Crop again to apply.", Toast.LENGTH_LONG).show();
            } else {
                applyCrop();
            }
        });
        // Rotate feature.
        Button button_rotate = findViewById(R.id.button_rotate);
        button_rotate.setOnClickListener(v -> {
            if (loadUri == null) {
                Toast.makeText(this, "Please open an image first", Toast.LENGTH_SHORT).show();
                return;
            }
            jpegtran.rotate(loadUri, RotationDegree.ROTATE_90, p -> {
                if (p != null) {
                    Bitmap bitmap = BitmapFactory.decodeFile(p.getAbsolutePath());
                    runOnUiThread(() -> {
                        updateLoadUri(p);
                        imageView.setImageBitmap(bitmap);
                        blurOverlayView.clearRegions();
                        cropOverlayView.setVisibility(View.GONE);
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Rotation failed", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });


        // Register handler for open file selector
        //  -> Get JPEG property and display it
        startForResultOpen = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == RESULT_OK) {
                            Intent data = result.getData();
                            if (data != null) {
                                Uri lloadUri = (Uri) data.getData();
                                clearTempFiles();
                                loadUri = lloadUri;
                                imageView.setImageURI(lloadUri);
                                blurOverlayView.clearRegions();
                                cropOverlayView.setVisibility(View.GONE);
                                findViewById(R.id.button_save).setVisibility(View.VISIBLE);


                                assert lloadUri != null;
                                jpegtran.getProperties(lloadUri, p -> {
                                    // Display properties
                                    TextView textView = (TextView) findViewById(R.id.text_view);
                                    propertyStr = "File name : " + p.fileName + "\n";
                                    propertyStr += "File size : " + p.fileSize + "\n";
                                    propertyStr += "Width : " + p.width + "\n" + "Height : " + p.height + "\n";
                                    propertyStr += "MCU Width : " + p.MCU_Width + "\n" + "MCU Height : " + p.MCU_Height + "\n";
                                    propertyStr += "Color space : " + p.Color_space + "\n";
                                    textView.setText(propertyStr);

                                });
                            }
                        }
                    }
                });

        // Register handler for save file selector
        //  -> Execute Jpegtran
        startForResultSave = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == RESULT_OK) {
                            Intent data = result.getData();
                            String jniresult;
                            if (data != null) {
                                Uri saveUri = (Uri) data.getData();
                                ContentResolver resolver = getContentResolver();
                                try (ParcelFileDescriptor wparcelFd = resolver.openFileDescriptor(saveUri, "w");
                                     ParcelFileDescriptor rparcelFd = "file".equals(loadUri.getScheme()) ?
                                             ParcelFileDescriptor.open(new File(Objects.requireNonNull(loadUri.getPath())),
                                                     ParcelFileDescriptor.MODE_READ_ONLY) :
                                             resolver.openFileDescriptor(loadUri, "r");
                                     FileInputStream fis = new FileInputStream(rparcelFd.getFileDescriptor());
                                     FileOutputStream fos = new FileOutputStream(wparcelFd.getFileDescriptor())) {
                                    // Sequentially read and write to save uri.
                                    byte[] buf = new byte[8192];
                                    int len;
                                    while ((len = fis.read(buf)) > 0) {
                                        fos.write(buf, 0, len);
                                    }
                                    jniresult = "OK";
                                } catch (IOException e) {
                                    jniresult = getString(R.string.mess_error_file);
                                }
                                if (jniresult.startsWith("OK")) {
                                    try {
                                        // Workaround : To update mediastore, execute empty write.
                                        OutputStream outstream = resolver.openOutputStream(saveUri, "wa");
                                        outstream.flush();
                                        outstream.close();
                                    } catch (IOException e) {
                                        // Discard exception
                                        //  Mediastore may not be updated, but no recovery operation and continuable.
                                    }
                                    Toast.makeText(getApplicationContext(), getString(R.string.mess_success), Toast.LENGTH_LONG).show();
                                } else {
                                    // When error is occurred, zero size file still remain.
                                    // It should be deleted.
                                    try {
                                        DocumentsContract.deleteDocument(resolver, saveUri);
                                    } catch (FileNotFoundException e) {
                                        // Discard exception
                                        //  The file is not exist, it is OK.
                                    }
                                    Toast.makeText(getApplicationContext(), jniresult, Toast.LENGTH_LONG).show();
                                }
                            }
                        }
                    }
                });
    }

    /**
     * Call file selector for reading JPEG file with readonly mode.
     */
    private void callFileSelection() {
        Intent intentGallery = new Intent(Intent.ACTION_GET_CONTENT);
        intentGallery.addCategory(Intent.CATEGORY_OPENABLE);
        intentGallery.setType("image/jpeg");
        startForResultOpen.launch(intentGallery);
    }

    /**
     * Call file selector for writing JPEG file with create mode.
     */
    private void callFileSelectionCreate() {
        Intent intentGallery = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intentGallery.addCategory(Intent.CATEGORY_OPENABLE);
        intentGallery.setType("image/jpeg");
        // Default file name
        intentGallery.putExtra(Intent.EXTRA_TITLE, "output.jpg");
        startForResultSave.launch(intentGallery);
    }

    /**
     * Handler after requesting permissions.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, int[] grantResults) {
        boolean locAccessDeny = false;
        // Check requested permission is granted or not.
        for (int count = 0; count < grantResults.length; count++) {
            if (permissions[count].equals(Manifest.permission.ACCESS_MEDIA_LOCATION)) {
                if (grantResults[count] != PackageManager.PERMISSION_GRANTED) {
                    locAccessDeny = true;
                }
            }
        }
        if (locAccessDeny) {
            // When permission for accessing GEOTAGs is denied, warn it with toast.
            Toast.makeText(getApplicationContext(), getString(R.string.mess_nopermission_location), Toast.LENGTH_LONG).show();
            // GEOTAGs will be lost, but file can be accessed. So continue the process.
        }
        callFileSelection();
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * Open dialog for displaying app information.
     * This app uses IJG code.
     * This matter must be displayed.
     */
    public static class AboutFragmentDialog extends DialogFragment {
        @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
            builder.setTitle(getString(R.string.button_about));
            builder.setMessage(getString(R.string.app_name) + " \n\n" + getString(R.string.main_ijg));
            builder.setPositiveButton("OK", null);
            return builder.create();
        }
    }

    // Gets the regions for crop and applies them.
    private void applyCrop() {
        Drawable drawable = imageView.getDrawable();
        if (drawable == null || loadUri == null) return;

        int imageWidth = drawable.getIntrinsicWidth();
        int imageHeight = drawable.getIntrinsicHeight();

        Matrix imageMatrix = imageView.getImageMatrix();
        float[] values = new float[9];
        imageMatrix.getValues(values);

        float scaleX = values[Matrix.MSCALE_X];
        float scaleY = values[Matrix.MSCALE_Y];
        float transX = values[Matrix.MTRANS_X];
        float transY = values[Matrix.MTRANS_Y];

        RectF cropRect = cropOverlayView.getCropRect();

        float imageLeft = (cropRect.left - transX) / scaleX;
        float imageTop = (cropRect.top - transY) / scaleY;
        float imageRight = (cropRect.right - transX) / scaleX;
        float imageBottom = (cropRect.bottom - transY) / scaleY;

        int x = Math.max(0, (int) imageLeft);
        int y = Math.max(0, (int) imageTop);
        int width = Math.min(imageWidth - x, (int) (imageRight - imageLeft));
        int height = Math.min(imageHeight - y, (int) (imageBottom - imageTop));

        if (width <= 0 || height <= 0) {
            Toast.makeText(this, "Invalid crop area", Toast.LENGTH_SHORT).show();
            return;
        }

        jpegtran.crop(loadUri, width, height, x, y, p -> {
            if (p != null) {
                Bitmap bitmap = BitmapFactory.decodeFile(p.getAbsolutePath());
                runOnUiThread(() -> {
                    updateLoadUri(p);
                    imageView.setImageBitmap(bitmap);
                    cropOverlayView.setVisibility(View.GONE);
                });
                Toast.makeText(this, "Cropped successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Crop failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private RectF getBitmapRect(ImageView imageView) {
        Drawable drawable = imageView.getDrawable();
        if (drawable == null) return new RectF();

        RectF rect = new RectF(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        Matrix matrix = imageView.getImageMatrix();
        matrix.mapRect(rect);
        return rect;
    }


    private void updateLoadUri(File newFile) {
        if (newFile == null) return;

        // Save the current loadUri to delete if it is a temp file
        if (loadUri != null && "file".equals(loadUri.getScheme())) {
            String path = loadUri.getPath();
            if (path != null) {
                File oldFile = new File(path);
                if (tempFiles.contains(oldFile)) {
                    oldFile.delete();
                    tempFiles.remove(oldFile);
                }
            }
        }

        tempFiles.add(newFile);
        loadUri = Uri.parse("file://" + newFile.getAbsolutePath());
    }

    private void clearTempFiles() {
        for (File file : tempFiles) {
            if (file != null && file.exists()) {
                file.delete();
            }
        }
        tempFiles.clear();
    }

    /**
     * When app closed, clear variable.
     */
    @Override
    protected void onDestroy() {
        clearTempFiles();
        if (isFinishing()) {
            loadUri = null;
            propertyStr = null;
        }
        super.onDestroy();
    }

}