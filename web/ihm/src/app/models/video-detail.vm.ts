// Types UI simples (aucune dépendance Angular)

export type TimeseriesPoint = [number, number];

export interface LatestRowVm {
  platform: string;
  views: number;
  last_snapshot_at: string; //ISO
}

export interface ReactionsRowVm {
  platform: string;
  likes: number;
  comments: number;
  shares: number;
}
