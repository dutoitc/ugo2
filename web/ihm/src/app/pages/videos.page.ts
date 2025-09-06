import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { VideosApi, VideoListItem, SortKey, Platform } from '../services/videos.api';
import { toZurichDate, n } from '../shared/date.util';

@Component({
standalone: true,
selector: 'ugo-videos',
imports: [CommonModule, FormsModule],
templateUrl: './videos.page.html',
styleUrls: ['./videos.page.css']
})
export class VideosPage {
private api = inject(VideosApi);

page = 1;
size = 20;
pages = 1;
sort: SortKey = 'published_desc';          // ⬅️ défaut: date desc
platform: Platform | undefined = undefined;
q = '';

rows = signal<VideoListItem[]>([]);
loading = signal<boolean>(false);
error = signal<string | null>(null);

toZurichDate = toZurichDate;
n = n;

constructor() { this.reload(); }

  vp(v: VideoListItem, p: Platform): number | null {
    const bp = (v.by_platform ?? {}) as Record<string, unknown>;
    const raw = bp[p];
    const num = typeof raw === 'number' ? raw : (raw != null ? Number(raw) : null);
    return Number.isFinite(num as number) ? (num as number) : null;
  }

  async reload() {
    this.loading.set(true);
    this.error.set(null);
    try {
      const res = await this.api.list({
        page: this.page, size: this.size,
        sort: this.sort,
        platform: this.platform,
        q: this.q?.trim() || undefined
      });
      this.rows.set(res.items || []);
      this.pages = Math.max(1, Math.ceil((res.total || 0) / (res.size || this.size)));
    } catch (e: any) {
      this.error.set(e?.message || 'Erreur de chargement');
      this.rows.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  async go(p: number) {
    this.page = Math.max(1, Math.min(this.pages, p));
    await this.reload();
  }

  async toggleDateSort() {
    this.sort = this.sort === 'published_desc' ? 'published_asc' : 'published_desc';
    await this.reload();
  }
}
