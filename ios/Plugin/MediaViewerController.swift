import UIKit
import AVKit
import AVFoundation

class MediaViewerController: UIViewController, UIScrollViewDelegate, UIGestureRecognizerDelegate {
    private var mediaItems: [MediaItem] = []
    private var currentIndex: Int = 0
    private var titleText: String = ""
    private weak var plugin: MediaViewerPlugin?
    
    // Views
    private var containerView: UIView!
    private var videoContainer: UIView!
    private var playerLayer: AVPlayerLayer?
    private var imageScrollView: UIScrollView?
    private var imageView: UIImageView?
    private var videoThumbnail: UIImageView?
    private var overlayContainer: UIView!
    
    // Video player
    private var player: AVPlayer?
    private var playerItem: AVPlayerItem?
    private var playbackObserver: NSKeyValueObservation?
    private var timeObserver: Any?
    private var playbackTimer: Timer?
    
    // Controls
    private var controlsContainer: UIView!
    private var centerControls: UIView!
    private var backButton: UIButton!
    private var settingsButton: UIButton!
    private var playPauseButton: UIButton!
    private var loadingSpinner: UIActivityIndicatorView!
    private var prevButton: UIButton!
    private var nextButton: UIButton!
    private var volumeButton: UIButton!
    private var fullscreenButton: UIButton!
    private var seekBar: UISlider!
    private var currentTimeLabel: UILabel!
    private var durationLabel: UILabel!
    private var controlsVisible = true
    private var hideControlsTimer: Timer?
    
    // State
    private var currentQuality: String = "Auto"
    private var actualPlayingQuality: String?
    private var currentPlaybackSpeed: Float = 1.0
    private var captionsEnabled = false
    private var playbackEnded = false
    private var errorRetryCount = 0
    private let maxRetryCount = 3
    private var qualityVariants: [QualityVariant] = []
    private var currentVideoUrl: String?
    
    // Gestures
    private var panGestureRecognizer: UIPanGestureRecognizer!
    private var tapGestureRecognizer: UITapGestureRecognizer!
    private var doubleTapGestureRecognizer: UITapGestureRecognizer!
    private var initialTouchPoint: CGPoint = CGPoint.zero
    private var isSwiping = false
    private var swipeStartX: CGFloat = 0
    private var swipeTotalDistance: CGFloat = 0
    private var swipeDirection: Int = 0 // -1 for left (next), 1 for right (previous)
    
    // Screen wake lock
    private var keepScreenOn = false
    
    init(mediaItems: [MediaItem], currentIndex: Int, title: String, plugin: MediaViewerPlugin) {
        self.mediaItems = mediaItems
        self.currentIndex = currentIndex
        self.titleText = title
        self.plugin = plugin
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        setupGestures()
        displayCurrentMedia()
        
        // Keep screen on during playback
        keepScreenAwake()
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        allowScreenToSleep()
    }
    
