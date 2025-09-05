
    (function(){
      const app = document.getElementById('app');

      // --- Tiny state & router (hash-based) ------------------------------------
      const state = { page:1, pageSize:12, q:'', platform:'', withMetrics:false };
      window.addEventListener('hashchange', handleRoute);
      document.addEventListener('DOMContentLoaded', handleRoute);

      function handleRoute(){
        const h = location.hash || '#/';
        const m = h.match(/^#\/v\/(\d+)/);
        if (m) {
          renderVideoDetail(parseInt(m[1],10));
        } else {
          renderList();
        }
      }

      // --- API helpers ----------------------------------------------------------
      async function apiList(){
        const params = new URLSearchParams();
        params.set('page', state.page);
        params.set('pageSize', state.pageSize);
        if (state.q) params.set('q', state.q);
        if (state.platform) params.set('platform', state.platform);
        if (state.withMetrics) params.set('withMetrics', '1');
        const url = `/api/v1/videos?${params.toString()}`;
        return fetchJson(url);
      }
      async function apiGet(id){
        const url = `/api/v1/video?id=${id}`;
        return fetchJson(url);
      }
      async function fetchJson(url){
        const r = await fetch(url, { headers:{'Accept':'application/json'} });
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json();
      }

      // --- UI builders ----------------------------------------------------------
      function controls(){
        const wrap = document.createElement('div');
        wrap.className = 'card';

        const row1 = document.createElement('div');
        row1.className = 'row';

        const input = document.createElement('input');
        input.className = 'input';
        input.placeholder = 'Rechercher un titre…';
        input.value = state.q;
        input.addEventListener('keydown', (e)=>{ if (e.key==='Enter'){ state.page=1; state.q=input.value.trim(); renderList(); }});

        const sel = document.createElement('select');
        sel.className = 'input';
        sel.innerHTML = `<option value="">Toutes plateformes</option>
                         <option>YOUTUBE</option>
                         <option>FACEBOOK</option>
                         <option>INSTAGRAM</option>
                         <option>WORDPRESS</option>`;
        sel.value = state.platform;
        sel.onchange = ()=>{ state.page=1; state.platform = sel.value; renderList(); };

        const cb = document.createElement('input');
        cb.type = 'checkbox'; cb.checked = state.withMetrics; cb.id = 'cbMetrics';
        cb.onchange = ()=>{ state.withMetrics = cb.checked; renderList(); };
        const cbLbl = document.createElement('label'); cbLbl.htmlFor='cbMetrics'; cbLbl.className='small'; cbLbl.textContent='Charger métriques';

        const clearBtn = el('button','button','Effacer');
        clearBtn.onclick = ()=>{ state.q=''; state.platform=''; state.page=1; sel.value=''; input.value=''; renderList(); };

        row1.append(input, sel, cb, cbLbl, clearBtn);

        wrap.append(row1);
        return wrap;
      }

      function el(tag, cls, text){ const x=document.createElement(tag); if(cls)x.className=cls; if(text)x.textContent=text; return x; }

      function platformBadges(plats){
        const frag = document.createElement('div'); frag.className='row';
        plats.forEach(p=>{ const b=el('span',`badge ${p}` , p); frag.append(b); });
        return frag;
      }

        function fmtDate(v){
          if (!v) return '—';
          // accepte ISO ou DATETIME MySQL "YYYY-MM-DD HH:MM:SS(.ms)"
          let d;
          if (typeof v === 'string' && /^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d(\.\d+)?$/.test(v)) {
            d = new Date(v.replace(' ', 'T') + 'Z'); // traite comme UTC
          } else {
            d = new Date(v);
          }
          if (isNaN(d)) return '—';
          return d.toLocaleString('fr-CH', {
            timeZone: 'Europe/Zurich',
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit'
          });
        }

      // --- Screens --------------------------------------------------------------
      async function renderList(){
        app.innerHTML = '';
        app.append(controls());

        const loading = el('div','center', '');
        loading.append(el('div','spinner'));
        app.append(loading);

        try {
          const data = await apiList();
          loading.remove();

          if (!data.items || data.items.length===0){
            app.append(el('div','empty','Aucune vidéo.'));
            return;
          }

          const grid = el('div','grid videos');
          data.items.forEach(v=>{
            const card = el('div','card');

            const h = el('h3', null, (v.title && v.title.trim()) ? v.title : '(sans titre)');

            const when = el('div','small', `Publié: ${fmtDate(v.published_at)}`);


            // platforms present in sources
            const plats = Object.keys(v.by_platform || {}); // YOUTUBE / FACEBOOK / …

            const badges = platformBadges(plats);

            const kpi = el('div','kpi');
            const t1 = el('div','tile', `${plats.length} plateforme(s)`);
            const t2 = el('div','tile', `${0} vues (3s)`);
            kpi.append(t1,t2);

            const open = el('button','button','Ouvrir');
            open.onclick = ()=>{ location.hash = `#/v/${v.id}`; };

            card.append(h, when, badges, el('hr','sep'), kpi, open);
            grid.append(card);
          });
          app.append(grid);

          // pagination
          const total = data.total||0; const pageSize = data.pageSize||state.pageSize; const page=data.page||1;
          const pages = Math.max(1, Math.ceil(total/pageSize));
          const pager = el('div','row');
          const prev = el('button','button','◀ Précédent'); prev.disabled = page<=1;
          const next = el('button','button','Suivant ▶'); next.disabled = page>=pages;
          prev.onclick = ()=>{ state.page=Math.max(1,page-1); renderList(); };
          next.onclick = ()=>{ state.page=Math.min(pages,page+1); renderList(); };
          pager.append(prev, el('span','small',`Page ${page}/${pages} — ${total} vidéos`), next);
          app.append(el('hr','sep'));
          app.append(pager);

        } catch (e) {
          loading.remove();
          const err = el('div','err', `Erreur lors du chargement: ${e.message}`);
          app.append(err);
        }
      }

      async function renderVideoDetail(id){
        app.innerHTML='';
        const back = el('button','button','← Retour'); back.onclick = ()=>{ history.back(); };
        app.append(back);

        const loading = el('div','center',''); loading.append(el('div','spinner')); app.append(loading);

        try {
          const v = await apiGet(id);
          loading.remove();
          if (v.error){ app.append(el('div','err', `${v.error}: ${v.message||''}`)); return; }

          const card = el('div','card');
          card.append(el('h2',null,v.title||'(sans titre)'));
          card.append(el('div','small',`Publié: ${fmtDate(v.published_at_local)}`));
          if (v.description){ const d=el('p',null,v.description); card.append(d); }

          const kpi = el('div','kpi');
          kpi.append(el('div','tile',`${v.total_views_3s||0} vues (3s) total`));
          kpi.append(platformBadges((v.sources||[]).map(s=>s.platform).filter((x,i,a)=>a.indexOf(x)===i)));
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
              <td>${s.permalink? `<a class="a" href="${s.permalink || s.url}" target="_blank" rel="noopener">ouvrir</a>`:''}</td>
              <td>${s.latest_views_3s??''}</td>`;
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
                <td>${p.count}</td><td>${p.teasers}</td><td>${p.total_latest_views_3s}</td>
                <td>${fmtDate(p.first_published_at_local||'')}</td>
                <td>${fmtDate(p.last_published_at_local||'')}</td>
                <td>${p.primary_permalink? `<a class="a" href="${p.primary_permalink}" target="_blank" rel="noopener">ouvrir</a>`:''}</td>`;
              tb2.append(tr);
            });
            card.append(t2);
          }

          app.append(card);

        } catch(e){
          loading.remove();
          app.append(el('div','err', `Erreur: ${e.message}`));
        }
      }

      function escapeHtml(s){ return String(s).replace(/[&<>"']/g, c=>({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[c])); }
    })();