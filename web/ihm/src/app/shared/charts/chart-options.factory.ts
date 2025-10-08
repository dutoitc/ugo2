// Pur, réutilisable, zéro dépendance runtime.
// Imports "type-only" pour la robustesse sans impacter le bundle.

export type LinePoint = [number, number];

export type EChartsOption = import('echarts').EChartsOption;
export type Grid = import('echarts').GridComponentOption;

export interface LineCfg {
  /** Afficher les axes (x/y). Défaut: true */
  axis?: boolean;
/** Remplissage sous la courbe. Défaut: false */
area?: boolean;
/** Overrides du grid. */
grid?: Partial<Grid>;
}

/**
* Options standardisées pour une courbe temporelle:
* - xAxis: 'time'
* - yAxis: 'value'
* - tooltip: axis
* - sampling: 'lttb'
*/
export function buildLine(series: LinePoint[], cfg: LineCfg = {}): EChartsOption {
  const showAxes = cfg.axis !== false; // défaut: true

  const baseGrid: Grid = {
    containLabel: true,
    top: 8,
    right: 8,
    bottom: 16,
    left: 8,
  };

  const grid: Grid = { ...baseGrid, ...(cfg.grid ?? {}) } as Grid;

  const options: EChartsOption = {
    grid,
    animation: true,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' },
    },
    xAxis: {
      type: 'time',
      // boundaryGap retiré (type picky selon defs, inutile en 'time')
      show: showAxes,
      axisLine: { show: showAxes },
      axisTick: { show: showAxes },
      axisLabel: { show: showAxes },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      show: showAxes,
      axisLine: { show: showAxes },
      axisTick: { show: showAxes },
      axisLabel: { show: showAxes },
      splitLine: { show: true },
    },
    series: [
      {
        type: 'line',
        data: series as unknown as number[][],
        symbol: 'none',
        smooth: false,
        showSymbol: false,
        sampling: 'lttb',
        areaStyle: cfg.area ? {} : undefined,
        lineStyle: { width: 2 },
      },
    ],
  };

  return options;
}
