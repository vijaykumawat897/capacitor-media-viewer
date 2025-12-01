package com.capacitor.mediaviewer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import com.bumptech.glide.Glide;
import com.capacitor.mediaviewer.R;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MediaViewerFragment extends DialogFragment {

    private static final String ARG_ITEMS = "items";
    private static final String ARG_CURRENT_INDEX = "currentIndex";
    private static final String ARG_TITLE = "title";

    private List<MediaItem> mediaItems;
    private int currentIndex;
    private String title;
    private MediaViewerListener listener;

    private FrameLayout videoContainer;
    private FrameLayout overlayContainer;
    private TextureView textureView;
    private TouchImageView mediaImageView;
    private ImageView videoThumbnail;
    private ExoPlayer exoPlayer;
    private Surface videoSurface;
    private GestureDetector gestureDetector;
    private Handler playbackHandler;
    private Runnable playbackRunnable;
    private String currentQuality = "Auto"; // Default to Auto
    private String actualPlayingQuality = null; // Track actual quality when Auto is selected
    private boolean playbackEnded = false;

    // Custom controls
    private LinearLayout controlsContainer;
    private ImageView closeButton;
    private ImageView playPauseButton;
    private ImageView qualityButton;
    private SeekBar seekBar;
    private TextView currentTimeText;
    private TextView durationText;
    private boolean controlsVisible = true; // Start visible
    private Runnable hideControlsRunnable;

    // Swipe animation state
    private ViewGroup rootView;
    private FrameLayout currentMediaContainer;
    private FrameLayout nextMediaContainer;
    private boolean isSwiping = false;
    private float swipeStartX = 0;
    private float swipeTotalDistance = 0;
    private int swipeDirection = 0; // -1 for left (next), 1 for right (previous)
    private ObjectAnimator currentSwipeAnimator;
    private ViewTreeObserver.OnGlobalLayoutListener layoutListener;
    private int lastContainerWidth = 0;
    private int lastContainerHeight = 0;
    private List<QualityVariant> qualityVariants = new ArrayList<>();
    private String currentVideoUrl = null;
    private int lastVideoWidth = 0;
    private int lastVideoHeight = 0;
    private float lastPixelRatio = 1f;

    public static MediaViewerFragment newInstance(List<MediaItem> items, int currentIndex, String title, MediaViewerListener listener) {
        MediaViewerFragment fragment = new MediaViewerFragment();
        fragment.mediaItems = items;
        fragment.currentIndex = currentIndex;
        fragment.title = title;
        fragment.listener = listener;
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        playbackHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onStart() {
        super.onStart();
        // Ensure the dialog takes full screen and hide navigation bar
        hideSystemUI();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Ensure full screen on resume (handles orientation changes) and hide navigation bar
        hideSystemUI();
    }

    private void hideSystemUI() {
        if (getDialog() == null || getDialog().getWindow() == null) {
            return;
        }

        Window window = getDialog().getWindow();

        // Set full screen layout
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        // Hide navigation bar
        View decorView = window.getDecorView();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // API 30+ (Android 11+)
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            // API < 30
            int uiOptions =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }

        // Set window flags to extend behind system bars
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.mediaviewer_fragment, container, false);
        this.rootView = (ViewGroup) view;
        initializeLayout(view);
        setupGestureDetector();
        setupOverlayTouchHandling();

        displayCurrentMedia();

        return view;
    }

    private void initializeLayout(View rootView) {
        videoContainer = rootView.findViewById(R.id.video_container);
        mediaImageView = rootView.findViewById(R.id.media_image);
        if (mediaImageView != null) {
            // Set swipe listener so image view can trigger swipes when not zoomed
            mediaImageView.setSwipeListener(
                new com.capacitor.mediaviewer.SwipeListener() {
                    @Override
                    public void onSwipeLeft() {
                        // Swipe left - next (use existing swipe animation system)
                        if (currentIndex < mediaItems.size() - 1 && !isSwiping) {
                            startSwipeAnimation(-1, 0); // -1 = left (next)
                        }
                    }

                    @Override
                    public void onSwipeRight() {
                        // Swipe right - previous (use existing swipe animation system)
                        if (currentIndex > 0 && !isSwiping) {
                            startSwipeAnimation(1, 0); // 1 = right (previous)
                        }
                    }
                }
            );
        }
        videoThumbnail = rootView.findViewById(R.id.video_thumbnail);
        overlayContainer = rootView.findViewById(R.id.overlay_container);
        controlsContainer = rootView.findViewById(R.id.controls_container);
        closeButton = rootView.findViewById(R.id.close_button);
        playPauseButton = rootView.findViewById(R.id.play_pause_button);
        qualityButton = rootView.findViewById(R.id.quality_button);
        seekBar = rootView.findViewById(R.id.seek_bar);
        currentTimeText = rootView.findViewById(R.id.current_time_text);
        durationText = rootView.findViewById(R.id.duration_text);

        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dismiss());
        }

        // Set up the current media container reference
        currentMediaContainer = (FrameLayout) rootView.findViewById(R.id.video_container);

        setupTextureView();

        initializeControlListeners();
    }

    private void setupTextureView() {
        if (videoContainer == null) return;

        // If an existing TextureView is already added to container, keep it
        if (textureView != null && textureView.getParent() == videoContainer) {
            return;
        }

        // Remove old textureView if present (defensive)
        if (textureView != null && textureView.getParent() != null) {
            ((ViewGroup) textureView.getParent()).removeView(textureView);
        }

        textureView = new TextureView(requireContext());
        textureView.setOpaque(false);
        textureView.setClickable(false);
        textureView.setFocusable(false);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        params.gravity = android.view.Gravity.CENTER;
        textureView.setLayoutParams(params);

        // Reset all transforms and positioning to ensure proper centering
        textureView.setX(0f);
        textureView.setY(0f);
        textureView.setScaleX(1f);
        textureView.setScaleY(1f);
        
        // Initialize transform to identity just in case
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        textureView.setTransform(matrix);

        // Add TextureView at index 0 (behind thumbnail)
        videoContainer.addView(textureView, 0);
    }

    private void initializeControlListeners() {
        if (qualityButton != null) {
            qualityButton.setOnClickListener(v -> showQualitySelector());
            // Initially disable until we check if it's HLS
            qualityButton.setEnabled(false);
            qualityButton.setAlpha(0.5f);
        }

        if (playPauseButton != null) {
            playPauseButton.setOnClickListener(v -> togglePlayPause());
        }

        if (seekBar != null) {
            seekBar.setMax(1000);
            seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser && exoPlayer != null) {
                            long duration = exoPlayer.getDuration();
                            if (duration > 0) {
                                long position = (long) (progress * duration / 1000.0);
                                updateCurrentTimeText(position);
                            }
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        scheduleControlsHide();
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        if (exoPlayer != null) {
                            long duration = exoPlayer.getDuration();
                            if (duration > 0) {
                                long position = (long) (seekBar.getProgress() * duration / 1000.0);
                                exoPlayer.seekTo(position);
                            }
                        }
                        scheduleControlsHide();
                    }
                }
            );
        }

        if (controlsContainer != null) {
            controlsContainer.setVisibility(View.VISIBLE);
            controlsVisible = true;
        }
    }

    private void setupGestureDetector() {
        gestureDetector =
            new GestureDetector(
                requireContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    private static final int SWIPE_THRESHOLD = 100;
                    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

                    @Override
                    public boolean onDown(MotionEvent e) {
                        if (isSwiping) {
                            cancelCurrentSwipe();
                        }
                        return true;
                    }

                    @Override
                    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                        if (e1 == null || e2 == null) {
                            return false;
                        }

                        // If already swiping, update progress
                        if (isSwiping) {
                            swipeTotalDistance -= distanceX; // distanceX is negative when scrolling right
                            updateSwipeProgress(swipeTotalDistance);
                            return true;
                        }

                        float diffX = e2.getX() - e1.getX();
                        float diffY = e2.getY() - e1.getY();

                        // Only handle horizontal swipes
                        if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 50) {
                            // Don't swipe if user is interacting with controls
                            if (controlsContainer != null && controlsContainer.getVisibility() == View.VISIBLE) {
                                int[] location = new int[2];
                                controlsContainer.getLocationOnScreen(location);
                                float tapY = e1.getY();
                                if (tapY >= location[1] - 100) {
                                    return false;
                                }
                            }

                            // Determine swipe direction
                            int direction = diffX > 0 ? 1 : -1; // 1 = right (previous), -1 = left (next)

                            // Check if we can swipe in that direction
                            if ((direction > 0 && currentIndex > 0) || (direction < 0 && currentIndex < mediaItems.size() - 1)) {
                                swipeTotalDistance = diffX;
                                startSwipeAnimation(direction, diffX);
                                return true;
                            }
                        }
                        return false;
                    }

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                        if (e1 == null || e2 == null) return false;

                        float diffX = e2.getX() - e1.getX();
                        float diffY = e2.getY() - e1.getY();

                        // Only handle horizontal swipes (ignore if controls are being interacted with)
                        if (
                            Math.abs(diffX) > Math.abs(diffY) &&
                            Math.abs(diffX) > SWIPE_THRESHOLD &&
                            Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                        ) {
                            // Don't swipe if user is interacting with controls
                            if (controlsContainer != null && controlsContainer.getVisibility() == View.VISIBLE) {
                                int[] location = new int[2];
                                controlsContainer.getLocationOnScreen(location);
                                float tapY = e1.getY();
                                // Check if swipe started in controls area
                                if (tapY >= location[1] - 100) { // Add some margin
                                    return false;
                                }
                            }

                            if (isSwiping) {
                                // Complete the swipe animation
                                completeSwipeAnimation(velocityX);
                                return true;
                            }

                            if (diffX > 0) {
                                // Swipe right - previous
                                startSwipeAnimation(1, 0); // 1 = right
                            } else {
                                // Swipe left - next
                                startSwipeAnimation(-1, 0); // -1 = left
                            }
                            return true;
                        } else if (isSwiping) {
                            // Cancel swipe if it doesn't meet threshold
                            cancelCurrentSwipe();
                        }
                        return false;
                    }

                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        // Check if tap is on video container or controls
                        if (textureView != null && textureView.getVisibility() == View.VISIBLE) {
                            // Check if tap is on controls container - don't toggle in that case
                            if (controlsContainer != null && controlsContainer.getVisibility() == View.VISIBLE) {
                                int[] location = new int[2];
                                controlsContainer.getLocationOnScreen(location);
                                int x = location[0];
                                int y = location[1];
                                int width = controlsContainer.getWidth();
                                int height = controlsContainer.getHeight();
                                int tapX = (int) e.getRawX();
                                int tapY = (int) e.getRawY();

                                // If tap is within controls bounds, don't toggle
                                if (tapX >= x && tapX <= x + width && tapY >= y && tapY <= y + height) {
                                    return false; // Let controls handle the tap
                                }
                            }
                            // Toggle controls visibility for video
                            toggleControls();
                            return true;
                        }
                        // For images, do nothing - only close button can dismiss
                        return false;
                    }
                }
            );

        // Enable long press to toggle controls (optional)
        gestureDetector.setIsLongpressEnabled(false);
    }

    private void setupOverlayTouchHandling() {
        if (overlayContainer == null) {
            return;
        }

        overlayContainer.setOnTouchListener((v, event) -> {
            // If image is visible, we need to handle swipes even though image view consumes ACTION_DOWN
            if (mediaImageView != null && mediaImageView.getVisibility() == View.VISIBLE) {
                // Only handle touches on close button or controls
                if (closeButton != null && isPointInsideView(closeButton, event)) {
                    return false; // Let close button handle it
                }
                if (controlsContainer != null && isPointInsideView(controlsContainer, event)) {
                    return false; // Let controls handle it
                }
                // For images, we need to intercept touch events to handle swipes
                // The image view consumes ACTION_DOWN, but we can still detect swipes on MOVE
                return handleOverlayTouchForImage(event);
            }
            return handleOverlayTouch(event);
        });

        // Make overlay not clickable when image is visible to allow touch events to pass through
        updateOverlayClickable();
    }

    private void updateOverlayClickable() {
        if (overlayContainer == null) return;
        boolean imageVisible = mediaImageView != null && mediaImageView.getVisibility() == View.VISIBLE;
        overlayContainer.setClickable(!imageVisible);
        overlayContainer.setFocusable(!imageVisible);
    }

    private boolean handleOverlayTouchForImage(MotionEvent event) {
        // Special handling for images - detect horizontal swipes even if image view consumed ACTION_DOWN
        if (gestureDetector != null) {
            // Try to handle with gesture detector - it might work for fling gestures
            boolean handled = gestureDetector.onTouchEvent(event);
            if (handled) {
                return true;
            }
        }

        // For MOVE events, check if it's a horizontal swipe
        if (event.getAction() == MotionEvent.ACTION_MOVE && event.getPointerCount() == 1) {
            // We can't easily detect swipe here since we don't have the start position
            // But gesture detector's onScroll might work
            return false;
        }

        return false;
    }

    private boolean handleOverlayTouch(MotionEvent event) {
        // If touch is within controls or close button, let them handle it
        if (controlsContainer != null && controlsContainer.getVisibility() == View.VISIBLE && isPointInsideView(controlsContainer, event)) {
            if (isSwiping && event.getAction() == MotionEvent.ACTION_UP) {
                cancelCurrentSwipe();
            }
            return false;
        }
        if (closeButton != null && closeButton.getVisibility() == View.VISIBLE && isPointInsideView(closeButton, event)) {
            if (isSwiping && event.getAction() == MotionEvent.ACTION_UP) {
                cancelCurrentSwipe();
            }
            return false;
        }

        // If image is visible, let it handle touch events (for zoom)
        if (mediaImageView != null && mediaImageView.getVisibility() == View.VISIBLE) {
            // Only handle swipe gestures, let image view handle zoom
            if (isSwiping && event.getAction() == MotionEvent.ACTION_UP) {
                completeSwipeAnimation(0);
                return true;
            }
            // For images, only handle horizontal swipes, let image view handle everything else
            if (gestureDetector != null && event.getPointerCount() == 1) {
                // Check if it's a horizontal swipe
                return gestureDetector.onTouchEvent(event);
            }
            // Let image view handle multi-touch (pinch zoom)
            return false;
        }

        // Handle swipe completion on finger release
        if (isSwiping && event.getAction() == MotionEvent.ACTION_UP) {
            completeSwipeAnimation(0);
            return true;
        }

        if (gestureDetector != null) {
            boolean handled = gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // Ensure we keep receiving subsequent events
                return true;
            }
            return handled;
        }
        return false;
    }

    private boolean isPointInsideView(View view, MotionEvent event) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= location[0] && x <= location[0] + view.getWidth() && y >= location[1] && y <= location[1] + view.getHeight();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Gestures are handled by the gesture detector on the container

        // Add layout listener to handle orientation changes
        setupLayoutListener();
    }

    private void setupLayoutListener() {
        if (rootView == null) {
            return;
        }

        layoutListener =
            new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (rootView == null) {
                        return;
                    }

                    int currentWidth = rootView.getWidth();
                    int currentHeight = rootView.getHeight();

                    // Check if size actually changed
                    if (currentWidth != lastContainerWidth || currentHeight != lastContainerHeight) {
                        lastContainerWidth = currentWidth;
                        lastContainerHeight = currentHeight;

                        // Update video aspect ratio if video is playing
                        if (exoPlayer != null && textureView != null && textureView.getVisibility() == View.VISIBLE) {
                            if (exoPlayer.getVideoSize().width > 0 && exoPlayer.getVideoSize().height > 0) {
                                updateTextureViewAspectRatio(exoPlayer.getVideoSize().width, exoPlayer.getVideoSize().height);
                            }
                        }

                        // Re-fit image if image is displayed
                        if (mediaImageView != null && mediaImageView.getVisibility() == View.VISIBLE) {
                            mediaImageView.post(() -> {
                                if (
                                    mediaImageView.getDrawable() != null && mediaImageView.getWidth() > 0 && mediaImageView.getHeight() > 0
                                ) {
                                    mediaImageView.fitToScreenPublic();
                                }
                            });
                        }
                    }
                }
            };

        rootView.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
    }

    private void displayCurrentMedia() {
        if (mediaItems == null || currentIndex < 0 || currentIndex >= mediaItems.size()) {
            return;
        }

        releasePlayer();
        resetMediaViews();

        MediaItem item = mediaItems.get(currentIndex);

        // Reset quality to Auto for new video
        if ("VIDEO".equals(item.type)) {
            currentQuality = "Auto";
            actualPlayingQuality = null;
            currentVideoUrl = item.path;

            // Parse HLS playlist if it's an HLS video
            if (HlsPlaylistParser.isHlsUrl(item.path)) {
                // Parse quality variants on a background thread
                new Thread(() -> {
                    List<QualityVariant> variants = HlsPlaylistParser.parseMasterPlaylist(item.path);
                    requireActivity()
                        .runOnUiThread(() -> {
                            qualityVariants = variants;
                            // Enable/disable quality button based on whether variants are available
                            if (qualityButton != null) {
                                qualityButton.setEnabled(variants.size() > 0);
                                qualityButton.setAlpha(variants.size() > 0 ? 1.0f : 0.5f);
                            }
                        });
                })
                    .start();
            } else {
                qualityVariants.clear();
                if (qualityButton != null) {
                    qualityButton.setEnabled(false);
                    qualityButton.setAlpha(0.5f);
                }
            }
        }

        if ("VIDEO".equals(item.type)) {
            displayVideo(item);
        } else {
            displayImage(item);
        }

        if (listener != null) {
            listener.onMediaIndexChanged(currentIndex);
        }
    }

    private void resetMediaViews() {
        if (textureView != null) {
            textureView.setSurfaceTextureListener(null);
            textureView.setAlpha(0f);
        }
        if (mediaImageView != null) {
            mediaImageView.setImageDrawable(null);
            mediaImageView.setVisibility(View.GONE);
        }
        if (videoThumbnail != null) {
            videoThumbnail.setImageDrawable(null);
            videoThumbnail.setVisibility(View.GONE);
        }
    }

    @UnstableApi
    private void displayVideo(MediaItem item) {
        if (textureView == null) {
            return;
        }

        // Keep TextureView visible for surface to work, but thumbnail will cover it
        textureView.setVisibility(View.VISIBLE);
        // Set alpha to 0 initially so it's transparent until video starts
        textureView.setAlpha(0f);
        if (mediaImageView != null) {
            mediaImageView.setVisibility(View.GONE);
        }

        // Show thumbnail if available, otherwise keep black background
        if (videoThumbnail != null) {
            Log.d("MediaViewerFragment", "videoThumbnail: " + videoThumbnail.toString() + "item.thumbnail: " + item.thumbnail);
            if (item.thumbnail != null && !item.thumbnail.isEmpty()) {
                videoThumbnail.setVisibility(View.VISIBLE);
                Glide.with(this).load(item.thumbnail).into(videoThumbnail);
            } else {
                videoThumbnail.setVisibility(View.GONE);
            }
        }

        if (controlsContainer != null) {
            controlsContainer.setVisibility(View.VISIBLE);
            controlsVisible = true;
        }

        TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull android.graphics.SurfaceTexture surface, int width, int height) {
                preparePlayerWithSurface(item, surface);
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull android.graphics.SurfaceTexture surface, int width, int height) {
                // Don't update aspect ratio here - use video size from ExoPlayer instead
                // This callback gives surface texture size, not video size
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull android.graphics.SurfaceTexture surface) {
                if (exoPlayer != null) {
                    exoPlayer.clearVideoSurface();
                }
                releaseVideoSurface();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull android.graphics.SurfaceTexture surface) {
                // No-op
            }
        };

        textureView.setSurfaceTextureListener(textureListener);

        if (textureView.isAvailable()) {
            preparePlayerWithSurface(item, textureView.getSurfaceTexture());
        }
    }

    private void preparePlayerWithSurface(MediaItem item, @Nullable android.graphics.SurfaceTexture surfaceTexture) {
        preparePlayerWithSurface(item, surfaceTexture, 0L, true);
    }

    private void preparePlayerWithSurface(
        MediaItem item,
        @Nullable android.graphics.SurfaceTexture surfaceTexture,
        long startPositionMs,
        boolean playWhenReady
    ) {
        if (surfaceTexture == null) {
            return;
        }
        Surface surface = new Surface(surfaceTexture);
        releaseVideoSurface();
        videoSurface = surface;
        setupExoPlayer(surface, item, startPositionMs, playWhenReady);
    }

    private void releaseVideoSurface() {
        if (videoSurface != null) {
            videoSurface.release();
            videoSurface = null;
        }
    }

    @UnstableApi
    private void setupExoPlayer(Surface surface, MediaItem item) {
        setupExoPlayer(surface, item, 0L, true);
    }

    @UnstableApi
    private void setupExoPlayer(Surface surface, MediaItem item, long startPositionMs, boolean playWhenReady) {
        // Release existing player if any
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }

        // Set up ExoPlayer
        exoPlayer = new ExoPlayer.Builder(requireContext()).build();

        // Set up player listeners
        exoPlayer.addListener(
            new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    updatePlaybackState();
                    Log.d("MediaViewerFragment", "onPlaybackStateChanged: " + playbackState);
                    if (playbackState == Player.STATE_ENDED) {
                        // Playback has ended
                        playbackEnded = true;
                        updatePlayPauseButton(false);
                        showControls(); // Show controls when playback ends
                        // Update current quality being played if in auto mode
                        updateAutoQuality();
                    } else if (playbackState == Player.STATE_READY) {
                        Log.d("MediaViewerFragment", "onPlaybackStateChanged: STATE_READY");
                        playbackEnded = false;
                        long duration = exoPlayer.getDuration();
                        if (duration > 0) {
                            seekBar.setMax(1000); // Use 1000 for percentage-based seeking
                            updateDurationText(duration);
                        }

                        // Update video aspect ratio when ready
                        if (exoPlayer.getVideoSize().width > 0 && exoPlayer.getVideoSize().height > 0) {
                            Log.d(
                                "MediaViewerFragment",
                                "onPlaybackStateChanged: STATE_READY: Video size changed to: " +
                                exoPlayer.getVideoSize().width +
                                "x" +
                                exoPlayer.getVideoSize().height
                            );
                            updateTextureViewAspectRatio(exoPlayer.getVideoSize().width, exoPlayer.getVideoSize().height);
                            // Update auto quality detection when video is ready
                            if ("Auto".equals(currentQuality)) {
                                updateAutoQuality();
                            }
                        }

                        // Fade in TextureView and hide thumbnail when video is ready and playing
                        // Use a small delay to ensure we have actual video frames
                        if (exoPlayer.isPlaying() && textureView != null && textureView.getAlpha() < 1f) {
                            playbackHandler.postDelayed(
                                () -> {
                                    if (exoPlayer != null && exoPlayer.isPlaying() && textureView != null) {
                                        textureView.animate().alpha(1f).setDuration(200).start();
                                        if (videoThumbnail != null && videoThumbnail.getVisibility() == View.VISIBLE) {
                                            videoThumbnail.setVisibility(View.GONE);
                                        }
                                    }
                                },
                                150
                            ); // Small delay to skip the first frame
                        }

                        // Update current quality being played if in auto mode
                        updateAutoQuality();
                    } else {
                        playbackEnded = false;
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    updatePlaybackState();
                    Log.d("MediaViewerFragment", "onIsPlayingChanged: " + isPlaying);
                    updatePlayPauseButton(isPlaying);

                    // Fade in TextureView and hide thumbnail when playback actually starts
                    // Use a small delay to ensure we have actual video frames, not just the first frame
                    if (isPlaying && textureView != null && textureView.getAlpha() < 1f) {
                        playbackHandler.postDelayed(
                            () -> {
                                if (exoPlayer != null && exoPlayer.isPlaying() && textureView != null) {
                                    textureView.animate().alpha(1f).setDuration(200).start();
                                    if (videoThumbnail != null && videoThumbnail.getVisibility() == View.VISIBLE) {
                                        videoThumbnail.setVisibility(View.GONE);
                                    }
                                }
                            },
                            150
                        ); // Small delay to skip the first frame
                    }
                }

                @Override
                public void onTracksChanged(Tracks tracks) {
                    // Track selection changed - update quality detection for auto mode
                    if ("Auto".equals(currentQuality)) {
                        Log.d("MediaViewerFragment", "Tracks changed, updating quality");
                        // Check multiple times to catch quality changes during adaptive streaming
                        playbackHandler.postDelayed(() -> updateAutoQuality(), 300);
                        playbackHandler.postDelayed(() -> updateAutoQuality(), 800);
                        playbackHandler.postDelayed(() -> updateAutoQuality(), 1500);
                    }
                }

                @Override
                public void onVideoSizeChanged(androidx.media3.common.VideoSize videoSize) {
                    // Video size changed - this happens when quality changes
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        Log.d("MediaViewerFragment", "Video size changed to: " + videoSize.width + "x" + videoSize.height);
                        // Update TextureView aspect ratio to match new video size immediately
                        // Use post to ensure we're on UI thread and layout is ready
                        if (textureView != null) {
                            textureView.post(() -> {
                                if (textureView != null && exoPlayer != null) {
                                    updateTextureViewAspectRatio(videoSize.width, videoSize.height);
                                }
                            });
                        }

                        // Check quality if in Auto mode
                        if ("Auto".equals(currentQuality)) {
                            // Check quality immediately and again after a delay to catch changes
                            updateAutoQuality();
                            playbackHandler.postDelayed(() -> updateAutoQuality(), 500);
                        }
                    }
                }
            }
        );

        
        // Ensure the surface is properly attached
        if (surface != null && surface.isValid()) {
            exoPlayer.setVideoSurface(surface);
        }

        androidx.media3.common.MediaItem mediaItem = androidx.media3.common.MediaItem.fromUri(item.path);
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        if (startPositionMs > 0) {
            exoPlayer.seekTo(startPositionMs);
        }
        exoPlayer.setPlayWhenReady(playWhenReady);

        // If HLS and no quality variants yet, parse them in background
        if ((item.qualityVariants == null || item.qualityVariants.isEmpty()) && HlsPlaylistParser.isHlsUrl(item.path)) {
            new Thread(() -> {
                List<QualityVariant> variants = HlsPlaylistParser.parseMasterPlaylist(item.path);
                if (variants != null && !variants.isEmpty()) {
                    requireActivity()
                        .runOnUiThread(() -> {
                            item.qualityVariants = variants;
                        });
                }
            })
                .start();
        }

        // Show controls initially, then auto-hide
        showControls();
        scheduleControlsHide();

        // Start monitoring playback state
        startPlaybackStateMonitoring();
    }

    private void showQualitySelector() {
        MediaItem currentItem = mediaItems.get(currentIndex);
        if (currentItem == null || currentItem.qualityVariants == null || currentItem.qualityVariants.isEmpty()) {
            // No quality variants available
            return;
        }

        // Create quality labels array with "Auto" as first option
        String[] qualityLabels = new String[currentItem.qualityVariants.size() + 1];
        qualityLabels[0] = "Auto";
        for (int i = 0; i < currentItem.qualityVariants.size(); i++) {
            qualityLabels[i + 1] = currentItem.qualityVariants.get(i).label;
        }

        // Build display labels (show actual quality next to Auto if available)
        String[] displayLabels = new String[qualityLabels.length];
        if (currentQuality == null || "Auto".equals(currentQuality)) {
            if (actualPlayingQuality != null && !actualPlayingQuality.isEmpty()) {
                displayLabels[0] = "Auto (" + actualPlayingQuality + ")";
            } else {
                displayLabels[0] = "Auto";
            }
        } else {
            displayLabels[0] = "Auto";
        }
        for (int i = 0; i < currentItem.qualityVariants.size(); i++) {
            displayLabels[i + 1] = qualityLabels[i + 1];
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, displayLabels) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);
                textView.setTextColor(Color.WHITE);

                // Highlight current selection
                String quality = qualityLabels[position];
                if (
                    quality.equals(currentQuality) || (quality.equals("Auto") && (currentQuality == null || currentQuality.equals("Auto")))
                ) {
                    textView.setTextColor(Color.parseColor("#4CAF50")); // Green for selected
                }

                return view;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Select Quality");
        builder.setAdapter(
            adapter,
            (dialog, which) -> {
                String selectedQuality = qualityLabels[which];
                setQuality(selectedQuality);
            }
        );
        builder.show();
    }


    private void updateTextureViewAspectRatio(int videoWidth, int videoHeight) {
        if (videoWidth <= 0 || videoHeight <= 0 || textureView == null || videoContainer == null) {
            return;
        }

        // Get pixel aspect ratio from ExoPlayer if available (fallback to 1f)
        float pixelAspectRatio = 1f;
        try {
            androidx.media3.common.VideoSize vs = exoPlayer != null ? exoPlayer.getVideoSize() : null;
            if (vs != null && vs.pixelWidthHeightRatio > 0f) {
                pixelAspectRatio = vs.pixelWidthHeightRatio;
            }
        } catch (Exception ignored) {}

        // If nothing changed, avoid relayout thrash
        if (videoWidth == lastVideoWidth && videoHeight == lastVideoHeight && Math.abs(pixelAspectRatio - lastPixelRatio) < 0.0001f) {
            return;
        }
        lastVideoWidth = videoWidth;
        lastVideoHeight = videoHeight;
        lastPixelRatio = pixelAspectRatio;

        final float videoAspect = (videoWidth * pixelAspectRatio) / (float) videoHeight;

        // Post onto UI thread to make sure container is measured
        textureView.post(() -> {
            int containerW = videoContainer.getWidth();
            int containerH = videoContainer.getHeight();

            if (containerW == 0 || containerH == 0) {
                // container not laid out yet; try again shortly
                textureView.postDelayed(() -> updateTextureViewAspectRatio(videoWidth, videoHeight), 50);
                return;
            }

            float containerAspect = containerW / (float) containerH;

            int newW, newH;
            if (containerAspect > videoAspect) {
                // container is wider => match height
                newH = containerH;
                newW = Math.round(containerH * videoAspect);
            } else {
                // container is taller => match width
                newW = containerW;
                newH = Math.round(containerW / videoAspect);
            }

            // Apply new layout params (wrap_content in xml/code allows this)
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) textureView.getLayoutParams();
            lp.width = newW;
            lp.height = newH;
            lp.gravity = android.view.Gravity.CENTER;
            textureView.setLayoutParams(lp);

            // Reset any translation/positioning - let gravity handle centering
            textureView.setX(0f);
            textureView.setY(0f);

            // Reset any scaling transforms to avoid distortion
            textureView.setScaleX(1f);
            textureView.setScaleY(1f);
            
            // Ensure transform matrix is identity (no scaling/translation)
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            textureView.setTransform(matrix);
        });
    }

    private void updateAutoQuality() {
        // Update actual playing quality when in Auto mode
        if (exoPlayer == null || !"Auto".equals(currentQuality)) {
            return;
        }

        try {
            // First, try to get resolution from the currently selected track (most accurate)
            int width = 0;
            int height = 0;
            String selectedTrackId = null;

            Tracks tracks = exoPlayer.getCurrentTracks();
            if (tracks != null && tracks.getGroups().size() > 0) {
                for (Tracks.Group trackGroup : tracks.getGroups()) {
                    if (trackGroup.getType() == C.TRACK_TYPE_VIDEO && trackGroup.length > 0) {
                        for (int i = 0; i < trackGroup.length; i++) {
                            if (trackGroup.isTrackSelected(i)) {
                                androidx.media3.common.Format format = trackGroup.getTrackFormat(i);
                                if (format.width > 0 && format.height > 0) {
                                    width = format.width;
                                    height = format.height;
                                    selectedTrackId = format.id != null ? format.id : "";
                                    Log.d(
                                        "MediaViewerFragment",
                                        "Selected track: " +
                                        width +
                                        "x" +
                                        height +
                                        " (ID: " +
                                        selectedTrackId +
                                        ", bitrate: " +
                                        format.bitrate +
                                        ")"
                                    );
                                    break;
                                }
                            }
                        }
                        if (width > 0 && height > 0) {
                            break;
                        }
                    }
                }
            }

            // Fallback to ExoPlayer's video size if track info not available
            if (width == 0 || height == 0) {
                width = exoPlayer.getVideoSize().width;
                height = exoPlayer.getVideoSize().height;
                if (width > 0 && height > 0) {
                    Log.d("MediaViewerFragment", "Using video size fallback: " + width + "x" + height);
                }
            }

            if (width > 0 && height > 0) {
                MediaItem currentItem = mediaItems.get(currentIndex);
                // Use qualityVariants from MediaItem, or fallback to instance variable
                List<QualityVariant> variantsToUse = null;
                if (currentItem != null && currentItem.qualityVariants != null && !currentItem.qualityVariants.isEmpty()) {
                    variantsToUse = currentItem.qualityVariants;
                } else if (qualityVariants != null && !qualityVariants.isEmpty()) {
                    variantsToUse = qualityVariants;
                }

                if (variantsToUse != null && !variantsToUse.isEmpty()) {
                    // Try to match video size with quality variants
                    String detectedQuality = detectQualityFromSize(width, height, variantsToUse);
                    if (detectedQuality != null) {
                        if (!detectedQuality.equals(actualPlayingQuality)) {
                            String oldQuality = actualPlayingQuality != null ? actualPlayingQuality : "none";
                            actualPlayingQuality = detectedQuality;
                            Log.d(
                                "MediaViewerFragment",
                                "Auto quality changed: " +
                                oldQuality +
                                " -> " +
                                detectedQuality +
                                " (Resolution: " +
                                width +
                                "x" +
                                height +
                                ", TrackID: " +
                                selectedTrackId +
                                ")"
                            );
                        }
                    } else {
                        Log.d(
                            "MediaViewerFragment",
                            "Could not detect quality for resolution: " + width + "x" + height + " (TrackID: " + selectedTrackId + ")"
                        );
                    }
                } else {
                    Log.d("MediaViewerFragment", "No quality variants available for matching");
                }
            } else {
                Log.d("MediaViewerFragment", "No valid video dimensions available");
            }
        } catch (Exception e) {
            // Ignore errors in quality detection
            Log.e("MediaViewerFragment", "Error detecting quality: " + e.getMessage(), e);
        }
    }

    private String detectQualityFromSize(int width, int height, List<QualityVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            Log.d("MediaViewerFragment", "No variants to match against");
            return null;
        }

        Log.d("MediaViewerFragment", "Detecting quality for " + width + "x" + height + " from " + variants.size() + " variants");

        // First, try to match by extracting actual resolution from variant labels
        // and comparing directly with the detected resolution
        QualityVariant bestMatch = null;
        int minHeightDiff = Integer.MAX_VALUE;
        int totalPixels = width * height;

        // Try exact or very close height match first
        for (QualityVariant variant : variants) {
            String label = variant.label;
            int variantHeight = extractHeightFromLabel(label);

            if (variantHeight > 0) {
                // Match by height (more accurate for standard resolutions)
                int heightDiff = Math.abs(variantHeight - height);
                if (heightDiff < minHeightDiff) {
                    minHeightDiff = heightDiff;
                    bestMatch = variant;
                }

                // If we find an exact or very close match (within 10 pixels), use it immediately
                if (heightDiff <= 10) {
                    Log.d("MediaViewerFragment", "Found exact/close height match: " + label + " (" + variantHeight + "p)");
                    return variant.label;
                }
            }
        }

        // If we found a reasonable match (within 50 pixels), use it
        if (bestMatch != null && minHeightDiff <= 50) {
            Log.d("MediaViewerFragment", "Found reasonable height match: " + bestMatch.label + " (diff: " + minHeightDiff + ")");
            return bestMatch.label;
        }

        // Fallback: match by pixel count and height ranges (for non-standard labels or when height matching isn't precise)
        for (QualityVariant variant : variants) {
            String label = variant.label.toLowerCase();

            // Check for common patterns with tighter ranges
            if (label.contains("4k") || label.contains("2160")) {
                if (height >= 2000 && totalPixels >= 3500000) { // 4K: 3840x2160 or close
                    Log.d("MediaViewerFragment", "Matched 4K pattern: " + variant.label);
                    return variant.label;
                }
            } else if (label.contains("1080") || label.contains("full hd")) {
                if (height >= 1000 && height < 2000 && totalPixels >= 1800000 && totalPixels < 3500000) {
                    Log.d("MediaViewerFragment", "Matched 1080p pattern: " + variant.label);
                    return variant.label;
                }
            } else if (label.contains("720") || label.contains("hd")) {
                if (height >= 650 && height < 1000 && totalPixels >= 800000 && totalPixels < 1800000) {
                    Log.d("MediaViewerFragment", "Matched 720p pattern: " + variant.label);
                    return variant.label;
                }
            } else if (label.contains("480") || label.contains("sd")) {
                if (height >= 400 && height < 650 && totalPixels >= 300000 && totalPixels < 800000) {
                    Log.d("MediaViewerFragment", "Matched 480p pattern: " + variant.label);
                    return variant.label;
                }
            } else if (label.contains("360")) {
                if (height >= 300 && height < 400 && totalPixels >= 100000 && totalPixels < 300000) {
                    Log.d("MediaViewerFragment", "Matched 360p pattern: " + variant.label);
                    return variant.label;
                }
            }
        }

        // Last resort: return the variant closest in height (even if difference is larger)
        if (bestMatch != null) {
            Log.d("MediaViewerFragment", "Using best match by height: " + bestMatch.label + " (diff: " + minHeightDiff + ")");
            return bestMatch.label;
        }

        Log.d("MediaViewerFragment", "Could not match any variant");
        return null;
    }

    private int extractHeightFromLabel(String label) {
        // Extract height value from labels like "720p", "1080p", "2160p", etc.
        label = label.toLowerCase().trim();

        // Try to find a number followed by 'p' (e.g., "720p", "1080p")
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)\\s*p");
        java.util.regex.Matcher matcher = pattern.matcher(label);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        // Try to find just a number (might be height)
        pattern = java.util.regex.Pattern.compile("\\b(\\d{3,4})\\b");
        matcher = pattern.matcher(label);
        if (matcher.find()) {
            try {
                int value = Integer.parseInt(matcher.group(1));
                // Common video heights: 360, 480, 720, 1080, 2160
                if (value == 360 || value == 480 || value == 720 || value == 1080 || value == 2160 || (value >= 300 && value <= 2500)) {
                    return value;
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        return 0;
    }

    private void togglePlayPause() {
        if (exoPlayer != null) {
            if (playbackEnded) {
                // Restart playback from beginning
                exoPlayer.seekTo(0);
                exoPlayer.setPlayWhenReady(true);
                playbackEnded = false;
                showControls();
            } else if (exoPlayer.isPlaying()) {
                exoPlayer.pause();
            } else {
                exoPlayer.play();
            }
        }
        scheduleControlsHide();
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        if (playPauseButton != null) {
            if (isPlaying) {
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
            } else {
                playPauseButton.setImageResource(android.R.drawable.ic_media_play);
            }
        }
    }

    private void toggleControls() {
        if (controlsVisible) {
            hideControls();
        } else {
            showControls();
            scheduleControlsHide();
        }
    }

    private void showControls() {
        if (controlsContainer != null) {
            controlsContainer.setVisibility(View.VISIBLE);
            controlsVisible = true;
        }
        if (closeButton != null) {
            closeButton.setVisibility(View.VISIBLE);
        }
    }

    private void hideControls() {
        if (controlsContainer != null) {
            controlsContainer.setVisibility(View.GONE);
            controlsVisible = false;
        }
        // Keep close button always visible
        // if (closeButton != null) {
        //     closeButton.setVisibility(View.VISIBLE);
        // }
    }

    private void scheduleControlsHide() {
        if (hideControlsRunnable != null) {
            playbackHandler.removeCallbacks(hideControlsRunnable);
        }
        hideControlsRunnable =
            () -> {
                if (exoPlayer != null && exoPlayer.isPlaying()) {
                    hideControls();
                }
            };
        playbackHandler.postDelayed(hideControlsRunnable, 3000); // Hide after 3 seconds
    }

    private void updateCurrentTimeText(long positionMs) {
        if (currentTimeText != null) {
            currentTimeText.setText(formatTime(positionMs));
        }
    }

    private void updateDurationText(long durationMs) {
        if (durationText != null) {
            durationText.setText(formatTime(durationMs));
        }
    }

    private String formatTime(long timeMs) {
        long seconds = timeMs / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private void displayImage(MediaItem item) {
        if (textureView != null) {
            textureView.setVisibility(View.GONE);
        }
        if (mediaImageView == null) {
            return;
        }

        mediaImageView.setVisibility(View.VISIBLE);

        // Update overlay to allow touch events through
        updateOverlayClickable();

        // Reset zoom when displaying new image (but don't fit yet - wait for image to load)
        mediaImageView.resetZoom();

        Glide
            .with(this)
            .load(item.path)
            .listener(
                new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(
                        @Nullable com.bumptech.glide.load.engine.GlideException e,
                        Object model,
                        com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                        boolean isFirstResource
                    ) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(
                        android.graphics.drawable.Drawable resource,
                        Object model,
                        com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                        com.bumptech.glide.load.DataSource dataSource,
                        boolean isFirstResource
                    ) {
                        // Fit to screen after image is loaded (only once to prevent flickering)
                        if (mediaImageView != null) {
                            mediaImageView.postDelayed(
                                () -> {
                                    if (
                                        mediaImageView.getDrawable() != null &&
                                        mediaImageView.getWidth() > 0 &&
                                        mediaImageView.getHeight() > 0
                                    ) {
                                        mediaImageView.fitToScreenPublic();
                                    }
                                },
                                50
                            ); // Small delay to ensure layout is complete
                        }
                        return false;
                    }
                }
            )
            .into(mediaImageView);

        hideControls();
    }

    private void startPlaybackStateMonitoring() {
        if (playbackRunnable != null) {
            playbackHandler.removeCallbacks(playbackRunnable);
        }

        playbackRunnable =
            new Runnable() {
                private int qualityCheckCounter = 0;
                private int lastDetectedWidth = 0;
                private int lastDetectedHeight = 0;
                private String lastDetectedQuality = null;

                @Override
                public void run() {
                    if (exoPlayer != null && listener != null) {
                        updatePlaybackState();

                        // Check quality continuously when in Auto mode
                        if ("Auto".equals(currentQuality)) {
                            qualityCheckCounter++;
                            // Check every call (every 500ms) for more responsive updates
                            int currentWidth = exoPlayer.getVideoSize().width;
                            int currentHeight = exoPlayer.getVideoSize().height;

                            // Always check if dimensions or track selection might have changed
                            if (currentWidth != lastDetectedWidth || currentHeight != lastDetectedHeight) {
                                lastDetectedWidth = currentWidth;
                                lastDetectedHeight = currentHeight;
                                Log.d("MediaViewerFragment", "Video size changed in monitoring: " + currentWidth + "x" + currentHeight);
                                updateAutoQuality();
                                // Also check if quality changed
                                if (actualPlayingQuality != null && !actualPlayingQuality.equals(lastDetectedQuality)) {
                                    lastDetectedQuality = actualPlayingQuality;
                                    Log.d("MediaViewerFragment", "Quality updated to: " + actualPlayingQuality);
                                }
                            } else if (qualityCheckCounter % 2 == 0) {
                                // Check every 1 second even if size hasn't changed (for track selection changes)
                                updateAutoQuality();
                            }
                        }
                    }
                    playbackHandler.postDelayed(this, 500);
                }
            };
        playbackHandler.post(playbackRunnable);
    }

    private void updatePlaybackState() {
        if (exoPlayer != null) {
            // Update seek bar
            if (seekBar != null && exoPlayer.getDuration() > 0) {
                long currentPosition = exoPlayer.getCurrentPosition();
                long duration = exoPlayer.getDuration();
                // Calculate progress as percentage (0-1000)
                int progress = duration > 0 ? (int) ((currentPosition * 1000L) / duration) : 0;
                if (progress >= 0 && progress <= 1000) {
                    seekBar.setProgress(progress);
                }
                updateCurrentTimeText(currentPosition);
            }

            // Notify listener
            if (listener != null) {
                PlaybackState state = new PlaybackState();
                state.isPlaying = exoPlayer.isPlaying();
                state.currentTime = exoPlayer.getCurrentPosition() / 1000.0; // Convert to seconds
                state.duration = exoPlayer.getDuration() / 1000.0; // Convert to seconds
                state.currentQuality = currentQuality;
                listener.onPlaybackStateChanged(state);
            }
        }
    }

    private void showNext() {
        if (currentIndex < mediaItems.size() - 1) {
            currentIndex++;
            displayCurrentMedia();
        }
    }

    private void showPrevious() {
        if (currentIndex > 0) {
            currentIndex--;
            displayCurrentMedia();
        }
    }

    private void startSwipeAnimation(int direction, float initialOffset) {
        if (isSwiping || rootView == null) return;

        // Determine target index
        int targetIndex = direction > 0 ? currentIndex - 1 : currentIndex + 1;
        if (targetIndex < 0 || targetIndex >= mediaItems.size()) return;

        swipeDirection = direction;
        swipeTotalDistance = initialOffset;
        isSwiping = true;

        // Pause video playback during swipe
        if (exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.pause();
        }

        // Get current media container (video or image)
        View currentView = getCurrentMediaView();
        if (currentView == null) {
            isSwiping = false;
            return;
        }

        // Create and prepare next/previous item view
        nextMediaContainer = createNextItemContainer(targetIndex);
        if (nextMediaContainer == null) {
            isSwiping = false;
            return;
        }

        // Position next item off-screen in the swipe direction
        int screenWidth = rootView.getWidth();
        float nextStartX = direction > 0 ? -screenWidth : screenWidth;
        nextMediaContainer.setTranslationX(nextStartX);
        nextMediaContainer.setVisibility(View.VISIBLE);
        nextMediaContainer.setElevation(1); // Lower elevation than current

        // Add next item container to root
        rootView.addView(nextMediaContainer);

        // Start tracking swipe progress
        updateSwipeProgress(initialOffset);
    }

    private void updateSwipeProgress(float currentOffset) {
        if (!isSwiping || rootView == null) return;

        View currentView = getCurrentMediaView();
        if (currentView == null || nextMediaContainer == null) return;

        int screenWidth = rootView.getWidth();

        // Clamp offset to screen width
        float deltaX;
        if (swipeDirection > 0) {
            deltaX = Math.max(0, Math.min(screenWidth, currentOffset));
        } else {
            deltaX = Math.min(0, Math.max(-screenWidth, currentOffset));
        }

        // Translate current view
        currentView.setTranslationX(deltaX);

        // Translate next view (opposite direction, moves from off-screen to center)
        float nextStartX = swipeDirection > 0 ? -screenWidth : screenWidth;
        nextMediaContainer.setTranslationX(nextStartX + deltaX);
    }

    private void completeSwipeAnimation(float velocity) {
        if (!isSwiping || rootView == null) return;

        View currentView = getCurrentMediaView();
        if (currentView == null || nextMediaContainer == null) {
            cancelCurrentSwipe();
            return;
        }

        int screenWidth = rootView.getWidth();
        float currentTranslateX = currentView.getTranslationX();
        float threshold = screenWidth * 0.3f; // 30% threshold
        boolean shouldComplete =
            Math.abs(velocity) > 1000 ||
            (swipeDirection > 0 && currentTranslateX > threshold) ||
            (swipeDirection < 0 && currentTranslateX < -threshold);

        if (shouldComplete) {
            // Complete swipe - animate to final position
            float finalCurrentX = swipeDirection > 0 ? screenWidth : -screenWidth;
            float finalNextX = 0;

            // Animate both views to final positions
            ObjectAnimator currentAnim = ObjectAnimator.ofFloat(currentView, "translationX", currentTranslateX, finalCurrentX);
            ObjectAnimator nextAnim = ObjectAnimator.ofFloat(
                nextMediaContainer,
                "translationX",
                nextMediaContainer.getTranslationX(),
                finalNextX
            );

            currentAnim.setDuration(200);
            nextAnim.setDuration(200);

            nextAnim.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // Switch to next item
                        if (swipeDirection > 0) {
                            if (currentIndex > 0) {
                                currentIndex--;
                            }
                        } else {
                            if (currentIndex < mediaItems.size() - 1) {
                                currentIndex++;
                            }
                        }

                        // Clean up and display new media
                        cleanupSwipe();
                        displayCurrentMedia();
                    }
                }
            );

            currentSwipeAnimator = currentAnim;
            currentAnim.start();
            nextAnim.start();
        } else {
            // Cancel swipe - animate back to start
            cancelCurrentSwipe();
        }
    }

    private void cancelCurrentSwipe() {
        if (!isSwiping) return;

        View currentView = getCurrentMediaView();
        if (currentView == null || nextMediaContainer == null) {
            cleanupSwipe();
            return;
        }

        // Animate both views back to start position
        ObjectAnimator currentAnim = ObjectAnimator.ofFloat(currentView, "translationX", currentView.getTranslationX(), 0);
        ObjectAnimator nextAnim = ObjectAnimator.ofFloat(
            nextMediaContainer,
            "translationX",
            nextMediaContainer.getTranslationX(),
            swipeDirection > 0 ? -rootView.getWidth() : rootView.getWidth()
        );

        currentAnim.setDuration(200);
        nextAnim.setDuration(200);

        nextAnim.addListener(
            new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    cleanupSwipe();
                }
            }
        );

        currentSwipeAnimator = currentAnim;
        currentAnim.start();
        nextAnim.start();
    }

    private void cleanupSwipe() {
        if (currentSwipeAnimator != null) {
            currentSwipeAnimator.cancel();
            currentSwipeAnimator = null;
        }

        View currentView = getCurrentMediaView();
        if (currentView != null) {
            currentView.setTranslationX(0);
        }

        if (nextMediaContainer != null && rootView != null) {
            rootView.removeView(nextMediaContainer);
            nextMediaContainer = null;
        }

        isSwiping = false;
        swipeDirection = 0;
        swipeStartX = 0;
        swipeTotalDistance = 0;
    }

    private View getCurrentMediaView() {
        // Check which media type is currently displayed
        if (mediaImageView != null && mediaImageView.getVisibility() == View.VISIBLE) {
            return mediaImageView;
        } else if (videoContainer != null) {
            // Video container is used for videos (check if textureView or thumbnail is visible)
            if (
                (textureView != null && textureView.getVisibility() == View.VISIBLE) ||
                (videoThumbnail != null && videoThumbnail.getVisibility() == View.VISIBLE)
            ) {
                return videoContainer;
            }
            // Fallback: if we have a current item and it's a video, return videoContainer
            if (mediaItems != null && currentIndex >= 0 && currentIndex < mediaItems.size()) {
                MediaItem item = mediaItems.get(currentIndex);
                if ("VIDEO".equals(item.type)) {
                    return videoContainer;
                }
            }
        }
        return null;
    }

    private FrameLayout createNextItemContainer(int targetIndex) {
        if (mediaItems == null || targetIndex < 0 || targetIndex >= mediaItems.size()) {
            return null;
        }

        MediaItem item = mediaItems.get(targetIndex);
        FrameLayout container = new FrameLayout(requireContext());
        container.setLayoutParams(
            new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        );
        container.setBackgroundColor(Color.BLACK);

        if ("VIDEO".equals(item.type)) {
            // Create video thumbnail view
            ImageView thumbnail = new ImageView(requireContext());
            thumbnail.setLayoutParams(
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            );
            thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
            container.addView(thumbnail);

            if (item.thumbnail != null && !item.thumbnail.isEmpty()) {
                Glide.with(this).load(item.thumbnail).into(thumbnail);
            }
        } else {
            // Create image view
            ImageView imageView = new ImageView(requireContext());
            imageView.setLayoutParams(
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            );
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            container.addView(imageView);

            Glide.with(this).load(item.path).into(imageView);
        }

        return container;
    }

    public void play() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(true);
        }
    }

    public void pause() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
        }
    }

    public void seek(long timeMs) {
        if (exoPlayer != null) {
            exoPlayer.seekTo(timeMs);
        }
    }

    public void setQuality(String quality) {
        if (mediaItems == null || currentIndex < 0 || currentIndex >= mediaItems.size()) {
            return;
        }

        MediaItem currentItem = mediaItems.get(currentIndex);
        if (currentItem == null) {
            return;
        }

        // Handle "Auto" option
        if ("Auto".equals(quality)) {
            long currentPosition = exoPlayer != null ? exoPlayer.getCurrentPosition() : 0;
            boolean wasPlaying = exoPlayer != null && exoPlayer.isPlaying();
            currentQuality = "Auto";
            actualPlayingQuality = null; // Reset, will be detected when playback starts

            // Use original path and let ExoPlayer choose automatically
            MediaItem playbackItem = cloneMediaItemWithUrl(currentItem, currentItem.path);
            android.graphics.SurfaceTexture surfaceTexture = textureView != null ? textureView.getSurfaceTexture() : null;

            // Show thumbnail again when switching quality (if available)
            if (videoThumbnail != null && currentItem.thumbnail != null && !currentItem.thumbnail.isEmpty()) {
                videoThumbnail.setVisibility(View.VISIBLE);
                Glide.with(this).load(currentItem.thumbnail).into(videoThumbnail);
            } else if (videoThumbnail != null) {
                videoThumbnail.setVisibility(View.GONE);
            }

            // Hide video initially when switching quality (use alpha)
            if (textureView != null) {
                textureView.setAlpha(0f);
                
                // Reset TextureView to full screen size before switching
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) textureView.getLayoutParams();
                params.width = FrameLayout.LayoutParams.MATCH_PARENT;
                params.height = FrameLayout.LayoutParams.MATCH_PARENT;
                params.gravity = android.view.Gravity.CENTER;
                textureView.setLayoutParams(params);

                // Reset all transforms and positioning to avoid zoom/offset issues
                textureView.setX(0f);
                textureView.setY(0f);
                textureView.setScaleX(1f);
                textureView.setScaleY(1f);
                
                // Reset transform matrix to identity - will be recalculated when new video size is known
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                textureView.setTransform(matrix);
                textureView.requestLayout();
            }

            releasePlayer(false);

            if (surfaceTexture != null) {
                preparePlayerWithSurface(playbackItem, surfaceTexture, currentPosition, wasPlaying);
            } else if (textureView != null) {
                textureView.post(() -> {
                    android.graphics.SurfaceTexture availableSurface = textureView.getSurfaceTexture();
                    if (availableSurface != null) {
                        preparePlayerWithSurface(playbackItem, availableSurface, currentPosition, wasPlaying);
                    }
                });
            }
            return;
        }

        // Handle manual quality selection
        if (currentItem.qualityVariants == null) {
            return;
        }

        for (QualityVariant variant : currentItem.qualityVariants) {
            if (variant.label.equals(quality)) {
                long currentPosition = exoPlayer != null ? exoPlayer.getCurrentPosition() : 0;
                boolean wasPlaying = exoPlayer != null && exoPlayer.isPlaying();
                currentQuality = quality;
                actualPlayingQuality = quality; // Set actual quality to selected one

                MediaItem playbackItem = cloneMediaItemWithUrl(currentItem, variant.url);
                android.graphics.SurfaceTexture surfaceTexture = textureView != null ? textureView.getSurfaceTexture() : null;

                // Show thumbnail again when switching quality (if available)
                if (videoThumbnail != null && currentItem.thumbnail != null && !currentItem.thumbnail.isEmpty()) {
                    videoThumbnail.setVisibility(View.VISIBLE);
                    Glide.with(this).load(currentItem.thumbnail).into(videoThumbnail);
                } else if (videoThumbnail != null) {
                    videoThumbnail.setVisibility(View.GONE);
                }

                // Hide video initially when switching quality (use alpha)
                if (textureView != null) {
                    textureView.setAlpha(0f);
                    
                    // Reset TextureView to full screen size before switching
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) textureView.getLayoutParams();
                    params.width = FrameLayout.LayoutParams.MATCH_PARENT;
                    params.height = FrameLayout.LayoutParams.MATCH_PARENT;
                    params.gravity = android.view.Gravity.CENTER;
                    textureView.setLayoutParams(params);

                    // Reset all transforms and positioning to avoid zoom/offset issues
                    textureView.setX(0f);
                    textureView.setY(0f);
                    textureView.setScaleX(1f);
                    textureView.setScaleY(1f);
                    
                    // Reset transform matrix to identity - will be recalculated when new video size is known
                    android.graphics.Matrix matrix = new android.graphics.Matrix();
                    textureView.setTransform(matrix);
                    textureView.requestLayout();
                }

                releasePlayer(false);

                if (surfaceTexture != null) {
                    preparePlayerWithSurface(playbackItem, surfaceTexture, currentPosition, wasPlaying);
                } else if (textureView != null) {
                    textureView.post(() -> {
                        android.graphics.SurfaceTexture availableSurface = textureView.getSurfaceTexture();
                        if (availableSurface != null) {
                            preparePlayerWithSurface(playbackItem, availableSurface, currentPosition, wasPlaying);
                        }
                    });
                }
                break;
            }
        }
    }

    private MediaItem cloneMediaItemWithUrl(MediaItem baseItem, String path) {
        MediaItem clone = new MediaItem();
        clone.path = path;
        clone.type = baseItem.type;
        clone.alt = baseItem.alt;
        clone.thumbnail = baseItem.thumbnail;
        clone.qualityVariants = baseItem.qualityVariants;
        return clone;
    }

    public PlaybackState getPlaybackState() {
        PlaybackState state = new PlaybackState();
        if (exoPlayer != null) {
            state.isPlaying = exoPlayer.isPlaying();
            state.currentTime = exoPlayer.getCurrentPosition() / 1000.0;
            state.duration = exoPlayer.getDuration() / 1000.0;
            state.currentQuality = currentQuality;
        }
        return state;
    }

    private void releasePlayer() {
        releasePlayer(true);
    }

    private void releasePlayer(boolean detachTextureListener) {
        if (playbackRunnable != null) {
            playbackHandler.removeCallbacks(playbackRunnable);
            playbackRunnable = null;
        }

        if (hideControlsRunnable != null) {
            playbackHandler.removeCallbacks(hideControlsRunnable);
            hideControlsRunnable = null;
        }

        if (exoPlayer != null) {
            exoPlayer.clearVideoSurface();
            exoPlayer.release();
            exoPlayer = null;
        }

        releaseVideoSurface();

        if (detachTextureListener && textureView != null) {
            textureView.setSurfaceTextureListener(null);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Remove layout listener
        if (rootView != null && layoutListener != null) {
            rootView.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
            layoutListener = null;
        }

        releasePlayer();
        overlayContainer = null;
        videoContainer = null;
        controlsContainer = null;
        closeButton = null;
        playPauseButton = null;
        qualityButton = null;
        seekBar = null;
        currentTimeText = null;
        durationText = null;
        textureView = null;
        mediaImageView = null;
        rootView = null;
        if (listener != null) {
            listener.onViewerDismissed();
        }
    }

    private QualityVariant findVariantForUrl(String url) {
        if (qualityVariants == null) return null;
        for (QualityVariant v : qualityVariants) {
            if (url.equals(v.url)) return v;
        }
        return null;
    }
}
