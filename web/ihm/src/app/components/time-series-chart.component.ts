import { Component, ElementRef, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import * as echarts from 'echarts';
import type { ECharts, EChartsCoreOption as EChartsOption } from 'echarts';
import { buildLine } from '../shared/charts/chart-options.factory';

@Component({
selector: 'app-time-series-chart',
standalone: true,
template: `
<div #chartRef [style.height.px]="height" style="width: 100%;"></div>
`,
})
export class TimeSeriesChartComponent implements OnInit, OnChanges, OnDestroy {
/** Paires [timestampMs, value] */
@Input({ required: true }) series: [number, number][] = [];
/** Hauteur en pixels du conteneur */
@Input() height = 220;
/** Affichage des axes */
@Input() axis = true;
/** Renderer ECharts ('canvas' | 'svg') */
@Input() renderer: 'canvas' | 'svg' = 'canvas';

@ViewChild('chartRef', { static: true }) private chartEl!: ElementRef<HTMLDivElement>;

private chart?: ECharts;
private resizeObs?: ResizeObserver;

ngOnInit(): void {
    this.chart = echarts.init(this.chartEl.nativeElement, undefined, { renderer: this.renderer });
    this.applyOptions();

    // Responsive sans ngx-echarts
    this.resizeObs = new ResizeObserver(() => {
      this.chart?.resize();
    });
    this.resizeObs.observe(this.chartEl.nativeElement);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.chart) return; // init pas encore fait
    // Rebuild options à chaque changement d'inputs pertinents
    this.applyOptions();
  }

  ngOnDestroy(): void {
    this.resizeObs?.disconnect();
    if (this.chart) {
      this.chart.dispose();
      this.chart = undefined;
    }
  }

  private applyOptions(): void {
    const options: EChartsOption = buildLine(this.series ?? [], {
      axis: this.axis,
      area: false,
    });
    this.chart?.setOption(options as any, { notMerge: true });
  }
}
