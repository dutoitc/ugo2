import { Component } from '@angular/core';

@Component({
  standalone: true,
  selector: 'ugo-home',
  template: `
    <h1>Bienvenue 👋</h1>
    <p>Étape 0 — scaffolding & thème CAPStv.</p>
    <ul>
      <li>Header + sidebar responsive</li>
      <li>Couleurs CAPStv</li>
      <li><a routerLink="/health">Healthcheck API</a></li>
    </ul>
  `
})
export class HomePage {}
