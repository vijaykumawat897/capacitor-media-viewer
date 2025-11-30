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
    private static final int SWIPE = 3;
    private boolean swipeTriggered = false;

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGestureDetector;
    private boolean isInitialFit = true;
    private SwipeListener swipeListener;

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
        // Don't call fitToScreen here - wait for image to load
    }
    
    public void fitToScreenPublic() {
        // Fit to screen - allow re-fitting on orientation changes
        if (getDrawable() != null && viewWidth > 0 && viewHeight > 0) {
            // Reset scale to allow re-fitting
            if (!isInitialFit) {
                saveScale = 1.0f;
                mode = NONE;
            }
            fitToScreen();
            isInitialFit = false;
        }
    }
    
    public void setSwipeListener(SwipeListener listener) {
        this.swipeListener = listener;
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
            // Don't auto-fit in onLayout - wait for explicit call after image loads to prevent flickering
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
        
        // Use fitCenter behavior: fit to screen while maintaining aspect ratio
        // This will show black bars if aspect ratios don't match, but won't crop
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
        boolean isZoomed = saveScale > minScale + 0.01f; // Small threshold to account for floating point
        
        // Always let gesture detectors see events first (for double-tap and pinch)
        // But don't consume the event yet - we'll decide based on what happens
        
        // Handle scale gesture (pinch zoom) - needs to see all events
        boolean scaleHandled = false;
        if (mScaleDetector != null && event.getPointerCount() > 1) {
            scaleHandled = mScaleDetector.onTouchEvent(event);
            if (scaleHandled) {
                // Prevent parent from intercepting during pinch zoom
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                setImageMatrix(matrix);
                return true;
            }
        }
        
        // Handle double tap gesture - needs to see all events
        boolean doubleTapHandled = false;
        if (mGestureDetector != null) {
            doubleTapHandled = mGestureDetector.onTouchEvent(event);
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
                swipeTriggered = false; // Reset swipe flag
                if (mode != ZOOM) {
                    if (isZoomed) {
                        mode = DRAG;
                        // Prevent parent from intercepting when zoomed (we need to handle drag)
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        return true; // Consume when zoomed
                    } else {
                        mode = NONE;
                        // When not zoomed, allow parent to intercept for swipes
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(false);
                        }
                        // Return true to get events for gesture detectors (double-tap, pinch)
                        // But we'll handle swipes ourselves via callback
                        return true;
                    }
                }
                return true;

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
                } else if (!isZoomed && event.getPointerCount() == 1 && mode == NONE && !swipeTriggered) {
                    // Image is at minimum scale - check if it's a horizontal swipe
                    float deltaX = curr.x - start.x;
                    float deltaY = Math.abs(curr.y - start.y);
                    float absDeltaX = Math.abs(deltaX);
                    // If it's primarily a horizontal movement, trigger swipe
                    if (absDeltaX > 50 && absDeltaX > deltaY * 1.5 && swipeListener != null) {
                        // Horizontal swipe detected - trigger parent's swipe handling
                        swipeTriggered = true;
                        mode = SWIPE; // Mark as swipe to prevent further processing
                        if (deltaX > 0) {
                            // Swipe right - previous
                            swipeListener.onSwipeRight();
                        } else {
                            // Swipe left - next
                            swipeListener.onSwipeLeft();
                        }
                        return true; // Consume the event
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                // Reset parent intercept flag
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                
                if (mode == ZOOM) {
                    mode = NONE;
                    swipeTriggered = false;
                    return true;
                } else if (mode == DRAG) {
                    mode = NONE;
                    swipeTriggered = false;
                    if (isZoomed) {
                        return true;
                    }
                } else if (mode == SWIPE) {
                    mode = NONE;
                    swipeTriggered = false;
                    return true; // We handled the swipe
                } else {
                    mode = NONE;
                    swipeTriggered = false;
                }
                return false;
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
        
        // When not zoomed and single touch, let parent handle swipes
        // But we already handled double-tap and pinch above, so those will work
        if (!isZoomed && event.getPointerCount() == 1 && mode == NONE) {
            // For ACTION_UP, check if it was a tap (small movement)
            if (event.getAction() == MotionEvent.ACTION_UP) {
                int xDiff = (int) Math.abs(curr.x - start.x);
                int yDiff = (int) Math.abs(curr.y - start.y);
                if (xDiff < 10 && yDiff < 10) {
                    // Small tap - could be part of double-tap, but gesture detector already handled it
                    return false; // Let parent handle if not double-tap
                }
            }
            return false; // Let parent handle swipes
        }
        
        return false;
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

