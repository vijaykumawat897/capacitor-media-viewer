# Capacitor Media Viewer

A native media viewer plugin for Capacitor apps with support for videos (including .m3u8 HLS streams) and images. Features smooth swipe navigation, quality selection, auto quality detection, and full playback controls.

## Features

- ✅ Native video playback on iOS and Android
- ✅ Image viewing support  
- ✅ Smooth swipe left/right to navigate between media items (with visual transitions)
- ✅ Play/Pause controls with auto-restart on completion
- ✅ Seek functionality with progress bar
- ✅ Quality selector with "Auto" option that shows current quality being played
- ✅ Automatic quality detection for HLS streams
- ✅ Support for .m3u8 (HLS) and other video formats
- ✅ Web/PWA implementation for development
- ✅ Event listeners for playback state and navigation changes
- ✅ Thumbnail support for videos

## Installation

### From npm

```bash
npm install capacitor-media-viewer
npx cap sync
```

### Local Development

For local testing and development:

```bash
# In the plugin directory
npm install
npm run build
npm link

# In your Ionic/Capacitor project
npm link capacitor-media-viewer
npx cap sync
```

**Alternative**: Use file path in `package.json`:
```json
{
  "dependencies": {
    "capacitor-media-viewer": "file:../capacitor-media-viewer"
  }
}
```

Then run `npm install` in your project.

## Usage

### Basic Example

```typescript
import { MediaViewer } from 'capacitor-media-viewer';

// Show media viewer with array of media items
const showMediaViewer = async () => {
  await MediaViewer.show({
    items: [
      {
        url: 'https://example.com/video1.mp4',
        type: 'video',
        title: 'Video 1',
        thumbnailUrl: 'https://example.com/thumb1.jpg'
      },
      {
        url: 'https://example.com/image1.jpg',
        type: 'image',
        title: 'Image 1'
      },
      {
        url: 'https://example.com/video2.m3u8',
        type: 'video',
        title: 'HLS Stream'
        // Quality variants will be automatically detected from the master playlist!
      }
    ],
    currentIndex: 0,
    title: 'Media Gallery'
  });
};

// Dismiss the viewer
const dismissViewer = async () => {
  await MediaViewer.dismiss();
};

// Control playback
const playVideo = async () => {
  await MediaViewer.play();
};

const pauseVideo = async () => {
  await MediaViewer.pause();
};

const seekTo = async (timeInSeconds: number) => {
  await MediaViewer.seek({ time: timeInSeconds });
};

// Change quality (or select "Auto" for automatic quality selection)
const changeQuality = async (qualityLabel: string) => {
  await MediaViewer.setQuality({ quality: qualityLabel });
};

// Get current playback state
const getState = async () => {
  const state = await MediaViewer.getPlaybackState();
  console.log('Is playing:', state.isPlaying);
  console.log('Current time:', state.currentTime);
  console.log('Duration:', state.duration);
  console.log('Quality:', state.currentQuality);
};
```

### Event Listeners

```typescript
import { MediaViewer } from 'capacitor-media-viewer';

// Listen for playback state changes
const playbackListener = await MediaViewer.addListener(
  'playbackStateChanged',
  (state) => {
    console.log('Playback state:', state);
    // state.isPlaying, state.currentTime, state.duration, state.currentQuality
  }
);

// Listen for media index changes (when user swipes)
const indexListener = await MediaViewer.addListener(
  'mediaIndexChanged',
  (data) => {
    console.log('Current index:', data.index);
    // Update your UI to reflect current media
  }
);

// Listen for viewer dismissal
const dismissListener = await MediaViewer.addListener(
  'viewerDismissed',
  () => {
    console.log('Viewer was dismissed');
    // Clean up any related state
    MediaViewer.removeAllListeners();
  }
);

// Remove specific listener
playbackListener.remove();

// Or remove all listeners
await MediaViewer.removeAllListeners();
```

### Ionic React Example

