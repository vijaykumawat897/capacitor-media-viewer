package com.capacitor.mediaviewer;

import java.util.List;

public class MediaItem {
    public String path;
    public String type; // "IMAGE" or "VIDEO"
    public String alt;
    public String thumbnail;
    // Internal use only - quality variants are auto-detected
    public List<QualityVariant> qualityVariants;
}

