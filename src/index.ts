import { registerPlugin } from '@capacitor/core';

import type { MediaViewerPlugin } from '../capacitor.plugin';

const MediaViewer = registerPlugin<MediaViewerPlugin>('MediaViewer', {
  web: () => import('./web').then(m => new m.MediaViewerWeb()),
});

export * from '../capacitor.plugin';
export { MediaViewer };

