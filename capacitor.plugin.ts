import { PluginListenerHandle } from '@capacitor/core';

export interface MediaItem {
  /**
   * Media URL (video or image)
   */
  url: string;
  /**
   * Media type: 'video' or 'image'
   */
  type: 'video' | 'image';
  /**
   * Optional title/name for the media
   */
  title?: string;
  /**
   * Optional thumbnail URL for videos
   */
  thumbnailUrl?: string;
  /**
   * Optional quality variants for video (e.g., [{ label: 'HD', url: '...' }])
   */
  qualityVariants?: QualityVariant[];
}

export interface QualityVariant {
  /**
   * Quality label (e.g., 'HD', 'SD', '720p')
   */
  label: string;
  /**
   * Video URL for this quality
   */
  url: string;
}

export interface ShowMediaViewerOptions {
  /**
   * Array of media items (videos/images) to display
   */
  items: MediaItem[];
  /**
   * Current media index to show initially (0-based)
   */
  currentIndex: number;
  /**
   * Optional title for the viewer
   */
  title?: string;
}

export interface PlaybackState {
  /**
   * Whether video is currently playing
   */
  isPlaying: boolean;
  /**
   * Current playback position in seconds
   */
  currentTime: number;
  /**
   * Total duration in seconds
   */
  duration: number;
  /**
   * Current quality label if applicable
   */
  currentQuality?: string;
}

export interface MediaViewerPlugin {
  /**
   * Show the media viewer with the provided media items
   */
  show(options: ShowMediaViewerOptions): Promise<void>;

  /**
   * Dismiss the media viewer
   */
  dismiss(): Promise<void>;

  /**
   * Play the current video
   */
  play(): Promise<void>;

  /**
   * Pause the current video
   */
  pause(): Promise<void>;

  /**
   * Seek to a specific time in seconds
   */
  seek(options: { time: number }): Promise<void>;

  /**
   * Change video quality
   */
  setQuality(options: { quality: string }): Promise<void>;

  /**
   * Get current playback state
   */
  getPlaybackState(): Promise<PlaybackState>;

  /**
   * Listen for playback state changes
   */
  addListener(
    eventName: 'playbackStateChanged',
    listenerFunc: (state: PlaybackState) => void
  ): PluginListenerHandle;

  /**
   * Listen for media index changes (when user swipes)
   */
  addListener(
    eventName: 'mediaIndexChanged',
    listenerFunc: (data: { index: number }) => void
  ): PluginListenerHandle;

  /**
   * Listen for viewer dismissal
   */
  addListener(
    eventName: 'viewerDismissed',
    listenerFunc: () => void
  ): PluginListenerHandle;

  /**
   * Remove all listeners for this plugin
   */
  removeAllListeners(): Promise<void>;
}

