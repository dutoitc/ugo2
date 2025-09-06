import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../services/api.service';
import { VideoDetailResponse } from '../models';

@Component({
standalone: true,
selector: 'app-video-detail',
imports: [CommonModule, RouterLink],
templateUrl: './video-detail.component.html',
styleUrls: ['./video-detail.component.css'],
})
export class VideoDetailComponent implements OnInit {
private route = inject(ActivatedRoute);
private api = inject(ApiService);

data = signal<VideoDetailResponse | null>(null);

ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isFinite(id)) return;
    this.api.getVideoById(id).subscribe({
      next: (r) => this.data.set(r),
      error: (e) => console.error(e),
    });
  }

  getPlat(key: string): number | null {
    const by = this.data()?.by_platform || {};
    const raw = (by as any)[key];
    return raw == null ? null : Number(raw);
  }
  totalPlat(): number {
    const keys = ['YOUTUBE', 'FACEBOOK', 'INSTAGRAM', 'TIKTOK'];
    return keys.reduce((s, k) => s + (this.getPlat(k) || 0), 0);
  }

  fmtInt(n: number | null | undefined): string {
    return n == null ? '—' : String(Math.trunc(n));
  }

  toLocal(iso: string | null | undefined): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('fr-CH', { timeZone: 'Europe/Zurich' });
  }
}
