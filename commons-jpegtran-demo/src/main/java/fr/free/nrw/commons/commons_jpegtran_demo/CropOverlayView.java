package fr.free.nrw.commons.commons_jpegtran_demo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom View that draws a resizable crop rectangle with handles.
 * Supports dragging corners/edges to resize and dragging center to move.
 */
public class CropOverlayView extends View {

    private final float density;
    private final float handleRadius;
    private final float cornerHandleRadius;
    private final float minCropSize;
    private final float touchSlop;
    private final float cornerTouchSlop;
    private final float borderWidth;
    private final float gridStrokeWidth;
    private final float centerIndicatorSize;

    private final RectF cropRect = new RectF();
    private final RectF imageBounds = new RectF();

    private final Paint borderPaint = new Paint();
    private final Paint handlePaint = new Paint();
    private final Paint activeHandlePaint = new Paint();
    private final Paint handleBorderPaint = new Paint();
    private final Paint overlayPaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final Paint centerIndicatorPaint = new Paint();

    private Handle activeHandle = null;
    private float lastTouchX = 0f;
    private float lastTouchY = 0f;

    public enum Handle {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        TOP, BOTTOM, LEFT, RIGHT, CENTER
    }

    public CropOverlayView(Context context) {
        this(context, null);
    }

    public CropOverlayView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CropOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        density = context.getResources().getDisplayMetrics().density;
        handleRadius = 12f * density;
        cornerHandleRadius = 16f * density;
        minCropSize = 80f * density;
        touchSlop = 48f * density;
        cornerTouchSlop = 56f * density;
        borderWidth = 2f * density;
        gridStrokeWidth = 1f * density;
        centerIndicatorSize = 16f * density;

