
    (function(){
      'use strict';
      const el = (t,c,txt)=>{const n=document.createElement(t); if(c) n.className=c; if(txt!=null) n.textContent=String(txt); return n;};
      const $ = s=>document.querySelector(s);

      const state = { q:'', period:'all', withMetrics:true, sort:'date', dir:'desc', page:1, pageSize:50 };

      document.addEventListener('DOMContentLoaded', init);

      async function init(){
        $('#q').addEventListener('keydown', e=>{ if(e.key==='Enter'){ state.q=$('#q').value.trim(); state.page=1; load(); }});
        $('#period').addEventListener('change', ()=>{ state.period=$('#period').value; state.page=1; load(); });
        $('#withMetrics').addEventListener('change', ()=>{ state.withMetrics=$('#withMetrics').checked; load(); });
        load();
      }

      function getRange(){
        if (state.period==='90') { const d=new Date(); d.setDate(d.getDate()-90); return {from:d.toISOString()}; }
        if (state.period==='365'){ const d=new Date(); d.setDate(d.getDate()-365); return {from:d.toISOString()}; }
        return {}; // all
      }

      async function load(){
        const params = new URLSearchParams();
        params.set('page', state.page);
        params.set('pageSize', state.pageSize);
        if (state.q) params.set('q', state.q);
        const r = getRange(); if (r.from) params.set('from', r.from);
        if (state.withMetrics) params.set('withMetrics','1');

        const url = `/api/v1/videos?${params.toString()}`;
        const res = await fetch(url, {headers:{'Accept':'application/json'}, cache:'no-store'});
        if (!res.ok) return renderError(`HTTP ${res.status}`);
        const data = await res.json();
        $('#lastRefresh').textContent = `Dernière mise à jour : ${new Date().toLocaleString('fr-CH')}`;
        renderTable(data.items||[]);
      }

         function sumPlatforms(v){
           const acc = {FACEBOOK:0, YOUTUBE:0, INSTAGRAM:0, WORDPRESS:0};
           // 1) si on a les sources détaillées (page détail) → somme latest_views_3s
           if (Array.isArray(v.sources) && v.sources.length) {
             v.sources.forEach(s=>{
               const plat = s.platform || '';
               const val = Number(s.latest_views_3s ?? s.views_native ?? s.views ?? 0) || 0;
               if (acc[plat] == null) acc[plat] = 0;
               acc[plat] += val;
             });
           // 2) sinon, utiliser l’agrégat by_platform renvoyé par /api/v1/videos
           } else if (v.by_platform && typeof v.by_platform === 'object') {
             Object.entries(v.by_platform).forEach(([plat, val])=>{
               if (acc[plat] == null) acc[plat] = 0;
               acc[plat] += Number(val) || 0;
             });
           }
           const sommeSansWp = (acc.FACEBOOK||0) + (acc.YOUTUBE||0) + (acc.INSTAGRAM||0);
           return {acc, sommeSansWp};
         }

      function renderTable(items){
        const wrap = $('#tableWrap');
        wrap.innerHTML='';

        if (!items.length){ wrap.append(el('div','empty','Aucune vidéo.')); return; }

        // client-side sort
        const sorted = items.slice();
        sorted.sort((a,b)=>{
          const dir = state.dir==='asc'?1:-1;
          const va = keyVal(a, state.sort); const vb = keyVal(b, state.sort);
          if (va<vb) return -1*dir; if (va>vb) return 1*dir; return 0;
        });

        // compute column totals on current page
        let totFB=0, totYT=0, totIG=0, totWP=0, totSum=0;

        const table = el('table','table');
        const thead = el('thead');
        const trh = el('tr');

        const makeTh = (label,key)=>{
          const th=el('th','sortable');
          th.innerHTML = `<span>${label}</span><span class="arrow">${state.sort===key?(state.dir==='asc'?'↑':'↓'):'↕'}</span>`;
          th.onclick = ()=>{ if(state.sort===key){ state.dir = state.dir==='asc'?'desc':'asc'; } else { state.sort=key; state.dir='desc'; } renderTable(items); };
          return th;
        };

        trh.append(
          makeTh('Date','date'),
          makeTh('Titre','title'),
          makeTh('FB','fb'),
          makeTh('YT','yt'),
          makeTh('IG','ig'),
          makeTh('WP','wp'),
          makeTh('Somme (sans wp)','sum')
        );
        thead.append(trh);

        const tbody = el('tbody');

        sorted.forEach(v=>{
          const {acc, sommeSansWp} = sumPlatforms(v);
          totFB += acc.FACEBOOK||0; totYT += acc.YOUTUBE||0; totIG += acc.INSTAGRAM||0; totWP += acc.WORDPRESS||0; totSum += sommeSansWp||0;

          const tr = el('tr');
          const dateCell = el('td','date', new Date(v.published_at || v.published_at_local || v.official_published_at).toLocaleDateString('fr-CH',{dateStyle:'medium'}));
          const titleCell = el('td','title');
          const a = document.createElement('a'); a.className='a'; a.href=`/src/front/video.html?id=${v.id}`; a.textContent=v.title||'(sans titre)';
          titleCell.append(a);

          const fb = chip(acc.FACEBOOK);
          const yt = chip(acc.YOUTUBE);
          const ig = chip(acc.INSTAGRAM);
          const wp = chip(acc.WORDPRESS,true); // muted
          const sum = chip(sommeSansWp);

          tr.append(dateCell, titleCell, el('td',null,'').appendChild(fb).parentNode, el('td',null,'').appendChild(yt).parentNode, el('td',null,'').appendChild(ig).parentNode, el('td',null,'').appendChild(wp).parentNode, el('td',null,'').appendChild(sum).parentNode);
          tbody.append(tr);
        });

        const tfoot = el('tfoot');
        const trf = el('tr');
        const th1 = el('th',null,''); th1.colSpan=2;
        const thFB = el('th',null, format(totFB));
        const thYT = el('th',null, format(totYT));
        const thIG = el('th',null, format(totIG));
        const thWP = el('th',null, format(totWP));
        const thSUM= el('th',null, format(totSum));
        trf.append(th1, thFB, thYT, thIG, thWP, thSUM);
        tfoot.append(trf);

        table.append(thead, tbody, tfoot);
        wrap.append(table);
      }

      function chip(v, muted){
        const c = el('span','val'+(v>0?' pos':' zero')+(muted?' muted':''), format(v));
        return c;
      }
      function format(n){ n = parseInt(n||0,10)||0; return n.toLocaleString('fr-CH'); }

      function keyVal(v, key){
        const {acc, sommeSansWp} = sumPlatforms(v);
        switch(key){
          case 'date': {
                const t = new Date(v.published_at || v.published_at_local || v.official_published_at).getTime();
                return isNaN(t) ? 0 : t;
          }
          case 'title': return (v.canonical_title||'').toLowerCase();
          case 'fb': return acc.FACEBOOK||0;
          case 'yt': return acc.YOUTUBE||0;
          case 'ig': return acc.INSTAGRAM||0;
          case 'wp': return acc.WORDPRESS||0;
          case 'sum': return sommeSansWp||0;
          default: return 0;
        }
      }

      function renderError(msg){
        const wrap = $('#tableWrap');
        wrap.innerHTML='';
        const err = el('div','err', msg);
        wrap.append(err);
      }
    })();