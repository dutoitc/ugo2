import { Component, Input, OnChanges, SimpleChanges, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgxEchartsDirective, provideEcharts } from 'ngx-echarts';

@Component({
standalone: true,
selector: 'app-time-series-chart',
imports: [CommonModule, NgxEchartsDirective],
template: `
<div echarts [options]="opts" [initOpts]="{ renderer: renderer }" [autoResize]="true" [style.height.px]="height"></div>
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

opts: any = this.buildOpts([]);

ngOnChanges(changes: SimpleChanges): void {
  this.opts = this.buildOpts(this.series || []);
}

private buildOpts(series: Array<[number, number]>): any {
  return {
    animation: false,
    grid: { left: 42, right: 8, top: 8, bottom: 26 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'time',
      boundaryGap: false,
      axisLabel: { hideOverlap: true }
    },
    yAxis: {
      type: 'value',
      min: 'dataMin',
      max: 'dataMax',
      axisLabel: { formatter: (v: number) => Math.round(v).toLocaleString('fr-CH') }
    },
    series: [{
      type: 'line',
      showSymbol: false,
      data: series
    }]
  };
}
}
