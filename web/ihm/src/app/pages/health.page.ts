import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  standalone: true,
  imports: [CommonModule],
  template: `
    <h2>Health API</h2>
    <button (click)="check()" style="margin-bottom:12px">Tester /api/health</button>
    <pre *ngIf="result">{{ result }}</pre>
  `
})
export class HealthPage {
  result = '';
  async check(){
    try{
      const r = await fetch('/api/health', { headers: { 'Accept':'text/plain, text/html, application/json' }});
      const txt = await r.text();
      this.result = `HTTP ${r.status}\n\n${txt}`;
    }catch(e:any){
      this.result = 'Erreur: ' + (e?.message ?? e);
    }
  }
}
