package com.capacitor.mediaviewer;

import com.getcapacitor.JSObject;

public class PlaybackState {
    public boolean isPlaying;
    public double currentTime;
    public double duration;
    public String currentQuality;

    public JSObject toJSObject() {
        JSObject obj = new JSObject();
        obj.put("isPlaying", isPlaying);
        obj.put("currentTime", currentTime);
        obj.put("duration", duration);
        if (currentQuality != null) {
            obj.put("currentQuality", currentQuality);
        }
        return obj;
    }
}

