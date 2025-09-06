import { Routes } from '@angular/router';
import { HealthPage } from './pages/health.page';
import { HomePage } from './pages/home.page';

export const routes: Routes = [
  { path: '', component: HomePage, title: 'UGO — Dashboard' },
  { path: 'health', component: HealthPage, title: 'UGO — Health' },
  { path: '**', redirectTo: '' }
];
