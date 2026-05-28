package fr.free.nrw.commons.commons_jpegtran_demo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import fr.free.nrw.commons.jpegtran.BlurRegion;

/**
 * Custom overlay view to allow users to draw and select multiple rectangular
 * regions on top of an image. Supports pinch-to-zoom (two fingers) and
 * double-tap to reset while single-finger drag draws blur rectangles.
 */
public class BlurOverlayView extends View {

    private final List<RectF> regions = new ArrayList<>();
    private RectF currentActiveBox = null;
    private float startX, startY;

    private Paint borderPaint;
    private Paint fillPaint;

    private final Matrix viewMatrix = new Matrix();
    private final Matrix inverseMatrix = new Matrix();
    private float scaleFactor = 1.0f;
    private boolean isScaling = false;
    private float lastMidX, lastMidY;

    private static final float DELETE_MARKER_RADIUS_DP = 7f;         // Radius of the red background circle
    private static final float DELETE_MARKER_X_SIZE_DP = 2f;           // Half-length of the cross lines inside the 'X'
    private static final float DELETE_MARKER_STROKE_WIDTH_DP = 1f;     // Stroke thickness of the 'X' lines
    private static final float DELETE_MARKER_TOUCH_RADIUS_DP = 10f;    // Radius of the interactive touch target area

    private float density;
    private Paint deleteCirclePaint;
    private Paint deleteXPaint;
    private int deleteBoxIndex = -1;
    private float startScreenX, startScreenY;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector doubleTapDetector;

    private ImageView imageView;
    private OnZoomChangedListener zoomListener;

    /**
     * Callback so MainActivity can sync the ImageView matrix.
     */
    public interface OnZoomChangedListener {
        void onZoomChanged(Matrix viewMatrix);
    }

    public void setImageView(ImageView imageView) {
        this.imageView = imageView;
    }

    public void setOnZoomChangedListener(OnZoomChangedListener l) {
        this.zoomListener = l;
    }

    public BlurOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;

