import { Routes } from '@angular/router';

export const APP_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/videos.page').then(m => m.VideosPage),
  },
  {
    path: 'v/:id',
    loadComponent: () =>
      import('./pages/video-detail.page').then(m => m.VideoDetailPage),
  },
  { path: '**', redirectTo: '' },
];
