export interface VideoListItem {
  id: number;
  slug?: string | null;
  title: string | null;
  description?: string | null;
  published_at: string | null;       // ISO UTC Z
  duration_seconds?: number | null;
  is_locked: boolean;
  by_platform?: Record<string, number | null> | null; // { YOUTUBE: 123, FACEBOOK: 456, ... }
  last_snapshot_at?: string | null;  // ISO UTC Z
}

export interface VideoListResponse {
  page: number;
  size: number;
  total: number;
  items: VideoListItem[];
}

export interface VideoDetailResponse {
  video: {
    id: number;
    slug?: string | null;
    title: string | null;
    description?: string | null;
    published_at: string | null;     // ISO UTC Z
    duration_seconds?: number | null;
    is_locked: boolean;
    last_snapshot_at?: string | null;
  };
  by_platform?: Record<string, number | null> | null;
  sources: Array<{
    source_video_id: number;
    platform: string;                // UPPERCASE
    platform_video_id?: string | null;
    last_snapshot_at?: string | null;
    views_native?: number | null;
    avg_watch_seconds?: number | null;
    total_watch_seconds?: number | null;
    reactions?: {
      like?: number | null;
      love?: number | null;
      wow?: number | null;
      haha?: number | null;
      sad?: number | null;
      angry?: number | null;
    } | null;
  }>;
}
