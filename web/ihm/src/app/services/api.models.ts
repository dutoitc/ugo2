export interface VideoListItem {
  id: number;
  slug?: string | null;
  title: string | null;
  description?: string | null;
  published_at: string | null;       // ISO-like "YYYY-MM-DD HH:mm:ss.SSS"
  duration_seconds?: number | null;
  is_locked: boolean;

  by_platform?: Record<string, number | string | null> | null; // { YOUTUBE: 123, FACEBOOK: 456, ... }
  last_snapshot_at?: string | null;  // "YYYY-MM-DD HH:mm:ss.SSS"

  // agrégats éventuels (souvent renvoyés en string)
  views_native_sum?: number | string | null;
  likes_sum?: number | string | null;
  comments_sum?: number | string | null;
  shares_sum?: number | string | null;
  engagement_rate_sum?: number | string | null;
  total_watch_seconds_sum?: number | string | null;
  avg_watch_ratio_est?: number | string | null;
  watch_equivalent_sum?: number | string | null;
  trend?: {
    slope: number;
    stars: number;
  };
}

export interface VideoListSum {
  youtube: number;
  facebook: number;
  instagram: number;
  tiktok: number;
}

export interface VideoListResponse {
  page: number;
  size: number;
  total: number;
  sum: VideoListSum;
  items: Array<VideoListItem>;
}

/** ========= Détail ========= */
export interface VideoDetailResponse {

  video: {
    id: number;
    slug?: string | null;
    title: string | null;
    description?: string | null;
    published_at: string | null;
    duration_seconds?: number | null;
    is_locked: boolean;
  };

  rollup?: {
    video_id: number;
    slug?: string | null;
    video_title?: string | null;
    video_published_at?: string | null;
    canonical_length_seconds?: number | null;

    views_native_sum?: number | string | null;
    likes_sum?: number | string | null;
    comments_sum?: number | string | null;
    shares_sum?: number | string | null;
    total_watch_seconds_sum?: number | string | null;

    views_yt?: number | string | null;
    views_fb?: number | string | null;
    views_ig?: number | string | null;
    views_tt?: number | string | null;

    avg_watch_ratio_est?: number | string | null;
    watch_equivalent_sum?: number | string | null;
    engagement_rate_sum?: number | string | null;
  } | null;

  sources: Array<{
    id: number;
    platform: string;                 // YOUTUBE | FACEBOOK | …
    platform_format?: string | null;  // VIDEO | REEL | …
    platform_video_id?: string | null;
    title?: string | null;
    url?: string | null;
    published_at?: string | null;
    duration_seconds?: number | null;
    is_active?: boolean;

    latest?: {
      id: number;
      source_video_id: number;
      video_id: number;
      platform: string;
      platform_format?: string | null;
      platform_video_id?: string | null;
      url?: string | null;

      source_title?: string | null;
      source_published_at?: string | null;
      video_title?: string | null;
      video_published_at?: string | null;

      snapshot_at?: string | null;
      views_native?: number | string | null;
      avg_watch_seconds?: number | string | null;
      total_watch_seconds?: number | string | null;
      length_seconds?: number | string | null;

      reach?: number | string | null;
      unique_viewers?: number | string | null;

      likes?: number | string | null;
      comments?: number | string | null;
      shares?: number | string | null;

      reactions_total?: number | string | null;
      reactions_like?: number | string | null;
      reactions_love?: number | string | null;
      reactions_wow?: number | string | null;
      reactions_haha?: number | string | null;
      reactions_sad?: number | string | null;
      reactions_angry?: number | string | null;

      avg_watch_ratio?: number | string | null;
      watch_equivalent?: number | string | null;
      engagement_rate?: number | string | null;
    } | null;

    timeseries?: unknown | null;
  }>;

}


export interface DuplicatesResponse {
  params: {
    window_h: number;
    duration_tol_s: number;
    limit: number;
    offset: number;
  };
  count: number;
  items: DuplicateItem[];
}

export interface DuplicateItem {
  delta_h: number;
  source1: {
    id: number;
    video_id: number;
    title: string;
    published_at: string;
    duration_seconds: number | null;
  };
  source2: {
    id: number;
    video_id: number;
    title: string;
    published_at: string;
    duration_seconds: number | null;
  };
}

export interface PercentileBands {
  p10?: BackendTimeseriesPoint[];
  p25: BackendTimeseriesPoint[];
  p50?: BackendTimeseriesPoint[];
  p75: BackendTimeseriesPoint[];
  p90?: BackendTimeseriesPoint[];
  count_videos: number;
}

export interface VideoTimeseriesResponse {
  timeseries: {
    views: BackendTimeseriesPoint[];
    [platform: string]: BackendTimeseriesPoint[];
  };
  percentiles?: {
    [platform: string]: PercentileBands;
  };
  granularity: 'hour' | 'day';
  metric: string;
  from: string;
  to: string;
}


export interface BackendTimeseriesPoint {
  ts: string;     // "YYYY-MM-DD HH:mm:ss.SSS"
  value: number;
}
export interface HealthPlatform {
  platform: string;
  status: 'OK' | 'WARNING' | 'ERROR' | string;
  token_status: string;
  token_expires_at?: string | null;
  last_success_at?: string | null;
  success_age_hours?: number | null;
  last_snapshot_at?: string | null;
  snapshot_age_hours?: number | null;
  last_duration_ms?: number | null;
  last_items?: number | null;
  last_error_at?: string | null;
  last_error?: string | null;
  source_count: number;
  message?: string | null;
}

export interface HealthResponse {
  ok: boolean;
  service: string;
  now_utc?: string;
  alerts: string[];
  platforms: HealthPlatform[];
  last_batch?: {
    run_id: string;
    started_at: string;
    finished_at?: string | null;
    duration_ms?: number | null;
    status: string;
    items?: number | null;
    error?: string | null;
  } | null;
  refresh?: {
    dirty_since?: string | null;
    last_started_at?: string | null;
    last_success_at?: string | null;
    last_duration_ms?: number | null;
    last_status: string;
    last_error?: string | null;
  } | null;
}

