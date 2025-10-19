import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { VideoDetailComponent } from '../components/video-detail.component';

@Component({
  standalone: true,
  selector: 'ugo-video-detail-page',
  imports: [CommonModule, VideoDetailComponent],
  templateUrl: './video-detail.page.html',
})
export class VideoDetailPage {
  // This page acts as a container for the VideoDetailComponent.
}