        initPaints();
    }

    private void initPaints() {
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(borderWidth);
        borderPaint.setAntiAlias(true);

        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setAntiAlias(true);

        activeHandlePaint.setColor(Color.parseColor("#2196F3")); // Material Blue
        activeHandlePaint.setStyle(Paint.Style.FILL);
        activeHandlePaint.setAntiAlias(true);

        handleBorderPaint.setColor(Color.parseColor("#424242")); // Dark gray
        handleBorderPaint.setStyle(Paint.Style.STROKE);
        handleBorderPaint.setStrokeWidth(1.5f * density);
        handleBorderPaint.setAntiAlias(true);

        overlayPaint.setColor(Color.argb(128, 0, 0, 0));
        overlayPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(Color.argb(180, 255, 255, 255));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(gridStrokeWidth);
        gridPaint.setAntiAlias(true);

        centerIndicatorPaint.setColor(Color.argb(100, 255, 255, 255));
        centerIndicatorPaint.setStyle(Paint.Style.STROKE);
        centerIndicatorPaint.setStrokeWidth(1.5f * density);
        centerIndicatorPaint.setAntiAlias(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (cropRect.isEmpty()) {
            resetCropRect();
        }
    }

    public void setImageBounds(float left, float top, float right, float bottom) {
        imageBounds.set(left, top, right, bottom);
        resetCropRect();
        invalidate();
    }

    public void resetCropRect() {
        if (!imageBounds.isEmpty()) {
            cropRect.set(imageBounds);
        } else {
            float padding = 50f * density;
            cropRect.set(
                    padding,
                    padding,
                    getWidth() - padding,
                    getHeight() - padding
            );
        }
        invalidate();
    }

    @NonNull
    public RectF getCropRect() {
        return new RectF(cropRect);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        // Draw dark overlay outside crop area
        canvas.drawRect(0f, 0f, getWidth(), cropRect.top, overlayPaint);
        canvas.drawRect(0f, cropRect.bottom, getWidth(), getHeight(), overlayPaint);
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint);
        canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, overlayPaint);

        // Draw crop border
        canvas.drawRect(cropRect, borderPaint);

        // Draw rule of thirds grid
        float thirdWidth = cropRect.width() / 3;
        float thirdHeight = cropRect.height() / 3;
        for (int i = 1; i <= 2; i++) {
            canvas.drawLine(
                    cropRect.left + thirdWidth * i, cropRect.top,
                    cropRect.left + thirdWidth * i, cropRect.bottom,
                    gridPaint
            );
            canvas.drawLine(
                    cropRect.left, cropRect.top + thirdHeight * i,
                    cropRect.right, cropRect.top + thirdHeight * i,
                    gridPaint
            );
        }

        // Draw center indicator
        drawCenterIndicator(canvas);

        // Draw corner handles (larger)
        drawCornerHandle(canvas, cropRect.left, cropRect.top, Handle.TOP_LEFT);
        drawCornerHandle(canvas, cropRect.right, cropRect.top, Handle.TOP_RIGHT);
        drawCornerHandle(canvas, cropRect.left, cropRect.bottom, Handle.BOTTOM_LEFT);
        drawCornerHandle(canvas, cropRect.right, cropRect.bottom, Handle.BOTTOM_RIGHT);

        // Draw edge handles (smaller)
        drawEdgeHandle(canvas, cropRect.centerX(), cropRect.top, Handle.TOP);
        drawEdgeHandle(canvas, cropRect.centerX(), cropRect.bottom, Handle.BOTTOM);
        drawEdgeHandle(canvas, cropRect.left, cropRect.centerY(), Handle.LEFT);
        drawEdgeHandle(canvas, cropRect.right, cropRect.centerY(), Handle.RIGHT);

        updateGestureExclusion();
    }

    private void drawCornerHandle(Canvas canvas, float x, float y, Handle handle) {
        boolean isActive = activeHandle == handle;
        Paint paint = isActive ? activeHandlePaint : handlePaint;

        canvas.drawCircle(x, y, cornerHandleRadius, paint);
        canvas.drawCircle(x, y, cornerHandleRadius, handleBorderPaint);
    }

    private void drawEdgeHandle(Canvas canvas, float x, float y, Handle handle) {
        boolean isActive = activeHandle == handle;
        Paint paint = isActive ? activeHandlePaint : handlePaint;

        canvas.drawCircle(x, y, handleRadius, paint);
        canvas.drawCircle(x, y, handleRadius, handleBorderPaint);
    }

    private void drawCenterIndicator(Canvas canvas) {
        float centerX = cropRect.centerX();
        float centerY = cropRect.centerY();

        canvas.drawLine(
                centerX - centerIndicatorSize, centerY,
                centerX + centerIndicatorSize, centerY,
                centerIndicatorPaint
        );
        canvas.drawLine(
                centerX, centerY - centerIndicatorSize,
                centerX, centerY + centerIndicatorSize,
                centerIndicatorPaint
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                activeHandle = findHandle(x, y);
                if (activeHandle != null) {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    lastTouchX = x;
                    lastTouchY = y;
                    invalidate();
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (activeHandle != null) {
                    float dx = x - lastTouchX;
                    float dy = y - lastTouchY;
                    moveHandle(activeHandle, dx, dy);
                    lastTouchX = x;
                    lastTouchY = y;
                    invalidate();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (activeHandle != null) {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    activeHandle = null;
                    invalidate();
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    @Nullable
    private Handle findHandle(float x, float y) {
        // Corners first
        if (isNear(x, y, cropRect.left, cropRect.top, cornerTouchSlop)) return Handle.TOP_LEFT;
        if (isNear(x, y, cropRect.right, cropRect.top, cornerTouchSlop)) return Handle.TOP_RIGHT;
        if (isNear(x, y, cropRect.left, cropRect.bottom, cornerTouchSlop))
            return Handle.BOTTOM_LEFT;
        if (isNear(x, y, cropRect.right, cropRect.bottom, cornerTouchSlop))
            return Handle.BOTTOM_RIGHT;

        // Edges second
        if (isNear(x, y, cropRect.centerX(), cropRect.top, touchSlop)) return Handle.TOP;
        if (isNear(x, y, cropRect.centerX(), cropRect.bottom, touchSlop)) return Handle.BOTTOM;
        if (isNear(x, y, cropRect.left, cropRect.centerY(), touchSlop)) return Handle.LEFT;
        if (isNear(x, y, cropRect.right, cropRect.centerY(), touchSlop)) return Handle.RIGHT;

        // Center third
        if (cropRect.contains(x, y)) return Handle.CENTER;

        return null;
    }

    private boolean isNear(float x, float y, float targetX, float targetY, float slop) {
        float dx = x - targetX;
        float dy = y - targetY;
        return dx * dx + dy * dy <= slop * slop;
    }

    private void moveHandle(Handle handle, float dx, float dy) {
        RectF bounds = !imageBounds.isEmpty() ? imageBounds : new RectF(0f, 0f, getWidth(), getHeight());

        switch (handle) {
            case TOP_LEFT:
                cropRect.left = constrainMin(cropRect.left + dx, bounds.left, cropRect.right - minCropSize);
                cropRect.top = constrainMin(cropRect.top + dy, bounds.top, cropRect.bottom - minCropSize);
                break;
            case TOP_RIGHT:
                cropRect.right = constrainMax(cropRect.right + dx, cropRect.left + minCropSize, bounds.right);
                cropRect.top = constrainMin(cropRect.top + dy, bounds.top, cropRect.bottom - minCropSize);
                break;
            case BOTTOM_LEFT:
                cropRect.left = constrainMin(cropRect.left + dx, bounds.left, cropRect.right - minCropSize);
                cropRect.bottom = constrainMax(cropRect.bottom + dy, cropRect.top + minCropSize, bounds.bottom);
                break;
            case BOTTOM_RIGHT:
                cropRect.right = constrainMax(cropRect.right + dx, cropRect.left + minCropSize, bounds.right);
                cropRect.bottom = constrainMax(cropRect.bottom + dy, cropRect.top + minCropSize, bounds.bottom);
                break;
            case TOP:
                cropRect.top = constrainMin(cropRect.top + dy, bounds.top, cropRect.bottom - minCropSize);
                break;
            case BOTTOM:
                cropRect.bottom = constrainMax(cropRect.bottom + dy, cropRect.top + minCropSize, bounds.bottom);
                break;
            case LEFT:
                cropRect.left = constrainMin(cropRect.left + dx, bounds.left, cropRect.right - minCropSize);
                break;
            case RIGHT:
                cropRect.right = constrainMax(cropRect.right + dx, cropRect.left + minCropSize, bounds.right);
                break;
            case CENTER:
                float newLeft = cropRect.left + dx;
                float newTop = cropRect.top + dy;
                float newRight = cropRect.right + dx;
                float newBottom = cropRect.bottom + dy;

                if (newLeft < bounds.left) {
                    newRight += bounds.left - newLeft;
                    newLeft = bounds.left;
                }
                if (newRight > bounds.right) {
                    newLeft -= newRight - bounds.right;
                    newRight = bounds.right;
                }
                if (newTop < bounds.top) {
                    newBottom += bounds.top - newTop;
                    newTop = bounds.top;
                }
                if (newBottom > bounds.bottom) {
                    newTop -= newBottom - bounds.bottom;
                    newBottom = bounds.bottom;
                }

                cropRect.set(newLeft, newTop, newRight, newBottom);
                break;
        }
    }

    private float constrainMin(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    private float constrainMax(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    private void updateGestureExclusion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            List<Rect> exclusionRects = new ArrayList<>();
            int handleExclusion = (int) (48 * density);
            int handleSize = (int) cornerTouchSlop;

            if (cropRect.left < handleSize) {
                int leftX = 0;
                int rightX = handleSize;
                for (float cy : new float[]{cropRect.top, cropRect.centerY(), cropRect.bottom}) {
                    exclusionRects.add(new Rect(
                            leftX, (int) (cy - handleExclusion / 2.0f),
                            rightX, (int) (cy + handleExclusion / 2.0f)
                    ));
                }
            }

            if (getWidth() - cropRect.right < handleSize) {
                int leftX = getWidth() - handleSize;
                int rightX = getWidth();
                for (float cy : new float[]{cropRect.top, cropRect.centerY(), cropRect.bottom}) {
                    exclusionRects.add(new Rect(
                            leftX, (int) (cy - handleExclusion / 2.0f),
                            rightX, (int) (cy + handleExclusion / 2.0f)
                    ));
                }
            }
            setSystemGestureExclusionRects(exclusionRects);
        }
    }
}
