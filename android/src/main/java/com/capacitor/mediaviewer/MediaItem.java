package com.capacitor.mediaviewer;

import java.util.List;

public class MediaItem {
    public String url;
    public String type; // "video" or "image"
    public String title;
    public String thumbnailUrl;
    public List<QualityVariant> qualityVariants;
}

