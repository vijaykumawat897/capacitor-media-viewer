import Foundation
import Capacitor
import AVKit
import AVFoundation
import UIKit

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(MediaViewerPlugin)
public class MediaViewerPlugin: CAPPlugin {
    private var mediaViewerController: MediaViewerController?
    
    @objc func show(_ call: CAPPluginCall) {
        guard let itemsArray = call.getArray("items", [String: Any].self) else {
            call.reject("Items array is required")
            return
        }
        
        guard let currentIndex = call.getInt("currentIndex") else {
            call.reject("Current index is required")
            return
        }
        
        let title = call.getString("title") ?? ""
        
        var mediaItems: [MediaItem] = []
        for itemDict in itemsArray {
            guard let urlString = itemDict["url"] as? String,
                  let typeString = itemDict["type"] as? String else {
                continue
            }
            
            let item = MediaItem()
            item.url = urlString
            item.type = typeString
            item.title = itemDict["title"] as? String
            
            if let qualityVariantsArray = itemDict["qualityVariants"] as? [[String: Any]] {
                item.qualityVariants = []
                for variantDict in qualityVariantsArray {
                    if let label = variantDict["label"] as? String,
                       let url = variantDict["url"] as? String {
                        let variant = QualityVariant()
                        variant.label = label
                        variant.url = url
                        item.qualityVariants.append(variant)
                    }
                }
            } else if item.type == "video" && HlsPlaylistParser.isHlsUrl(item.url) {
                // Automatically parse HLS master playlist for quality variants
                HlsPlaylistParser.parseMasterPlaylist(item.url) { variants in
                    if !variants.isEmpty {
                        item.qualityVariants = variants
                    }
                }
            }
            
            mediaItems.append(item)
        }
        
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.mediaViewerController = MediaViewerController(
                mediaItems: mediaItems,
                currentIndex: currentIndex,
                title: title,
                plugin: self
            )
            
            if let viewController = self.bridge?.viewController {
                self.mediaViewerController?.modalPresentationStyle = .fullScreen
                viewController.present(self.mediaViewerController!, animated: true) {
                    call.resolve()
                }
            } else {
                call.reject("View controller not available")
            }
        }
    }
    
    @objc func dismiss(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.mediaViewerController?.dismiss(animated: true) {
                self.mediaViewerController = nil
                call.resolve()
            }
        }
    }
    
    @objc func play(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.mediaViewerController?.play()
            call.resolve()
        }
    }
    
    @objc func pause(_ call: CAPPluginCall) {
        DispatchQueue.main.async { [weak self] in
            self?.mediaViewerController?.pause()
            call.resolve()
        }
    }
    
    @objc func seek(_ call: CAPPluginCall) {
        guard let time = call.getDouble("time") else {
            call.reject("Time is required")
            return
        }
        
        DispatchQueue.main.async { [weak self] in
            self?.mediaViewerController?.seek(to: time)
            call.resolve()
        }
    }
    
    @objc func setQuality(_ call: CAPPluginCall) {
        guard let quality = call.getString("quality") else {
            call.reject("Quality is required")
            return
        }
        
        DispatchQueue.main.async { [weak self] in
            self?.mediaViewerController?.setQuality(quality)
            call.resolve()
        }
    }
    
    @objc func getPlaybackState(_ call: CAPPluginCall) {
        guard let state = mediaViewerController?.getPlaybackState() else {
            call.reject("Media viewer is not showing")
            return
        }
        
        var result: [String: Any] = [
            "isPlaying": state.isPlaying,
            "currentTime": state.currentTime,
            "duration": state.duration
        ]
        
        if let quality = state.currentQuality {
            result["currentQuality"] = quality
        }
        
        call.resolve(result)
    }
    
    func notifyPlaybackStateChanged(_ state: PlaybackState) {
        var data: [String: Any] = [
            "isPlaying": state.isPlaying,
            "currentTime": state.currentTime,
            "duration": state.duration
        ]
        
        if let quality = state.currentQuality {
            data["currentQuality"] = quality
        }
        
        notifyListeners("playbackStateChanged", data: data)
    }
    
    func notifyMediaIndexChanged(_ index: Int) {
        notifyListeners("mediaIndexChanged", data: ["index": index])
    }
    
    func notifyViewerDismissed() {
        notifyListeners("viewerDismissed", data: [:])
    }
}