    override var prefersStatusBarHidden: Bool {
        return true
    }
    
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        return .all
    }
    
    private func keepScreenAwake() {
        keepScreenOn = true
        UIApplication.shared.isIdleTimerDisabled = true
    }
    
    private func allowScreenToSleep() {
        keepScreenOn = false
        UIApplication.shared.isIdleTimerDisabled = false
    }
    
    private func setupUI() {
        view.backgroundColor = .black
        
        // Container for video/image
        containerView = UIView()
        containerView.translatesAutoresizingMaskIntoConstraints = false
        containerView.backgroundColor = .black
        view.addSubview(containerView)
        
        // Video container
        videoContainer = UIView()
        videoContainer.translatesAutoresizingMaskIntoConstraints = false
        videoContainer.backgroundColor = .black
        containerView.addSubview(videoContainer)
        
        // Video thumbnail
        videoThumbnail = UIImageView()
        videoThumbnail?.contentMode = .scaleAspectFit
        videoThumbnail?.translatesAutoresizingMaskIntoConstraints = false
        videoThumbnail?.backgroundColor = .black
        videoContainer.addSubview(videoThumbnail!)
        
        // Overlay container for controls
        overlayContainer = UIView()
        overlayContainer.translatesAutoresizingMaskIntoConstraints = false
        overlayContainer.backgroundColor = .clear
        overlayContainer.isUserInteractionEnabled = true
        view.addSubview(overlayContainer)
        
        NSLayoutConstraint.activate([
            containerView.topAnchor.constraint(equalTo: view.topAnchor),
            containerView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            containerView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            containerView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            
            videoContainer.topAnchor.constraint(equalTo: containerView.topAnchor),
            videoContainer.leadingAnchor.constraint(equalTo: containerView.leadingAnchor),
            videoContainer.trailingAnchor.constraint(equalTo: containerView.trailingAnchor),
            videoContainer.bottomAnchor.constraint(equalTo: containerView.bottomAnchor),
            
            videoThumbnail!.topAnchor.constraint(equalTo: videoContainer.topAnchor),
            videoThumbnail!.leadingAnchor.constraint(equalTo: videoContainer.leadingAnchor),
            videoThumbnail!.trailingAnchor.constraint(equalTo: videoContainer.trailingAnchor),
            videoThumbnail!.bottomAnchor.constraint(equalTo: videoContainer.bottomAnchor),
            
            overlayContainer.topAnchor.constraint(equalTo: view.topAnchor),
            overlayContainer.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            overlayContainer.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            overlayContainer.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        
        setupControls()
    }
    
    private func setupControls() {
        // Back button
        backButton = UIButton(type: .system)
        backButton.setImage(UIImage(systemName: "chevron.left"), for: .normal)
        backButton.tintColor = .white
        backButton.translatesAutoresizingMaskIntoConstraints = false
        backButton.addTarget(self, action: #selector(closeButtonTapped), for: .touchUpInside)
        overlayContainer.addSubview(backButton)
        
        // Settings button
        settingsButton = UIButton(type: .system)
        settingsButton.setImage(UIImage(systemName: "gearshape.fill"), for: .normal)
        settingsButton.tintColor = .white
        settingsButton.translatesAutoresizingMaskIntoConstraints = false
        settingsButton.addTarget(self, action: #selector(showSettingsPopup), for: .touchUpInside)
        overlayContainer.addSubview(settingsButton)
        
        // Center controls
        centerControls = UIView()
        centerControls.translatesAutoresizingMaskIntoConstraints = false
        overlayContainer.addSubview(centerControls)
        
        // Previous button
        prevButton = UIButton(type: .system)
        prevButton.setImage(UIImage(systemName: "backward.fill"), for: .normal)
        prevButton.tintColor = .white
        prevButton.translatesAutoresizingMaskIntoConstraints = false
        prevButton.addTarget(self, action: #selector(showPrevious), for: .touchUpInside)
        centerControls.addSubview(prevButton)
        
        // Play/Pause button container
        let playPauseContainer = UIView()
        playPauseContainer.translatesAutoresizingMaskIntoConstraints = false
        centerControls.addSubview(playPauseContainer)
        
        playPauseButton = UIButton(type: .system)
        playPauseButton.setImage(UIImage(systemName: "play.fill"), for: .normal)
        playPauseButton.tintColor = .white
        playPauseButton.translatesAutoresizingMaskIntoConstraints = false
        playPauseButton.addTarget(self, action: #selector(togglePlayPause), for: .touchUpInside)
        playPauseContainer.addSubview(playPauseButton)
        
        loadingSpinner = UIActivityIndicatorView(style: .large)
        loadingSpinner.color = .white
        loadingSpinner.translatesAutoresizingMaskIntoConstraints = false
        loadingSpinner.hidesWhenStopped = true
        playPauseContainer.addSubview(loadingSpinner)
        
        // Next button
        nextButton = UIButton(type: .system)
        nextButton.setImage(UIImage(systemName: "forward.fill"), for: .normal)
        nextButton.tintColor = .white
        nextButton.translatesAutoresizingMaskIntoConstraints = false
        nextButton.addTarget(self, action: #selector(showNext), for: .touchUpInside)
        centerControls.addSubview(nextButton)
        
        // Bottom controls container
        controlsContainer = UIView()
        controlsContainer.translatesAutoresizingMaskIntoConstraints = false
        controlsContainer.backgroundColor = UIColor(red: 0, green: 0, blue: 0, alpha: 0.5) // #80000000
        overlayContainer.addSubview(controlsContainer)
        
        // Seek bar container
        let seekBarContainer = UIView()
        seekBarContainer.translatesAutoresizingMaskIntoConstraints = false
        controlsContainer.addSubview(seekBarContainer)
        
        currentTimeLabel = UILabel()
        currentTimeLabel.text = "0:00"
        currentTimeLabel.textColor = .white
        currentTimeLabel.font = .systemFont(ofSize: 12)
        currentTimeLabel.translatesAutoresizingMaskIntoConstraints = false
        seekBarContainer.addSubview(currentTimeLabel)
        
        seekBar = UISlider()
        seekBar.minimumValue = 0
        seekBar.maximumValue = 1000
        seekBar.value = 0
        seekBar.translatesAutoresizingMaskIntoConstraints = false
        seekBar.addTarget(self, action: #selector(seekBarValueChanged(_:)), for: .valueChanged)
        seekBar.addTarget(self, action: #selector(seekBarTouchDown), for: .touchDown)
        seekBar.addTarget(self, action: #selector(seekBarTouchUp), for: [.touchUpInside, .touchUpOutside])
        seekBarContainer.addSubview(seekBar)
        
        durationLabel = UILabel()
        durationLabel.text = "0:00"
        durationLabel.textColor = .white
        durationLabel.font = .systemFont(ofSize: 12)
        durationLabel.translatesAutoresizingMaskIntoConstraints = false
        seekBarContainer.addSubview(durationLabel)
        
        // Bottom row
        let bottomRow = UIView()
        bottomRow.translatesAutoresizingMaskIntoConstraints = false
        controlsContainer.addSubview(bottomRow)
        
        volumeButton = UIButton(type: .system)
        volumeButton.setImage(UIImage(systemName: "speaker.wave.3.fill"), for: .normal)
        volumeButton.tintColor = .white
        volumeButton.translatesAutoresizingMaskIntoConstraints = false
        volumeButton.addTarget(self, action: #selector(toggleVolume), for: .touchUpInside)
        bottomRow.addSubview(volumeButton)
        
        fullscreenButton = UIButton(type: .system)
        fullscreenButton.setImage(UIImage(systemName: "arrow.up.left.and.arrow.down.right"), for: .normal)
        fullscreenButton.tintColor = .white
        fullscreenButton.translatesAutoresizingMaskIntoConstraints = false
        bottomRow.addSubview(fullscreenButton)
        
        // Spacer view between volume and fullscreen buttons (like Android's weight=1 View)
        let spacerView = UIView()
        spacerView.translatesAutoresizingMaskIntoConstraints = false
        bottomRow.addSubview(spacerView)
        
        // Layout constraints
        NSLayoutConstraint.activate([
            backButton.topAnchor.constraint(equalTo: overlayContainer.safeAreaLayoutGuide.topAnchor, constant: 20),
            backButton.leadingAnchor.constraint(equalTo: overlayContainer.leadingAnchor, constant: 20),
            backButton.widthAnchor.constraint(equalToConstant: 44),
            backButton.heightAnchor.constraint(equalToConstant: 44),
            
            settingsButton.topAnchor.constraint(equalTo: overlayContainer.safeAreaLayoutGuide.topAnchor, constant: 20),
            settingsButton.trailingAnchor.constraint(equalTo: overlayContainer.trailingAnchor, constant: -20),
            settingsButton.widthAnchor.constraint(equalToConstant: 44),
            settingsButton.heightAnchor.constraint(equalToConstant: 44),
            
            centerControls.centerXAnchor.constraint(equalTo: overlayContainer.centerXAnchor),
            centerControls.centerYAnchor.constraint(equalTo: overlayContainer.centerYAnchor),
            centerControls.heightAnchor.constraint(equalToConstant: 80),
            
            prevButton.leadingAnchor.constraint(equalTo: centerControls.leadingAnchor),
            prevButton.centerYAnchor.constraint(equalTo: centerControls.centerYAnchor),
            prevButton.widthAnchor.constraint(equalToConstant: 60),
            prevButton.heightAnchor.constraint(equalToConstant: 60),
            
            playPauseContainer.leadingAnchor.constraint(equalTo: prevButton.trailingAnchor, constant: 30),
            playPauseContainer.centerYAnchor.constraint(equalTo: centerControls.centerYAnchor),
            playPauseContainer.widthAnchor.constraint(equalToConstant: 80),
            playPauseContainer.heightAnchor.constraint(equalToConstant: 80),
            
            playPauseButton.centerXAnchor.constraint(equalTo: playPauseContainer.centerXAnchor),
            playPauseButton.centerYAnchor.constraint(equalTo: playPauseContainer.centerYAnchor),
            playPauseButton.widthAnchor.constraint(equalToConstant: 80),
            playPauseButton.heightAnchor.constraint(equalToConstant: 80),
            
            loadingSpinner.centerXAnchor.constraint(equalTo: playPauseContainer.centerXAnchor),
            loadingSpinner.centerYAnchor.constraint(equalTo: playPauseContainer.centerYAnchor),
            loadingSpinner.widthAnchor.constraint(equalToConstant: 48),
            loadingSpinner.heightAnchor.constraint(equalToConstant: 48),
            
            nextButton.leadingAnchor.constraint(equalTo: playPauseContainer.trailingAnchor, constant: 30),
            nextButton.centerYAnchor.constraint(equalTo: centerControls.centerYAnchor),
            nextButton.widthAnchor.constraint(equalToConstant: 60),
            nextButton.heightAnchor.constraint(equalToConstant: 60),
            nextButton.trailingAnchor.constraint(equalTo: centerControls.trailingAnchor),
            
            controlsContainer.leadingAnchor.constraint(equalTo: overlayContainer.leadingAnchor),
            controlsContainer.trailingAnchor.constraint(equalTo: overlayContainer.trailingAnchor),
            controlsContainer.bottomAnchor.constraint(equalTo: overlayContainer.safeAreaLayoutGuide.bottomAnchor),
            
            seekBarContainer.leadingAnchor.constraint(equalTo: controlsContainer.leadingAnchor, constant: 20),
            seekBarContainer.trailingAnchor.constraint(equalTo: controlsContainer.trailingAnchor, constant: -20),
            seekBarContainer.topAnchor.constraint(equalTo: controlsContainer.topAnchor, constant: 10),
            seekBarContainer.heightAnchor.constraint(equalToConstant: 44),
            
            currentTimeLabel.leadingAnchor.constraint(equalTo: seekBarContainer.leadingAnchor),
            currentTimeLabel.centerYAnchor.constraint(equalTo: seekBarContainer.centerYAnchor),
            currentTimeLabel.widthAnchor.constraint(equalToConstant: 50),
            
            seekBar.leadingAnchor.constraint(equalTo: currentTimeLabel.trailingAnchor, constant: 10),
            seekBar.trailingAnchor.constraint(equalTo: durationLabel.leadingAnchor, constant: -10),
            seekBar.centerYAnchor.constraint(equalTo: seekBarContainer.centerYAnchor),
            
            durationLabel.trailingAnchor.constraint(equalTo: seekBarContainer.trailingAnchor),
            durationLabel.centerYAnchor.constraint(equalTo: seekBarContainer.centerYAnchor),
            durationLabel.widthAnchor.constraint(equalToConstant: 50),
            
            bottomRow.leadingAnchor.constraint(equalTo: controlsContainer.leadingAnchor, constant: 20),
            bottomRow.trailingAnchor.constraint(equalTo: controlsContainer.trailingAnchor, constant: -20),
            bottomRow.topAnchor.constraint(equalTo: seekBarContainer.bottomAnchor, constant: 10),
            bottomRow.bottomAnchor.constraint(equalTo: controlsContainer.bottomAnchor, constant: -20),
            bottomRow.heightAnchor.constraint(equalToConstant: 44),
            
            volumeButton.leadingAnchor.constraint(equalTo: bottomRow.leadingAnchor),
            volumeButton.centerYAnchor.constraint(equalTo: bottomRow.centerYAnchor),
            volumeButton.widthAnchor.constraint(greaterThanOrEqualToConstant: 44),
            volumeButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 44),
            
            fullscreenButton.trailingAnchor.constraint(equalTo: bottomRow.trailingAnchor),
            fullscreenButton.centerYAnchor.constraint(equalTo: bottomRow.centerYAnchor),
            fullscreenButton.widthAnchor.constraint(greaterThanOrEqualToConstant: 44),
            fullscreenButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 44),
            
            // Spacer view between volume and fullscreen buttons (like Android's weight=1 View)
            spacerView.leadingAnchor.constraint(equalTo: volumeButton.trailingAnchor),
            spacerView.trailingAnchor.constraint(equalTo: fullscreenButton.leadingAnchor),
            spacerView.centerYAnchor.constraint(equalTo: bottomRow.centerYAnchor),
            spacerView.heightAnchor.constraint(equalToConstant: 1)
        ])
    }
    
    private func setupGestures() {
        // Pan gesture for swipes
        panGestureRecognizer = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        panGestureRecognizer.delegate = self
        overlayContainer.addGestureRecognizer(panGestureRecognizer)
        
        // Tap gesture to toggle controls
        tapGestureRecognizer = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
        tapGestureRecognizer.delegate = self
        overlayContainer.addGestureRecognizer(tapGestureRecognizer)
        
        // Double tap for fast forward/rewind on video
        doubleTapGestureRecognizer = UITapGestureRecognizer(target: self, action: #selector(handleDoubleTap(_:)))
        doubleTapGestureRecognizer.numberOfTapsRequired = 2
        doubleTapGestureRecognizer.delegate = self
        overlayContainer.addGestureRecognizer(doubleTapGestureRecognizer)
        
        tapGestureRecognizer.require(toFail: doubleTapGestureRecognizer)
    }
    
    @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
        // Don't handle pan if image is zoomed
        if let scrollView = imageScrollView, scrollView.zoomScale > scrollView.minimumZoomScale {
            return
        }
        
        let translation = gesture.translation(in: view)
        
        switch gesture.state {
        case .began:
            if isSwiping {
                cancelCurrentSwipe()
            }
            initialTouchPoint = gesture.location(in: view)
            swipeStartX = initialTouchPoint.x
            
            // Don't start swipe if touching controls
            if isPointInsideView(controlsContainer, point: initialTouchPoint) ||
               isPointInsideView(centerControls, point: initialTouchPoint) ||
               isPointInsideView(backButton, point: initialTouchPoint) ||
               isPointInsideView(settingsButton, point: initialTouchPoint) {
                return
            }
            
        case .changed:
            if !isSwiping && abs(translation.x) > 50 && abs(translation.x) > abs(translation.y) * 1.5 {
                // Start swipe
                let direction = translation.x > 0 ? 1 : -1
                if (direction > 0 && currentIndex > 0) || (direction < 0 && currentIndex < mediaItems.count - 1) {
                    swipeTotalDistance = translation.x
                    startSwipeAnimation(direction: direction, initialOffset: translation.x)
                }
            }
            
            if isSwiping {
                swipeTotalDistance = translation.x
                updateSwipeProgress(swipeTotalDistance)
            }
            
        case .ended, .cancelled:
            if isSwiping {
                let velocity = gesture.velocity(in: view)
                completeSwipeAnimation(velocity: velocity.x)
            }
            
        default:
            break
        }
    }
    
    @objc private func handleTap(_ gesture: UITapGestureRecognizer) {
        let location = gesture.location(in: view)
        
        // Don't toggle if tapping on controls
        if isPointInsideView(controlsContainer, point: location) ||
           isPointInsideView(centerControls, point: location) ||
           isPointInsideView(backButton, point: location) ||
           isPointInsideView(settingsButton, point: location) {
            return
        }
        
        // Only toggle controls for video
        if player != nil {
            toggleControls()
        }
    }
    
    @objc private func handleDoubleTap(_ gesture: UITapGestureRecognizer) {
        guard let player = player, player.rate > 0 || !playbackEnded else { return }
        
        let location = gesture.location(in: view)
        let screenWidth = view.bounds.width
        let halfScreen = screenWidth / 2
        
        let currentTime = CMTimeGetSeconds(player.currentTime())
        let duration = CMTimeGetSeconds(player.currentItem?.duration ?? CMTime.zero)
        
        if location.x > halfScreen {
            // Double-tap on right side - fast forward 10 seconds
            let newTime = min(currentTime + 10, duration)
            seek(to: newTime)
        } else {
            // Double-tap on left side - rewind 10 seconds
            let newTime = max(currentTime - 10, 0)
            seek(to: newTime)
        }
    }
    
    private func isPointInsideView(_ view: UIView, point: CGPoint) -> Bool {
        let frame = view.convert(view.bounds, to: self.view)
        return frame.contains(point)
    }
    
    @objc private func closeButtonTapped() {
        dismiss(animated: true) {
            self.plugin?.notifyViewerDismissed()
        }
    }
    
    @objc private func showPrevious() {
        if currentIndex > 0 {
            currentIndex -= 1
            displayCurrentMedia()
        }
    }
    
    @objc private func showNext() {
        if currentIndex < mediaItems.count - 1 {
            currentIndex += 1
            displayCurrentMedia()
        }
    }
    
    @objc private func togglePlayPause() {
        guard let player = player else { return }
        
        if playbackEnded {
            // Restart playback from beginning
            seek(to: 0)
            player.play()
            playbackEnded = false
            showControls()
        } else if player.rate > 0 {
            player.pause()
        } else {
            player.play()
        }
        scheduleControlsHide()
    }
    
    @objc private func toggleVolume() {
        // Toggle mute/unmute
        guard let player = player else { return }
        player.isMuted.toggle()
        volumeButton.setImage(
            UIImage(systemName: player.isMuted ? "speaker.slash.fill" : "speaker.wave.3.fill"),
            for: .normal
        )
    }
    
    @objc private func seekBarValueChanged(_ slider: UISlider) {
        guard let player = player else { return }
        let duration = CMTimeGetSeconds(player.currentItem?.duration ?? CMTime.zero)
        if duration > 0 {
            let time = Double(slider.value) * duration / 1000.0
            updateCurrentTimeText(time)
        }
    }
    
    @objc private func seekBarTouchDown() {
        scheduleControlsHide()
    }
    
    @objc private func seekBarTouchUp() {
        guard let player = player else { return }
        let duration = CMTimeGetSeconds(player.currentItem?.duration ?? CMTime.zero)
        if duration > 0 {
            let time = Double(seekBar.value) * duration / 1000.0
            seek(to: time)
        }
        scheduleControlsHide()
    }
    
    private func displayCurrentMedia() {
        guard currentIndex >= 0 && currentIndex < mediaItems.count else { return }
        
        releasePlayer()
        resetMediaViews()
        
        let item = mediaItems[currentIndex]
        
        // Reset quality to Auto for new video
        if item.type == "VIDEO" {
            currentQuality = "Auto"
            actualPlayingQuality = nil
            currentVideoUrl = item.path
            
            // Parse HLS playlist if it's an HLS video
            if HlsPlaylistParser.isHlsUrl(item.path) {
                HlsPlaylistParser.parseMasterPlaylist(item.path) { [weak self] variants in
                    DispatchQueue.main.async {
                        self?.qualityVariants = variants
                        item.qualityVariants = variants
                    }
                }
            } else {
                qualityVariants = []
            }
        }
        
        if item.type == "VIDEO" {
            displayVideo(item)
        } else {
            displayImage(item)
        }
        
        plugin?.notifyMediaIndexChanged(currentIndex)
    }
    
    private func resetMediaViews() {
        if let playerLayer = playerLayer {
            playerLayer.removeFromSuperlayer()
            self.playerLayer = nil
        }
        imageView?.image = nil
        imageView?.removeFromSuperview()
        imageView = nil
        imageScrollView?.removeFromSuperview()
        imageScrollView = nil
        videoThumbnail?.image = nil
        videoThumbnail?.isHidden = true
    }
    
    private func displayVideo(_ item: MediaItem) {
        guard let url = URL(string: item.path) else { return }
        
        videoThumbnail?.isHidden = false
        
        // Show thumbnail if available
        if let thumbnailUrlString = item.thumbnail, let thumbnailUrl = URL(string: thumbnailUrlString) {
            loadImage(from: thumbnailUrl) { [weak self] image in
                DispatchQueue.main.async {
                    self?.videoThumbnail?.image = image
                    self?.videoThumbnail?.isHidden = image == nil
                }
            }
        } else {
            videoThumbnail?.isHidden = true
        }
        
        // Show controls
        showControls()
        
        // Setup player layer
        if playerLayer == nil {
            playerLayer = AVPlayerLayer()
            playerLayer?.videoGravity = .resizeAspect
            videoContainer.layer.addSublayer(playerLayer!)
        }
        
        // Create player item
        let asset = AVURLAsset(url: url)
        playerItem = AVPlayerItem(asset: asset)
        playerItem?.audioTimePitchAlgorithm = .timeDomain
        
        // Create player
        player = AVPlayer(playerItem: playerItem)
        playerLayer?.player = player
        
        // Set playback speed
        player?.rate = currentPlaybackSpeed
        
        // Setup observers
        setupPlayerObservers()
        
        // Start playback
        player?.play()
        
        // Start monitoring
        startPlaybackStateMonitoring()
    }
    
    private func setupPlayerObservers() {
        guard let player = player, let playerItem = playerItem else { return }
        
        // Playback state observer
        playbackObserver = playerItem.observe(\.status) { [weak self] item, _ in
            DispatchQueue.main.async {
                self?.handlePlayerStatusChange(item.status)
            }
        }
        
        // Time observer
        let interval = CMTime(seconds: 0.5, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
        timeObserver = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            self?.updatePlaybackState()
        }
        
        // Notification observers
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(playerDidFinishPlaying),
            name: .AVPlayerItemDidPlayToEndTime,
            object: playerItem
        )
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(playerFailedToPlay),
            name: .AVPlayerItemFailedToPlayToEndTime,
            object: playerItem
        )
    }
    
    private func handlePlayerStatusChange(_ status: AVPlayerItem.Status) {
        switch status {
        case .readyToPlay:
            errorRetryCount = 0
            showLoadingSpinner(false)
            updatePlayPauseButton(isPlaying: player?.rate ?? 0 > 0)
            if let duration = player?.currentItem?.duration, CMTimeGetSeconds(duration) > 0 {
                updateDurationText(CMTimeGetSeconds(duration))
            }
            // Update video layout
            updatePlayerLayerFrame()
            
        case .failed:
            if let error = playerItem?.error {
                handlePlayerError(error)
            }
            
        case .unknown:
            showLoadingSpinner(true)
            
        @unknown default:
            break
        }
    }
    
    @objc private func playerDidFinishPlaying() {
        playbackEnded = true
        updatePlayPauseButton(isPlaying: false)
        showLoadingSpinner(false)
        showControls()
        updateAutoQuality()
    }
    
    @objc private func playerFailedToPlay() {
        if let error = playerItem?.error {
            handlePlayerError(error)
        }
    }
    
    private func handlePlayerError(_ error: Error) {
        errorRetryCount += 1
        
        if errorRetryCount <= maxRetryCount {
            let alert = UIAlertController(
                title: "Playback Error",
                message: "Failed to load video. Attempt \(errorRetryCount) of \(maxRetryCount)\n\n\(error.localizedDescription)",
                preferredStyle: .alert
            )
            
            alert.addAction(UIAlertAction(title: "Retry", style: .default) { [weak self] _ in
                self?.retryPlayback()
            })
            
            alert.addAction(UIAlertAction(title: "Cancel", style: .cancel) { [weak self] _ in
                self?.errorRetryCount = self?.maxRetryCount ?? 0 + 1
            })
            
            present(alert, animated: true)
        } else {
            let alert = UIAlertController(
                title: "Playback Failed",
                message: "Failed to load video after \(maxRetryCount) attempts.\n\n\(error.localizedDescription)",
                preferredStyle: .alert
            )
            
            alert.addAction(UIAlertAction(title: "Try Again", style: .default) { [weak self] _ in
                self?.errorRetryCount = 0
                self?.retryPlayback()
            })
            
            alert.addAction(UIAlertAction(title: "Close", style: .cancel) { [weak self] _ in
                self?.dismiss(animated: true)
            })
            
            present(alert, animated: true)
        }
    }
    
    private func retryPlayback() {
        guard currentIndex >= 0 && currentIndex < mediaItems.count else { return }
        let item = mediaItems[currentIndex]
        if item.type == "VIDEO" {
            displayVideo(item)
        }
    }
    
    private func displayImage(_ item: MediaItem) {
        guard let url = URL(string: item.path) else { return }
        
        videoThumbnail?.isHidden = true
        
        // Remove previous scroll view if exists
        imageScrollView?.removeFromSuperview()
        imageScrollView = nil
        imageView = nil
        
        // Create scroll view for zoom
        let scrollView = UIScrollView()
        scrollView.delegate = self
        scrollView.minimumZoomScale = 1.0
        scrollView.maximumZoomScale = 4.0
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.showsVerticalScrollIndicator = false
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.backgroundColor = .black
        
        // Create image view
        let imgView = UIImageView()
        imgView.contentMode = .scaleAspectFit
        imgView.translatesAutoresizingMaskIntoConstraints = false
        imgView.backgroundColor = .black
        imgView.isUserInteractionEnabled = true
        
        // Add double tap gesture for zoom
        let doubleTapGesture = UITapGestureRecognizer(target: self, action: #selector(handleImageDoubleTap(_:)))
        doubleTapGesture.numberOfTapsRequired = 2
        imgView.addGestureRecognizer(doubleTapGesture)
        
        scrollView.addSubview(imgView)
        containerView.addSubview(scrollView)
        
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: containerView.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: containerView.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: containerView.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: containerView.bottomAnchor),
            
            imgView.topAnchor.constraint(equalTo: scrollView.topAnchor),
            imgView.leadingAnchor.constraint(equalTo: scrollView.leadingAnchor),
            imgView.trailingAnchor.constraint(equalTo: scrollView.trailingAnchor),
            imgView.bottomAnchor.constraint(equalTo: scrollView.bottomAnchor),
            imgView.widthAnchor.constraint(equalTo: scrollView.widthAnchor),
            imgView.heightAnchor.constraint(equalTo: scrollView.heightAnchor)
        ])
        
        imageScrollView = scrollView
        imageView = imgView
        
        // Load image asynchronously
        if url.isFileURL {
            imgView.image = UIImage(contentsOfFile: url.path)
        } else {
            loadImage(from: url) { [weak self] image in
                DispatchQueue.main.async {
                    self?.imageView?.image = image
                }
            }
        }
        
        hideControls()
    }
    
    @objc private func handleImageDoubleTap(_ gesture: UITapGestureRecognizer) {
        guard let scrollView = imageScrollView else { return }
        
        if scrollView.zoomScale > scrollView.minimumZoomScale {
            // Zoom out
            scrollView.setZoomScale(scrollView.minimumZoomScale, animated: true)
        } else {
            // Zoom in
            let point = gesture.location(in: imageView)
            let zoomRect = CGRect(
                x: point.x - scrollView.bounds.width / 4,
                y: point.y - scrollView.bounds.height / 4,
                width: scrollView.bounds.width / 2,
                height: scrollView.bounds.height / 2
            )
            scrollView.zoom(to: zoomRect, animated: true)
        }
    }
    
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updatePlayerLayerFrame()
    }
    
    private func updatePlayerLayerFrame() {
        playerLayer?.frame = videoContainer.bounds
    }
    
    private func startPlaybackStateMonitoring() {
        playbackTimer?.invalidate()
        playbackTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            self?.updatePlaybackState()
        }
    }
    
    private func updatePlaybackState() {
        guard let player = player else { return }
        
        // Update seek bar
        let duration = CMTimeGetSeconds(player.currentItem?.duration ?? CMTime.zero)
        if duration > 0 {
            let currentTime = CMTimeGetSeconds(player.currentTime())
            seekBar.value = Float((currentTime / duration) * 1000)
            updateCurrentTimeText(currentTime)
        }
        
        // Update quality detection if in Auto mode
        if currentQuality == "Auto" {
            updateAutoQuality()
        }
        
        // Notify plugin
        var state = PlaybackState()
        state.isPlaying = player.rate > 0
        state.currentTime = CMTimeGetSeconds(player.currentTime())
        state.duration = duration
        state.currentQuality = currentQuality
        plugin?.notifyPlaybackStateChanged(state)
    }
    
    private func updateCurrentTimeText(_ time: Double) {
        currentTimeLabel.text = formatTime(time)
    }
    
    private func updateDurationText(_ time: Double) {
        durationLabel.text = formatTime(time)
    }
    
    private func formatTime(_ time: Double) -> String {
        let seconds = Int(time) % 60
        let minutes = Int(time) / 60
        return String(format: "%d:%02d", minutes, seconds)
    }
    
    private func updatePlayPauseButton(isPlaying: Bool) {
        playPauseButton.setImage(
            UIImage(systemName: isPlaying ? "pause.fill" : "play.fill"),
            for: .normal
        )
    }
    
    private func showLoadingSpinner(_ show: Bool) {
        if show {
            loadingSpinner.startAnimating()
            playPauseButton.isHidden = true
        } else {
            loadingSpinner.stopAnimating()
            playPauseButton.isHidden = false
        }
    }
    
    private func toggleControls() {
        if controlsVisible {
            hideControls()
        } else {
            showControls()
            scheduleControlsHide()
        }
    }
    
    private func showControls() {
        controlsContainer.isHidden = false
        centerControls.isHidden = false
        backButton.isHidden = false
        settingsButton.isHidden = false
        controlsVisible = true
    }
    
    private func hideControls() {
        controlsContainer.isHidden = true
        centerControls.isHidden = true
        // Keep back and settings buttons visible
        controlsVisible = false
    }
    
    private func scheduleControlsHide() {
        hideControlsTimer?.invalidate()
        hideControlsTimer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: false) { [weak self] _ in
            if let player = self?.player, player.rate > 0 {
                self?.hideControls()
            }
        }
    }
    
    // MARK: - Settings Popup
    
    @objc private func showSettingsPopup() {
        let alert = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        
        // Quality option
        let qualityDisplay: String
        if currentQuality == "Auto" {
            if let actual = actualPlayingQuality, !actual.isEmpty {
                qualityDisplay = "Auto (\(actual))"
            } else {
                qualityDisplay = "Auto"
            }
        } else {
            qualityDisplay = currentQuality
        }
        
        alert.addAction(UIAlertAction(title: "Quality: \(qualityDisplay)", style: .default) { [weak self] _ in
            self?.showQualitySelector()
        })
        
        // Speed option
        var speedDisplay: String
        if currentPlaybackSpeed == Float(Int(currentPlaybackSpeed)) {
            speedDisplay = "\(Int(currentPlaybackSpeed))x"
        } else {
            speedDisplay = String(format: "%.2fx", currentPlaybackSpeed).trimmingCharacters(in: CharacterSet(charactersIn: "0")).trimmingCharacters(in: CharacterSet(charactersIn: "."))
            if !speedDisplay.hasSuffix("x") {
                speedDisplay = speedDisplay + "x"
            }
        }
        
        alert.addAction(UIAlertAction(title: "Speed: \(speedDisplay)", style: .default) { [weak self] _ in
            self?.showPlaybackSpeedSelector()
        })
        
        // Captions option
        alert.addAction(UIAlertAction(title: "Captions: \(captionsEnabled ? "On" : "Off")", style: .default) { [weak self] _ in
            self?.toggleCaptions()
        })
        
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        
        // For iPad
        if let popover = alert.popoverPresentationController {
            popover.sourceView = settingsButton
            popover.sourceRect = settingsButton.bounds
        }
        
        present(alert, animated: true)
    }
    
    private func showPlaybackSpeedSelector() {
        let speeds: [(label: String, value: Float)] = [
            ("0.5x", 0.5),
            ("0.75x", 0.75),
            ("1.0x", 1.0),
            ("1.25x", 1.25),
            ("1.5x", 1.5),
            ("2.0x", 2.0)
        ]
        
        let alert = UIAlertController(title: "Playback Speed", message: nil, preferredStyle: .actionSheet)
        
        for speed in speeds {
            let title = speed.value == currentPlaybackSpeed ? "✓ \(speed.label)" : speed.label
            alert.addAction(UIAlertAction(title: title, style: .default) { [weak self] _ in
                self?.setPlaybackSpeed(speed.value)
            })
        }
        
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        
        // For iPad
        if let popover = alert.popoverPresentationController {
            popover.sourceView = settingsButton
            popover.sourceRect = settingsButton.bounds
        }
        
        present(alert, animated: true)
    }
    
    private func setPlaybackSpeed(_ speed: Float) {
        currentPlaybackSpeed = speed
        player?.rate = speed
    }
    
    private func toggleCaptions() {
        captionsEnabled.toggle()
        // Show feedback
        let message = captionsEnabled ? "Captions enabled" : "Captions disabled"
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        present(alert, animated: true)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            alert.dismiss(animated: true)
        }
    }
    
    private func showQualitySelector() {
        let currentItem = mediaItems[currentIndex]
        let variants = currentItem.qualityVariants.isEmpty ? qualityVariants : currentItem.qualityVariants
        
        if variants.isEmpty {
            let alert = UIAlertController(title: "Quality", message: "No quality options available", preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default))
            present(alert, animated: true)
            return
        }
        
        let alert = UIAlertController(title: "Quality", message: nil, preferredStyle: .actionSheet)
        
        // Auto option
        let autoTitle: String
        if currentQuality == "Auto" {
            if let actual = actualPlayingQuality, !actual.isEmpty {
                autoTitle = "✓ Auto (\(actual))"
            } else {
                autoTitle = "✓ Auto"
            }
        } else {
            if let actual = actualPlayingQuality, !actual.isEmpty {
                autoTitle = "Auto (\(actual))"
            } else {
                autoTitle = "Auto"
            }
        }
        
        alert.addAction(UIAlertAction(title: autoTitle, style: .default) { [weak self] _ in
            self?.setQuality("Auto")
        })
        
        // Quality options
        for variant in variants {
            let title = variant.label == currentQuality ? "✓ \(variant.label)" : variant.label
            alert.addAction(UIAlertAction(title: title, style: .default) { [weak self] _ in
                self?.setQuality(variant.label)
            })
        }
        
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        
        // For iPad
        if let popover = alert.popoverPresentationController {
            popover.sourceView = settingsButton
            popover.sourceRect = settingsButton.bounds
        }
        
        present(alert, animated: true)
    }
    
    
    private func switchVideoUrl(_ url: URL, seekTo time: CMTime) {
        let wasPlaying = player?.rate ?? 0 > 0
        let seekTime = CMTimeGetSeconds(time)
        
        // Show thumbnail again
        let currentItem = mediaItems[currentIndex]
        if let thumbnailUrlString = currentItem.thumbnail, let thumbnailUrl = URL(string: thumbnailUrlString) {
            videoThumbnail?.isHidden = false
            loadImage(from: thumbnailUrl) { [weak self] image in
                DispatchQueue.main.async {
                    self?.videoThumbnail?.image = image
                }
            }
        }
        
        // Hide video
        playerLayer?.opacity = 0
        
        // Create new player item
        let asset = AVURLAsset(url: url)
        let newPlayerItem = AVPlayerItem(asset: asset)
        newPlayerItem.audioTimePitchAlgorithm = .timeDomain
        
        // Replace player item
        player?.replaceCurrentItem(with: newPlayerItem)
        playerItem = newPlayerItem
        
        // Setup observers again
        setupPlayerObservers()
        
        // Seek to previous position
        player?.seek(to: time) { [weak self] _ in
            DispatchQueue.main.async {
                // Fade in video
                UIView.animate(withDuration: 0.2) {
                    self?.playerLayer?.opacity = 1
                }
                
                // Hide thumbnail
                self?.videoThumbnail?.isHidden = true
                
                // Resume playback if was playing
                if wasPlaying {
                    self?.player?.play()
                }
            }
        }
        
        // Set playback speed
        player?.rate = currentPlaybackSpeed
    }
    
    private func updateAutoQuality() {
        guard currentQuality == "Auto", let player = player else { return }
        
        // Try to detect quality from current video dimensions
        // This is a simplified version - in reality, you'd need to check the current track
        if let item = player.currentItem, let asset = item.asset as? AVURLAsset {
            let tracks = asset.tracks(withMediaType: .video)
            if let track = tracks.first {
                let size = track.naturalSize
                let width = Int(size.width)
                let height = Int(size.height)
                
                // Match with quality variants
                let currentItem = mediaItems[currentIndex]
                let variants = currentItem.qualityVariants.isEmpty ? qualityVariants : currentItem.qualityVariants
                
                if let detected = detectQualityFromSize(width: width, height: height, variants: variants) {
                    if detected != actualPlayingQuality {
                        actualPlayingQuality = detected
                    }
                }
            }
        }
    }
    
    private func detectQualityFromSize(width: Int, height: Int, variants: [QualityVariant]) -> String? {
        guard !variants.isEmpty else { return nil }
        
        var bestMatch: QualityVariant?
        var minHeightDiff = Int.max
        
        // Try exact or close height match
        for variant in variants {
            if variant.height > 0 {
                let heightDiff = abs(variant.height - height)
                if heightDiff < minHeightDiff {
                    minHeightDiff = heightDiff
                    bestMatch = variant
                }
                
                if heightDiff <= 10 {
                    return variant.label
                }
            }
        }
        
        // If reasonable match found
        if let match = bestMatch, minHeightDiff <= 50 {
            return match.label
        }
        
        return bestMatch?.label
    }
    
    // MARK: - Swipe Animation
    
    private func startSwipeAnimation(direction: Int, initialOffset: CGFloat) {
        if isSwiping { return }
        
        let targetIndex = direction > 0 ? currentIndex - 1 : currentIndex + 1
        guard targetIndex >= 0 && targetIndex < mediaItems.count else { return }
        
        swipeDirection = direction
        swipeTotalDistance = initialOffset
        isSwiping = true
        
        // Pause video playback during swipe
        player?.pause()
        
        // Create next item preview
        let targetItem = mediaItems[targetIndex]
        let previewView = createMediaPreview(for: targetItem)
        previewView.translatesAutoresizingMaskIntoConstraints = false
        previewView.alpha = 0.5
        containerView.addSubview(previewView)
        
        NSLayoutConstraint.activate([
            previewView.topAnchor.constraint(equalTo: containerView.topAnchor),
            previewView.leadingAnchor.constraint(equalTo: containerView.leadingAnchor),
            previewView.trailingAnchor.constraint(equalTo: containerView.trailingAnchor),
            previewView.bottomAnchor.constraint(equalTo: containerView.bottomAnchor)
        ])
        
        let screenWidth = view.bounds.width
        let startX: CGFloat = direction > 0 ? -screenWidth : screenWidth
        previewView.transform = CGAffineTransform(translationX: startX, y: 0)
        previewView.tag = 999 // Tag to identify preview view
        
        updateSwipeProgress(initialOffset)
    }
    
    private func updateSwipeProgress(_ offset: CGFloat) {
        guard isSwiping else { return }
        
        let screenWidth = view.bounds.width
        let clampedOffset = max(-screenWidth, min(screenWidth, offset))
        
        // Move current view
        if let currentView = getCurrentMediaView() {
            currentView.transform = CGAffineTransform(translationX: clampedOffset, y: 0)
        }
        
        // Move preview view
        if let previewView = containerView.viewWithTag(999) {
            let startX: CGFloat = swipeDirection > 0 ? -screenWidth : screenWidth
            previewView.transform = CGAffineTransform(translationX: startX + clampedOffset, y: 0)
        }
    }
    
    private func completeSwipeAnimation(velocity: CGFloat) {
        guard isSwiping else { return }
        
        let screenWidth = view.bounds.width
        let currentOffset = swipeTotalDistance
        let threshold = screenWidth * 0.3
        let shouldComplete = abs(velocity) > 1000 || abs(currentOffset) > threshold
        
        if shouldComplete {
            // Complete swipe
            UIView.animate(withDuration: 0.2, animations: {
                if let currentView = self.getCurrentMediaView() {
                    let finalX = self.swipeDirection > 0 ? screenWidth : -screenWidth
                    currentView.transform = CGAffineTransform(translationX: finalX, y: 0)
                }
                
                if let previewView = self.containerView.viewWithTag(999) {
                    previewView.transform = .identity
                    previewView.alpha = 1.0
                }
            }) { _ in
                // Switch to next item
                if self.swipeDirection > 0 && self.currentIndex > 0 {
                    self.currentIndex -= 1
                } else if self.swipeDirection < 0 && self.currentIndex < self.mediaItems.count - 1 {
                    self.currentIndex += 1
                }
                
                // Clean up and display new media
                self.cleanupSwipe()
                self.displayCurrentMedia()
            }
        } else {
            // Cancel swipe
            cancelCurrentSwipe()
        }
    }
    
    private func cancelCurrentSwipe() {
        guard isSwiping else { return }
        
        UIView.animate(withDuration: 0.2, animations: {
            if let currentView = self.getCurrentMediaView() {
                currentView.transform = .identity
            }
            
            if let previewView = self.containerView.viewWithTag(999) {
                let screenWidth = self.view.bounds.width
                let startX: CGFloat = self.swipeDirection > 0 ? -screenWidth : screenWidth
                previewView.transform = CGAffineTransform(translationX: startX, y: 0)
            }
        }) { _ in
            self.cleanupSwipe()
        }
    }
    
    private func cleanupSwipe() {
        if let previewView = containerView.viewWithTag(999) {
            previewView.removeFromSuperview()
        }
        
        if let currentView = getCurrentMediaView() {
            currentView.transform = .identity
        }
        
        isSwiping = false
        swipeDirection = 0
        swipeStartX = 0
        swipeTotalDistance = 0
    }
    
    private func getCurrentMediaView() -> UIView? {
        if imageScrollView != nil && imageScrollView?.isHidden == false {
            return imageScrollView
        } else if videoContainer != nil && videoContainer.isHidden == false {
            return videoContainer
        }
        return nil
    }
    
    private func createMediaPreview(for item: MediaItem) -> UIView {
        let container = UIView()
        container.backgroundColor = .black
        
        if item.type == "VIDEO" {
            let imageView = UIImageView()
            imageView.contentMode = .scaleAspectFit
            imageView.translatesAutoresizingMaskIntoConstraints = false
            container.addSubview(imageView)
            
            NSLayoutConstraint.activate([
                imageView.topAnchor.constraint(equalTo: container.topAnchor),
                imageView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
                imageView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
                imageView.bottomAnchor.constraint(equalTo: container.bottomAnchor)
            ])
            
            if let thumbnailUrlString = item.thumbnail, let thumbnailUrl = URL(string: thumbnailUrlString) {
                loadImage(from: thumbnailUrl) { image in
                    DispatchQueue.main.async {
                        imageView.image = image
                    }
                }
            }
        } else {
            let imageView = UIImageView()
            imageView.contentMode = .scaleAspectFit
            imageView.translatesAutoresizingMaskIntoConstraints = false
            container.addSubview(imageView)
            
            NSLayoutConstraint.activate([
                imageView.topAnchor.constraint(equalTo: container.topAnchor),
                imageView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
                imageView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
                imageView.bottomAnchor.constraint(equalTo: container.bottomAnchor)
            ])
            
            if let url = URL(string: item.path) {
                if url.isFileURL {
                    imageView.image = UIImage(contentsOfFile: url.path)
                } else {
                    loadImage(from: url) { image in
                        DispatchQueue.main.async {
                            imageView.image = image
                        }
                    }
                }
            }
        }
        
        return container
    }
    
    // MARK: - UIScrollViewDelegate
    
    func viewForZooming(in scrollView: UIScrollView) -> UIView? {
        return imageView
    }
    
    // MARK: - UIGestureRecognizerDelegate
    
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        if otherGestureRecognizer.view is UIScrollView {
            return true
        }
        return false
    }
    
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRequireFailureOf otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        if let scrollView = imageScrollView, scrollView.zoomScale > scrollView.minimumZoomScale {
            if otherGestureRecognizer.view is UIScrollView {
                return true
            }
        }
        return false
    }
    
    // MARK: - Public Methods
    
    func play() {
        player?.play()
    }
    
    func pause() {
        player?.pause()
    }
    
    func seek(to time: Double) {
        let cmTime = CMTime(seconds: time, preferredTimescale: 600)
        player?.seek(to: cmTime)
    }
    
    func setQuality(_ quality: String) {
        applyQuality(quality)
    }
    
    private func applyQuality(_ quality: String) {
        guard currentIndex >= 0 && currentIndex < mediaItems.count else { return }
        
        let currentItem = mediaItems[currentIndex]
        let variants = currentItem.qualityVariants.isEmpty ? qualityVariants : currentItem.qualityVariants
        
        if quality == "Auto" {
            currentQuality = "Auto"
            actualPlayingQuality = nil
            // Use original URL
            if let url = URL(string: currentItem.path) {
                switchVideoUrl(url, seekTo: player?.currentTime() ?? CMTime.zero)
            }
        } else {
            // Find variant
            if let variant = variants.first(where: { $0.label == quality }) {
                currentQuality = quality
                actualPlayingQuality = quality
                if let url = URL(string: variant.url) {
                    switchVideoUrl(url, seekTo: player?.currentTime() ?? CMTime.zero)
                }
            }
        }
    }
    
    func getPlaybackState() -> PlaybackState? {
        guard let player = player else { return nil }
        
        var state = PlaybackState()
        state.isPlaying = player.rate > 0
        state.currentTime = CMTimeGetSeconds(player.currentTime())
        state.duration = CMTimeGetSeconds(player.currentItem?.duration ?? CMTime.zero)
        state.currentQuality = currentQuality
        return state
    }
    
    // MARK: - Image Loading
    
    private func loadImage(from url: URL, completion: @escaping (UIImage?) -> Void) {
        if url.isFileURL {
            let image = UIImage(contentsOfFile: url.path)
            completion(image)
            return
        }
        
        DispatchQueue.global().async {
            if let data = try? Data(contentsOf: url), let image = UIImage(data: data) {
                completion(image)
            } else {
                completion(nil)
            }
        }
    }
    
    // MARK: - Cleanup
    
    private func releasePlayer() {
        playbackTimer?.invalidate()
        playbackTimer = nil
        
        hideControlsTimer?.invalidate()
        hideControlsTimer = nil
        
        playbackObserver?.invalidate()
        playbackObserver = nil
        
        if let timeObserver = timeObserver {
            player?.removeTimeObserver(timeObserver)
            self.timeObserver = nil
        }
        
        NotificationCenter.default.removeObserver(self)
        
        player?.pause()
        player = nil
        playerItem = nil
        
        if let playerLayer = playerLayer {
            playerLayer.removeFromSuperlayer()
            self.playerLayer = nil
        }
    }
    
    deinit {
        allowScreenToSleep()
        releasePlayer()
    }
}
