import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="ugo-header ugo-topbar">
      <img src="assets/logo.png" alt="CAPStv" class="ugo-logo" />
      <div class="ugo-title">UGO</div>

      <nav class="ugo-menu">
        <a routerLink="" routerLinkActive="active" [routerLinkActiveOptions]="{exact:true}">Accueil</a>
        <a routerLink="/videos" routerLinkActive="active">Vidéos</a>
        <a routerLink="/health" routerLinkActive="active">Health</a>
      </nav>
    </header>

    <main class="app-main">
      <router-outlet/>
    </main>
  `,
})
export class AppComponent {}
