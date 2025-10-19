import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { VideoListComponent } from '../components/video-list.component';

@Component({
  standalone: true,
  selector: 'ugo-videos',
  imports: [CommonModule, VideoListComponent],
  templateUrl: './videos.page.html',
  styleUrls: ['./videos.page.css']
})
export class VideosPage {
}