        borderPaint = new Paint();
        borderPaint.setColor(Color.CYAN);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4.0f);
        borderPaint.setAntiAlias(true);

        fillPaint = new Paint();
        fillPaint.setColor(Color.argb(80, 0, 255, 255));
        fillPaint.setStyle(Paint.Style.FILL);

        deleteCirclePaint = new Paint();
        deleteCirclePaint.setColor(Color.parseColor("#E53935")); // Material Red
        deleteCirclePaint.setStyle(Paint.Style.FILL);
        deleteCirclePaint.setAntiAlias(true);

        deleteXPaint = new Paint();
        deleteXPaint.setColor(Color.WHITE);
        deleteXPaint.setStyle(Paint.Style.STROKE);
        deleteXPaint.setStrokeWidth(DELETE_MARKER_STROKE_WIDTH_DP * density);
        deleteXPaint.setStrokeCap(Paint.Cap.ROUND);
        deleteXPaint.setAntiAlias(true);

        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector d) {
                        float factor = d.getScaleFactor();
                        float newScale = Math.max(1.0f, Math.min(5.0f, scaleFactor * factor));
                        float actual = newScale / scaleFactor;
                        viewMatrix.postScale(actual, actual, d.getFocusX(), d.getFocusY());
                        scaleFactor = newScale;
                        constrainPan();
                        viewMatrix.invert(inverseMatrix);
                        if (zoomListener != null)
                            zoomListener.onZoomChanged(new Matrix(viewMatrix));
                        invalidate();
                        return true;
                    }
                });
        scaleDetector.setQuickScaleEnabled(false);

        doubleTapDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        resetZoom();
                        return true;
                    }
                });
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.concat(viewMatrix);

        for (RectF rect : regions) {
            canvas.drawRect(rect, fillPaint);
            canvas.drawRect(rect, borderPaint);
        }
        if (currentActiveBox != null) {
            canvas.drawRect(currentActiveBox, fillPaint);
            canvas.drawRect(currentActiveBox, borderPaint);
        }
        canvas.restore();

        // Draw X markers in the top-right corner of each bounding boxes.
        float markerRadius = DELETE_MARKER_RADIUS_DP * density;
        float markerXSize = DELETE_MARKER_X_SIZE_DP * density;
        float[] cornerPt = new float[2];

        for (RectF rect : regions) {
            if (rect.width() > 0 && rect.height() > 0) {
                cornerPt[0] = rect.right;
                cornerPt[1] = rect.top;
                viewMatrix.mapPoints(cornerPt);
                float cx = cornerPt[0];
                float cy = cornerPt[1];

                // Draw background circle
                canvas.drawCircle(cx, cy, markerRadius, deleteCirclePaint);
                // Draw crossing lines for the X
                canvas.drawLine(cx - markerXSize, cy - markerXSize, cx + markerXSize, cy + markerXSize, deleteXPaint);
                canvas.drawLine(cx + markerXSize, cy - markerXSize, cx - markerXSize, cy + markerXSize, deleteXPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        doubleTapDetector.onTouchEvent(event);

        // Two-finger gestures - zoom + pan
        if (event.getPointerCount() >= 2) {
            isScaling = true;
            currentActiveBox = null;
            deleteBoxIndex = -1;

            float midX = (event.getX(0) + event.getX(1)) / 2f;
            float midY = (event.getY(0) + event.getY(1)) / 2f;

            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = midX - lastMidX;
                float dy = midY - lastMidY;
                viewMatrix.postTranslate(dx, dy);
                constrainPan();
                viewMatrix.invert(inverseMatrix);
                if (zoomListener != null) zoomListener.onZoomChanged(new Matrix(viewMatrix));
                invalidate();
            }

            lastMidX = midX;
            lastMidY = midY;
            return true;
        }

        if (isScaling) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) isScaling = false;
            return true;
        }

        // Map screen touch → pre-zoom coordinates
        float[] pt = {event.getX(), event.getY()};
        inverseMatrix.mapPoints(pt);
        float x = pt[0], y = pt[1];

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = x;
                startY = y;
                startScreenX = event.getX();
                startScreenY = event.getY();
                deleteBoxIndex = -1;

                // Check if touch is on any box's 'X' marker (top-right corner)
                float touchRadius = DELETE_MARKER_TOUCH_RADIUS_DP * density;
                float screenTouchX = event.getX();
                float screenTouchY = event.getY();
                float[] cornerPt = new float[2];

                for (int i = 0; i < regions.size(); i++) {
                    RectF rect = regions.get(i);
                    cornerPt[0] = rect.right;
                    cornerPt[1] = rect.top;
                    viewMatrix.mapPoints(cornerPt);

                    float dx = screenTouchX - cornerPt[0];
                    float dy = screenTouchY - cornerPt[1];
                    if (dx * dx + dy * dy <= touchRadius * touchRadius) {
                        deleteBoxIndex = i;
                        break;
                    }
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (deleteBoxIndex != -1) {
                    float dxDrag = event.getX() - startScreenX;
                    float dyDrag = event.getY() - startScreenY;
                    // If they drag significantly, cancel delete and start drawing
                    if (dxDrag * dxDrag + dyDrag * dyDrag > (8f * density) * (8f * density)) {
                        deleteBoxIndex = -1;
                    } else {
                        // Suppress drawing while holding the 'X' marker
                        return true;
                    }
                }

                if (currentActiveBox == null) {
                    currentActiveBox = new RectF(startX, startY, startX, startY);
                }
                currentActiveBox.left = Math.min(startX, x);
                currentActiveBox.top = Math.min(startY, y);
                currentActiveBox.right = Math.max(startX, x);
                currentActiveBox.bottom = Math.max(startY, y);
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                if (deleteBoxIndex != -1) {
                    if (deleteBoxIndex < regions.size()) {
                        regions.remove(deleteBoxIndex);
                    }
                    deleteBoxIndex = -1;
                    invalidate();
                    return true;
                }

                if (currentActiveBox != null) {
                    if (currentActiveBox.width() > 15 && currentActiveBox.height() > 15) {
                        regions.add(currentActiveBox);
                    }
                    currentActiveBox = null;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                deleteBoxIndex = -1;
                currentActiveBox = null;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void constrainPan() {
        float[] v = new float[9];
        viewMatrix.getValues(v);
        float s = v[Matrix.MSCALE_X], tx = v[Matrix.MTRANS_X], ty = v[Matrix.MTRANS_Y];
        int w = getWidth(), h = getHeight();

        float dx = Math.max(w - w * s, Math.min(0, tx)) - tx;
        float dy = Math.max(h - h * s, Math.min(0, ty)) - ty;
        if (dx != 0 || dy != 0) viewMatrix.postTranslate(dx, dy);
    }


    @NonNull
    public List<RectF> getDrawnRegions() {
        return regions;
    }

    public void resetZoom() {
        scaleFactor = 1.0f;
        viewMatrix.reset();
        inverseMatrix.reset();
        if (zoomListener != null) zoomListener.onZoomChanged(new Matrix(viewMatrix));
        invalidate();
    }

    public void clearRegions() {
        regions.clear();
        invalidate();
    }

    protected List<BlurRegion> getMappedBlurRegions(ImageView imageView, BlurOverlayView overlayView) {
        List<BlurRegion> mappedRegions = new ArrayList<>();
        Drawable drawable = imageView.getDrawable();
        if (drawable == null) return mappedRegions;

        int imageWidth = drawable.getIntrinsicWidth();
        int imageHeight = drawable.getIntrinsicHeight();

        Matrix imageMatrix = imageView.getImageMatrix();
        float[] vals = new float[9];
        imageMatrix.getValues(vals);
        float sx = vals[Matrix.MSCALE_X], sy = vals[Matrix.MSCALE_Y];
        float tx = vals[Matrix.MTRANS_X], ty = vals[Matrix.MTRANS_Y];

        for (RectF r : overlayView.getDrawnRegions()) {
            // Map from overlay image-space → screen via viewMatrix, then → pixel coords
            RectF screen = new RectF(r);
            viewMatrix.mapRect(screen);

            int cornerX = Math.max(0, (int) ((screen.left - tx) / sx));
            int cornerY = Math.max(0, (int) ((screen.top - ty) / sy));
            int w = Math.min(imageWidth - cornerX, (int) ((screen.right - screen.left) / sx));
            int h = Math.min(imageHeight - cornerY, (int) ((screen.bottom - screen.top) / sy));

            if (w > 0 && h > 0) {
                mappedRegions.add(new BlurRegion(w, h, cornerX, cornerY, 100, 100, true));
            }
        }
        return mappedRegions;
    }
}
