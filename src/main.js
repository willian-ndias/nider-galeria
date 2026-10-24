import './style.css';
import 'photoswipe/style.css';
import PhotoSwipe from 'photoswipe';
import { App } from '@capacitor/app';
import { Capacitor, registerPlugin } from '@capacitor/core';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { PrivacyScreen } from '@capacitor-community/privacy-screen';
import { vault, toBase64, fromBase64 } from './vault.js';

// Ponte com o Android (android/.../NiderPlugin.java): Compartilhar, apagar original, devolver à galeria.
const Nider = registerPlugin('Nider');
const TRASH = 'lixeira';

const VERSION = '2.3.1 beta';
const ICON_G = './nider-galeria.svg';
const ICON_F = './nider-forte.svg';

const $app = document.getElementById('app');
const $picker = document.getElementById('picker');

const st = {
  screen: 'gallery', // gallery | forte | settings
  drawer: false,
  space: null,
  folder: null,
  pin: null, // { mode, step, first, value, error, busy }
  toast: null,
  dialog: null,
  sheet: null,
  wipe: false,
  picking: false,
  tab: 'fotos', // fotos | pastas | fav | lixo (galeria do celular)
  trash: null, // { items } lixeira do Android
  target: null, // nome da pasta Nider nova: escolhendo as fotos para ela
  album: null, // pasta do celular aberta
  lib: null, // { items } fotos e vídeos do celular; null = ainda não leu
  libLimit: 600,
  gsel: null, // fotos do celular selecionadas
  update: null, // { latest, notes, url } quando tem versão nova
  forteExists: false, // sem Forte criado, o menu mostra a entrada para criar
  sel: null, // ids selecionados dentro de uma pasta ou da lixeira
  media: null, // { read, manage }: liberações do Android para apagar da galeria sem perguntar
};

const thumbs = new Map();
const fulls = new Map();
let toastTimer = 0;
let pswp = null;

const plural = (n) => `${n} ${n === 1 ? 'item' : 'itens'}`;
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