```tsx
import React, { useState, useEffect } from 'react';
import { IonButton, IonContent, IonPage } from '@ionic/react';
import { MediaViewer } from 'capacitor-media-viewer';

const MediaViewerExample: React.FC = () => {
  const [isViewerOpen, setIsViewerOpen] = useState(false);

  const mediaItems = [
    {
      url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
      type: 'video' as const,
      title: 'Big Buck Bunny',
      thumbnailUrl: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg'
    },
    {
      url: 'https://picsum.photos/1920/1080',
      type: 'image' as const,
      title: 'Sample Image'
    },
    {
      url: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
      type: 'video' as const,
      title: 'HLS Stream Test'
      // Quality variants will be automatically detected!
    }
  ];

  const openViewer = async () => {
    setIsViewerOpen(true);
    
    // Set up listeners
    await MediaViewer.addListener('playbackStateChanged', (state) => {
      console.log('State:', state);
    });
    
    await MediaViewer.addListener('mediaIndexChanged', (data) => {
      console.log('Index:', data.index);
    });
    
    await MediaViewer.addListener('viewerDismissed', () => {
      setIsViewerOpen(false);
      MediaViewer.removeAllListeners();
    });
    
    await MediaViewer.show({
      items: mediaItems,
      currentIndex: 0,
      title: 'My Media Gallery'
    });
  };

  return (
    <IonPage>
      <IonContent>
        <IonButton onClick={openViewer}>Open Media Viewer</IonButton>
      </IonContent>
    </IonPage>
  );
};

export default MediaViewerExample;
```

### Advanced Example with State Management

```tsx
import React, { useState, useEffect } from 'react';
import { IonButton, IonContent, IonPage, IonItem, IonLabel, IonToggle } from '@ionic/react';
import { MediaViewer, PlaybackState } from 'capacitor-media-viewer';

const AdvancedMediaViewer: React.FC = () => {
  const [playbackState, setPlaybackState] = useState<PlaybackState | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);

  useEffect(() => {
    let playbackListener: any;
    let indexListener: any;
    let dismissListener: any;

    const setupListeners = async () => {
      playbackListener = await MediaViewer.addListener(
        'playbackStateChanged',
        (state) => {
          setPlaybackState(state);
        }
      );

      indexListener = await MediaViewer.addListener(
        'mediaIndexChanged',
        (data) => {
          setCurrentIndex(data.index);
        }
      );

      dismissListener = await MediaViewer.addListener(
        'viewerDismissed',
        () => {
          setPlaybackState(null);
          setCurrentIndex(0);
        }
      );
    };

    setupListeners();

    return () => {
      playbackListener?.remove();
      indexListener?.remove();
      dismissListener?.remove();
    };
  }, []);

  const mediaItems = [
    // Your media items here
  ];

  return (
    <IonPage>
      <IonContent>
        <IonButton onClick={() => MediaViewer.show({ items: mediaItems, currentIndex: 0 })}>
          Open Viewer
        </IonButton>

        {playbackState && (
          <>
            <IonItem>
              <IonLabel>Playing: {playbackState.isPlaying ? 'Yes' : 'No'}</IonLabel>
              <IonToggle
                checked={playbackState.isPlaying}
                onIonChange={(e) => {
                  if (e.detail.checked) {
                    MediaViewer.play();
                  } else {
                    MediaViewer.pause();
                  }
                }}
              />
            </IonItem>
            <IonItem>
              <IonLabel>
                Time: {Math.floor(playbackState.currentTime)}s / {Math.floor(playbackState.duration)}s
              </IonLabel>
            </IonItem>
            <IonItem>
              <IonLabel>Quality: {playbackState.currentQuality || 'Auto'}</IonLabel>
            </IonItem>
            <IonItem>
              <IonLabel>Current Index: {currentIndex}</IonLabel>
            </IonItem>
          </>
        )}
      </IonContent>
    </IonPage>
  );
};

export default AdvancedMediaViewer;
```

### Handling Local Files

