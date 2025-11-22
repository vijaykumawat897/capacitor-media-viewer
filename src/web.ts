import { WebPlugin } from '@capacitor/core';
import type {
  MediaViewerPlugin,
  ShowMediaViewerOptions,
  PlaybackState,
} from '../capacitor.plugin';

export class MediaViewerWeb extends WebPlugin implements MediaViewerPlugin {
  private modalElement: HTMLElement | null = null;
  private videoElement: HTMLVideoElement | null = null;
  private imageElement: HTMLImageElement | null = null;
  private currentIndex = 0;
  private mediaItems: any[] = [];
  private currentQuality = '';
  private playbackStateInterval: number | null = null;

  async show(options: ShowMediaViewerOptions): Promise<void> {
    this.mediaItems = options.items;
    this.currentIndex = options.currentIndex;

    // Create modal container
    this.modalElement = document.createElement('div');
    this.modalElement.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.95);
      z-index: 10000;
      display: flex;
      align-items: center;
      justify-content: center;
    `;

    // Create close button
    const closeBtn = document.createElement('button');
    closeBtn.innerHTML = '✕';
    closeBtn.style.cssText = `
      position: absolute;
      top: 20px;
      right: 20px;
      background: rgba(255, 255, 255, 0.2);
      border: none;
      color: white;
      width: 40px;
      height: 40px;
      border-radius: 50%;
      cursor: pointer;
      font-size: 24px;
      z-index: 10001;
    `;
    closeBtn.onclick = () => this.dismiss();

    // Create media container
    const mediaContainer = document.createElement('div');
    mediaContainer.style.cssText = `
      position: relative;
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: hidden;
    `;

    this.modalElement.appendChild(closeBtn);
    this.modalElement.appendChild(mediaContainer);
    document.body.appendChild(this.modalElement);

    // Add touch handlers for swipe
    let touchStartX = 0;
    let touchEndX = 0;

    const handleTouchStart = (e: TouchEvent) => {
      touchStartX = e.changedTouches[0].screenX;
    };

    const handleTouchEnd = (e: TouchEvent) => {
      touchEndX = e.changedTouches[0].screenX;
      this.handleSwipe(touchStartX, touchEndX);
    };

    mediaContainer.addEventListener('touchstart', handleTouchStart);
    mediaContainer.addEventListener('touchend', handleTouchEnd);

    this.renderCurrentMedia();

    // Prevent body scroll
    document.body.style.overflow = 'hidden';
  }

  private handleSwipe(startX: number, endX: number): void {
    const threshold = 50;
    const diff = startX - endX;

    if (Math.abs(diff) > threshold) {
      if (diff > 0 && this.currentIndex < this.mediaItems.length - 1) {
        // Swipe left - next
        this.currentIndex++;
        this.renderCurrentMedia();
        this.notifyListeners('mediaIndexChanged', { index: this.currentIndex });
      } else if (diff < 0 && this.currentIndex > 0) {
        // Swipe right - previous
        this.currentIndex--;
        this.renderCurrentMedia();
        this.notifyListeners('mediaIndexChanged', { index: this.currentIndex });
      }
    }
  }

  private renderCurrentMedia(): void {
    if (!this.modalElement) return;

    const mediaContainer = this.modalElement.querySelector('div:last-child') as HTMLElement;
    if (!mediaContainer) return;

    // Clear previous media
    if (this.videoElement) {
      this.videoElement.remove();
      this.videoElement = null;
    }
    if (this.imageElement) {
      this.imageElement.remove();
      this.imageElement = null;
    }

    if (this.playbackStateInterval) {
      clearInterval(this.playbackStateInterval);
      this.playbackStateInterval = null;
    }

    const currentItem = this.mediaItems[this.currentIndex];
    if (!currentItem) return;

    if (currentItem.type === 'video') {
      this.videoElement = document.createElement('video');
      this.videoElement.src = currentItem.url;
      this.videoElement.controls = true;
      this.videoElement.style.cssText = `
        max-width: 100%;
        max-height: 100%;
        width: auto;
        height: auto;
      `;

      mediaContainer.appendChild(this.videoElement);

      // Start monitoring playback state
      this.startPlaybackStateMonitoring();
    } else {
      this.imageElement = document.createElement('img');
      this.imageElement.src = currentItem.url;
      this.imageElement.style.cssText = `
        max-width: 100%;
        max-height: 100%;
        width: auto;
        height: auto;
        object-fit: contain;
      `;

      mediaContainer.appendChild(this.imageElement);
    }

    // Update title if available
    if (currentItem.title) {
      // You can add title display logic here
    }
  }

  private startPlaybackStateMonitoring(): void {
    if (!this.videoElement) return;

    this.playbackStateInterval = window.setInterval(() => {
      if (this.videoElement) {
        const state: PlaybackState = {
          isPlaying: !this.videoElement.paused,
          currentTime: this.videoElement.currentTime,
          duration: this.videoElement.duration,
          currentQuality: this.currentQuality || undefined,
        };
        this.notifyListeners('playbackStateChanged', state);
      }
    }, 500);
  }

  async dismiss(): Promise<void> {
    if (this.playbackStateInterval) {
      clearInterval(this.playbackStateInterval);
      this.playbackStateInterval = null;
    }

    if (this.modalElement) {
      this.modalElement.remove();
      this.modalElement = null;
    }

    this.videoElement = null;
    this.imageElement = null;

    document.body.style.overflow = '';
    this.notifyListeners('viewerDismissed', undefined);
  }

  async play(): Promise<void> {
    if (this.videoElement) {
      await this.videoElement.play();
    }
  }

  async pause(): Promise<void> {
    if (this.videoElement) {
      this.videoElement.pause();
    }
  }

  async seek(options: { time: number }): Promise<void> {
    if (this.videoElement) {
      this.videoElement.currentTime = options.time;
    }
  }

  async setQuality(options: { quality: string }): Promise<void> {
    const currentItem = this.mediaItems[this.currentIndex];
    if (currentItem && currentItem.qualityVariants) {
      const variant = currentItem.qualityVariants.find(
        (v: any) => v.label === options.quality
      );
      if (variant && this.videoElement) {
        this.videoElement.src = variant.url;
        this.currentQuality = options.quality;
        await this.videoElement.load();
      }
    }
  }

  async getPlaybackState(): Promise<PlaybackState> {
    if (this.videoElement) {
      return {
        isPlaying: !this.videoElement.paused,
        currentTime: this.videoElement.currentTime,
        duration: this.videoElement.duration,
        currentQuality: this.currentQuality || undefined,
      };
    }
    return {
      isPlaying: false,
      currentTime: 0,
      duration: 0,
    };
  }
}

