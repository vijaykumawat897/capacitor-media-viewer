import UIKit
import AVKit
import AVFoundation

class MediaViewerController: UIViewController, UIScrollViewDelegate, UIGestureRecognizerDelegate {
    private var mediaItems: [MediaItem] = []
    private var currentIndex: Int = 0
    private var titleText: String = ""
    private weak var plugin: MediaViewerPlugin?
    
    private var containerView: UIView!
    private var playerViewController: AVPlayerViewController?
    private var imageScrollView: UIScrollView?
    private var imageView: UIImageView?
    private var player: AVPlayer?
    private var playbackObserver: NSKeyValueObservation?
    private var timeObserver: Any?
    private var playbackTimer: Timer?
    private var currentQuality: String?
    
    private var gestureRecognizer: UIPanGestureRecognizer!
    private var initialTouchPoint: CGPoint = CGPoint(x: 0, y: 0)
    
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
    }
    
    private func setupUI() {
        view.backgroundColor = .black
        
        containerView = UIView()
        containerView.translatesAutoresizingMaskIntoConstraints = false
        containerView.backgroundColor = .black
        view.addSubview(containerView)
        
        NSLayoutConstraint.activate([
            containerView.topAnchor.constraint(equalTo: view.topAnchor),
            containerView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            containerView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            containerView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        
        // Close button
        let closeButton = UIButton(type: .system)
        closeButton.setTitle("✕", for: .normal)
        closeButton.titleLabel?.font = UIFont.systemFont(ofSize: 24)
        closeButton.setTitleColor(.white, for: .normal)
        closeButton.backgroundColor = UIColor.white.withAlphaComponent(0.2)
        closeButton.layer.cornerRadius = 20
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        closeButton.addTarget(self, action: #selector(closeButtonTapped), for: .touchUpInside)
        view.addSubview(closeButton)
        
        NSLayoutConstraint.activate([
            closeButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 20),
            closeButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            closeButton.widthAnchor.constraint(equalToConstant: 40),
            closeButton.heightAnchor.constraint(equalToConstant: 40)
        ])
    }
    
    private func setupGestures() {
        gestureRecognizer = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        gestureRecognizer.delegate = self
        view.addGestureRecognizer(gestureRecognizer)
    }
    
    @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
        // Don't handle pan if image is zoomed (let scroll view handle it)
        if let scrollView = imageScrollView, scrollView.zoomScale > scrollView.minimumZoomScale {
            return
        }
        
        let translation = gesture.translation(in: view)
        
        switch gesture.state {
        case .began:
            initialTouchPoint = gesture.location(in: view)
        case .changed:
            break
        case .ended, .cancelled:
            let velocity = gesture.velocity(in: view)
            let threshold: CGFloat = 100
            
            if abs(translation.x) > threshold || abs(velocity.x) > 500 {
                if translation.x > 0 && currentIndex > 0 {
                    // Swipe right - previous
                    currentIndex -= 1
                    displayCurrentMedia()
                    plugin?.notifyMediaIndexChanged(currentIndex)
                } else if translation.x < 0 && currentIndex < mediaItems.count - 1 {
                    // Swipe left - next
                    currentIndex += 1
                    displayCurrentMedia()
                    plugin?.notifyMediaIndexChanged(currentIndex)
                }
            }
        default:
            break
        }
    }
    
    @objc private func closeButtonTapped() {
        dismiss(animated: true) {
            self.plugin?.notifyViewerDismissed()
        }
    }
    
    private func displayCurrentMedia() {
        guard currentIndex >= 0 && currentIndex < mediaItems.count else {
            return
        }
        
        // Clear previous media
        containerView.subviews.forEach { $0.removeFromSuperview() }
        imageScrollView = nil
        imageView = nil
        releasePlayer()
        
        let item = mediaItems[currentIndex]
        
        if item.type == "VIDEO" {
            displayVideo(item)
        } else {
            displayImage(item)
        }
        
        plugin?.notifyMediaIndexChanged(currentIndex)
    }
    
    private func displayVideo(_ item: MediaItem) {
        guard let url = URL(string: item.path) else {
            return
        }
        
        player = AVPlayer(url: url)
        
        playerViewController = AVPlayerViewController()
        playerViewController?.player = player
        playerViewController?.showsPlaybackControls = true
        
        if let playerVC = playerViewController {
            addChild(playerVC)
            playerVC.view.translatesAutoresizingMaskIntoConstraints = false
            containerView.addSubview(playerVC.view)
            
            NSLayoutConstraint.activate([
                playerVC.view.topAnchor.constraint(equalTo: containerView.topAnchor),
                playerVC.view.leadingAnchor.constraint(equalTo: containerView.leadingAnchor),
                playerVC.view.trailingAnchor.constraint(equalTo: containerView.trailingAnchor),
                playerVC.view.bottomAnchor.constraint(equalTo: containerView.bottomAnchor)
            ])
            
            playerVC.didMove(toParent: self)
        }
        
        player?.play()
        startPlaybackStateMonitoring()
    }
    
    private func displayImage(_ item: MediaItem) {
        guard let url = URL(string: item.path) else {
            return
        }
        
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
        let doubleTapGesture = UITapGestureRecognizer(target: self, action: #selector(handleDoubleTap(_:)))
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
        DispatchQueue.global().async { [weak self] in
            var image: UIImage?
            
            // Try loading from URL data first
            if let data = try? Data(contentsOf: url) {
                image = UIImage(data: data)
            }
            
            // If that fails and it's a local file, try loading directly
            if image == nil && url.isFileURL {
                image = UIImage(contentsOfFile: url.path)
            }
            
            if let image = image {
                DispatchQueue.main.async {
                    self?.imageView?.image = image
                }
            }
        }
    }
    
    @objc private func handleDoubleTap(_ gesture: UITapGestureRecognizer) {
        guard let scrollView = imageScrollView else { return }
        
        if scrollView.zoomScale > scrollView.minimumZoomScale {
            // Zoom out
            scrollView.setZoomScale(scrollView.minimumZoomScale, animated: true)
        } else {
            // Zoom in
            let point = gesture.location(in: imageView)
            let zoomRect = CGRect(x: point.x - scrollView.bounds.width / 4,
                                 y: point.y - scrollView.bounds.height / 4,
                                 width: scrollView.bounds.width / 2,
                                 height: scrollView.bounds.height / 2)
            scrollView.zoom(to: zoomRect, animated: true)
        }
    }
    
    private func startPlaybackStateMonitoring() {
        playbackTimer?.invalidate()
        playbackTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            self?.updatePlaybackState()
        }
    }
    
    private func updatePlaybackState() {
        guard let player = player else {
            return
        }
        
        var state = PlaybackState()
        state.isPlaying = player.rate > 0
        state.currentTime = CMTimeGetSeconds(player.currentTime())
        state.duration = CMTimeGetSeconds(player.currentItem?.duration ?? CMTime.zero)
        state.currentQuality = currentQuality
        
        plugin?.notifyPlaybackStateChanged(state)
    }
    
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
        // Quality selection removed - quality variants no longer supported
    }
    
    func getPlaybackState() -> PlaybackState? {
        guard let player = player else {
            return nil
        }
        
        var state = PlaybackState()
        state.isPlaying = player.rate > 0
        state.currentTime = CMTimeGetSeconds(player.currentTime())
        state.duration = CMTimeGetSeconds(player.currentItem?.duration ?? CMTime.zero)
        state.currentQuality = currentQuality
        return state
    }
    
    func viewForZooming(in scrollView: UIScrollView) -> UIView? {
        return imageView
    }
    
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        // Allow pan gesture to work with scroll view zoom
        if otherGestureRecognizer.view is UIScrollView {
            return true
        }
        return false
    }
    
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRequireFailureOf otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        // Don't interfere with scroll view gestures when zoomed
        if let scrollView = imageScrollView, scrollView.zoomScale > scrollView.minimumZoomScale {
            if otherGestureRecognizer.view is UIScrollView {
                return true
            }
        }
        return false
    }
    
    private func releasePlayer() {
        playbackTimer?.invalidate()
        playbackTimer = nil
        
        if let playerVC = playerViewController {
            playerVC.willMove(toParent: nil)
            playerVC.view.removeFromSuperview()
            playerVC.removeFromParent()
            playerViewController = nil
        }
        
        player?.pause()
        player = nil
        imageView = nil
        imageScrollView = nil
    }
    
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        releasePlayer()
    }
}

