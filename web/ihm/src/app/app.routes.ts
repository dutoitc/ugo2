import { Routes } from '@angular/router';
import { HealthPage } from './pages/health.page';
import { HomePage } from './pages/home.page';
import { VideosPage } from './pages/videos.page';

export const routes: Routes = [
  { path: '', component: HomePage, title: 'UGO — Dashboard' },
  { path: 'videos', component: VideosPage, title: 'UGO — Vidéos' },
  { path: 'health', component: HealthPage, title: 'UGO — Health' },
  { path: '**', redirectTo: '' }
];
