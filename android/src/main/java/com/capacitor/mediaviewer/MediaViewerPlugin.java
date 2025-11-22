package com.capacitor.mediaviewer;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.List;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(name = "MediaViewer")
public class MediaViewerPlugin extends Plugin {

    private MediaViewerFragment mediaViewerFragment;
    private MediaViewerListener mediaViewerListener;

    @Override
    public void load() {
        mediaViewerListener = new MediaViewerListener() {
            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                notifyListeners("playbackStateChanged", state.toJSObject());
            }

            @Override
            public void onMediaIndexChanged(int index) {
                JSObject data = new JSObject();
                data.put("index", index);
                notifyListeners("mediaIndexChanged", data);
            }

            @Override
            public void onViewerDismissed() {
                notifyListeners("viewerDismissed", new JSObject());
            }
        };
    }

    @PluginMethod
    public void show(PluginCall call) {
        try {
            JSONObject options = call.getData();
            JSONArray itemsArray = options.getJSONArray("items");
            int currentIndex = options.getInt("currentIndex");
            String title = options.optString("title", "");

            List<MediaItem> mediaItems = parseMediaItems(itemsArray);

            Activity activity = getActivity();
            if (activity == null) {
                call.reject("Activity is null");
                return;
            }

            if (!(activity instanceof FragmentActivity)) {
                call.reject("Activity must be a FragmentActivity");
                return;
            }

            FragmentActivity fragmentActivity = (FragmentActivity) activity;
            fragmentActivity.runOnUiThread(() -> {
                mediaViewerFragment = MediaViewerFragment.newInstance(
                    mediaItems,
                    currentIndex,
                    title,
                    mediaViewerListener
                );
                FragmentManager fragmentManager = fragmentActivity.getSupportFragmentManager();
                mediaViewerFragment.show(
                    fragmentManager,
                    "MediaViewerFragment"
                );
                call.resolve();
            });
        } catch (JSONException e) {
            call.reject("Error parsing options: " + e.getMessage());
        }
    }

    @PluginMethod
    public void dismiss(PluginCall call) {
        Activity activity = getActivity();
        if (activity != null && mediaViewerFragment != null) {
            activity.runOnUiThread(() -> {
                mediaViewerFragment.dismiss();
                mediaViewerFragment = null;
                call.resolve();
            });
        } else {
            call.resolve();
        }
    }

    @PluginMethod
    public void play(PluginCall call) {
        if (mediaViewerFragment != null) {
            mediaViewerFragment.play();
            call.resolve();
        } else {
            call.reject("Media viewer is not showing");
        }
    }

    @PluginMethod
    public void pause(PluginCall call) {
        if (mediaViewerFragment != null) {
            mediaViewerFragment.pause();
            call.resolve();
        } else {
            call.reject("Media viewer is not showing");
        }
    }

    @PluginMethod
    public void seek(PluginCall call) {
        try {
            double time = call.getDouble("time");
            if (mediaViewerFragment != null) {
                mediaViewerFragment.seek((long) (time * 1000)); // Convert to milliseconds
                call.resolve();
            } else {
                call.reject("Media viewer is not showing");
            }
        } catch (Exception e) {
            call.reject("Error seeking: " + e.getMessage());
        }
    }

    @PluginMethod
    public void setQuality(PluginCall call) {
        try {
            String quality = call.getString("quality");
            if (mediaViewerFragment != null) {
                mediaViewerFragment.setQuality(quality);
                call.resolve();
            } else {
                call.reject("Media viewer is not showing");
            }
        } catch (Exception e) {
            call.reject("Error setting quality: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getPlaybackState(PluginCall call) {
        if (mediaViewerFragment != null) {
            PlaybackState state = mediaViewerFragment.getPlaybackState();
            call.resolve(state.toJSObject());
        } else {
            call.reject("Media viewer is not showing");
        }
    }

    private List<MediaItem> parseMediaItems(JSONArray itemsArray) throws JSONException {
        List<MediaItem> items = new ArrayList<>();
        for (int i = 0; i < itemsArray.length(); i++) {
            JSONObject itemObj = itemsArray.getJSONObject(i);
            MediaItem item = new MediaItem();
            item.url = itemObj.getString("url");
            item.type = itemObj.getString("type");
            item.title = itemObj.optString("title", null);

            if (itemObj.has("qualityVariants")) {
                JSONArray qualityArray = itemObj.getJSONArray("qualityVariants");
                item.qualityVariants = new ArrayList<>();
                for (int j = 0; j < qualityArray.length(); j++) {
                    JSONObject qualityObj = qualityArray.getJSONObject(j);
                    QualityVariant variant = new QualityVariant();
                    variant.label = qualityObj.getString("label");
                    variant.url = qualityObj.getString("url");
                    item.qualityVariants.add(variant);
                }
            } else if ("video".equals(item.type) && HlsPlaylistParser.isHlsUrl(item.url)) {
                // Automatically parse HLS master playlist for quality variants
                new Thread(() -> {
                    List<QualityVariant> variants = HlsPlaylistParser.parseMasterPlaylist(item.url);
                    if (variants != null && !variants.isEmpty()) {
                        item.qualityVariants = variants;
                    }
                }).start();
            }

            items.add(item);
        }
        return items;
    }
}

