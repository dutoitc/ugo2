import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { VideoListResponse, VideoDetailResponse } from '../services/api.models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private base = '/api/v1';

  // ======================== /videos ========================
  listVideos(opts: {
    page?: number;
    size?: number;
    sort?: string;
    q?: string | null;
    platform?: string | null;
    format?: string | null;
    from?: string | null;
    to?: string | null;
  } = {}): Observable<VideoListResponse> {
    let params = new HttpParams();
    if (opts.page != null) { params = params.set('page', String(opts.page)); }
    if (opts.size != null) { params = params.set('size', String(opts.size)); }
    if (opts.sort) { params = params.set('sort', opts.sort); }
    if (opts.q) { params = params.set('q', opts.q); }
    if (opts.platform) { params = params.set('platform', opts.platform); }
    if (opts.format) { params = params.set('format', opts.format); }
    if (opts.from) { params = params.set('from', opts.from); }
    if (opts.to) { params = params.set('to', opts.to); }
    return this.http.get<VideoListResponse>(`${this.base}/videos`, { params });
  }

  // ======================== /video (sélection) ========================
  getVideo(params: {
    id?: number | string;
    slug?: string;
    platform?: string;
    platform_video_id?: string;
    timeseries?: 0 | 1 | boolean;
    ts_limit?: number;
  }): Observable<VideoDetailResponse> {
    let httpParams = new HttpParams();

    if (params.id != null && params.id !== '') {
      httpParams = httpParams.set('id', String(params.id));
    } else if (params.slug) {
      httpParams = httpParams.set('slug', params.slug);
    } else if (params.platform && params.platform_video_id) {
      httpParams = httpParams.set('platform', params.platform)
                             .set('platform_video_id', params.platform_video_id);
    }

    if (params.timeseries != null) {
      const timeseriesValue = Number(params.timeseries);
      httpParams = httpParams.set('timeseries', String(timeseriesValue));
    }

    if (params.ts_limit != null) {
      httpParams = httpParams.set('ts_limit', String(params.ts_limit));
    }

    return this.http.get<VideoDetailResponse>(`${this.base}/video`, { params: httpParams });
  }

  // Helpers spécifiques utilisés par les composants actuels
  getVideoById(id: number | string, opts?: { timeseries?: 0 | 1 | boolean; ts_limit?: number }): Observable<VideoDetailResponse> {
    return this.getVideo({ id, timeseries: opts?.timeseries ?? 0, ts_limit: opts?.ts_limit });
  }

  getVideoBySlug(slug: string, opts?: { timeseries?: 0 | 1 | boolean; ts_limit?: number }): Observable<VideoDetailResponse> {
    return this.getVideo({ slug, timeseries: opts?.timeseries ?? 0, ts_limit: opts?.ts_limit });
  }

  getVideoByPlatform(platform: string, platform_video_id: string, opts?: { timeseries?: 0 | 1 | boolean; ts_limit?: number }): Observable<VideoDetailResponse> {
    return this.getVideo({ platform, platform_video_id, timeseries: opts?.timeseries ?? 0, ts_limit: opts?.ts_limit });
  }

  // ======================== /video/{id}/timeseries ========================
  getVideoTimeseries(id: number | string, params?: {
    metric?: 'views_native' | 'likes' | 'comments' | 'shares' | 'total_watch_seconds';
    interval?: 'hour' | 'day';
    range?: string;
    platforms?: string;     // CSV 'FACEBOOK,YOUTUBE'
    agg?: 'sum' | 'cumsum';
    limit?: number;         // >0 => downsample
  }): Observable<unknown> {
    const queryParams: { [param: string]: string | number } = {};

    if (params) {
      if (params.metric) { queryParams['metric'] = params.metric; }
      if (params.interval) { queryParams['interval'] = params.interval; }
      if (params.range) { queryParams['range'] = params.range; }
      if (params.platforms) { queryParams['platforms'] = params.platforms; }
      if (params.agg) { queryParams['agg'] = params.agg; }
      if (params.limit != null && params.limit > 0) { queryParams['limit'] = params.limit; }
    }

    const httpParams = new HttpParams({ fromObject: queryParams });
    return this.http.get(`${this.base}/video/${id}/timeseries`, { params: httpParams });
  }
}
