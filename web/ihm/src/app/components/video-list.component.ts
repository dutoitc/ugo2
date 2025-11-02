import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { toZurichDate, n } from '../shared/date.util';
import { ApiService } from '../services/api.service';
import { firstValueFrom } from 'rxjs';
import { VideoListItem } from '../services/api.models';

// Types locaux pour l'UI
type Platform = 'YOUTUBE' | 'FACEBOOK' | 'INSTAGRAM' | 'TIKTOK' | 'SUM';
type SortKey =
  | 'views_desc' | 'published_desc' | 'published_asc'
  | 'engagement_desc' | 'watch_eq_desc'
  | 'title_asc' | 'title_desc';

@Component({
  standalone: true,
  selector: 'app-video-list',
  imports: [CommonModule, FormsModule],
  templateUrl: './video-list.component.html',
  styleUrls: ['./video-list.component.css'],
})
export class VideoListComponent {
  private api = inject(ApiService);

  page = 1;
  size = 50;
  pages = 1;
  sort: SortKey = 'published_desc';
  platform: Platform | undefined = undefined;
  q = '';
  sum = { youtube:0, facebook:0, instagram:0, tiktok:0 }
  nbVideos = 0;

  rows = signal<VideoListItem[]>([]);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);

  toZurichDate = toZurichDate;
  n = n;

  constructor() { this.reload(); }


  async reload() {
    this.loading.set(true);
    this.error.set(null);
    try {
      const res = await firstValueFrom(this.api.listVideos({
        page: this.page, size: this.size,
        sort: this.sort,
        platform: this.platform,
        q: this.q?.trim() || undefined
      }));
      this.rows.set(res.items || []);
      this.pages = Math.max(1, Math.ceil((res.total || 0) / (res.size || this.size)));
      this.sum = res.sum;
      this.nbVideos = res.total;
    } catch (e: any) {
      this.error.set(e?.message || 'Erreur de chargement');
      this.rows.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  async goToPage(p: number) {
    this.page = Math.max(1, Math.min(this.pages, p));
    await this.reload();
  }

  async toggleDateSort() {
    this.sort = this.sort === 'published_desc' ? 'published_asc' : 'published_desc';
    await this.reload();
  }

  /** Vues de la plateforme */
  vp(v: VideoListItem, p: Platform): number | null {
    const bp = (v as any).by_platform ?? {};
    const raw = (bp as Record<string, unknown>)[p];
    const num = typeof raw === 'number' ? raw : (raw != null ? Number(raw) : null);
    return Number.isFinite(num as number) ? (num as number) : null;
  }

  /** Couleur de fond  pour les vues de la plateforme*/
  vpBg(v: VideoListItem, p: Platform): string | null {
    const val = (p === 'SUM')?(v.views_native_sum as number?? 0):this.vp(v, p);
    if (val == null) return null;

    if (p === 'SUM') {
      if (val >= 2000) return 'num bggreen';
      if (val >= 1000) return 'num bgyellow';
    }
    if (p === 'FACEBOOK') {
      if (val >= 2000) return 'num bggreen';
      if (val >= 1000) return 'num bgyellow';
    }
    if (p === 'YOUTUBE') {
      if (val >= 400) return 'num bggreen';
      if (val >= 200) return 'num bgyellow';
    }
    return 'num';
  }

}
