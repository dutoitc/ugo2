import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild
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

  /** Données principales: [[epoch_ms,value], ...] */
  @Input() series: TimeseriesPoint[] = [];

  /** Bandes percentiles (fond gris) */
  @Input() bands?: {
    p25: TimeseriesPoint[];
    p75: TimeseriesPoint[];
    p10?: TimeseriesPoint[];
    p90?: TimeseriesPoint[];
  };

  @Input() height = 260;
  @Input() title = '';
  @Input() renderer: 'canvas' | 'svg' = 'canvas';
  @Input() axis = true;

  private chart?: echarts.ECharts;
  private ro?: ResizeObserver;
  private lastW = 0;
  private lastH = 0;
  private dbgLastKey = '';

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

    if (
      changes['series'] ||
      changes['bands'] ||
      changes['title'] ||
      changes['height'] ||
      changes['axis']
    ) {
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
    this.chartEl.nativeElement.style.height = `${this.height}px`;
  }

  private applyOption(): void {
    const data = this.normalizeSeries(this.series);
    const nf = new Intl.NumberFormat('fr-CH');
    const timeFmt = this.buildTimeFormatter(data);

    const series: echarts.SeriesOption[] = [];

    // -------- Bands (alignés sur la série principale) --------
    const p10 = this.alignBandToBase(data, this.bands?.p10);
    const p25 = this.alignBandToBase(data, this.bands?.p25);
    const p75 = this.alignBandToBase(data, this.bands?.p75);
    const p90 = this.alignBandToBase(data, this.bands?.p90);

    // Bande large p10 → p90 (gris clair)
    if (p10.length && p90.length) {
      series.push(
        // ligne basse (invisible)
        {
          name: 'p10',
          type: 'line',
          data: p10,
          showSymbol: false,
          lineStyle: { width: 0 },
          emphasis: { disabled: true },
          silent: true,
          z: 1
        },
        // ligne haute avec area jusqu'à la ligne basse (via stack=false => on utilise "areaStyle" + "lineStyle" 0,
        // et on s'assure que c'est au-dessus de p10)
        {
          name: 'p90',
          type: 'line',
          data: p90,
          showSymbol: false,
          lineStyle: { width: 0 },
          areaStyle: { color: 'rgba(160,160,160,0.20)' },
          emphasis: { disabled: true },
          silent: true,
          z: 2
        }
      );
    }

    // Bande centrale p25 → p75 (gris plus foncé)
    if (p25.length && p75.length) {
      series.push(
        {
          name: 'p25',
          type: 'line',
          data: p25,
          showSymbol: false,
          lineStyle: { width: 0 },
          emphasis: { disabled: true },
          silent: true,
          z: 3
        },
        {
          name: 'p75',
          type: 'line',
          data: p75,
          showSymbol: false,
          lineStyle: { width: 0 },
          areaStyle: { color: 'rgba(120,120,120,0.28)' },
          emphasis: { disabled: true },
          silent: true,
          z: 4
        }
      );
    }

    // -------- Courbe principale --------
    series.push({
      name: 'Vues',
      type: 'line',
      data,
      showSymbol: false,
      lineStyle: { width: this.axis ? 2 : 1.2 },
      z: 10
    });

    // Debug léger
    const key = `${data.length}|${(this.bands?.p25?.length ?? 0)}|${(this.bands?.p75?.length ?? 0)}`;
    if (key !== this.dbgLastKey) {
      this.dbgLastKey = key;
      // eslint-disable-next-line no-console
      console.log('[chart] update', {
        data: data.length,
        p10: p10.length,
        p25: p25.length,
        p75: p75.length,
        p90: p90.length
      });
    }

    const option: echarts.EChartsOption = {
      title: this.axis && this.title ? { text: this.title, left: 'left' } : undefined,
      tooltip: this.axis
        ? {
            trigger: 'axis',
            confine: true,
            valueFormatter: (v) => nf.format(Number(v)),
            axisPointer: { type: 'line' }
          }
        : { show: false },
      grid: this.axis
        ? { left: 72, right: 16, top: this.title ? 28 : 12, bottom: 28, containLabel: true }
        : { left: 2, right: 2, top: 2, bottom: 2, containLabel: false },
      xAxis: this.axis
        ? {
            type: 'time',
            axisLabel: {
              hideOverlap: true,
              formatter: (val: number) => timeFmt(new Date(val))
            }
          }
        : { type: 'time', show: false },
      yAxis: this.axis
        ? {
            type: 'value',
            axisLabel: { formatter: (v: number) => nf.format(v) },
            min: 0,
            scale: false
          }
        : { type: 'value', show: false, min: 0, scale: false },
      series
    };

    this.chart?.setOption(option, true);
  }

  // ---------- Helpers ----------

  private normalizeSeries(s: TimeseriesPoint[] | null | undefined): TimeseriesPoint[] {
    if (!Array.isArray(s)) return [];
    // tri + filtre NaN
    return s
      .filter(p => Array.isArray(p) && Number.isFinite(p[0]) && Number.isFinite(p[1]))
      .slice()
      .sort((a, b) => a[0] - b[0]);
  }

  /**
   * Aligne un band sur la base:
   * - garde uniquement les timestamps présents dans la base
   * - reconstruit la liste DANS LE MÊME ORDRE que la base
   * (=> plus de mismatch d'index)
   */
  private alignBandToBase(base: TimeseriesPoint[], band?: TimeseriesPoint[]): TimeseriesPoint[] {
    if (!base.length || !band?.length) return [];
    const m = new Map<number, number>(band.map(p => [p[0], p[1]]));
    const out: TimeseriesPoint[] = [];
    for (const [t] of base) {
      const v = m.get(t);
      if (v == null) continue;
      out.push([t, v] as TimeseriesPoint);
    }
    return out;
  }

  /** Format date adaptatif selon l’étendue */
  private buildTimeFormatter(series: TimeseriesPoint[]): (d: Date) => string {
    if (!series.length) {
      const f = new Intl.DateTimeFormat('fr-CH', { day: '2-digit', month: '2-digit' });
      return (d) => f.format(d);
    }
    const spanMs = series[series.length - 1][0] - series[0][0];
    const spanDays = spanMs / 86_400_000;

    if (spanDays <= 2) {
      const f = new Intl.DateTimeFormat('fr-CH', {
        day: '2-digit',
        month: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      });
      return (d) => f.format(d);
    }
    const f = new Intl.DateTimeFormat('fr-CH', { day: '2-digit', month: '2-digit' });
    return (d) => f.format(d);
  }
}
