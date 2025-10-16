import{Component, inject, signal}from '@angular/core';
import {CommonModule}from '@angular/common';
import {FormsModule}from '@angular/forms';
import {toZurichDate, n}from '../shared/date.util';
import {ApiService}from '../services/api.service';
import {firstValueFrom}from 'rxjs';
import {VideoListItem}from '../models'; // ⬅️ on s'aligne sur le type existant

// Types locaux pour l'UI (VideosApi supprimé)
type Platform = 'YOUTUBE'|'FACEBOOK'|'INSTAGRAM'|'TIKTOK';
type SortKey =
  | 'views_desc' | 'published_desc' | 'published_asc'
  | 'engagement_desc' | 'watch_eq_desc'
  | 'title_asc' | 'title_desc';

@Component({
  standalone: true,
  selector: 'ugo-videos',
  imports: [CommonModule, FormsModule],
  templateUrl: './videos.page.html',
  styleUrls: ['./videos.page.css']
})
export class VideosPage {
  private api = inject(ApiService);

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
    const bp = (v as any).by_platform ?? {};
    const raw = (bp as Record<string, unknown>)[p];
    const num = typeof raw === 'number' ? raw : (raw != null ? Number(raw) : null);
    return Number.isFinite(num as number) ? (num as number) : null;
  }

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