```tsx
import { Filesystem, Directory } from '@capacitor/filesystem';
import { MediaViewer } from 'capacitor-media-viewer';
import { Capacitor } from '@capacitor/core';

const loadLocalMedia = async () => {
  if (Capacitor.isNativePlatform()) {
    // For local files, use file:// protocol
    const mediaItems = [
      {
        url: 'file:///path/to/local/video.mp4',
        type: 'video' as const,
        title: 'Local Video'
      }
    ];

    await MediaViewer.show({
      items: mediaItems,
      currentIndex: 0
    });
  } else {
    // For web, use Data URLs or blob URLs
    const videoData = await Filesystem.readFile({
      path: 'video.mp4',
      directory: Directory.Data
    });

    const blob = new Blob([videoData.data], { type: 'video/mp4' });
    const url = URL.createObjectURL(blob);

    await MediaViewer.show({
      items: [
        {
          url: url,
          type: 'video' as const,
          title: 'Local Video'
        }
      ],
      currentIndex: 0
    });
  }
};
```

## API Reference

### Methods

#### `show(options: ShowMediaViewerOptions): Promise<void>`
Shows the media viewer with the provided media items.

**Options:**
- `items: MediaItem[]` - Array of media items to display
- `currentIndex?: number` - Index of the item to show initially (default: 0)
- `title?: string` - Optional title for the viewer

#### `dismiss(): Promise<void>`
Dismisses the media viewer.

#### `play(): Promise<void>`
Plays the current video. If the video has ended, it will restart from the beginning.

#### `pause(): Promise<void>`
Pauses the current video.

#### `seek(options: { time: number }): Promise<void>`
Seeks to a specific time in seconds.

**Options:**
- `time: number` - Time in seconds to seek to

#### `setQuality(options: { quality: string }): Promise<void>`
Changes the video quality. Set to `"Auto"` for automatic quality selection (default).

**Options:**
- `quality: string` - Quality label from the `qualityVariants` array or `"Auto"`

**Note:** When "Auto" is selected, the plugin will automatically choose the best quality based on network conditions and device capabilities. The actual quality being played will be shown in the quality selector (e.g., "Auto (1080p)").

#### `getPlaybackState(): Promise<PlaybackState>`
Returns the current playback state.

#### `addListener(eventName, listenerFunc): PluginListenerHandle`
Adds a listener for plugin events.

#### `removeAllListeners(): Promise<void>`
Removes all listeners.

### Interfaces

#### `MediaItem`
```typescript
interface MediaItem {
  url: string;                    // Media URL (required)
  type: 'video' | 'image';        // Media type (required)
  title?: string;                 // Optional title
  thumbnailUrl?: string;          // Optional thumbnail URL for videos
  qualityVariants?: QualityVariant[]; // Optional quality variants for video
}
```

#### `QualityVariant`
```typescript
interface QualityVariant {
  label: string;  // Quality label (e.g., 'Auto', 'HD', 'SD', '720p', '1080p')
  url: string;    // Video URL for this quality
}
```

#### `PlaybackState`
```typescript
interface PlaybackState {
  isPlaying: boolean;        // Whether video is playing
  currentTime: number;       // Current time in seconds
  duration: number;          // Total duration in seconds
  currentQuality?: string;   // Current quality label (e.g., 'Auto', '1080p')
}
```

#### `ShowMediaViewerOptions`
```typescript
interface ShowMediaViewerOptions {
  items: MediaItem[];      // Array of media items
  currentIndex?: number;   // Starting index (default: 0)
  title?: string;          // Optional title
}
```

### Events

- **`playbackStateChanged`**: Fired when playback state changes
  - `isPlaying: boolean` - Whether video is playing
  - `currentTime: number` - Current time in seconds
  - `duration: number` - Total duration in seconds
  - `currentQuality?: string` - Current quality label

- **`mediaIndexChanged`**: Fired when user swipes to a different media item
  - `index: number` - New media index

- **`viewerDismissed`**: Fired when the viewer is closed

## Platform Setup

### Android

#### Required Android Manifest Configuration

Ensure your main Android project has the following configuration in `android/app/src/main/AndroidManifest.xml`:

**1. Enable Hardware Acceleration:**
```xml
<application
    android:hardwareAccelerated="true"
    ...>
    
    <activity
        android:name=".MainActivity"
        android:hardwareAccelerated="true"
        ...>
        ...
    </activity>
</application>
```

