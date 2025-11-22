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
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import com.bumptech.glide.Glide;
import com.capacitor.mediaviewer.R;
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

    public static MediaViewerFragment newInstance(
        List<MediaItem> items,
        int currentIndex,
        String title,
        MediaViewerListener listener
    ) {
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
        // Ensure the dialog takes full screen
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
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
        if (videoContainer == null) {
            return;
        }

        if (textureView != null && textureView.getParent() == videoContainer) {
            return;
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

        // Remove only TextureView if it exists, keep thumbnail
        if (textureView.getParent() != null) {
            ((ViewGroup) textureView.getParent()).removeView(textureView);
        }
        
        // Add TextureView at index 0 (behind thumbnail)
        videoContainer.addView(textureView, 0);
    }

    private void initializeControlListeners() {
        if (qualityButton != null) {
            qualityButton.setOnClickListener(v -> showQualitySelector());
        }

        if (playPauseButton != null) {
            playPauseButton.setOnClickListener(v -> togglePlayPause());
        }

        if (seekBar != null) {
            seekBar.setMax(1000);
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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
            });
        }

        if (controlsContainer != null) {
            controlsContainer.setVisibility(View.VISIBLE);
            controlsVisible = true;
        }
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
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
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
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
        });
        
        // Enable long press to toggle controls (optional)
        gestureDetector.setIsLongpressEnabled(false);
    }

    private void setupOverlayTouchHandling() {
        if (overlayContainer == null) {
            return;
        }
        
        overlayContainer.setOnTouchListener((v, event) -> {
            // If image is visible, check if we should handle swipe gestures
            if (mediaImageView != null && mediaImageView.getVisibility() == View.VISIBLE) {
                // Only handle touches on close button or controls
                if (closeButton != null && isPointInsideView(closeButton, event)) {
                    return false; // Let close button handle it
                }
                if (controlsContainer != null && isPointInsideView(controlsContainer, event)) {
                    return false; // Let controls handle it
                }
                // For images, let gesture detector handle swipes (it will work with image view)
                // The image view will return false for horizontal swipes when not zoomed
                return handleOverlayTouch(event);
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

    private boolean handleOverlayTouch(MotionEvent event) {
        // If touch is within controls or close button, let them handle it
        if (controlsContainer != null && controlsContainer.getVisibility() == View.VISIBLE &&
            isPointInsideView(controlsContainer, event)) {
            if (isSwiping && event.getAction() == MotionEvent.ACTION_UP) {
                cancelCurrentSwipe();
            }
            return false;
        }
        if (closeButton != null && closeButton.getVisibility() == View.VISIBLE &&
            isPointInsideView(closeButton, event)) {
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
        return x >= location[0] && x <= location[0] + view.getWidth() &&
               y >= location[1] && y <= location[1] + view.getHeight();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Gestures are handled by the gesture detector on the container
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
                Glide.with(this)
                    .load(item.thumbnail)
                    .into(videoThumbnail);
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
                updateTextureViewAspectRatio(width, height);
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

        // Set up player listeners
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updatePlaybackState();
                
                if (playbackState == Player.STATE_ENDED) {
                    // Playback has ended
                    playbackEnded = true;
                    updatePlayPauseButton(false);
                    showControls(); // Show controls when playback ends
                    // Update current quality being played if in auto mode
                    updateAutoQuality();
                } else if (playbackState == Player.STATE_READY) {
                    playbackEnded = false;
                    long duration = exoPlayer.getDuration();
                    if (duration > 0) {
                        seekBar.setMax(1000); // Use 1000 for percentage-based seeking
                        updateDurationText(duration);
                    }
                    
                    // Update video aspect ratio when ready
                    if (exoPlayer.getVideoSize().width > 0 && exoPlayer.getVideoSize().height > 0) {
                        updateTextureViewAspectRatio(exoPlayer.getVideoSize().width, exoPlayer.getVideoSize().height);
                    }
                    
                    // Fade in TextureView and hide thumbnail when video is ready and playing
                    // Use a small delay to ensure we have actual video frames
                    if (exoPlayer.isPlaying() && textureView != null && textureView.getAlpha() < 1f) {
                        playbackHandler.postDelayed(() -> {
                            if (exoPlayer != null && exoPlayer.isPlaying() && textureView != null) {
                                textureView.animate().alpha(1f).setDuration(200).start();
                                if (videoThumbnail != null && videoThumbnail.getVisibility() == View.VISIBLE) {
                                    videoThumbnail.setVisibility(View.GONE);
                                }
                            }
                        }, 150); // Small delay to skip the first frame
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
                updatePlayPauseButton(isPlaying);
                
                // Fade in TextureView and hide thumbnail when playback actually starts
                // Use a small delay to ensure we have actual video frames, not just the first frame
                if (isPlaying && textureView != null && textureView.getAlpha() < 1f) {
                    playbackHandler.postDelayed(() -> {
                        if (exoPlayer != null && exoPlayer.isPlaying() && textureView != null) {
                            textureView.animate().alpha(1f).setDuration(200).start();
                            if (videoThumbnail != null && videoThumbnail.getVisibility() == View.VISIBLE) {
                                videoThumbnail.setVisibility(View.GONE);
                            }
                        }
                    }, 150); // Small delay to skip the first frame
                }
            }
        });


        // Show controls initially, then auto-hide
        showControls();
        scheduleControlsHide();

        // Start monitoring playback state
        startPlaybackStateMonitoring();
    }

    private void showQualitySelector() {
        // Quality selector removed - quality variants no longer supported
        return;
    }
    
    private void updateTextureViewAspectRatio(int videoWidth, int videoHeight) {
        if (textureView == null || videoWidth <= 0 || videoHeight <= 0) {
            return;
        }

        View parent = (View) textureView.getParent();
        int containerWidth = parent != null ? parent.getWidth() : textureView.getWidth();
        int containerHeight = parent != null ? parent.getHeight() : textureView.getHeight();

        if (containerWidth <= 0 || containerHeight <= 0) {
            return;
        }

        float videoAspectRatio = (float) videoWidth / videoHeight;
        float containerAspectRatio = (float) containerWidth / containerHeight;

        int newWidth;
        int newHeight;
        if (videoAspectRatio > containerAspectRatio) {
            newWidth = containerWidth;
            newHeight = (int) (containerWidth / videoAspectRatio);
        } else {
            newHeight = containerHeight;
            newWidth = (int) (containerHeight * videoAspectRatio);
        }

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) textureView.getLayoutParams();
        params.width = newWidth;
        params.height = newHeight;
        params.gravity = android.view.Gravity.CENTER;
        textureView.setLayoutParams(params);
        textureView.requestLayout();
    }

    private void updateAutoQuality() {
        // Quality detection removed - quality variants no longer supported
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
        hideControlsRunnable = () -> {
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
        
        // Reset zoom when displaying new image
        mediaImageView.resetZoom();

        Glide.with(this)
            .load(item.path)
            .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                @Override
                public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                    return false;
                }

                @Override
                public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                    // Fit to screen after image is loaded
                    if (mediaImageView != null) {
                        mediaImageView.post(() -> {
                            if (mediaImageView.getDrawable() != null) {
                                mediaImageView.fitToScreenPublic();
                            }
                        });
                    }
                    return false;
                }
            })
            .into(mediaImageView);

        hideControls();
    }

    private void startPlaybackStateMonitoring() {
        if (playbackRunnable != null) {
            playbackHandler.removeCallbacks(playbackRunnable);
        }

        playbackRunnable = new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null && listener != null) {
                    updatePlaybackState();
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
        boolean shouldComplete = Math.abs(velocity) > 1000 || 
                                 (swipeDirection > 0 && currentTranslateX > threshold) ||
                                 (swipeDirection < 0 && currentTranslateX < -threshold);
        
        if (shouldComplete) {
            // Complete swipe - animate to final position
            float finalCurrentX = swipeDirection > 0 ? screenWidth : -screenWidth;
            float finalNextX = 0;
            
            // Animate both views to final positions
            ObjectAnimator currentAnim = ObjectAnimator.ofFloat(currentView, "translationX", 
                currentTranslateX, finalCurrentX);
            ObjectAnimator nextAnim = ObjectAnimator.ofFloat(nextMediaContainer, "translationX",
                nextMediaContainer.getTranslationX(), finalNextX);
            
            currentAnim.setDuration(200);
            nextAnim.setDuration(200);
            
            nextAnim.addListener(new AnimatorListenerAdapter() {
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
            });
            
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
        ObjectAnimator currentAnim = ObjectAnimator.ofFloat(currentView, "translationX",
            currentView.getTranslationX(), 0);
        ObjectAnimator nextAnim = ObjectAnimator.ofFloat(nextMediaContainer, "translationX",
            nextMediaContainer.getTranslationX(), swipeDirection > 0 ? -rootView.getWidth() : rootView.getWidth());
        
        currentAnim.setDuration(200);
        nextAnim.setDuration(200);
        
        nextAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                cleanupSwipe();
            }
        });
        
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
            if ((textureView != null && textureView.getVisibility() == View.VISIBLE) ||
                (videoThumbnail != null && videoThumbnail.getVisibility() == View.VISIBLE)) {
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
        container.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ));
        container.setBackgroundColor(Color.BLACK);
        
        if ("VIDEO".equals(item.type)) {
            // Create video thumbnail view
            ImageView thumbnail = new ImageView(requireContext());
            thumbnail.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ));
            thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
            container.addView(thumbnail);
            
            if (item.thumbnail != null && !item.thumbnail.isEmpty()) {
                Glide.with(this).load(item.thumbnail).into(thumbnail);
            }
        } else {
            // Create image view
            ImageView imageView = new ImageView(requireContext());
            imageView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ));
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
        // Quality selection removed - quality variants no longer supported
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
        if (listener != null) {
            listener.onViewerDismissed();
        }
    }
}

