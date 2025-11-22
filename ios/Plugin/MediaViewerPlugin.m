#import <Capacitor/Capacitor.h>

// Define the plugin using the CAP_PLUGIN Macro, and
// each method the plugin supports using the CAP_PLUGIN_METHOD macro.
CAP_PLUGIN(MediaViewerPlugin, "MediaViewer",
           CAP_PLUGIN_METHOD(show, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(dismiss, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(play, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(pause, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(seek, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(setQuality, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(getPlaybackState, CAPPluginReturnPromise);
)

