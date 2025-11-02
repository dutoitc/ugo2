import{Component, OnInit, OnDestroy, inject, signal}from '@angular/core';
import {CommonModule}from '@angular/common';
import {ActivatedRoute, RouterLink}from '@angular/router';
import {HmsPipe}from '../shared/pipes/hms.pipe';
import {IntFrPipe}from '../shared/pipes/int-fr.pipe';
import {LocalDateTimePipe }from '../shared/pipes/local-datetime.pipe';
import {ApiService}from '../services/api.service';
import {DuplicatesResponse}from '../services/api.models';
import {TimeSeriesChartComponent}from './time-series-chart.component';
import {Subscription}from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-duplicates',
  imports: [
    CommonModule,
    RouterLink,
    TimeSeriesChartComponent,
    HmsPipe,
    IntFrPipe,
    LocalDateTimePipe
  ],
  templateUrl: './duplicates.component.html',
  styleUrls: ['./duplicates.component.css'],
})
export class DuplicatesComponent implements OnInit, OnDestroy {
private route = inject(ActivatedRoute);
private api = inject(ApiService);

// ✅ signal unique, typé
readonly data = signal<DuplicatesResponse | null>(null);
private sub?: Subscription;

ngOnInit(): void {
    this.sub = this.api.getDuplicates().subscribe({
      next: res => this.data.set(res),
      error: err => console.error('[duplicates] getDuplicates failed', err),
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }


  keep(videoIdToKeep: number, videoIdToDelete: number, videoSourceIdToUpdate: number): void {
    const confirmMsg = `Confirmer la résolution du doublon ?\n\n` +
                       `Garder la vidéo #${videoIdToKeep}\n` +
                       `Supprimer la vidéo #${videoIdToDelete}\n` +
                       `Mettre à jour source_video #${videoSourceIdToUpdate}`;
    if (!confirm(confirmMsg)) return;

    this.api.resolveDuplicate(videoIdToKeep, videoIdToDelete, videoSourceIdToUpdate)
      .subscribe({
        next: () => alert('Doublon résolu avec succès.'),
        error: (err) => alert('Erreur: ' + (err.error?.message || 'échec de la requête')),
      });
  }



}
