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
            String currentLabel = null;
            String currentVariantUrl = null;
            int currentWidth = 0;
            int currentHeight = 0;

            Pattern resolutionPattern = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)");
            Pattern bandwidthPattern = Pattern.compile("BANDWIDTH=(\\d+)");

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("#EXT-X-STREAM-INF:")) {
                    currentWidth = 0;
                    currentHeight = 0;
                    currentLabel = null;

                    Matcher resolutionMatcher = resolutionPattern.matcher(line);
                    if (resolutionMatcher.find()) {
                        currentWidth = Integer.parseInt(resolutionMatcher.group(1));
                        currentHeight = Integer.parseInt(resolutionMatcher.group(2));
                        currentLabel = currentHeight + "p"; // e.g., 1080p
                    }

                    if (currentLabel == null) {
                        Matcher bandwidthMatcher = bandwidthPattern.matcher(line);
                        if (bandwidthMatcher.find()) {
                            int bandwidth = Integer.parseInt(bandwidthMatcher.group(1));
                            if (bandwidth < 500000) {
                                currentLabel = "SD";
                            } else if (bandwidth < 2000000) {
                                currentLabel = "HD";
                            } else {
                                currentLabel = "Full HD";
                            }
                        }
                    }
                } else if (!line.startsWith("#") && !line.isEmpty()) {
                    currentVariantUrl = resolveUrl(playlistUrl, line);

                    if (currentVariantUrl != null && currentLabel != null) {
                        QualityVariant variant = new QualityVariant();
                        variant.label = currentLabel;
                        variant.url = currentVariantUrl;
                        variant.width = currentWidth;
                        variant.height = currentHeight;

                        variants.add(variant);

                        // Reset flags
                        currentLabel = null;
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
