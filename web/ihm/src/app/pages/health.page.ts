import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { HealthResponse } from '../services/api.models';
import { ApiService } from '../services/api.service';

@Component({
  standalone: true,
  imports: [CommonModule],
  template: `
    <h1>Santé des collecteurs</h1>
    <button class="button" (click)="load()" [disabled]="loading()">Actualiser</button>

    <p *ngIf="loading()">Chargement…</p>
    <p class="error" *ngIf="error()">{{ error() }}</p>

    <ng-container *ngIf="health() as h">
      <div class="health-alert" *ngFor="let alert of h.alerts">{{ alert }}</div>

      <table class="table health-table">
        <thead>
          <tr>
            <th>Plateforme</th>
            <th>État</th>
            <th>Dernier snapshot</th>
            <th>Fraîcheur</th>
            <th>Dernier succès</th>
            <th>Fraîcheur collecte</th>
            <th>Durée</th>
            <th>Token</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let p of h.platforms">
            <td><strong>{{ p.platform }}</strong></td>
            <td><span [class]="'status status-' + p.status.toLowerCase()">{{ p.status }}</span></td>
            <td>{{ p.last_snapshot_at || '—' }}</td>
            <td>{{ age(p.snapshot_age_hours) }}</td>
            <td>{{ p.last_success_at || '—' }}</td>
            <td>{{ age(p.success_age_hours) }}</td>
            <td>{{ duration(p.last_duration_ms) }}</td>
            <td>
              {{ p.token_status }}
              <div class="meta" *ngIf="p.token_expires_at">expire le {{ p.token_expires_at }}</div>
              <div class="meta" *ngIf="p.message">{{ p.message }}</div>
            </td>
          </tr>
        </tbody>
      </table>

      <section class="health-summary">
        <div class="card">
          <h2>Dernier batch</h2>
          <ng-container *ngIf="h.last_batch as batch; else noBatch">
            <p>{{ batch.status }} — {{ duration(batch.duration_ms) }} — {{ batch.items ?? 0 }} snapshots</p>
          </ng-container>
          <ng-template #noBatch><p>Aucun batch rapporté.</p></ng-template>
        </div>
        <div class="card">
          <h2>Refresh analytique</h2>
          <ng-container *ngIf="h.refresh as refresh; else noRefresh">
            <p>{{ refresh.last_status }} — {{ duration(refresh.last_duration_ms) }} — dernier succès {{ refresh.last_success_at || '—' }}</p>
          </ng-container>
          <ng-template #noRefresh><p>Aucun refresh rapporté.</p></ng-template>
        </div>
      </section>
    </ng-container>
  `,
  styles: [`
    .health-table { width: 100%; margin-top: 16px; }
    .health-alert { margin-top: 8px; padding: 10px; border: 1px solid #e0bd55; background: #fff3cd; }
    .health-summary { display: grid; grid-template-columns: repeat(auto-fit,minmax(280px,1fr)); gap: 12px; margin-top: 16px; }
    .status { font-weight: 700; }
    .status-ok { color: #137333; }
    .status-warning { color: #8a5a00; }
    .status-error { color: #b3261e; }
    .error { color: #b3261e; }
  `]
})
export class HealthPage implements OnInit {
  private readonly api = inject(ApiService);
  readonly health = signal<HealthResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    void this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.health.set(await firstValueFrom(this.api.getHealth()));
    } catch (e: any) {
      this.error.set(e?.message ?? 'Erreur de chargement');
    } finally {
      this.loading.set(false);
    }
  }

  age(hours?: number | null): string {
    if (hours == null) return 'inconnue';
    if (hours < 1) return `${Math.round(hours * 60)} min`;
    return `${hours.toFixed(1)} h`;
  }

  duration(ms?: number | null): string {
    if (ms == null) return '—';
    if (ms < 1000) return `${ms} ms`;
    return `${(ms / 1000).toFixed(1)} s`;
  }
}
