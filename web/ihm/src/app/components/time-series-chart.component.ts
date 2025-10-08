import {
  AfterViewInit, Component, ElementRef, Input, OnChanges,
OnDestroy, SimpleChanges, ViewChild
} from '@angular/core';
import * as echarts from 'echarts';
import { TimeseriesPoint } from '../models/video-detail.vm';

@Component({
selector: 'app-time-series-chart',
standalone: true,
templateUrl: './time-series-chart.component.html',
styleUrls: ['./time-series-chart.component.css']
})
export class TimeSeriesChartComponent implements AfterViewInit, OnChanges, OnDestroy {
@ViewChild('chartEl', { static: true }) chartEl!: ElementRef<HTMLDivElement>;

/** Données: [[epoch_ms,value], ...] */
@Input() series: TimeseriesPoint[] = [];
/** Hauteur en px */
@Input() height = 260;
/** Titre optionnel (utilisé seulement si axis=true) */
@Input() title = '';
/** 'canvas' (défaut) ou 'svg' */
@Input() renderer: 'canvas' | 'svg' = 'canvas';
/** Si false -> sparkline compacte (pas d’axes) */
@Input() axis = true;

private chart?: echarts.ECharts;
private ro?: ResizeObserver;

// >>> Ces deux propriétés doivent être sur la classe (pas dans une méthode)
private lastW = 0;
private lastH = 0;

ngAfterViewInit(): void {
    this.ensureHeight();
    this.chart = echarts.init(this.chartEl.nativeElement, undefined, { renderer: this.renderer });
    this.applyOption();

    this.ro = new ResizeObserver(() => {
      const el = this.chartEl.nativeElement;
      const w = el.clientWidth;
      const h = el.clientHeight;
      if (Math.abs(w - this.lastW) > 1 || Math.abs(h - this.lastH) > 1) {
        this.lastW = w;
        this.lastH = h;
        this.chart?.resize();
      }
    });
    this.ro.observe(this.chartEl.nativeElement);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.chart) return;

    if (changes['renderer']) {
      this.chart.dispose();
      this.chart = echarts.init(this.chartEl.nativeElement, undefined, { renderer: this.renderer });
    }

    if (changes['series'] || changes['title'] || changes['height'] || changes['axis'] || changes['renderer']) {
      this.ensureHeight();
      this.applyOption();
      this.chart.resize();
    }
  }

  ngOnDestroy(): void {
    this.ro?.disconnect();
    this.chart?.dispose();
    this.chart = undefined;
  }

  private ensureHeight(): void {
    const el = this.chartEl?.nativeElement;
    if (!el) return;
    // Respecte la hauteur demandée par l'@Input
    el.style.minHeight = '0px';
    el.style.height = `${this.height}px`;
  }

  private applyOption(): void {
    const data = Array.isArray(this.series) ? this.series : [];
    const nf = new Intl.NumberFormat('fr-CH');
    const timeFmt = this.buildTimeFormatter(data);

    const gridNormal: echarts.GridComponentOption = {
      left: 72, right: 16, top: this.title ? 28 : 12, bottom: 28, containLabel: true
    };
    const gridSpark: echarts.GridComponentOption = {
      left: 2, right: 2, top: 2, bottom: 2, containLabel: false
    };

    const yAxisNormal: echarts.YAXisComponentOption = {
      type: 'value',
      axisLabel: { margin: 10, formatter: (v: number) => nf.format(v) },
      splitLine: { show: true },
      scale: true
    };

    const xAxisNormal: echarts.XAXisComponentOption = {
      type: 'time',
      axisLabel: {
        hideOverlap: true,
        margin: 8,
        formatter: (val: number) => timeFmt(new Date(val))
      },
      axisPointer: { label: { formatter: (p: any) => timeFmt(new Date(p.value)) } }
    };

    const yAxisSpark: echarts.YAXisComponentOption = {
      type: 'value',
      min: 'dataMin', max: 'dataMax', scale: true,
      axisLabel: { show: false }, axisLine: { show: false },
      axisTick: { show: false }, splitLine: { show: false }
    };

    const xAxisSpark: echarts.XAXisComponentOption = {
      type: 'time',
      axisLabel: { show: false }, axisLine: { show: false },
      axisTick: { show: false }, splitLine: { show: false }
    };

    const option: echarts.EChartsOption = {
      title: this.axis && this.title ? { text: this.title, left: 'left' } : undefined,
      tooltip: this.axis ? {
        trigger: 'axis',
        confine: true,
        valueFormatter: (v) => nf.format(Number(v)),
        axisPointer: { type: 'line' }
      } : { show: false },
      grid: this.axis ? gridNormal : gridSpark,
      xAxis: this.axis ? xAxisNormal : xAxisSpark,
      yAxis: this.axis ? yAxisNormal : yAxisSpark,
      series: [
        {
          name: 'Vues',
          type: 'line',
          showSymbol: false,
          smooth: false,
          lineStyle: this.axis ? { width: 2 } : { width: 1.1 },
          data
        }
      ]
    };

    this.chart?.setOption(option, true);
  }

  /** Retourne une fonction de formatage de date adaptée à l’étendue de la série. */
  private buildTimeFormatter(series: TimeseriesPoint[]): (d: Date) => string {
    if (!series?.length) {
      const f = new Intl.DateTimeFormat('fr-CH', { day: '2-digit', month: '2-digit' });
      return (d) => f.format(d);
    }
    const minTs = series[0][0];
    const maxTs = series[series.length - 1][0];
    const spanMs = Math.max(1, maxTs - minTs);
    const spanDays = spanMs / 86_400_000;

    if (spanDays <= 2) {
      const f = new Intl.DateTimeFormat('fr-CH', {
        day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'
      });
      return (d) => f.format(d);
    }
    if (spanDays <= 180) {
      const f = new Intl.DateTimeFormat('fr-CH', { day: '2-digit', month: '2-digit' });
      return (d) => f.format(d);
    }
    const f = new Intl.DateTimeFormat('fr-CH', { day: '2-digit', month: '2-digit', year: 'numeric' });
    return (d) => f.format(d);
  }
}
