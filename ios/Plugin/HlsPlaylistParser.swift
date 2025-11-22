import Foundation
import AVFoundation

class HlsPlaylistParser {
    static func parseMasterPlaylist(_ playlistUrl: String, completion: @escaping ([QualityVariant]) -> Void) {
        guard let url = URL(string: playlistUrl) else {
            completion([])
            return
        }
        
        // Use background queue for network request
        DispatchQueue.global(qos: .userInitiated).async {
            var variants: [QualityVariant] = []
            
            do {
                // Fetch the playlist content
                let playlistContent = try String(contentsOf: url, encoding: .utf8)
                let lines = playlistContent.components(separatedBy: .newlines)
                
                var currentResolution: String?
                var currentBandwidth: String?
                var currentVariantUrl: String?
                
                // Patterns for parsing HLS playlist
                let resolutionPattern = try NSRegularExpression(pattern: "RESOLUTION=(\\d+)x(\\d+)", options: [])
                let bandwidthPattern = try NSRegularExpression(pattern: "BANDWIDTH=(\\d+)", options: [])
                
                for line in lines {
                    let trimmedLine = line.trimmingCharacters(in: .whitespaces)
                    
                    if trimmedLine.hasPrefix("#EXT-X-STREAM-INF:") {
                        // Extract resolution
                        let resolutionRange = NSRange(location: 0, length: trimmedLine.utf16.count)
                        if let resolutionMatch = resolutionPattern.firstMatch(in: trimmedLine, options: [], range: resolutionRange),
                           let heightRange = Range(resolutionMatch.range(at: 2), in: trimmedLine),
                           let height = Int(trimmedLine[heightRange]) {
                            currentResolution = "\(height)p"
                        }
                        
                        // Extract bandwidth if no resolution
                        if currentResolution == nil {
                            if let bandwidthMatch = bandwidthPattern.firstMatch(in: trimmedLine, options: [], range: resolutionRange),
                               let bandwidthRange = Range(bandwidthMatch.range(at: 1), in: trimmedLine),
                               let bandwidth = Int(trimmedLine[bandwidthRange]) {
                                if bandwidth < 500000 {
                                    currentResolution = "SD"
                                } else if bandwidth < 2000000 {
                                    currentResolution = "HD"
                                } else {
                                    currentResolution = "Full HD"
                                }
                                currentBandwidth = String(bandwidth)
                            }
                        }
                        
                        // If still no resolution, use default
                        if currentResolution == nil {
                            currentResolution = "Auto"
                        }
                    } else if !trimmedLine.hasPrefix("#") && !trimmedLine.isEmpty {
                        // This is a URL line
                        currentVariantUrl = resolveUrl(baseUrl: url, relativeUrl: trimmedLine)
                        
                        if let variantUrl = currentVariantUrl, let resolution = currentResolution {
                            let variant = QualityVariant()
                            variant.label = resolution
                            variant.url = variantUrl
                            variants.append(variant)
                            
                            // Reset for next variant
                            currentResolution = nil
                            currentBandwidth = nil
                            currentVariantUrl = nil
                        }
                    }
                }
                
                DispatchQueue.main.async {
                    completion(variants)
                }
            } catch {
                print("Error parsing HLS playlist: \(error)")
                DispatchQueue.main.async {
                    completion([])
                }
            }
        }
    }
    
    private static func resolveUrl(baseUrl: URL, relativeUrl: String) -> String? {
        if relativeUrl.hasPrefix("http://") || relativeUrl.hasPrefix("https://") {
            return relativeUrl
        }
        
        return URL(string: relativeUrl, relativeTo: baseUrl)?.absoluteString
    }
    
    static func isHlsUrl(_ url: String) -> Bool {
        return url.hasSuffix(".m3u8") || url.contains(".m3u8")
    }
}