const svg = {
  menu: '<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 7h16M4 12h16M4 17h16"/></svg>',
  back: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 5l-7 7 7 7"/></svg>',
  gear: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#C9D2DC" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/></svg>',
  starOn: '<svg width="16" height="16" viewBox="0 0 24 24" fill="#FFD166" stroke="#FFD166" stroke-width="1.5" stroke-linejoin="round"><path d="M12 3.5l2.6 5.3 5.8.8-4.2 4.1 1 5.8L12 16.8l-5.2 2.7 1-5.8-4.2-4.1 5.8-.8z"/></svg>',
  copy: '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"><rect x="8" y="8" width="12" height="12" rx="2.5"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"/></svg>',
  hide: '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12s3.5-6.5 9-6.5 9 6.5 9 6.5-3.5 6.5-9 6.5S3 12 3 12z"/><circle cx="12" cy="12" r="2.5"/><path d="M4 4l16 16"/></svg>',
  share: '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"><circle cx="18" cy="5.5" r="2.5"/><circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="18.5" r="2.5"/><path d="M8.2 10.8l7.6-4.1M8.2 13.2l7.6 4.1"/></svg>',
  lock: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#A9B3BF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="4.5" y="10.5" width="15" height="10" rx="2.5"/><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5"/></svg>',
  plus: '<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>',
  photo: '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4.5" width="18" height="15" rx="3"/><path d="M3 16l5-5 4 4 3-3 6 6"/><circle cx="15.5" cy="9" r="1.6"/></svg>',
  folder: (c = 'currentColor', s = 28) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="${c}" stroke-width="1.9" stroke-linejoin="round"><path d="M3 7.5A2.5 2.5 0 0 1 5.5 5H10l2 2.5h6.5A2.5 2.5 0 0 1 21 10v6.5A2.5 2.5 0 0 1 18.5 19h-13A2.5 2.5 0 0 1 3 16.5z"/></svg>`,
  folderPlus: '<svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 7.5A2.5 2.5 0 0 1 5.5 5H10l2 2.5h6.5A2.5 2.5 0 0 1 21 10v6.5A2.5 2.5 0 0 1 18.5 19h-13A2.5 2.5 0 0 1 3 16.5z"/><path d="M12 10.5v6M9 13.5h6"/></svg>',
  star: '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linejoin="round"><path d="M12 3.5l2.6 5.3 5.8.8-4.2 4.1 1 5.8L12 16.8l-5.2 2.7 1-5.8-4.2-4.1 5.8-.8z"/></svg>',
  trash: '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7h16M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13"/></svg>',
  import: '<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4v11M7.5 10.5L12 15l4.5-4.5M5 19.5h14"/></svg>',
  info: '<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="#C9D2DC" stroke-width="1.9" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5v.01"/></svg>',
  chev: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#9AA6B2" stroke-width="2" stroke-linecap="round"><path d="M9 6l6 6-6 6"/></svg>',
  pdf: '<svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="#FF9F8A" stroke-width="1.8" stroke-linejoin="round"><path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4"/></svg>',
  restore: '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20V9M7.5 13.5L12 9l4.5 4.5"/><path d="M5 4.5h14"/></svg>',
  edit: '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16z"/></svg>',
  undo: '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M9 14L4 9l5-5"/><path d="M4 9h10.5a5.5 5.5 0 0 1 0 11H11"/></svg>',
  close: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg>',
  tick: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="#0E1216" stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>',
  play: '<svg width="14" height="14" viewBox="0 0 24 24" fill="#fff"><path d="M7 4.5v15l12-7.5z"/></svg>',
  refresh: '<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="#C9D2DC" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M20 12a8 8 0 1 1-2.4-5.7M20 4v5h-5"/></svg>',
  eye: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#C9D2DC" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12s3.5-6.5 9-6.5 9 6.5 9 6.5-3.5 6.5-9 6.5S3 12 3 12z"/><path d="M4 4l16 16"/></svg>',
};

function say(text) {
  idle();
  clearTimeout(toastTimer);
  st.toast = text;
  render();
  toastTimer = setTimeout(() => { st.toast = null; render(); }, 3200);
}

// ---------- telas ----------

const fmtDur = (ms) => { const t = Math.round(ms / 1000); return `${Math.floor(t / 60)}:${String(t % 60).padStart(2, '0')}`; };
const monthOf = (t) => { const m = new Date(t).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' }); return m.charAt(0).toUpperCase() + m.slice(1); };

function albums() {
  const map = new Map();
  for (const it of st.lib.items) {
    if (!map.has(it.b)) map.set(it.b, { id: it.b, name: it.bn, items: [] });
    map.get(it.b).items.push(it);
  }
  return [...map.values()];
}

function galleryList() {
  if (st.tab === 'lixo') return st.trash ? st.trash.items : [];
  if (!st.lib || !st.lib.items) return [];
  if (st.album) return st.lib.items.filter((i) => i.b === st.album);
  if (st.tab === 'fav') return st.lib.items.filter((i) => i.fav);
  return st.lib.items;
}

const trashDays = (it) => it.exp ? Math.max(1, Math.ceil((it.exp - Date.now()) / 86400000)) : 30;

function tile(it) {
  const on = st.gsel && st.gsel.includes(it.uri);
  return `<button class="tile ${on ? 'picked' : ''}" data-act="gitem" data-uri="${esc(it.uri)}" data-hold="gitem" data-g="${esc(it.uri)}" aria-label="${esc(it.name || 'foto')}">
    ${it.video ? `<span class="vid">${svg.play}${fmtDur(it.d)}</span>` : ''}
    ${st.tab === 'lixo' ? `<span class="name">${trashDays(it)} ${trashDays(it) === 1 ? 'dia' : 'dias'}</span>` : it.fav && st.tab !== 'fav' ? `<span class="favmark">${svg.starOn}</span>` : ''}
    ${st.gsel ? `<span class="check ${on ? 'on' : ''}">${on ? svg.tick : ''}</span>` : ''}
  </button>`;
}

function viewGallery() {
  const lib = st.lib;
  let body;
  if (lib && lib.denied) {
    body = `<div class="empty"><img src="${ICON_G}" alt="" width="120" height="120"><b>Ver as fotos do celular</b><p>Para mostrar suas fotos e pastas, o Nider Galeria precisa da sua liberação.</p><button class="btn primary" style="height:48px;padding:0 22px" data-act="grantRead">Permitir</button></div>`;
  } else if (!lib) {
    body = '<div class="empty"><p>Carregando suas fotos…</p></div>';
  } else if (st.tab === 'pastas' && !st.album) {
    body = `<div class="folders">${albums().map((a) => {
      const cells = [0, 1, 2, 3].map((n) => `<i ${a.items[n] ? `data-g="${esc(a.items[n].uri)}"` : ''}></i>`).join('');
      return `<button class="folder" data-act="album" data-id="${esc(a.id)}"><div class="cover">${cells}<span class="badge">${svg.folder('#7FAAF9', 22)}</span></div><b>${esc(a.name)}</b><small>${a.items.length} ${a.items.length === 1 ? 'item' : 'itens'}</small></button>`;
    }).join('')}<button class="folder" data-act="newNiderFolder"><div class="newcard">${svg.folderPlus}</div><b style="color:#C9D2DC">Nova pasta</b></button></div>`;
  } else {
    const list = galleryList();
    const shown = list.slice(0, st.libLimit);
    if (!list.length) {
      body = st.tab === 'fav' ? '<div class="empty"><b>Nenhum favorito ainda</b><p>Segure uma foto e toque em Favoritar.</p></div>'
        : st.tab === 'lixo' ? (st.trash ? '<div class="empty"><b>A lixeira está vazia</b><p>O que você apagar fica aqui por 30 dias.</p></div>' : '<div class="empty"><p>Carregando a lixeira…</p></div>')
        : '<div class="empty"><b>Nenhuma foto aqui</b></div>';
    } else if (st.album || st.tab !== 'fotos') body = `<div class="grid">${shown.map(tile).join('')}</div>`;
    else {
      let html = '';
      let month = null;
      let open = false;
      for (const it of shown) {
        const m = monthOf(it.t);
        if (m !== month) { if (open) html += '</div></section>'; html += `<section class="month"><h2>${m}</h2><div class="grid">`; month = m; open = true; }
        html += tile(it);
      }
      if (open) html += '</div></section>';
      body = html;
    }
    if (list.length > shown.length) body += '<div id="more" class="more" aria-hidden="true"></div>';
  }
  const album = st.album && st.lib && albums().find((a) => a.id === st.album);
  const head = st.target
    ? `<button class="ib" data-act="targetCancel" aria-label="Cancelar">${svg.close}</button><div class="title"><b>Fotos para “${esc(st.target)}”</b><small>${st.gsel.length} ${st.gsel.length === 1 ? 'escolhida' : 'escolhidas'} · toque para escolher</small></div>`
    : st.gsel
    ? `<button class="ib" data-act="gselClear" aria-label="Cancelar seleção">${svg.close}</button><div class="title"><b>${st.gsel.length} ${st.gsel.length === 1 ? 'selecionada' : 'selecionadas'}</b></div><button class="pill" data-act="gselAll">Todas</button>`
    : album
      ? `<button class="ib" data-act="albumUp" aria-label="Voltar às pastas">${svg.back}</button><div class="title"><b>${esc(album.name)}</b><small>${album.items.length} itens · segure para selecionar</small></div>`
      : `<button class="ib" data-act="drawer" aria-label="Abrir menu" style="position:relative">${svg.menu}${st.update ? '<span class="dot"></span>' : ''}</button><div class="title"><b>${{ fotos: 'Fotos', pastas: 'Pastas', fav: 'Favoritos', lixo: 'Lixeira' }[st.tab]}</b><small>${st.tab === 'lixo' ? 'Some de vez em 30 dias · segure para selecionar' : st.tab === 'fav' ? `${galleryList().length} itens` : lib && lib.items ? `${lib.items.length} itens` : ''}</small></div>`;
  const n = st.gsel ? st.gsel.length : 0;
  const dis = n ? '' : 'disabled';
  const allFav = n && st.gsel.every((u) => (galleryList().find((x) => x.uri === u) || {}).fav);
  const nav = st.target
    ? `<nav class="actions"><button data-act="targetMove" ${dis}>${svg.folder()}Mover para cá</button><button data-act="targetCopy" ${dis}>${svg.copy}Copiar para cá</button></nav>`
    : st.gsel && st.tab === 'lixo'
    ? `<nav class="actions"><button data-act="gUntrash" ${dis}>${svg.undo}Restaurar</button><button data-act="gDeleteForever" class="bad" ${dis}>${svg.trash}Apagar de vez</button></nav>`
    : st.gsel
    ? `<nav class="actions">
        <button data-act="gShare" ${dis}>${svg.share}Enviar</button>
        <button data-act="gFav" ${dis}>${allFav ? svg.starOn : svg.star}${allFav ? 'Desfavoritar' : 'Favoritar'}</button>
        <button data-act="gMove" ${dis}>${svg.folder()}Mover</button>
        <button data-act="gToForte" ${dis}>${svg.hide}Ocultar</button>
        <button data-act="gTrash" class="bad" ${dis}>${svg.trash}Apagar</button>
      </nav>`
    : `<nav class="nav" aria-label="Seções">
      <button data-act="tabFotos" class="${st.tab === 'fotos' && !st.album ? 'on' : ''}">${svg.photo}Fotos</button>
      <button data-act="tabPastas" class="${st.tab === 'pastas' || st.album ? 'on' : ''}">${svg.folder()}Pastas</button>
      <button data-act="tabFav" class="${st.tab === 'fav' ? 'on' : ''}">${svg.star}Favoritos</button>
      <button data-act="tabLixo" class="${st.tab === 'lixo' ? 'on' : ''}">${svg.trash}Lixeira</button>
    </nav>`;
  return `<header class="top">${head}</header><main class="main" id="gmain">${body}</main>${nav}`;
}

// ---------- galeria do celular ----------

async function loadLibrary() {
  try {
    const acc = await Nider.mediaAccess();
    if (!acc.read) { st.lib = { denied: true }; render(); return; }
    const { items } = await Nider.listMedia();
    st.lib = { items };
  } catch {
    st.lib = { items: [] };
  }
  render();
}

const moreObs = new IntersectionObserver((entries) => {
  if (entries.some((e) => e.isIntersecting)) { moreObs.disconnect(); st.libLimit += 600; render(); }
}, { rootMargin: '1200px' });

const gthumbs = new Map();
let gqueue = [];
let gbusy = false;
const gobs = new IntersectionObserver((entries) => {
  for (const e of entries) {
    if (!e.isIntersecting) continue;
    gobs.unobserve(e.target);
    const u = e.target.dataset.g;
    if (!gthumbs.has(u) && !gqueue.includes(u)) gqueue.push(u);
  }
  pumpThumbs();
}, { rootMargin: '400px' });

async function pumpThumbs() {
  if (gbusy || !gqueue.length) return;
  gbusy = true;
  const batch = gqueue.splice(0, 24);
  try {
    const out = await Nider.thumbs({ uris: batch });
    for (const u of batch) gthumbs.set(u, out[u] || '');
    for (const el of $app.querySelectorAll('[data-g]')) {
      const url = gthumbs.get(el.dataset.g);
      if (url) el.style.backgroundImage = `url("${url}")`;
    }
  } catch { for (const u of batch) gthumbs.set(u, ''); }
  gbusy = false;
  pumpThumbs();
}

function attachThumbs() {
  for (const el of $app.querySelectorAll('[data-g]')) {
    const url = gthumbs.get(el.dataset.g);
    if (url) el.style.backgroundImage = `url("${url}")`;
    else if (url === undefined) gobs.observe(el);
  }
}

async function openGalleryPhoto(it) {
  if (it.video) {
    try { await Nider.openExternal({ uri: it.uri, mime: it.mime || 'video/*' }); } catch (e) { say(e.message || 'Nenhum app abre este vídeo'); }
    return;
  }
  const list = galleryList().filter((x) => !x.video);
  const start = list.indexOf(it);
  const src = async (x) => Capacitor.convertFileSrc((await Nider.viewPath({ uri: x.uri })).path);
  const slides = list.map((x) => ({ width: x.w || 1200, height: x.h || 1600, alt: x.name }));
  slides[start].src = await src(it);
  pswp = new PhotoSwipe({ dataSource: slides, index: start, bgOpacity: 1, spacing: 0.12, showHideAnimationType: 'fade', closeTitle: 'Fechar', zoomTitle: 'Zoom', arrowPrevTitle: 'Anterior', arrowNextTitle: 'Próxima' });
  const fill = (i) => {
    if (i < 0 || i >= list.length || slides[i].src || slides[i].loading) return;
    slides[i].loading = true;
    src(list[i]).then((u) => { slides[i].src = u; if (pswp) pswp.refreshSlideContent(i); }).catch(() => {});
  };
  pswp.addFilter('itemData', (data, i) => {
    if (!data.src) { fill(i); return { ...data, src: gthumbs.get(list[i].uri) || 'data:image/gif;base64,R0lGODlhAQABAAAAACw=' }; }
    return data;
  });
  pswp.on('change', () => { fill(pswp.currIndex - 1); fill(pswp.currIndex + 1); });
  pswp.on('destroy', () => { pswp = null; Nider.clearView().catch(() => {}); });
  pswp.init();
  fill(start - 1);
  fill(start + 1);
}

async function loadTrash() {
  try { st.trash = await Nider.listTrash(); } catch { st.trash = { items: [] }; }
  render();
}

async function toTarget(move) {
  const uris = [...st.gsel];
  const folder = st.target;
  busy(`${move ? 'Movendo' : 'Copiando'} ${uris.length} para ${folder}`);
  st.picking = true;
  try {
    const r = await Nider.copyTo({ uris, folder, move });
    st.target = null;
    st.gsel = null;
    st.tab = 'pastas';
    say(`Pasta “${folder}” criada com ${r.saved} ${r.saved === 1 ? 'item' : 'itens'}${move && r.deleted === false ? ' (as originais continuam)' : ''}`);
  } catch { say('Não consegui criar a pasta'); }
  setTimeout(() => { st.picking = false; }, 1500);
  loadLibrary();
}

// ---------- carregando: hexágono do Nider no meio da tela ----------
// Fica fora do #app e muda só o texto, para a tela não piscar a cada atualização.

const $busy = document.createElement('div');
$busy.className = 'busy-wrap';
$busy.hidden = true;
$busy.innerHTML = `<div class="busy-card" role="status" aria-live="polite">
  <svg class="hex" width="72" height="72" viewBox="0 0 100 100" aria-hidden="true">
    <polygon points="50,6 88,28 88,72 50,94 12,72 12,28" fill="none" stroke="#2A333D" stroke-width="7"/>
    <polygon class="hex-run" points="50,6 88,28 88,72 50,94 12,72 12,28" fill="none" stroke="#4F8CF7" stroke-width="7" stroke-linejoin="round"/>
    <polygon points="50,30 67,40 67,60 50,70 33,60 33,40" fill="#4F8CF7" opacity=".9"/>
  </svg>
  <b class="busy-pct"></b><span class="busy-text"></span></div>`;
document.body.appendChild($busy);

function busy(text, pct) {
  st.toast = null;
  $busy.hidden = false;
  $busy.querySelector('.busy-text').textContent = text;
  $busy.querySelector('.busy-pct').textContent = pct >= 0 ? `${pct}%` : '';
}
function idle() { $busy.hidden = true; }

// ---------- atualização pelo app ----------

function checkedToday() {
  try { return localStorage.getItem('nider.update') === new Date().toDateString(); } catch { return false; }
}

async function checkUpdate(manual) {
  if (!manual && checkedToday()) return;
  let info;
  try { info = await Nider.checkUpdate(); } catch { if (manual) say('Sem conexão. Tente de novo mais tarde'); return; }
  try { localStorage.setItem('nider.update', new Date().toDateString()); } catch { /* sem armazenamento */ }
  if (!info.newer) { st.update = null; render(); if (manual) say(`Você está na versão mais nova (${info.current})`); return; }
  st.update = info;
  render();
  const notes = (info.notes || '').split('\n').filter(Boolean).slice(0, 6).join('\n');
  if (await ask({ title: `Versão ${info.latest} disponível`, text: notes || 'Toque em Atualizar para instalar.', ok: 'Atualizar' })) doUpdate();
}

let installPending = false;
async function doUpdate() {
  if (!st.update) return;
  busy(`Baixando a versão ${st.update.latest}`, 0);
  const sub = await Nider.addListener('updateProgress', ({ pct }) => busy(`Baixando a versão ${st.update.latest}`, pct));
  try {
    st.picking = true;
    const r = await Nider.installUpdate({ url: st.update.url });
    idle();
    if (r.needsPermission) { installPending = true; say('Permita instalar apps do Nider Galeria e volte'); }
  } catch (e) { idle(); say(e.message || 'O download falhou'); }
  sub.remove();
}

function shownItems() {
  const items = st.space.index.items;
  return st.folder === TRASH ? items.filter((i) => i.trashed) : items.filter((i) => !i.trashed && i.folder === st.folder);
}

const daysLeft = (t) => Math.max(1, 30 - Math.floor((Date.now() - t) / 86400000));

function viewForte() {
  const sp = st.space;
  const inTrash = st.folder === TRASH;
  const folder = !inTrash && st.folder && sp.index.folders.find((f) => f.id === st.folder);
  const live = sp.index.items.filter((i) => !i.trashed);
  const trashed = sp.index.items.filter((i) => i.trashed);
  const sel = st.sel;
  let body;
  if (folder || inTrash) {
    const items = shownItems();
    body = items.length
      ? `<div class="grid">${items.map((it) => {
          const on = sel && sel.includes(it.id);
          return `<button class="tile ${on ? 'picked' : ''}" data-act="item" data-id="${it.id}" data-hold="item" ${it.kind === 'foto' ? `data-thumb="${it.id}"` : ''} aria-label="${esc(it.name)}" aria-pressed="${on ? 'true' : 'false'}">
            ${it.kind === 'pdf' ? `${svg.pdf}<span class="pdf">PDF</span>` : ''}
            ${sel ? `<span class="check ${on ? 'on' : ''}">${on ? svg.tick : ''}</span>` : ''}
            <span class="name">${inTrash ? `${daysLeft(it.trashed)} ${daysLeft(it.trashed) === 1 ? 'dia' : 'dias'}` : esc(it.name)}</span>
          </button>`;
        }).join('')}</div>`
      : inTrash
        ? '<div class="empty"><b>A lixeira está vazia</b><p>O que você excluir fica aqui por 30 dias e depois some de vez.</p></div>'
        : '<div class="empty"><b>Pasta vazia</b><p>Toque em Adicionar, ou compartilhe fotos e PDFs da galeria para o Nider Galeria. Eles ficam criptografados.</p></div>';
  } else {
    body = `<div class="folders">${sp.index.folders.map((f) => {
      const items = live.filter((i) => i.folder === f.id);
      const photos = items.filter((i) => i.kind === 'foto').slice(0, 4);
      const cells = [0, 1, 2, 3].map((n) => `<i ${photos[n] ? `data-thumb="${photos[n].id}"` : ''}></i>`).join('');
      return `<button class="folder" data-act="folder" data-id="${f.id}" data-hold="folder">
          <div class="cover">${cells}<span class="badge">${svg.folder('#C9D2DC', 22)}</span></div>
          <b>${esc(f.name)}</b><small>${plural(items.length)}</small>
        </button>`;
    }).join('')}
      <button class="folder" data-act="newFolder"><div class="newcard">${svg.folderPlus}</div><b style="color:#C9D2DC">Nova pasta</b></button>
      <button class="folder" data-act="folder" data-id="${TRASH}"><div class="newcard trashcard">${svg.trash}</div><b style="color:#C9D2DC">Lixeira</b><small>${plural(trashed.length)}</small></button>
    </div>`;
  }
  const head = sel
    ? `<button class="ib" data-act="selClear" aria-label="Cancelar seleção">${svg.close}</button>
       <div class="title"><b>${sel.length} ${sel.length === 1 ? 'selecionado' : 'selecionados'}</b></div>
       <button class="pill" data-act="selAll">Todos</button>`
    : `${folder || inTrash ? `<button class="ib" data-act="up" aria-label="Voltar às pastas">${svg.back}</button>` : ''}
       <div class="title"><b>${inTrash ? 'Lixeira' : folder ? esc(folder.name) : 'Forte'}</b><small>${inTrash ? 'Some de vez em 30 dias · segure para selecionar' : folder ? `${plural(shownItems().length)} · segure para selecionar` : `${plural(live.length)} · tranca ao sair`}</small></div>
       ${folder || inTrash ? '' : `<button class="ib" data-act="settings" aria-label="Configurações do Forte">${svg.gear}</button>`}
       <button class="pill" data-act="lock">${svg.lock}Trancar</button>`;
  const n = sel ? sel.length : 0;
  const bar = !sel ? '' : inTrash
    ? `<nav class="actions"><button data-act="untrashSel" ${n ? '' : 'disabled'}>${svg.undo}Recuperar</button><button data-act="purgeSel" class="bad" ${n ? '' : 'disabled'}>${svg.trash}Excluir de vez</button></nav>`
    : `<nav class="actions"><button data-act="restoreSel" ${n ? '' : 'disabled'}>${svg.restore}Restaurar</button><button data-act="moveSel" ${n ? '' : 'disabled'}>${svg.folder()}Mover</button><button data-act="renameSel" ${n === 1 ? '' : 'disabled'}>${svg.edit}Renomear</button><button data-act="trashSel" class="bad" ${n ? '' : 'disabled'}>${svg.trash}Excluir</button></nav>`;
  return `
    <header class="top">${head}</header>
    <main class="main">${body}</main>
    ${bar}
    ${folder && !sel ? `<button class="fab" data-act="add">${svg.plus}Adicionar</button>` : ''}`;
}

function viewSettings() {
  const sp = st.space;
  const real = sp.primary;
  const hasDecoy = real && !!sp.index.decoy;
  const lockOn = sp.index.settings.lockOnLeave !== false;
  const wipeOn = real ? st.wipe : !!sp.index.settings.wipe;
  return `
    <header class="top">
      <button class="ib" data-act="closeSettings" aria-label="Voltar ao Forte">${svg.back}</button>
      <div class="title"><b>Configurações do Forte</b></div>
    </header>
    <main class="main"><div class="cards">
      <div class="card">
        <img src="${ICON_F}" alt="" width="44" height="44" style="border-radius:12px;flex-shrink:0">
        <div style="flex:1"><b>Senha principal</b><p>Abre este Forte e estas configurações.</p>
          <div class="act"><span style="font-size:20px;letter-spacing:4px;color:#C9D2DC">••••</span><button class="btn" style="margin-left:auto" data-act="changePin">Trocar</button></div>
        </div>
      </div>
      ${real ? `<div class="card">
        <div class="ico">${svg.eye}</div>
        <div style="flex:1"><b>Senha de disfarce</b><p>Abre um Forte secundário, com coisas sem importância, e nada mais. Quem digitar essa senha não vê estas configurações.</p>
          <div class="act">
            <span style="white-space:nowrap;font-size:14px;font-weight:700;color:${hasDecoy ? '#C9D2DC' : '#FFB4A8'}">${hasDecoy ? '•••• definida' : 'Não definida'}</span>
            ${hasDecoy ? '<button class="btn danger" style="margin-left:auto" data-act="removeDecoy">Remover</button><button class="btn" data-act="setDecoy">Trocar</button>' : '<button class="btn primary" style="margin-left:auto" data-act="setDecoy">Criar</button>'}
          </div>
        </div>
      </div>` : ''}
      <div class="list">
        <button class="sw" data-act="toggleLock"><span><b>Trancar ao sair do app</b><small>Some também da tela de recentes</small></span><span class="track ${lockOn ? 'on' : ''}"></span></button>
        <button class="sw" data-act="toggleMenu"><span><b>Mostrar o Forte no menu</b><small>Desligado: entre segurando o ícone no topo do menu</small></span><span class="track ${forteInMenu() ? 'on' : ''}"></span></button>
        <button class="sw" data-act="grantMedia" ${st.media && st.media.read && st.media.manage ? 'disabled' : ''}><span><b>Sumir da galeria sem perguntar</b><small>${st.media && st.media.read && st.media.manage ? 'Liberado: o que vai para o Forte some da galeria' : 'Toque para liberar "Gerenciar mídia" uma vez'}</small></span><span class="track ${st.media && st.media.read && st.media.manage ? 'on' : ''}"></span></button>
        <button class="sw" data-act="toggleWipe"><span><b>Apagar após 10 erros</b><small>Apaga o Forte se errarem a senha 10 vezes</small></span><span class="track ${wipeOn ? 'on' : ''}"></span></button>
      </div>
    </div></main>`;
}

// O Forte some do menu depois de criado; entra segurando o ícone no topo do menu.
function forteInMenu() { try { return localStorage.getItem('nider.forteMenu') === '1'; } catch { return false; } }
function showForteRow() { return !st.forteExists || forteInMenu(); }

function viewDrawer() {
  return `<div class="scrim" data-act="closeDrawer"></div>
    <aside class="drawer">
      <div class="dhead"><button data-hold="logo" aria-label="Nider Galeria" style="border-radius:18px;flex-shrink:0"><img src="${ICON_G}" alt="" width="64" height="64" draggable="false" style="display:block;pointer-events:none"></button><div><b>Nider Galeria</b><small>${VERSION}</small></div></div>
      <div style="padding:8px 0">
        <button class="row" data-act="tabFotos">${svg.photo}Fotos</button>
        <button class="row" data-act="tabPastas">${svg.folder()}Pastas</button>
      </div>
      <div class="sep"></div>
      <div style="padding:8px 0">
        ${showForteRow() ? `<button class="row" data-act="openForte" style="min-height:64px"><img src="${ICON_F}" alt="" width="32" height="32" style="border-radius:9px"><span style="flex:1;font-weight:700">Nider Forte</span>${svg.chev}</button>` : ''}
        <button class="row" data-act="checkUpdate">${svg.refresh}<span style="flex:1">Verificar atualização</span>${st.update ? `<span class="badge-new">${esc(st.update.latest)}</span>` : ''}</button>
        <button class="row" data-act="about">${svg.info}Sobre</button>
      </div>
    </aside>`;
}

const PIN_TEXT = {
  unlock: () => ['Nider Forte', 'Digite a senha'],
  create: (s) => s === 1 ? ['Crie a senha principal', 'Ela abre o seu Forte e as configurações dele'] : ['Repita a senha principal', 'Para confirmar'],
  change: (s) => s === 1 ? ['Nova senha principal', '4 números'] : ['Repita a nova senha', 'Para confirmar'],
  decoy: (s) => s === 1 ? ['Senha de disfarce', 'Ela abre só o Forte secundário'] : ['Repita a senha de disfarce', 'Para confirmar'],
};

function viewPin() {
  const p = st.pin;
  const [title, sub] = PIN_TEXT[p.mode](p.step);
  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', 'del'];
  const enter = p.fresh;
  p.fresh = false;
  return `<div class="pin ${enter ? 'enter' : ''} ${p.busy ? 'busy' : ''}">
    <img src="${ICON_F}" alt="" width="78" height="78">
    <h2>${title}</h2><p>${p.busy ? 'Conferindo…' : sub}</p>
    <div class="dots ${p.shake ? 'shake' : ''}">${[0, 1, 2, 3].map((i) => `<i class="${i < p.value.length ? 'on' : ''}"></i>`).join('')}</div>
    <div class="err">${esc(p.error || '')}</div>
    <div class="keys">${keys.map((k) => k === '' ? '<span></span>'
      : `<button class="key ${k === 'del' ? 'ghost' : ''}" data-act="key" data-k="${k}" aria-label="${k === 'del' ? 'Apagar dígito' : k}">${k === 'del' ? '⌫' : k}</button>`).join('')}</div>
    <button class="cancel" data-act="cancelPin">Cancelar</button>
  </div>`;
}

function viewDialog() {
  const d = st.dialog;
  return `<div class="dialog-wrap"><div class="dialog" role="dialog" aria-modal="true">
    ${d.icon ? `<div class="about"><img src="${d.icon}" alt=""></div>` : ''}
    <h3 ${d.icon ? 'style="text-align:center;margin-top:12px"' : ''}>${esc(d.title)}</h3>
    ${d.text ? `<p ${d.icon ? 'style="text-align:center"' : ''}>${esc(d.text)}</p>` : ''}
    ${d.input !== undefined ? `<input id="dlgInput" value="${esc(d.input)}" placeholder="${esc(d.placeholder || '')}" maxlength="40">` : ''}
    <div class="acts">
      ${d.cancel === false ? '' : '<button data-act="dlgCancel">Cancelar</button>'}
      <button class="${d.danger ? 'bad' : 'ok'}" data-act="dlgOk">${esc(d.ok || 'OK')}</button>
    </div>
  </div></div>`;
}

function viewSheet() {
  const s = st.sheet;
  return `<div class="sheet-wrap" data-act="sheetClose"><div class="sheet">
    <div class="grip"></div><div class="head">${esc(s.title)}</div>
    ${s.options.map((o, i) => `<button class="row" data-act="sheetPick" data-i="${i}" style="${o.danger ? 'color:#FFB4A8' : ''}">${esc(o.label)}</button>`).join('')}
  </div></div>`;
}

// Cada render recria a tela; a rolagem da lista é guardada por tela para não voltar ao topo.
const scrolls = new Map();
const scrollKey = () => `${st.screen}|${st.tab}|${st.album}|${st.folder}`;
let lastKey = null;

function render() {
  const active = document.activeElement?.id;
  const oldMain = $app.querySelector('.main');
  if (oldMain && lastKey) scrolls.set(lastKey, oldMain.scrollTop);
  let html = st.screen === 'forte' && st.space ? viewForte() : st.screen === 'settings' && st.space ? viewSettings() : viewGallery();
  if (st.drawer) html += viewDrawer();
  if (st.pin) html += viewPin();
  if (st.sheet) html += viewSheet();
  if (st.dialog) html += viewDialog();
  if (st.toast) html += `<div class="toast" role="status">${esc(st.toast)}</div>`;
  $app.innerHTML = html;
  lastKey = scrollKey();
  const newMain = $app.querySelector('.main');
  if (newMain && scrolls.has(lastKey)) newMain.scrollTop = scrolls.get(lastKey);
  const more = document.getElementById('more');
  if (more) moreObs.observe(more);
  if (active === 'dlgInput' || (st.dialog && st.dialog.input !== undefined)) document.getElementById('dlgInput')?.focus();
  loadThumbs();
  attachThumbs();
}

// ---------- miniaturas e visualizador ----------

async function loadThumbs() {
  const sp = st.space;
  if (!sp) return;
  for (const el of $app.querySelectorAll('[data-thumb]')) {
    const id = el.dataset.thumb;
    if (!thumbs.has(id)) {
      thumbs.set(id, sp.thumb(id).then((b) => URL.createObjectURL(new Blob([b], { type: 'image/jpeg' }))).catch(() => ''));
    }
    const url = await thumbs.get(id);
    if (url && st.space === sp) el.style.backgroundImage = `url("${url}")`;
  }
}

function fullUrl(item) {
  if (!fulls.has(item.id)) {
    fulls.set(item.id, st.space.read(item.id).then((b) => URL.createObjectURL(new Blob([b], { type: item.mime }))));
  }
  return fulls.get(item.id);
}

async function openPhoto(item) {
  const list = st.space.index.items.filter((i) => i.folder === item.folder && i.kind === 'foto');
  const start = list.indexOf(item);
  const first = await fullUrl(item);
  const slides = list.map((it) => ({ width: it.w || 1200, height: it.h || 1600, alt: it.name, id: it.id }));
  slides[start].src = first;
  pswp = new PhotoSwipe({ dataSource: slides, index: start, bgOpacity: 1, spacing: 0.12, wheelToZoom: true, showHideAnimationType: 'fade', closeTitle: 'Fechar', zoomTitle: 'Zoom', arrowPrevTitle: 'Anterior', arrowNextTitle: 'Próxima' });
  const fill = (i) => {
    if (i < 0 || i >= list.length || slides[i].src) return;
    fullUrl(list[i]).then((url) => {
      slides[i].src = url;
      if (pswp) pswp.refreshSlideContent(i);
    });
  };
  pswp.addFilter('itemData', (data, i) => {
    if (!data.src) { fill(i); return { ...data, src: thumbsSync(list[i].id) || 'data:image/gif;base64,R0lGODlhAQABAAAAACw=' }; }
    return data;
  });
  pswp.on('change', () => { fill(pswp.currIndex - 1); fill(pswp.currIndex + 1); });
  pswp.on('destroy', () => { pswp = null; });
  pswp.init();
  fill(start - 1);
  fill(start + 1);
}

const thumbSync = new Map();
function thumbsSync(id) { return thumbSync.get(id); }

async function openPdf(item) {
  const [{ getDocument, GlobalWorkerOptions }, worker] = await Promise.all([
    import('pdfjs-dist'),
    import('pdfjs-dist/build/pdf.worker.min.mjs?url'),
  ]);
  GlobalWorkerOptions.workerSrc = worker.default;
  const data = await st.space.read(item.id);
  const wrap = document.createElement('div');
  wrap.className = 'pdfv';
  wrap.innerHTML = `<header class="top"><button class="ib" aria-label="Fechar">${svg.back}</button><div class="title"><b>${esc(item.name)}</b><small>PDF</small></div>
    <button class="ib" data-z="-1" aria-label="Diminuir" style="font-size:26px">−</button><button class="ib" data-z="1" aria-label="Aumentar" style="font-size:26px">+</button></header><div class="pages"></div>`;
  document.body.appendChild(wrap);
  const pagesEl = wrap.querySelector('.pages');
  const doc = await getDocument({ data }).promise;
  let zoom = 1;
  const draw = async () => {
    pagesEl.innerHTML = '';
    const width = (window.innerWidth - 16) * zoom;
    for (let n = 1; n <= doc.numPages; n++) {
      if (!wrap.isConnected) return;
      const page = await doc.getPage(n);
      const base = page.getViewport({ scale: 1 });
      const scale = width / base.width;
      const vp = page.getViewport({ scale: scale * (window.devicePixelRatio || 1) });
      const c = document.createElement('canvas');
      c.width = vp.width;
      c.height = vp.height;
      c.style.width = `${width}px`;
      pagesEl.appendChild(c);
      await page.render({ canvas: c, canvasContext: c.getContext('2d'), viewport: vp }).promise;
    }
  };
  const close = () => { wrap.remove(); doc.destroy(); pdfClose = null; };
  pdfClose = close;
  wrap.querySelector('.ib').onclick = close;
  wrap.querySelectorAll('[data-z]').forEach((b) => { b.onclick = () => { zoom = Math.min(3, Math.max(1, zoom + Number(b.dataset.z) * 0.5)); draw(); }; });
  draw();
}
let pdfClose = null;

// ---------- Forte ----------

function openPin(mode) {
  st.drawer = false;
  st.pin = { mode, step: 1, first: '', value: '', error: '', busy: false, shake: false, fresh: true };
  render();
}

async function enter(space) {
  st.space = space;
  st.folder = null;
  st.screen = 'forte';
  st.pin = null;
  st.wipe = space.primary ? await vault.getWipe() : false;
  try { await PrivacyScreen.enable(); } catch { /* web */ }
  space.purgeTrash().catch(() => {});
  render();
  if (space.primary) {
    try {
      await vault.ensureInbox(space);
      const n = await vault.importInbox(space);
      if (n && st.space === space) say(`${plural(n)} ${n === 1 ? 'novo' : 'novos'} em Recebidos`);
    } catch (e) { console.error(e); }
  }
}

function lock(quiet) {
  if (pswp) pswp.destroy();
  if (pdfClose) pdfClose();
  for (const p of [...thumbs.values(), ...fulls.values()]) p.then((u) => u && URL.revokeObjectURL(u)).catch(() => {});
  thumbs.clear();
  fulls.clear();
  thumbSync.clear();
  st.space = null;
  st.folder = null;
  st.sel = null;
  st.screen = 'gallery';
  st.sheet = null;
  st.dialog = null;
  try { PrivacyScreen.disable(); } catch { /* web */ }
  if (!quiet) say('Forte trancado');
  else render();
}

async function pinSubmit(v) {
  const p = st.pin;
  const fail = (msg) => { Object.assign(p, { value: '', error: msg, shake: true, busy: false }); render(); setTimeout(() => { p.shake = false; }, 450); };
  p.busy = true;
  render();
  try {
    if (p.mode === 'unlock') {
      const space = await vault.unlock(v);
      if (!space) {
        if (!(await vault.exists())) { st.forteExists = false; st.pin = null; say('O Forte foi apagado depois de 10 senhas erradas'); return; }
        fail('Senha incorreta');
        return;
      }
      await enter(space);
      return;
    }
    if (p.step === 1) {
      const selfId = st.space?.id;
      if (p.mode === 'decoy' && await vault.opensId(v, selfId)) { fail('Tem que ser diferente da principal'); return; }
      if (p.mode === 'change' && await vault.opensOther(v, selfId)) { fail(st.space.primary ? 'Tem que ser diferente da de disfarce' : 'Escolha outra senha'); return; }
      Object.assign(p, { step: 2, first: v, value: '', busy: false });
      render();
      return;
    }
    if (v !== p.first) { Object.assign(p, { step: 1, first: '' }); fail('As senhas não batem. Comece de novo'); return; }
    if (p.mode === 'create') { const space = await vault.createPrimary(v); st.forteExists = true; await enter(space); ask({ title: 'Forte criado', text: 'Para ninguém ver que ele existe, o Forte sai do menu. Para entrar: abra o menu e segure o ícone do Nider Galeria lá no topo. Dá para voltar a mostrar em Configurações do Forte.', ok: 'Entendi', cancel: false }); return; }
    if (p.mode === 'change') { await vault.changePin(st.space, v); st.pin = null; say('Senha principal trocada'); return; }
    if (p.mode === 'decoy') { await vault.setDecoy(st.space, v); st.pin = null; say('Senha de disfarce salva'); }
  } catch (e) {
    fail('Algo deu errado. Tente de novo');
    console.error(e);
  }
}

async function makeThumb(file) {
  const bmp = await createImageBitmap(file);
  const max = 360;
  const r = Math.min(1, max / Math.max(bmp.width, bmp.height));
  const c = document.createElement('canvas');
  c.width = Math.round(bmp.width * r);
  c.height = Math.round(bmp.height * r);
  c.getContext('2d').drawImage(bmp, 0, 0, c.width, c.height);
  const blob = await new Promise((res) => c.toBlob(res, 'image/jpeg', 0.82));
  const out = { w: bmp.width, h: bmp.height, thumb: new Uint8Array(await blob.arrayBuffer()) };
  bmp.close();
  return out;
}

async function storeOne(sp, folder, { name, mime, bytes }) {
  const isPdf = mime === 'application/pdf' || /\.pdf$/i.test(name);
  const isImg = mime.startsWith('image/');
  if (!isPdf && !isImg) return false;
  let meta = { name, mime: mime || (isPdf ? 'application/pdf' : 'image/jpeg'), kind: isPdf ? 'pdf' : 'foto', size: bytes.length };
  let thumb = null;
  if (isImg) {
    try { const t = await makeThumb(new Blob([bytes], { type: mime })); thumb = t.thumb; meta = { ...meta, w: t.w, h: t.h }; } catch { /* formato sem decodificador: guarda sem miniatura */ }
  }
  await sp.addItem(folder, meta, bytes, thumb);
  return true;
}

async function addFiles(files) {
  const sp = st.space;
  const folder = st.folder;
  if (!sp || !folder) return;
  let n = 0;
  for (const file of files) {
    n++;
    busy(`Guardando ${n} de ${files.length}`, Math.round((n - 1) * 100 / files.length));
    await storeOne(sp, folder, { name: file.name, mime: file.type || '', bytes: new Uint8Array(await file.arrayBuffer()) });
  }
  say(`${files.length} ${files.length === 1 ? 'item guardado' : 'itens guardados'} no Forte`);
}

// ---------- liberação para apagar da galeria sem perguntar ----------

async function refreshMedia() {
  try { st.media = await Nider.mediaAccess(); } catch { st.media = null; }
}

async function grantMedia() {
  st.picking = true;
  let acc = st.media || { read: false, manage: false };
  try {
    if (!acc.read) acc = await Nider.requestRead();
    if (!acc.manage) await Nider.openManageMedia();
  } catch { say('Libere em Ajustes do Android → Apps → Nider Galeria'); }
  st.media = acc;
  render();
}

// ---------- seleção: restaurar, renomear, excluir ----------

async function restoreToGallery(ids) {
  const sp = st.space;
  const done = [];
  let n = 0;
  for (const id of ids) {
    const it = sp.index.items.find((x) => x.id === id);
    if (!it) continue;
    n++;
    busy(`Restaurando ${n} de ${ids.length}`, Math.round((n - 1) * 100 / ids.length));
    const tmp = `restaurar/${id}`;
    try {
      await Filesystem.writeFile({ path: tmp, directory: Directory.Cache, data: toBase64(await sp.read(id)), recursive: true });
      const { uri } = await Filesystem.getUri({ path: tmp, directory: Directory.Cache });
      await Nider.saveToGallery({ path: decodeURI(uri.replace(/^file:\/\//, '')), name: it.name, mime: it.mime });
      done.push(id);
    } catch (e) { console.error(e); }
    await Filesystem.deleteFile({ path: tmp, directory: Directory.Cache }).catch(() => {});
  }
  if (done.length) await sp.deleteItems(done);
  st.sel = null;
  say(done.length === ids.length
    ? `${plural(done.length)} de volta na galeria, na pasta Nider Galeria`
    : `${done.length} de ${ids.length} restaurados. Os outros continuam no Forte`);
}

// ---------- ações ----------

function ask(dialog) {
  return new Promise((resolve) => { st.dialog = { ...dialog, resolve }; render(); });
}

const actions = {
  drawer: () => { st.drawer = true; render(); },
  closeDrawer: () => { st.drawer = false; render(); },
  soon: () => say('Chega numa próxima versão'),
  tabFotos: () => { st.drawer = false; st.tab = 'fotos'; st.album = null; st.gsel = null; st.libLimit = 600; render(); },
  tabFav: () => { st.drawer = false; st.tab = 'fav'; st.album = null; st.gsel = null; st.libLimit = 600; render(); },
  tabLixo: () => { st.drawer = false; st.tab = 'lixo'; st.album = null; st.gsel = null; st.libLimit = 600; render(); loadTrash(); },
  tabPastas: () => { st.drawer = false; st.tab = 'pastas'; st.album = null; st.gsel = null; render(); },
  album: (el) => { st.album = el.dataset.id; st.libLimit = 600; render(); },
  albumUp: () => { st.album = null; st.gsel = null; render(); },
  grantRead: async () => { try { await Nider.requestRead(); } catch { /* recusado */ } loadLibrary(); },
  gitem: (el) => {
    const it = galleryList().find((x) => x.uri === el.dataset.uri);
    if (!it) return;
    if (st.gsel || st.tab === 'lixo') {
      const sel = st.gsel || [];
      st.gsel = sel.includes(it.uri) ? sel.filter((u) => u !== it.uri) : [...sel, it.uri];
      render();
      return;
    }
    if (st.gsel) {
      st.gsel = st.gsel.includes(it.uri) ? st.gsel.filter((u) => u !== it.uri) : [...st.gsel, it.uri];
      render();
      return;
    }
    openGalleryPhoto(it).catch(() => say('Não consegui abrir esta foto'));
  },
  gselClear: () => { st.gsel = null; render(); },
  gselAll: () => { const all = galleryList().slice(0, st.libLimit).map((x) => x.uri); st.gsel = st.gsel && st.gsel.length === all.length ? [] : all; render(); },
  gToForte: async () => {
    const uris = [...st.gsel];
    busy(`Ocultando ${uris.length}`);
    st.picking = true;
    try {
      const r = await Nider.toForte({ uris });
      st.gsel = null;
      say(`${r.saved} ${r.saved === 1 ? 'item ocultado' : 'itens ocultados'}${r.deleted === false ? ', mas a original continua na galeria' : ''}`);
    } catch (e) { say(e.message || 'Não consegui ocultar'); }
    setTimeout(() => { st.picking = false; }, 1500);
    loadLibrary();
  },
  gFav: async () => {
    const uris = [...st.gsel];
    const on = !uris.every((u) => (galleryList().find((x) => x.uri === u) || {}).fav);
    st.picking = true;
    try {
      const r = await Nider.favorite({ uris, on });
      if (r.deleted) { st.gsel = null; say(on ? `${uris.length} nos Favoritos` : `${uris.length} fora dos Favoritos`); }
    } catch (e) { say(e.message || 'Não consegui favoritar'); }
    setTimeout(() => { st.picking = false; }, 1500);
    loadLibrary();
  },
  gUntrash: async () => {
    const uris = [...st.gsel];
    st.picking = true;
    try { const r = await Nider.untrash({ uris }); if (r.deleted) { st.gsel = null; say(`${uris.length} de volta na galeria`); } } catch (e) { say(e.message || 'Não consegui restaurar'); }
    setTimeout(() => { st.picking = false; }, 1500);
    loadTrash();
    loadLibrary();
  },
  gDeleteForever: async () => {
    const uris = [...st.gsel];
    if (!(await ask({ title: `Apagar ${uris.length} de vez?`, text: 'Não dá para desfazer.', ok: 'Apagar de vez', danger: true }))) return;
    st.picking = true;
    try { const r = await Nider.deleteForever({ uris }); if (r.deleted) { st.gsel = null; say('Apagado de vez'); } } catch (e) { say(e.message || 'Não consegui apagar'); }
    setTimeout(() => { st.picking = false; }, 1500);
    loadTrash();
  },
  newNiderFolder: async () => {
    const name = await ask({ title: 'Nova pasta', input: '', placeholder: 'Ex.: Viagem 2026', ok: 'Escolher fotos' });
    if (!name) return;
    st.target = name;
    st.tab = 'fotos';
    st.album = null;
    st.gsel = [];
    render();
  },
  targetCancel: () => { st.target = null; st.gsel = null; render(); },
  targetMove: () => toTarget(true),
  targetCopy: () => toTarget(false),
  gShare: async () => { try { await Nider.shareUris({ uris: st.gsel }); } catch { /* cancelado */ } },
  gMove: async () => {
    const uris = [...st.gsel];
    let folders = [];
    try { folders = (await Nider.niderFolders()).folders; } catch { /* sem pastas */ }
    const exec = async (folder, move) => {
      busy(`${move ? 'Movendo' : 'Copiando'} ${uris.length}`);
      st.picking = true;
      try {
        const r = await Nider.copyTo({ uris, folder, move });
        st.gsel = null;
        say(`${r.saved} ${move ? 'movido(s)' : 'copiado(s)'} para ${folder}${move && r.deleted === false ? ', mas a original continua' : ''}`);
      } catch { say('Não consegui mover'); }
      setTimeout(() => { st.picking = false; }, 1500);
      loadLibrary();
    };
    const go = (folder) => {
      st.sheet = { title: `Em “${folder}”`, options: [
        { label: 'Mover (sai da pasta de origem)', run: () => exec(folder, true) },
        { label: 'Copiar (fica nos dois lugares)', run: () => exec(folder, false) },
      ] };
      render();
    };
    const options = folders.map((f) => ({ label: f, run: () => go(f) }));
    options.push({ label: '+ Nova pasta', run: async () => { const name = await ask({ title: 'Nova pasta', input: '', placeholder: 'Ex.: Viagem 2026', ok: 'Criar' }); if (name) go(name); } });
    st.sheet = { title: `Mover ${uris.length} para`, options };
    render();
  },
  gTrash: async () => {
    const uris = [...st.gsel];
    st.picking = true;
    try {
      const r = await Nider.trashUris({ uris });
      if (r.deleted) { st.gsel = null; say(`${uris.length} na lixeira do celular`); }
    } catch (e) { say(e.message || 'Não consegui apagar'); }
    setTimeout(() => { st.picking = false; }, 1500);
    loadLibrary();
  },
  toggleMenu: () => { try { localStorage.setItem('nider.forteMenu', forteInMenu() ? '0' : '1'); } catch { /* sem armazenamento */ } render(); },
  checkUpdate: () => { st.drawer = false; render(); checkUpdate(true); },
  about: async () => {
    st.drawer = false;
    const upd = await ask({ icon: ICON_G, title: 'Nider Galeria', text: `Versão ${VERSION}`, ok: st.update ? `Atualizar para ${st.update.latest}` : 'Verificar atualização' });
    if (upd) { if (st.update) doUpdate(); else checkUpdate(true); }
  },
  openForte: async () => openPin((await vault.exists()) ? 'unlock' : 'create'),
  key: (el) => {
    const p = st.pin;
    if (!p || p.busy) return;
    const k = el.dataset.k;
    if (k === 'del') { p.value = p.value.slice(0, -1); p.error = ''; render(); return; }
    if (p.value.length >= 4) return;
    p.value += k;
    p.error = '';
    render();
    if (p.value.length === 4) setTimeout(() => pinSubmit(p.value), 150);
  },
  cancelPin: () => { st.pin = null; render(); },
  lock: () => lock(false),
  folder: (el) => { st.folder = el.dataset.id; st.sel = null; render(); },
  up: () => { st.folder = null; st.sel = null; render(); },
  selClear: () => { st.sel = null; render(); },
  selAll: () => { const all = shownItems().map((i) => i.id); st.sel = st.sel && st.sel.length === all.length ? [] : all; render(); },
  restoreSel: async () => {
    const ids = [...st.sel];
    if (!(await ask({ title: `Restaurar ${plural(ids.length)} para a galeria?`, text: 'Eles saem do Forte e voltam para a galeria do celular, na pasta Nider Galeria.', ok: 'Sim, restaurar' }))) return;
    await restoreToGallery(ids);
  },
  moveSel: () => {
    const ids = [...st.sel];
    const targets = st.space.index.folders.filter((f) => f.id !== st.folder);
    const go = async (fid, name) => { await st.space.moveItems(ids, fid); st.sel = null; say(`${plural(ids.length)} em ${name}`); };
    const options = targets.map((f) => ({ label: f.name, run: () => go(f.id, f.name) }));
    options.push({ label: '+ Nova pasta', run: async () => {
      const name = await ask({ title: 'Nova pasta', input: '', placeholder: 'Ex.: Documentos', ok: 'Criar' });
      if (!name) return;
      const f = await st.space.addFolder(name);
      go(f.id, f.name);
    } });
    st.sheet = { title: `Mover ${plural(ids.length)} para`, options };
    render();
  },
  renameSel: async () => {
    const it = st.space.index.items.find((i) => i.id === st.sel[0]);
    const name = await ask({ title: 'Renomear', input: it.name, ok: 'Salvar' });
    if (!name) return;
    await st.space.renameItem(it.id, name);
    st.sel = null;
    say('Nome trocado');
  },
  trashSel: async () => {
    const ids = [...st.sel];
    await st.space.trash(ids);
    st.sel = null;
    say(`${plural(ids.length)} na lixeira. Some de vez em 30 dias`);
  },
  untrashSel: async () => {
    const ids = [...st.sel];
    await st.space.untrash(ids);
    st.sel = null;
    say(`${plural(ids.length)} de volta no Forte`);
  },
  purgeSel: async () => {
    const ids = [...st.sel];
    if (!(await ask({ title: `Excluir ${plural(ids.length)} de vez?`, text: 'Não dá para desfazer.', ok: 'Excluir de vez', danger: true }))) return;
    await st.space.deleteItems(ids);
    st.sel = null;
    say('Excluído de vez');
  },
  settings: async () => { st.screen = 'settings'; await refreshMedia(); render(); },
  grantMedia: () => grantMedia(),
  closeSettings: () => { st.screen = 'forte'; render(); },
  changePin: () => openPin('change'),
  setDecoy: () => openPin('decoy'),
  removeDecoy: async () => {
    if (!(await ask({ title: 'Remover senha de disfarce?', text: 'O Forte secundário e tudo que estiver nele serão apagados.', ok: 'Remover', danger: true }))) return;
    await vault.removeDecoy(st.space);
    say('Senha de disfarce removida');
  },
  toggleLock: async () => { const s = st.space.index.settings; s.lockOnLeave = s.lockOnLeave === false; await st.space.save(); render(); },
  toggleWipe: async () => {
    if (st.space.primary) { st.wipe = !st.wipe; await vault.setWipe(st.wipe); } else { st.space.index.settings.wipe = !st.space.index.settings.wipe; await st.space.save(); }
    render();
  },
  newFolder: async () => {
    const name = await ask({ title: 'Nova pasta', input: '', placeholder: 'Ex.: Documentos', ok: 'Criar' });
    if (!name) return;
    const f = await st.space.addFolder(name);
    st.folder = f.id;
    render();
  },
  add: () => { st.picking = true; $picker.value = ''; $picker.click(); },
  item: async (el) => {
    const item = st.space.index.items.find((i) => i.id === el.dataset.id);
    if (!item) return;
    if (st.sel || st.folder === TRASH) {
      const sel = st.sel || [];
      st.sel = sel.includes(item.id) ? sel.filter((x) => x !== item.id) : [...sel, item.id];
      render();
      return;
    }
    try {
      if (item.kind === 'pdf') await openPdf(item);
      else {
        const t = await thumbs.get(item.id);
        if (t) thumbSync.set(item.id, t);
        await openPhoto(item);
      }
    } catch (e) { console.error(e); say('Não consegui abrir este arquivo'); }
  },
  dlgOk: () => {
    const d = st.dialog;
    const value = d.input !== undefined ? document.getElementById('dlgInput').value.trim() : true;
    st.dialog = null;
    render();
    d.resolve(value);
  },
  dlgCancel: () => { const d = st.dialog; st.dialog = null; render(); d.resolve(null); },
  sheetClose: (el, ev) => { if (ev.target === el) { st.sheet = null; render(); } },
  sheetPick: (el) => { const o = st.sheet.options[Number(el.dataset.i)]; st.sheet = null; render(); o.run(); },
};

const holds = {
  logo: () => actions.openForte(),
  gitem: (uri) => {
    const sel = st.gsel || [];
    if (!sel.includes(uri)) st.gsel = [...sel, uri];
    render();
  },
  item: (id) => {
    const sel = st.sel || [];
    if (!sel.includes(id)) st.sel = [...sel, id];
    render();
  },
  folder: (id) => {
    const f = st.space.index.folders.find((x) => x.id === id);
    st.sheet = { title: f.name, options: [
      { label: 'Renomear', run: async () => {
        const name = await ask({ title: 'Renomear pasta', input: f.name, ok: 'Salvar' });
        if (name) { await st.space.renameFolder(id, name); render(); }
      } },
      { label: 'Apagar pasta', danger: true, run: async () => {
        const n = st.space.index.items.filter((i) => i.folder === id).length;
        if (!(await ask({ title: 'Apagar a pasta?', text: n ? `Os ${n} itens dentro dela também serão apagados.` : 'A pasta está vazia.', ok: 'Apagar', danger: true }))) return;
        await st.space.deleteFolder(id);
        say('Pasta apagada');
      } },
    ] };
    render();
  },
};

// Segurar: o temporizador cobre o dedo parado; no Android o toque longo também
// dispara "contextmenu" (e às vezes cancela o pointer), então ele também conta.
let holdTimer = 0;
let held = false;
let holdAt = null;
const fireHold = (el) => {
  if (held) return;
  clearTimeout(holdTimer);
  held = true;
  navigator.vibrate?.(20);
  holds[el.dataset.hold](el.dataset.id || el.dataset.uri);
};
$app.addEventListener('pointerdown', (ev) => {
  const el = ev.target.closest('[data-hold]');
  held = false;
  holdAt = el ? { x: ev.clientX, y: ev.clientY } : null;
  if (!el) return;
  holdTimer = setTimeout(() => fireHold(el), 480);
});
$app.addEventListener('pointermove', (ev) => {
  if (holdAt && Math.hypot(ev.clientX - holdAt.x, ev.clientY - holdAt.y) > 12) { clearTimeout(holdTimer); holdAt = null; }
});
for (const t of ['pointerup', 'pointercancel', 'scroll']) $app.addEventListener(t, () => clearTimeout(holdTimer), true);
$app.addEventListener('contextmenu', (ev) => {
  ev.preventDefault();
  const el = ev.target.closest('[data-hold]');
  if (el && holdAt) fireHold(el);
});
$app.addEventListener('click', (ev) => {
  if (held) { held = false; ev.preventDefault(); return; }
  const el = ev.target.closest('[data-act]');
  if (el && actions[el.dataset.act]) actions[el.dataset.act](el, ev);
});
$app.addEventListener('keydown', (ev) => { if (ev.key === 'Enter' && st.dialog) actions.dlgOk(); });

$picker.addEventListener('change', async () => {
  const files = [...$picker.files];
  st.picking = false;
  if (files.length) {
    try { await addFiles(files); } catch (e) { console.error(e); say('Não consegui guardar algum arquivo'); }
  }
});

App.addListener('appStateChange', ({ isActive }) => {
  if (isActive) {
    if (st.screen === 'settings') refreshMedia().then(render);
    if (installPending) { installPending = false; Nider.installUpdate({}).catch(() => {}); }
    else if (st.screen === 'gallery' && !st.picking) loadLibrary();
    setTimeout(() => { st.picking = false; }, 1500);
    return;
  }
  if (st.space && !st.picking && st.space.index.settings.lockOnLeave !== false) lock(true);
});

App.addListener('backButton', () => {
  if (pswp) return pswp.close();
  if (pdfClose) return pdfClose();
  if (st.dialog) return actions.dlgCancel();
  if (st.sheet) { st.sheet = null; return render(); }
  if (st.sel) return actions.selClear();
  if (st.pin) return actions.cancelPin();
  if (st.drawer) return actions.closeDrawer();
  if (st.screen === 'settings') return actions.closeSettings();
  if (st.folder) return actions.up();
  if (st.space) return lock(false);
  if (st.target) return actions.targetCancel();
  if (st.gsel) return actions.gselClear();
  if (st.album) return actions.albumUp();
  App.minimizeApp();
});

render();
vault.exists().then((x) => { st.forteExists = x; });
loadLibrary();
setTimeout(() => checkUpdate(false), 1500);
