package com.capacitor.mediaviewer;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HlsPlaylistParser {
    private static final String TAG = "HlsPlaylistParser";
    
    public static List<QualityVariant> parseMasterPlaylist(String playlistUrl) {
        List<QualityVariant> variants = new ArrayList<>();
        
        try {
            URL url = new URL(playlistUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            InputStream inputStream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            
            String line;
            String currentResolution = null;
            String currentBandwidth = null;
            String currentVariantUrl = null;
            
            // Patterns for HLS playlist tags
            Pattern resolutionPattern = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)");
            Pattern bandwidthPattern = Pattern.compile("BANDWIDTH=(\\d+)");
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // Check for stream info line (contains RESOLUTION or BANDWIDTH)
                if (line.startsWith("#EXT-X-STREAM-INF:")) {
                    // Extract resolution
                    Matcher resolutionMatcher = resolutionPattern.matcher(line);
                    if (resolutionMatcher.find()) {
                        int height = Integer.parseInt(resolutionMatcher.group(2));
                        currentResolution = height + "p";
                    }
                    
                    // Extract bandwidth if no resolution
                    if (currentResolution == null) {
                        Matcher bandwidthMatcher = bandwidthPattern.matcher(line);
                        if (bandwidthMatcher.find()) {
                            int bandwidth = Integer.parseInt(bandwidthMatcher.group(1));
                            if (bandwidth < 500000) {
                                currentResolution = "SD";
                            } else if (bandwidth < 2000000) {
                                currentResolution = "HD";
                            } else {
                                currentResolution = "Full HD";
                            }
                            currentBandwidth = String.valueOf(bandwidth);
                        }
                    }
                    
                    // If still no resolution, use bandwidth or default
                    if (currentResolution == null) {
                        Matcher bandwidthMatcher = bandwidthPattern.matcher(line);
                        if (bandwidthMatcher.find()) {
                            currentBandwidth = bandwidthMatcher.group(1);
                            currentResolution = "Quality " + (variants.size() + 1);
                        } else {
                            currentResolution = "Auto";
                        }
                    }
                } else if (!line.startsWith("#") && !line.isEmpty()) {
                    // This is a URL line
                    currentVariantUrl = resolveUrl(playlistUrl, line);
                    
                    if (currentVariantUrl != null && currentResolution != null) {
                        QualityVariant variant = new QualityVariant();
                        variant.label = currentResolution;
                        variant.url = currentVariantUrl;
                        variants.add(variant);
                        
                        // Reset for next variant
                        currentResolution = null;
                        currentBandwidth = null;
                        currentVariantUrl = null;
                    }
                }
            }
            
            reader.close();
            inputStream.close();
            connection.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing HLS playlist: " + e.getMessage());
        }
        
        return variants;
    }
    
    private static String resolveUrl(String baseUrl, String relativeUrl) {
        try {
            if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                return relativeUrl;
            }
            
            URL base = new URL(baseUrl);
            URL resolved = new URL(base, relativeUrl);
            return resolved.toString();
        } catch (Exception e) {
            return relativeUrl;
        }
    }
    
    public static boolean isHlsUrl(String url) {
        return url != null && (url.endsWith(".m3u8") || url.contains(".m3u8"));
    }
}

