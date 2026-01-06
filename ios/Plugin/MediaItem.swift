import Foundation

class MediaItem {
    var path: String = ""
    var type: String = "" // "IMAGE" or "VIDEO"
    var alt: String?
    var thumbnail: String?
    // Internal use only - quality variants are auto-detected
    var qualityVariants: [QualityVariant] = []
}

