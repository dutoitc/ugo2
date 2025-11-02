import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DuplicatesComponent } from '../components/duplicates.component';

@Component({
  standalone: true,
  selector: 'ugo-duplicates-page',
  imports: [CommonModule, DuplicatesComponent],
  templateUrl: './duplicates.page.html',
})
export class DuplicatesPage {
}
