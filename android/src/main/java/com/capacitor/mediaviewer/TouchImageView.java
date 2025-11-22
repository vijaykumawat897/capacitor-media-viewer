package com.capacitor.mediaviewer;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;

public class TouchImageView extends ImageView implements View.OnTouchListener {
    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 4.0f;
    private static final float DOUBLE_TAP_SCALE = 2.0f;

    private Matrix matrix;
    private int viewWidth;
    private int viewHeight;
    private float saveScale = 1f;
    private PointF last = new PointF();
    private PointF start = new PointF();
    private float minScale;
    private float maxScale;
    private float[] m;

    private int mode = NONE;
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGestureDetector;
    private boolean isInitialFit = true;

    public TouchImageView(Context context) {
        super(context);
        init(context);
    }

    public TouchImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        super.setClickable(true);
        this.setOnTouchListener(this);
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        mGestureDetector = new GestureDetector(context, new GestureListener());
        matrix = new Matrix();
        m = new float[9];
        setImageMatrix(matrix);
        setScaleType(ScaleType.MATRIX);
    }
    
    public void resetZoom() {
        saveScale = 1.0f;
        mode = NONE;
        isInitialFit = true;
        matrix.reset();
        // Post to ensure view is measured and drawable is loaded
        post(() -> {
            if (getDrawable() != null && viewWidth > 0 && viewHeight > 0) {
                fitToScreen();
                isInitialFit = false;
            }
        });
    }
    
    public void fitToScreenPublic() {
        isInitialFit = true;
        if (getDrawable() != null && viewWidth > 0 && viewHeight > 0) {
            fitToScreen();
            isInitialFit = false;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        viewWidth = MeasureSpec.getSize(widthMeasureSpec);
        viewHeight = MeasureSpec.getSize(heightMeasureSpec);
        // Don't auto-fit in onMeasure, let it happen in onLayout or when image loads
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            viewWidth = right - left;
            viewHeight = bottom - top;
            if (getDrawable() != null && isInitialFit && viewWidth > 0 && viewHeight > 0) {
                fitToScreen();
                isInitialFit = false;
            }
        }
    }

    private void fitToScreen() {
        if (getDrawable() == null || viewWidth == 0 || viewHeight == 0) {
            return;
        }

        float imageWidth = getDrawable().getIntrinsicWidth();
        float imageHeight = getDrawable().getIntrinsicHeight();
        
        if (imageWidth <= 0 || imageHeight <= 0) {
            return;
        }

        float scaleX = (float) viewWidth / imageWidth;
        float scaleY = (float) viewHeight / imageHeight;
        float fitScale = Math.min(scaleX, scaleY);
        
        // For initial display, don't zoom in - only scale down if needed
        // If image is smaller than screen, display at 1.0 scale (natural size)
        // If image is larger than screen, scale down to fit
        float initialScale = Math.min(1.0f, fitScale);
        
        // minScale is the minimum scale (fit scale, but never > 1.0 for initial display)
        minScale = Math.min(1.0f, fitScale);
        // maxScale allows zooming up to MAX_SCALE times the initial scale
        maxScale = initialScale * MAX_SCALE;
        
        // Start with initial scale (1.0 or fitScale, whichever is smaller)
        saveScale = initialScale;

        matrix.reset();
        
        // Calculate how to center the image at initial scale
        // For MATRIX scale type: scale first, then translate to center
        
        float scaledWidth = imageWidth * initialScale;
        float scaledHeight = imageHeight * initialScale;
        
        // Calculate translation to center the scaled image in the view
        // Account for any padding
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();
        
        float availableWidth = viewWidth - paddingLeft - paddingRight;
        float availableHeight = viewHeight - paddingTop - paddingBottom;
        
        float dx = paddingLeft + (availableWidth - scaledWidth) / 2;
        float dy = paddingTop + (availableHeight - scaledHeight) / 2;
        
        // Scale around origin (0,0), then translate to center
        matrix.postScale(initialScale, initialScale);
        matrix.postTranslate(dx, dy);
        
        setImageMatrix(matrix);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        boolean handled = false;
        boolean isZoomed = saveScale > minScale + 0.01f; // Small threshold to account for floating point
        
        // Handle scale gesture first (pinch zoom)
        if (mScaleDetector != null && event.getPointerCount() > 1) {
            handled = mScaleDetector.onTouchEvent(event);
            if (handled) {
                // Prevent parent from intercepting during pinch zoom
                getParent().requestDisallowInterceptTouchEvent(true);
                setImageMatrix(matrix);
                return true;
            }
        }
        
        // Handle double tap gesture - need to see all events for this
        if (mGestureDetector != null) {
            boolean doubleTapHandled = mGestureDetector.onTouchEvent(event);
            if (doubleTapHandled) {
                setImageMatrix(matrix);
                return true;
            }
        }

        PointF curr = new PointF(event.getX(), event.getY());

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                last.set(curr);
                start.set(last);
                if (mode != ZOOM) {
                    if (isZoomed) {
                        mode = DRAG;
                        // Prevent parent from intercepting when zoomed (we need to handle drag)
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    } else {
                        mode = NONE;
                        // When not zoomed, don't consume ACTION_DOWN - let parent handle swipes
                        // This means we won't get events for double-tap, but swipes will work
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(false);
                        }
                        // Return false to let parent handle the swipe gesture
                        return false;
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                mode = ZOOM;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG && event.getPointerCount() == 1 && isZoomed) {
                    // Only handle drag if image is zoomed
                    float deltaX = curr.x - last.x;
                    float deltaY = curr.y - last.y;
                    float fixTransX = getFixDragTrans(deltaX, viewWidth, getImageWidth());
                    float fixTransY = getFixDragTrans(deltaY, viewHeight, getImageHeight());
                    matrix.postTranslate(fixTransX, fixTransY);
                    fixTrans();
                    last.set(curr.x, curr.y);
                    setImageMatrix(matrix);
                    return true;
                } else if (!isZoomed && event.getPointerCount() == 1 && mode == NONE) {
                    // Image is at minimum scale - check if it's a horizontal swipe
                    float deltaX = Math.abs(curr.x - start.x);
                    float deltaY = Math.abs(curr.y - start.y);
                    // If it's primarily a horizontal movement, let parent handle it (swipe)
                    if (deltaX > 20 && deltaX > deltaY * 1.2) {
                        // Horizontal swipe detected - allow parent to intercept and stop handling
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(false);
                        }
                        mode = NONE;
                        // Return false to let parent handle the swipe
                        return false;
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                // Reset parent intercept flag
                getParent().requestDisallowInterceptTouchEvent(false);
                
                if (mode == ZOOM) {
                    mode = NONE;
                } else if (mode == DRAG) {
                    mode = NONE;
                    if (isZoomed) {
                        return true;
                    }
                } else {
                    mode = NONE;
                    // If image is not zoomed and it was a small movement, might be a tap
                    if (!isZoomed) {
                        int xDiff = (int) Math.abs(curr.x - start.x);
                        int yDiff = (int) Math.abs(curr.y - start.y);
                        if (xDiff < 10 && yDiff < 10) {
                            performClick();
                            return true;
                        }
                    }
                }
                break;
        }

        // Only consume if we're actually handling something (zoom or drag when zoomed)
        if (mode == ZOOM) {
            setImageMatrix(matrix);
            return true;
        }
        
        if (mode == DRAG && isZoomed) {
            setImageMatrix(matrix);
            return true;
        }
        
        // When not zoomed and single touch, don't consume - let parent handle swipes
        // But we need to return true on ACTION_DOWN to get events for double-tap detection
        // So we'll handle it in ACTION_MOVE instead
        if (!isZoomed && event.getPointerCount() == 1) {
            // For ACTION_DOWN, return true to get events, but allow parent to intercept on MOVE
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                return true; // Need to see events for double-tap
            }
            // For other actions when not zoomed, return false to let parent handle
            return false;
        }
        
        return false; // Let parent handle swipes when not zoomed
    }

    private void fixTrans() {
        matrix.getValues(m);
        float transX = m[Matrix.MTRANS_X];
        float transY = m[Matrix.MTRANS_Y];

        float fixTransX = getFixTrans(transX, viewWidth, getImageWidth());
        float fixTransY = getFixTrans(transY, viewHeight, getImageHeight());

        if (fixTransX != 0 || fixTransY != 0) {
            matrix.postTranslate(fixTransX, fixTransY);
        }
    }

    private float getFixTrans(float trans, float viewSize, float contentSize) {
        float minTrans, maxTrans;

        if (contentSize <= viewSize) {
            minTrans = 0;
            maxTrans = viewSize - contentSize;
        } else {
            minTrans = viewSize - contentSize;
            maxTrans = 0;
        }

        if (trans < minTrans)
            return -trans + minTrans;
        if (trans > maxTrans)
            return -trans + maxTrans;
        return 0;
    }

    private float getFixDragTrans(float delta, float viewSize, float contentSize) {
        if (contentSize <= viewSize) {
            return 0;
        }
        return delta;
    }

    private float getImageWidth() {
        return getImageWidth(saveScale);
    }

    private float getImageWidth(float scale) {
        if (getDrawable() == null) {
            return 0;
        }
        return getDrawable().getIntrinsicWidth() * scale;
    }

    private float getImageHeight() {
        return getImageHeight(saveScale);
    }

    private float getImageHeight(float scale) {
        if (getDrawable() == null) {
            return 0;
        }
        return getDrawable().getIntrinsicHeight() * scale;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mode = ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float mScaleFactor = detector.getScaleFactor();
            float origScale = saveScale;
            saveScale *= mScaleFactor;
            
            // Clamp to min and max scale
            if (saveScale > maxScale) {
                saveScale = maxScale;
                mScaleFactor = maxScale / origScale;
            } else if (saveScale < minScale) {
                saveScale = minScale;
                mScaleFactor = minScale / origScale;
            }

            matrix.postScale(mScaleFactor, mScaleFactor, detector.getFocusX(), detector.getFocusY());
            fixTrans();
            return true;
        }
        
        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mode = NONE;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (Math.abs(saveScale - minScale) < 0.1f) {
                // Currently at minimum, zoom in
                float targetScale = minScale * DOUBLE_TAP_SCALE;
                if (targetScale > maxScale) {
                    targetScale = maxScale;
                }
                float scaleFactor = targetScale / saveScale;
                matrix.postScale(scaleFactor, scaleFactor, e.getX(), e.getY());
                saveScale = targetScale;
            } else {
                // Zoom out to fit screen
                matrix.reset();
                fitToScreen();
            }
            fixTrans();
            setImageMatrix(matrix);
            return true;
        }
    }
}

