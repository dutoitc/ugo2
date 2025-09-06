import { Component, computed, effect, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../services/api.service';
import { VideoListItem, VideoListResponse } from '../models';

@Component({
standalone: true,
selector: 'app-video-list',
imports: [CommonModule, RouterLink],
templateUrl: './video-list.component.html',
styleUrls: ['./video-list.component.css'],
})
export class VideoListComponent {
private api = inject(ApiService);

resp = signal<VideoListResponse | null>(null);
items = computed<VideoListItem[]>(() => this.resp()?.items ?? []);

q = signal<string>('');
page = signal<number>(1);
size = signal<number>(50);

constructor() {
    effect(() => {
      const q = this.q();
      const page = this.page();
      const size = this.size();
      this.api
        .listVideos({ page, size, sort: 'published_desc', q: q || undefined })
        .subscribe({
          next: (r) => this.resp.set(r),
          error: (e) => console.error(e),
        });
    });
  }

  onQ(val: string) {
    this.q.set(val);
  }

  getPlat(v: VideoListItem, key: string): number | null {
    const by = v.by_platform || {};
    const raw = (by as any)[key];
    return raw == null ? null : Number(raw);
  }

  totalPlat(v: VideoListItem): number {
    const keys = ['YOUTUBE', 'FACEBOOK', 'INSTAGRAM', 'TIKTOK'];
    return keys.reduce((s, k) => s + (this.getPlat(v, k) || 0), 0);
  }

  fmtInt(n: number | null | undefined): string {
    return n == null ? '—' : String(Math.trunc(n));
  }

  toLocalDate(iso: string | null | undefined): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('fr-CH', { timeZone: 'Europe/Zurich' });
  }

  toLocal(iso: string | null | undefined): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('fr-CH', { timeZone: 'Europe/Zurich' });
  }
}
