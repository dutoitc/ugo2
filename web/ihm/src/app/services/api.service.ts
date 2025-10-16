import{Injectable, inject}from '@angular/core';
import {HttpClient, HttpParams}from '@angular/common/http';
import {Observable}from 'rxjs';
import {VideoListResponse, VideoDetailResponse}from '../models';

@Injectable({ providedIn: 'root' })
export class ApiService {
private http = inject(HttpClient);
private base = '/api/v1';

// ======================== /videos ========================
listVideos(opts: {
    page?: number; size?: number; sort?: string;
    q?: string | null; platform?: string | null; format?: string | null;
    from?: string | null; to?: string | null;
  } = {}): Observable<VideoListResponse> {
    let params = new HttpParams();
    if (opts.page != null)     params = params.set('page', String(opts.page));
    if (opts.size != null)     params = params.set('size', String(opts.size));
    if (opts.sort)             params = params.set('sort', opts.sort);
    if (opts.q)                params = params.set('q', opts.q);
    if (opts.platform)         params = params.set('platform', opts.platform);
    if (opts.format)           params = params.set('format', opts.format);
    if (opts.from)             params = params.set('from', opts.from);
    if (opts.to)               params = params.set('to', opts.to);
    return this.http.get<VideoListResponse>(`${this.base}/videos`, { params });
  }

// ======================== /video (sélection) ========================
getVideo(params: {
    id?: number | string; slug?: string;
    platform?: string; platform_video_id?: string;
    timeseries?: 0|1|boolean; ts_limit?: number;
  }): Observable<VideoDetailResponse> {
    let p = new HttpParams();
    if (params.id != null && params.id !== '') {
      p = p.set('id', String(params.id));
    } else if (params.slug) {
      p = p.set('slug', params.slug);
    } else if (params.platform && params.platform_video_id) {
      p = p.set('platform', params.platform).set('platform_video_id', params.platform_video_id);
  }
    if (params.timeseries) p = p.set('timeseries', (params.timeseries === true ? 1 : (params.timeseries as any)).toString());
    if (params.ts_limit != null) p = p.set('ts_limit', String(params.ts_limit));
    return this.http.get<VideoDetailResponse>(`${this.base}/video`, { params: p });
  }

// Helpers spécifiques utilisés par les composants actuels
getVideoById(id: number | string, opts?: { timeseries?: 0|1|boolean; ts_limit?: number }): Observable<VideoDetailResponse> {
  return this.getVideo({ id, timeseries: opts?.timeseries ?? 0, ts_limit: opts?.ts_limit });
}
getVideoBySlug(slug: string, opts?: { timeseries?: 0|1|boolean; ts_limit?: number }): Observable<VideoDetailResponse> {
  return this.getVideo({ slug, timeseries: opts?.timeseries ?? 0, ts_limit: opts?.ts_limit });
}
getVideoByPlatform(platform: string, platform_video_id: string, opts?: { timeseries?: 0|1|boolean; ts_limit?: number }): Observable<VideoDetailResponse> {
  return this.getVideo({ platform, platform_video_id, timeseries: opts?.timeseries ?? 0, ts_limit: opts?.ts_limit });
}

// ======================== /video/{id}/timeseries ========================
getVideoTimeseries(id: number | string, params?: {
    metric?: 'views_native'|'likes'|'comments'|'shares'|'total_watch_seconds';
    interval?: 'hour'|'day';
    range?: string;
    platforms?: string;     // CSV 'FACEBOOK,YOUTUBE'
    agg?: 'sum'|'cumsum';
    limit?: number;         // >0 => downsample
  }): Observable<unknown> {
  const qp = new HttpParams({
    fromObject: {
      ...(params?.metric ? { metric: params.metric } : {}),
      ...(params?.interval ? { interval: params.interval } : {}),
      ...(params?.range ? { range: params.range } : {}),
      ...(params?.platforms ? { platforms: params.platforms } : {}),
      ...(params?.agg ? { agg: params.agg } : {}),
      ...(params?.limit != null && params.limit > 0 ? { limit: String(params.limit) } : {})
    }
  });
  return this.http.get(`${this.base}/video/${id}/timeseries`, { params: qp });
}

}
