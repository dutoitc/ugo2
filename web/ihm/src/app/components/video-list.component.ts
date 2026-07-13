import { Component, inject, signal, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { toZurichDate, n } from '../shared/date.util';
import { ApiService } from '../services/api.service';
import { firstValueFrom } from 'rxjs';
import { VideoListItem } from '../services/api.models';

type Platform = 'YOUTUBE' | 'FACEBOOK' | 'INSTAGRAM' | 'TIKTOK' | 'SUM';
type SortKey =
  | 'views_desc' | 'views_asc'
  | 'youtube_desc' | 'youtube_asc'
  | 'facebook_desc' | 'facebook_asc'
  | 'instagram_desc' | 'instagram_asc'
  | 'tiktok_desc' | 'tiktok_asc'
  | 'published_desc' | 'published_asc'
  | 'engagement_desc' | 'engagement_asc'
  | 'watch_eq_desc' | 'watch_eq_asc'
  | 'title_asc' | 'title_desc';

@Component({
  standalone: true,
  encapsulation: ViewEncapsulation.None,
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
  sum = { youtube: 0, facebook: 0, instagram: 0, tiktok: 0 };
  nbVideos = 0;

  rows = signal<VideoListItem[]>([]);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);

  toZurichDate = toZurichDate;
  n = n;

  constructor() {
    void this.reload();
  }

  async reload(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const res = await firstValueFrom(this.api.listVideos({
        page: this.page,
        size: this.size,
        sort: this.sort,
        platform: this.platform,
        q: this.q.trim() || undefined,
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

  async goToPage(p: number): Promise<void> {
    this.page = Math.max(1, Math.min(this.pages, p));
    await this.reload();
  }

  async applyFilters(): Promise<void> {
    this.page = 1;
    await this.reload();
  }

  async toggleSort(desc: SortKey, asc: SortKey): Promise<void> {
    this.sort = this.sort === desc ? asc : desc;
    this.page = 1;
    await this.reload();
  }

  sortCaret(desc: SortKey, asc: SortKey): string {
    if (this.sort === desc) return '↓';
    if (this.sort === asc) return '↑';
    return '';
  }

  vp(v: VideoListItem, p: Platform): number | null {
    const bp = v.by_platform ?? {};
    const raw = bp[p];
    const num = typeof raw === 'number' ? raw : (raw != null ? Number(raw) : null);
    return Number.isFinite(num as number) ? (num as number) : null;
  }

  vpBg(v: VideoListItem, p: Platform): string | null {
    const val = p === 'SUM' ? Number(v.views_native_sum ?? 0) : this.vp(v, p);
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
