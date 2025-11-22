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
            guard let pathString = itemDict["path"] as? String,
                  let typeString = itemDict["type"] as? String else {
                continue
            }
            
            let item = MediaItem()
            item.path = pathString
            item.type = typeString
            item.alt = itemDict["alt"] as? String
            item.thumbnail = itemDict["thumbnail"] as? String
            
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

