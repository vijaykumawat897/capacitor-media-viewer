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
  private imageContainer: HTMLElement | null = null;
  private currentIndex = 0;
  private mediaItems: any[] = [];
  private currentQuality = '';
  private playbackStateInterval: number | null = null;
  private imageScale = 1;
  private imageTranslateX = 0;
  private imageTranslateY = 0;
  private isImageDragging = false;
  private dragStartX = 0;
  private dragStartY = 0;
  private lastTouchDistance = 0;

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
    if (this.imageContainer) {
      this.imageContainer.remove();
      this.imageContainer = null;
      this.imageElement = null;
    }

    if (this.playbackStateInterval) {
      clearInterval(this.playbackStateInterval);
      this.playbackStateInterval = null;
    }

    const currentItem = this.mediaItems[this.currentIndex];
    if (!currentItem) return;

    if (currentItem.type === 'VIDEO') {
      this.videoElement = document.createElement('video');
      this.videoElement.src = currentItem.path;
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
      // Create container for zoomable image
      this.imageContainer = document.createElement('div');
      this.imageContainer.style.cssText = `
        position: relative;
        width: 100%;
        height: 100%;
        overflow: hidden;
        touch-action: none;
      `;

      this.imageElement = document.createElement('img');
      this.imageElement.src = currentItem.path;
      this.imageElement.style.cssText = `
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        max-width: 100%;
        max-height: 100%;
        width: auto;
        height: auto;
        object-fit: contain;
        user-select: none;
        touch-action: none;
      `;

      // Reset zoom state
      this.imageScale = 1;
      this.imageTranslateX = 0;
      this.imageTranslateY = 0;

      this.imageContainer.appendChild(this.imageElement);
      mediaContainer.appendChild(this.imageContainer);

      // Add zoom event handlers
      this.setupImageZoom();
    }

    // Update alt text if available
    if (currentItem.alt) {
      if (this.imageElement) {
        this.imageElement.alt = currentItem.alt;
      }
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
    this.imageContainer = null;

    document.body.style.overflow = '';
    this.notifyListeners('viewerDismissed', undefined);
  }

  private setupImageZoom(): void {
    if (!this.imageElement || !this.imageContainer) return;

    let lastTapTime = 0;
    let lastTapX = 0;
    let lastTapY = 0;

    // Double tap to zoom
    this.imageElement.addEventListener('click', (e) => {
      const currentTime = Date.now();
      const tapX = e.clientX;
      const tapY = e.clientY;

      if (currentTime - lastTapTime < 300 && 
          Math.abs(tapX - lastTapX) < 10 && 
          Math.abs(tapY - lastTapY) < 10) {
        // Double tap detected
        e.preventDefault();
        e.stopPropagation();
        
        if (this.imageScale > 1) {
          // Zoom out
          this.imageScale = 1;
          this.imageTranslateX = 0;
          this.imageTranslateY = 0;
        } else {
          // Zoom in
          const rect = this.imageElement!.getBoundingClientRect();
          const containerRect = this.imageContainer!.getBoundingClientRect();
          const centerX = containerRect.width / 2;
          const centerY = containerRect.height / 2;
          
          this.imageScale = 2;
          this.imageTranslateX = (centerX - tapX) * 2;
          this.imageTranslateY = (centerY - tapY) * 2;
        }
        this.updateImageTransform();
      }

      lastTapTime = currentTime;
      lastTapX = tapX;
      lastTapY = tapY;
    });

    // Pinch zoom
    let initialDistance = 0;
    let initialScale = 1;
    let initialTranslateX = 0;
    let initialTranslateY = 0;

    this.imageContainer.addEventListener('touchstart', (e) => {
      if (e.touches.length === 2) {
        // Pinch gesture
        const touch1 = e.touches[0];
        const touch2 = e.touches[1];
        initialDistance = Math.hypot(
          touch2.clientX - touch1.clientX,
          touch2.clientY - touch1.clientY
        );
        initialScale = this.imageScale;
        initialTranslateX = this.imageTranslateX;
        initialTranslateY = this.imageTranslateY;
      } else if (e.touches.length === 1 && this.imageScale > 1) {
        // Single touch drag when zoomed
        this.isImageDragging = true;
        this.dragStartX = e.touches[0].clientX - this.imageTranslateX;
        this.dragStartY = e.touches[0].clientY - this.imageTranslateY;
      }
    });

    this.imageContainer.addEventListener('touchmove', (e) => {
      e.preventDefault();
      
      if (e.touches.length === 2) {
        // Pinch zoom
        const touch1 = e.touches[0];
        const touch2 = e.touches[1];
        const currentDistance = Math.hypot(
          touch2.clientX - touch1.clientX,
          touch2.clientY - touch1.clientY
        );
        
        const scaleChange = currentDistance / initialDistance;
        this.imageScale = Math.max(1, Math.min(4, initialScale * scaleChange));
        
        // Calculate center point for zoom
        const centerX = (touch1.clientX + touch2.clientX) / 2;
        const centerY = (touch1.clientY + touch2.clientY) / 2;
        const containerRect = this.imageContainer!.getBoundingClientRect();
        const relativeX = centerX - containerRect.left - containerRect.width / 2;
        const relativeY = centerY - containerRect.top - containerRect.height / 2;
        
        this.imageTranslateX = initialTranslateX + (relativeX * (this.imageScale - initialScale));
        this.imageTranslateY = initialTranslateY + (relativeY * (this.imageScale - initialScale));
        
        this.updateImageTransform();
      } else if (e.touches.length === 1 && this.isImageDragging && this.imageScale > 1) {
        // Drag when zoomed
        this.imageTranslateX = e.touches[0].clientX - this.dragStartX;
        this.imageTranslateY = e.touches[0].clientY - this.dragStartY;
        this.updateImageTransform();
      }
    });

    this.imageContainer.addEventListener('touchend', () => {
      this.isImageDragging = false;
      this.constrainImagePosition();
    });

    // Mouse wheel zoom
    this.imageContainer.addEventListener('wheel', (e) => {
      e.preventDefault();
      const delta = e.deltaY > 0 ? 0.9 : 1.1;
      const newScale = Math.max(1, Math.min(4, this.imageScale * delta));
      
      if (newScale !== this.imageScale) {
        const rect = this.imageContainer!.getBoundingClientRect();
        const mouseX = e.clientX - rect.left - rect.width / 2;
        const mouseY = e.clientY - rect.top - rect.height / 2;
        
        this.imageScale = newScale;
        this.imageTranslateX = mouseX * (1 - this.imageScale);
        this.imageTranslateY = mouseY * (1 - this.imageScale);
        this.updateImageTransform();
        this.constrainImagePosition();
      }
    });
  }

  private updateImageTransform(): void {
    if (!this.imageElement) return;
    
    this.imageElement.style.transform = `
      translate(-50%, -50%)
      translate(${this.imageTranslateX}px, ${this.imageTranslateY}px)
      scale(${this.imageScale})
    `;
  }

  private constrainImagePosition(): void {
    if (!this.imageElement || !this.imageContainer || this.imageScale <= 1) {
      this.imageTranslateX = 0;
      this.imageTranslateY = 0;
      this.updateImageTransform();
      return;
    }

    const imgRect = this.imageElement.getBoundingClientRect();
    const containerRect = this.imageContainer.getBoundingClientRect();
    
    const maxX = (imgRect.width - containerRect.width) / 2;
    const maxY = (imgRect.height - containerRect.height) / 2;
    
    this.imageTranslateX = Math.max(-maxX, Math.min(maxX, this.imageTranslateX));
    this.imageTranslateY = Math.max(-maxY, Math.min(maxY, this.imageTranslateY));
    
    this.updateImageTransform();
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
    // Quality selection removed - quality variants no longer supported
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

