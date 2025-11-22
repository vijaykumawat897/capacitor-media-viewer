import Foundation

class MediaItem {
    var url: String = ""
    var type: String = "" // "video" or "image"
    var title: String?
    var thumbnailUrl: String?
    var qualityVariants: [QualityVariant] = []
}

class QualityVariant {
    var label: String = ""
    var url: String = ""
}

