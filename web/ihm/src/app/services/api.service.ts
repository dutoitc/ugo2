import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { VideoListResponse, VideoDetailResponse } from '../models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private base = '/api/v1';

  listVideos(opts: {
    page?: number; size?: number; sort?: string;
    q?: string | null; platform?: string | null;
  } = {}): Observable<VideoListResponse> {
    let params = new HttpParams();
    if (opts.page != null)     params = params.set('page', String(opts.page));
    if (opts.size != null)     params = params.set('size', String(opts.size));
    if (opts.sort)             params = params.set('sort', opts.sort);
    if (opts.q)                params = params.set('q', opts.q);
    if (opts.platform)         params = params.set('platform', opts.platform);
    return this.http.get<VideoListResponse>(`${this.base}/videos`, { params });
  }

  getVideoById(id: number): Observable<VideoDetailResponse> {
    const params = new HttpParams().set('id', String(id));
    return this.http.get<VideoDetailResponse>(`${this.base}/video`, { params });
  }

  getVideoBySlug(slug: string): Observable<VideoDetailResponse> {
    const params = new HttpParams().set('slug', slug);
    return this.http.get<VideoDetailResponse>(`${this.base}/video`, { params });
  }
}
