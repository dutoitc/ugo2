import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IntFrPipe } from '../shared/pipes/int-fr.pipe';
import { LocalDateTimePipe } from '../shared/pipes/local-datetime.pipe';

import { ApiService } from '../services/api.service';
import { VideoListItem, VideoListResponse } from '../models';

type SortKey = 'published_at' | 'title' | 'yt' | 'fb' | 'ig' | 'total' | 'engagements' | 'last_snapshot';
type SortDir = 'asc' | 'desc';

@Component({
standalone: true,
selector: 'app-video-list',
imports: [CommonModule, RouterLink, IntFrPipe, LocalDateTimePipe],
templateUrl: './video-list.component.html',
styleUrls: ['./video-list.component.css'],
})
export class VideoListComponent implements OnInit {
private api = inject(ApiService);

// state
readonly q = signal<string>('');
readonly rawItems = signal<VideoListItem[]>([]);

// tri
readonly sortKey = signal<SortKey>('published_at');
readonly sortDir = signal<SortDir>('desc');

ngOnInit(): void {
    this.reload();
  }

  onQ(val: string) {
    my:
    this.q.set(val);
    this.reload();
  }

  onSort(key: SortKey) {
    if (this.sortKey() === key) {
      this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortKey.set(key);
      this.sortDir.set(key === 'published_at' ? 'desc' : 'asc');
    }
  }

  private reload() {
    this.api.listVideos({ page: 1, size: 5000, q: this.q() || undefined }).subscribe({
      next: (res: VideoListResponse) => {
        this.rawItems.set(res.items ?? []);
      },
      error: (err) => {
        console.error('listVideos failed', err);
        this.rawItems.set([]);
      }
    });
  }

  /** ======= helpers ======= */

  private n(v: any): number {
    if (v == null || v === '') return 0;
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
  }

  getPlat(v: VideoListItem, platform: string): number | null {
    const raw = v?.by_platform?.[platform];
    if (raw == null) return null;
    return this.n(raw);
  }

  totalPlat(v: VideoListItem): number {
    const keys = ['YOUTUBE','FACEBOOK','INSTAGRAM','TIKTOK'];
    return keys.reduce((sum, k) => sum + this.n(v?.by_platform?.[k]), 0);
  }

  engagements(v: VideoListItem): number | null {
    const l = v?.likes_sum;
    const c = v?.comments_sum;
    const s = v?.shares_sum;
    if (l == null && c == null && s == null) return null;
    return this.n(l) + this.n(c) + this.n(s);
  }

  fmtInt(n: number | null | undefined): string {
    return (n == null) ? '—' : String(Math.trunc(n));
  }

  toLocalDate(iso: string | null | undefined): string {
    if (!iso || (typeof iso === 'string' && iso.startsWith('0000-'))) return '—';
    const d = new Date(iso);
    return isNaN(d.getTime()) ? '—' : d.toLocaleDateString('fr-CH', { timeZone: 'Europe/Zurich' });
  }

  toLocal(iso: string | null | undefined): string {
    if (!iso || (typeof iso === 'string' && iso.startsWith('0000-'))) return '—';
    const d = new Date(iso);
    return isNaN(d.getTime()) ? '—' : d.toLocaleString('fr-CH', { timeZone: 'Europe/Zurich' });
  }

  platClass(v: VideoListItem, platform: string): any {
    const val = this.getPlat(v, platform) ?? 0;
    if (platform === 'FACEBOOK') {
      return {
        'metric-bad': val > 0 && val < 1000,
        'metric-good': val >= 5000
      };
    }
    if (platform === 'YOUTUBE') {
      return {
        'metric-bad': val > 0 && val < 10,
        'metric-good': val >= 100
      };
    }
    return {};
  }

  /** mapping de colonnes vers valeurs triables */
  private sortableValue(v: VideoListItem, key: SortKey): number | string {
    switch (key) {
      case 'published_at': {
        const t = new Date(v.published_at ?? '').getTime();
        return Number.isFinite(t) ? t : -Infinity;
      }
      case 'title': return (v.title || '').toLowerCase();
      case 'yt': return this.getPlat(v, 'YOUTUBE') ?? -Infinity;
      case 'fb': return this.getPlat(v, 'FACEBOOK') ?? -Infinity;
      case 'ig': return this.getPlat(v, 'INSTAGRAM') ?? -Infinity;
      case 'total': return this.totalPlat(v);
      case 'engagements': return this.engagements(v) ?? -Infinity;
      case 'last_snapshot': {
        const t = new Date(v.last_snapshot_at ?? '').getTime();
        return Number.isFinite(t) ? t : -Infinity;
      }
    }
  }

  readonly items = computed(() => {
    const data = [...this.rawItems()];
    const k = this.sortKey();
    const dir = this.sortDir();
    data.sort((a, b) => {
      const av = this.sortableValue(a, k);
      const bv = this.sortableValue(b, k);
      if (typeof av === 'string' || typeof bv === 'string') {
        const cmp = String(av).localeCompare(String(bv));
        return dir === 'asc' ? cmp : -cmp;
      }
      const cmp = (Number(av) - Number(bv));
      return dir === 'asc' ? cmp : -cmp;
    });
    return data;
  });

  /** icône tri */
  sortIcon(key: SortKey): string {
    if (this.sortKey() !== key) return '↕';
    return this.sortDir() === 'asc' ? '↑' : '↓';
  }

  readonly total_yt = computed(
    () => this.items().reduce((sum, v) => sum + (this.getPlat(v, 'YOUTUBE') ?? 0), 0)
  );


  readonly total_fb = computed(
    () => this.items().reduce((sum, v) => sum + (this.getPlat(v, 'FACEBOOK') ?? 0), 0)
  );

  readonly total_ig = computed(
    () => this.items().reduce((sum, v) => sum + (this.getPlat(v, 'INSTAGRAM') ?? 0), 0)
  );

  readonly total_all = computed(
      () => this.items().reduce((sum, v) => sum + this.totalPlat(v), 0)
  );

}
