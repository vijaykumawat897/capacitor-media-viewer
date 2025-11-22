import UIKit
import AVKit
import AVFoundation

class MediaViewerController: UIViewController {
    private var mediaItems: [MediaItem] = []
    private var currentIndex: Int = 0
    private var titleText: String = ""
    private weak var plugin: MediaViewerPlugin?
    
    private var containerView: UIView!
    private var playerViewController: AVPlayerViewController?
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
        view.addGestureRecognizer(gestureRecognizer)
        
        let tapGesture = UITapGestureRecognizer(target: self, action: #selector(handleTap))
        view.addGestureRecognizer(tapGesture)
    }
    
    @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
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
    
    @objc private func handleTap() {
        dismiss(animated: true) {
            self.plugin?.notifyViewerDismissed()
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
        releasePlayer()
        
        let item = mediaItems[currentIndex]
        
        if item.type == "video" {
            displayVideo(item)
        } else {
            displayImage(item)
        }
        
        plugin?.notifyMediaIndexChanged(currentIndex)
    }
    
    private func displayVideo(_ item: MediaItem) {
        guard let url = URL(string: item.url) else {
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
        
        // If HLS and no quality variants yet, parse them in background
        if item.qualityVariants.isEmpty && HlsPlaylistParser.isHlsUrl(item.url) {
            HlsPlaylistParser.parseMasterPlaylist(item.url) { [weak self] variants in
                guard let self = self else { return }
                if !variants.isEmpty {
                    item.qualityVariants = variants
                }
            }
        }
        
        player?.play()
        startPlaybackStateMonitoring()
    }
    
    private func displayImage(_ item: MediaItem) {
        guard let url = URL(string: item.url) else {
            return
        }
        
        imageView = UIImageView()
        imageView?.contentMode = .scaleAspectFit
        imageView?.translatesAutoresizingMaskIntoConstraints = false
        imageView?.backgroundColor = .black
        
        if let imageView = imageView {
            containerView.addSubview(imageView)
            
            NSLayoutConstraint.activate([
                imageView.topAnchor.constraint(equalTo: containerView.topAnchor),
                imageView.leadingAnchor.constraint(equalTo: containerView.leadingAnchor),
                imageView.trailingAnchor.constraint(equalTo: containerView.trailingAnchor),
                imageView.bottomAnchor.constraint(equalTo: containerView.bottomAnchor)
            ])
            
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
        let item = mediaItems[currentIndex]
        guard item.type == "video", let qualityVariants = item.qualityVariants else {
            return
        }
        
        for variant in qualityVariants {
            if variant.label == quality, let url = URL(string: variant.url) {
                let currentTime = player?.currentTime()
                let wasPlaying = player?.rate ?? 0 > 0
                
                let newPlayerItem = AVPlayerItem(url: url)
                player?.replaceCurrentItem(with: newPlayerItem)
                
                if let currentTime = currentTime {
                    player?.seek(to: currentTime)
                }
                
                if wasPlaying {
                    player?.play()
                }
                
                currentQuality = quality
                break
            }
        }
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
    }
    
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        releasePlayer()
    }
}

