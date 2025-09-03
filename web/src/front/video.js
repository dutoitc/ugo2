
    (function(){
      'use strict';
      const el=(t,c,txt)=>{const n=document.createElement(t); if(c)n.className=c; if(txt!=null)n.textContent=String(txt); return n;};
      const $=s=>document.querySelector(s);
      document.addEventListener('DOMContentLoaded', init);

      async function init(){
        const id = new URLSearchParams(location.search).get('id');
        if (!id){ $('#detail').append(el('div','err','Paramètre id manquant')); return; }
        const url = `/api/v1/video?id=${encodeURIComponent(id)}`;
        const r = await fetch(url, {headers:{'Accept':'application/json'}, cache:'no-store'});
        if (!r.ok){ $('#detail').append(el('div','err',`HTTP ${r.status}`)); return; }
        const v = await r.json();
        if (v.error){ $('#detail').append(el('div','err', `${v.error}: ${v.message||''}`)); return; }

        const card = el('div','card');
        card.append(el('h2',null,v.title||'(sans titre)'));
        card.append(el('div','small',`Publié: ${fmtDate(v.published_at_local)}`));
        if (v.description){ card.append(el('p',null,v.description)); }

        const kpi = el('div','kpi');
        kpi.append(el('div','tile',`${(v.total_views_3s||0).toLocaleString('fr-CH')} vues (3s) total`));
        card.append(kpi);

        card.append(el('hr','sep'));

        // table sources
        const table = document.createElement('table'); table.className='table';
        table.innerHTML = `<thead><tr>
          <th>#</th><th>Plateforme</th><th>Titre</th><th>Publié</th><th>Teaser</th><th>Permalien</th><th>Vues 3s</th>
        </tr></thead><tbody></tbody>`;
        const tb = table.querySelector('tbody');
        (v.sources||[]).forEach((s,i)=>{
          const tr=document.createElement('tr');
          tr.innerHTML = `<td>${i+1}</td>
            <td><span class="badge ${s.platform}">${s.platform}</span></td>
            <td>${escapeHtml(s.title||'')}</td>
            <td>${fmtDate(s.published_at_local||s.published_at||'')}</td>
            <td>${s.is_teaser? 'oui':'non'}</td>
            <td>${s.permalink? `<a class="a" href="${s.permalink}" target="_blank" rel="noopener">ouvrir<\/a>`:''}</td>
            <td>${(s.latest_views_3s??'').toLocaleString?.('fr-CH')||s.latest_views_3s||''}</td>`;
          tb.append(tr);
        });
        card.append(table);

        // agrégats par plateforme
        if (Array.isArray(v.sources_by_platform) && v.sources_by_platform.length){
          card.append(el('hr','sep'));
          const t2 = document.createElement('table'); t2.className='table';
          t2.innerHTML = `<thead><tr>
            <th>Plateforme</th><th>#sources</th><th>#teasers</th><th>Vues 3s</th><th>1ère pub.</th><th>Dernière pub.</th><th>Permalien primaire</th>
          </tr></thead><tbody></tbody>`;
          const tb2 = t2.querySelector('tbody');
          v.sources_by_platform.forEach(p=>{
            const tr=document.createElement('tr');
            tr.innerHTML = `<td><span class="badge ${p.platform}">${p.platform}</span></td>
              <td>${p.count}</td><td>${p.teasers}</td><td>${(p.total_latest_views_3s||0).toLocaleString('fr-CH')}</td>
              <td>${fmtDate(p.first_published_at_local||'')}</td>
              <td>${fmtDate(p.last_published_at_local||'')}</td>
              <td>${p.primary_permalink? `<a class="a" href="${p.primary_permalink}" target="_blank" rel="noopener">ouvrir<\/a>`:''}</td>`;
            tb2.append(tr);
          });
          card.append(t2);
        }

        $('#detail').append(card);
      }

      function fmtDate(iso){ try{ return new Date(iso).toLocaleString('fr-CH',{ dateStyle:'medium', timeStyle:'short'});}catch{ return iso||''; } }
      function escapeHtml(s){ return String(s).replace(/[&<>"']/g, c=>({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[c])); }
    })();