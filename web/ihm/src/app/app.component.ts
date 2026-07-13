import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './services/api.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css'],
})
export class AppComponent {
  private readonly api = inject(ApiService);
  readonly healthAlerts = signal<string[]>([]);

  constructor() {
    void this.loadHealthAlerts();
  }

  private async loadHealthAlerts(): Promise<void> {
    try {
      const health = await firstValueFrom(this.api.getHealth());
      this.healthAlerts.set(health.alerts ?? []);
    } catch {
      this.healthAlerts.set(['État des collecteurs indisponible.']);
    }
  }
}