**2. Internet Permission (for remote videos):**
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    ...
</manifest>
```

Most modern Capacitor apps have these enabled by default.

**3. Sync and Build:**
```bash
npx cap sync android
cd android
./gradlew clean build
```

### iOS

#### Required Configuration

**1. Install Pods:**
```bash
npx cap sync ios
cd ios/App
pod install
```

**2. Info.plist (if accessing local files):**
If you need to access local files, ensure your `Info.plist` includes necessary permissions.

**3. Build in Xcode:**
```bash
npx cap open ios
```

## Platform Support

- ✅ **iOS** - Using AVPlayer for video, UIImageView for images
- ✅ **Android** - Using ExoPlayer (Media3) for video, ImageView with Glide for images
- ✅ **Web/PWA** - Using HTML5 video and img elements

## Requirements

- **Capacitor**: 5.x
- **iOS**: 13.0+
- **Android**: API 22+ (Android 5.1+)

## Key Features Explained

### Automatic Quality Detection
If you provide an HLS (.m3u8) URL without `qualityVariants`, the plugin will automatically parse the master playlist to detect available quality variants. This means you can simply provide the HLS URL and the plugin will handle quality selection automatically.

### Auto Quality Selection
By default, quality is set to "Auto", which lets the player automatically choose the best quality based on:
- Network conditions
- Device capabilities
- User's bandwidth

The quality selector will show "Auto (1080p)" format, displaying what quality is currently being played.

### Smooth Swipe Navigation
The plugin features smooth swipe transitions where both the current and next items move together during the swipe gesture, providing a native app-like experience.

### Playback Restart
When a video completes playback, clicking the play button will automatically restart the video from the beginning.

## Troubleshooting

### Module Not Found Error

If you get "Cannot find module 'capacitor-media-viewer'", make sure you've built the plugin:

```bash
# In plugin directory
npm install
npm run build

# In your project
npm install
npx cap sync
```

### Video Not Playing on Android

1. **Check AndroidManifest.xml** - Ensure hardware acceleration is enabled
2. **Check Logcat** - Look for ExoPlayer errors in Android Studio's Logcat
3. **Verify Video URL** - Make sure the video URL is accessible
4. **Check Network Permissions** - Ensure INTERNET permission is granted

### Video Not Playing on iOS

1. **Install Pods** - Run `pod install` in `ios/App` directory
2. **Check Info.plist** - Ensure necessary permissions are set if accessing local files
3. **Verify Video URL** - Make sure the video URL is accessible

### Quality Selector Not Showing

- Quality selector only appears for videos with multiple quality variants
- For HLS streams, quality variants are auto-detected - make sure your HLS master playlist is accessible
- If you manually provide quality variants, ensure the `qualityVariants` array is not empty

### TypeScript Errors

1. Make sure the plugin is built: `npm run build` in plugin directory
2. Restart your TypeScript server in your IDE
3. Check that `dist/esm/src/index.d.ts` exists
4. Try deleting `node_modules` and reinstalling

### Build Errors

**Android:**
```bash
cd android
./gradlew clean
./gradlew build
```

**iOS:**
```bash
cd ios/App
pod install
# Then rebuild in Xcode
```

## Development

### Building the Plugin

```bash
npm install
npm run build
```

### Local Testing

```bash
# In plugin directory
npm link

# In your project
npm link capacitor-media-viewer
npx cap sync
```

### Making Changes

After modifying plugin code:

1. **Rebuild the plugin:**
   ```bash
   npm run build
   ```

2. **Sync with your project:**
   ```bash
   npx cap sync
   ```

3. **Rebuild native app** (if native code changed)

## Dependencies

### Android
- `androidx.media3:media3-exoplayer:1.1.1`
- `androidx.media3:media3-ui:1.1.1`
- `androidx.media3:media3-exoplayer-hls:1.1.1`
- `com.github.bumptech.glide:glide:4.15.1`

### iOS
- Native AVKit and AVFoundation frameworks
- Native UIKit framework

### TypeScript/Web
- `@capacitor/core: ^5.0.0`

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

MIT

---

Made with ❤️ for the Capacitor community
