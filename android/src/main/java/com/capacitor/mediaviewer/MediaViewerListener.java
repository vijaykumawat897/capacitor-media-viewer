package com.capacitor.mediaviewer;

public interface MediaViewerListener {
    void onPlaybackStateChanged(PlaybackState state);
    void onMediaIndexChanged(int index);
    void onViewerDismissed();
}

