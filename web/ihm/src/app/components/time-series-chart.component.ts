import { Component, Input, OnChanges, SimpleChanges, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgxEchartsDirective, provideEcharts } from 'ngx-echarts';

@Component({
standalone: true,
selector: 'app-time-series-chart',
imports: [CommonModule, NgxEchartsDirective],
template: `
<div echarts
[options]="opts"
[initOpts]="{ renderer: renderer }"
[autoResize]="true"
[style.height.px]="height"></div>
`,
styles: [`
:host { display:block; }
`],
changeDetection: ChangeDetectionStrategy.OnPush,
providers: [provideEcharts()]
})
export class TimeSeriesChartComponent implements OnChanges {
  @Input() series: Array<[number, number]> = [];
  @Input() height: number = 180;
  @Input() renderer: 'svg' | 'canvas' = 'svg';
  @Input() axis: boolean = true; // input pour masquer les axes en sparkline

  opts: any = this.buildOpts([]);

  ngOnChanges(changes: SimpleChanges): void {
    try {
      const len = Array.isArray(this.series) ? this.series.length : -1;
      console.log('[TimeSeriesChartComponent] ngOnChanges axis=', this.axis, 'height=', this.height, 'renderer=', this.renderer, 'series.len=', len);
      if (len > 0) console.log('[TimeSeriesChartComponent] first3=', this.series.slice(0, 3));
    } catch {}
    this.opts = this.buildOpts(this.series || []);
  }

  private buildOpts(series: Array<[number, number]>): any {
    try {
      console.log('[TimeSeriesChartComponent] buildOpts axis=', this.axis, 'height=', this.height, 'series.len=', series?.length ?? -1);
    } catch {}
    return {
      animation: false,
      grid: (this.axis ? { left: 42, right: 8, top: 8, bottom: 26 } : { left: 0, right: 0, top: 0, bottom: 0 }),
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'time',
        boundaryGap: false,
        axisLabel: (this.axis ? { hideOverlap: true } : { show: false }),
        axisLine: { show: this.axis },
        axisTick: { show: this.axis },
        splitLine: { show: false }
      },
      yAxis: {
        type: 'value',
        min: 'dataMin',
        max: 'dataMax',
        axisLabel: (this.axis ? { formatter: (v: number) => Math.round(v).toLocaleString('fr-CH') } : { show: false }),
        axisLine: { show: this.axis },
        axisTick: { show: this.axis },
        splitLine: { show: false }
      },
      series: [{
        type: 'line',
        showSymbol: false,
        data: series
      }]
    };
  }
}
