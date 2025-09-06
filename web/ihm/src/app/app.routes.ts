import { Routes } from '@angular/router';

export const APP_ROUTES: Routes = [
{
path: '',
loadComponent: () =>
      import('./pages/video-list.component').then(m => m.VideoListComponent),
  },
  {
    path: 'v/:id',
    loadComponent: () =>
      import('./pages/video-detail.component').then(m => m.VideoDetailComponent),
  },
  { path: '**', redirectTo: '' },
];
