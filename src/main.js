import './style.css';
import 'photoswipe/style.css';
import PhotoSwipe from 'photoswipe';
import { App } from '@capacitor/app';
import { PrivacyScreen } from '@capacitor-community/privacy-screen';
import { vault } from './vault.js';

const VERSION = '2.1.0 beta';
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
  gear: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#C9D2DC" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M12 2.5v3M12 18.5v3M21.5 12h-3M5.5 12h-3M18.7 5.3l-2.1 2.1M7.4 16.6l-2.1 2.1M18.7 18.7l-2.1-2.1M7.4 7.4L5.3 5.3"/></svg>',
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
  eye: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#C9D2DC" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12s3.5-6.5 9-6.5 9 6.5 9 6.5-3.5 6.5-9 6.5S3 12 3 12z"/><path d="M4 4l16 16"/></svg>',
};

function say(text) {
  clearTimeout(toastTimer);
  st.toast = text;
  render();
  toastTimer = setTimeout(() => { st.toast = null; render(); }, 3200);
}

// ---------- telas ----------

function viewGallery() {
  return `
    <header class="top">
      <button class="ib" data-act="drawer" aria-label="Abrir menu">${svg.menu}</button>
      <div class="title"><b>Nider Galeria</b></div>
    </header>
    <main class="main">
      <div class="empty">
        <img src="${ICON_G}" alt="" width="132" height="132">
        <b>Suas fotos vão aparecer aqui</b>
        <p>Pastas do celular, importar e organizar chegam nas próximas versões.</p>
      </div>
    </main>
    <nav class="nav" aria-label="Seções">
      <button data-act="soon">${svg.photo}Fotos</button>
      <button data-act="soon">${svg.folder()}Pastas</button>
      <button data-act="soon">${svg.star}Favoritos</button>
      <button data-act="soon">${svg.trash}Lixeira</button>
    </nav>`;
}

function viewForte() {
  const sp = st.space;
  const folder = st.folder && sp.index.folders.find((f) => f.id === st.folder);
  const total = sp.index.items.length;
  let body;
  if (folder) {
    const items = sp.index.items.filter((i) => i.folder === folder.id);
    body = items.length
      ? `<div class="grid">${items.map((it) => `
          <button class="tile" data-act="item" data-id="${it.id}" data-hold="item" ${it.kind === 'foto' ? `data-thumb="${it.id}"` : ''} aria-label="${esc(it.name)}">
            ${it.kind === 'pdf' ? `${svg.pdf}<span class="pdf">PDF</span>` : ''}
            <span class="name">${esc(it.name)}</span>
          </button>`).join('')}</div>`
      : '<div class="empty"><b>Pasta vazia</b><p>Toque em Adicionar para guardar fotos e PDFs aqui. Eles ficam criptografados.</p></div>';
  } else {
    body = `<div class="folders">${sp.index.folders.map((f) => {
      const items = sp.index.items.filter((i) => i.folder === f.id);
      const photos = items.filter((i) => i.kind === 'foto').slice(0, 4);
      const cells = [0, 1, 2, 3].map((n) => `<i ${photos[n] ? `data-thumb="${photos[n].id}"` : ''}></i>`).join('');
      return `<button class="folder" data-act="folder" data-id="${f.id}" data-hold="folder">
          <div class="cover">${cells}<span class="badge">${svg.folder('#C9D2DC', 22)}</span></div>
          <b>${esc(f.name)}</b><small>${plural(items.length)}</small>
        </button>`;
    }).join('')}
      <button class="folder" data-act="newFolder"><div class="newcard">${svg.folderPlus}</div><b style="color:#C9D2DC">Nova pasta</b></button>
    </div>`;
  }
  return `
    <header class="top">
      ${folder ? `<button class="ib" data-act="up" aria-label="Voltar às pastas">${svg.back}</button>` : ''}
      <div class="title"><b>${folder ? esc(folder.name) : 'Forte'}</b><small>${folder ? plural(sp.index.items.filter((i) => i.folder === folder.id).length) : `${plural(total)} · tranca ao sair`}</small></div>
      ${folder ? '' : `<button class="ib" data-act="settings" aria-label="Configurações do Forte">${svg.gear}</button>`}
      <button class="pill" data-act="lock">${svg.lock}Trancar</button>
    </header>
    <main class="main">${body}</main>
    ${folder ? `<button class="fab" data-act="add">${svg.plus}Adicionar</button>` : ''}`;
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
        <button class="sw" data-act="toggleWipe"><span><b>Apagar após 10 erros</b><small>Apaga o Forte se errarem a senha 10 vezes</small></span><span class="track ${wipeOn ? 'on' : ''}"></span></button>
      </div>
    </div></main>`;
}

function viewDrawer() {
  return `<div class="scrim" data-act="closeDrawer"></div>
    <aside class="drawer">
      <div class="dhead"><img src="${ICON_G}" alt="" width="64" height="64"><div><b>Nider Galeria</b><small>${VERSION}</small></div></div>
      <div style="padding:8px 0">
        <button class="row off" data-act="soon">${svg.photo}Fotos<span class="soon">EM BREVE</span></button>
        <button class="row off" data-act="soon">${svg.folder()}Pastas<span class="soon">EM BREVE</span></button>
        <button class="row off" data-act="soon">${svg.import}Importar pasta<span class="soon">EM BREVE</span></button>
      </div>
      <div class="sep"></div>
      <div style="padding:8px 0">
        <button class="row" data-act="openForte" style="min-height:64px"><img src="${ICON_F}" alt="" width="32" height="32" style="border-radius:9px"><span style="flex:1;font-weight:700">Nider Forte</span>${svg.chev}</button>
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
    ${s.options.map((o, i) => `<button class="row" data-act="sheetPick" data-i="${i}" style="${o.danger ? 'color:#FFB4A8' : ''}">${o.label}</button>`).join('')}
  </div></div>`;
}

function render() {
  const active = document.activeElement?.id;
  let html = st.screen === 'forte' && st.space ? viewForte() : st.screen === 'settings' && st.space ? viewSettings() : viewGallery();
  if (st.drawer) html += viewDrawer();
  if (st.pin) html += viewPin();
  if (st.sheet) html += viewSheet();
  if (st.dialog) html += viewDialog();
  if (st.toast) html += `<div class="toast" role="status">${esc(st.toast)}</div>`;
  $app.innerHTML = html;
  if (active === 'dlgInput' || (st.dialog && st.dialog.input !== undefined)) document.getElementById('dlgInput')?.focus();
  loadThumbs();
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
  render();
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
        if (!(await vault.exists())) { st.pin = null; say('O Forte foi apagado depois de 10 senhas erradas'); return; }
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
    if (p.mode === 'create') { const space = await vault.createPrimary(v); await enter(space); say('Senha principal criada. Este é o seu Forte'); return; }
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

async function addFiles(files) {
  const sp = st.space;
  const folder = st.folder;
  if (!sp || !folder) return;
  let n = 0;
  for (const file of files) {
    n++;
    st.toast = `Guardando ${n} de ${files.length}…`;
    render();
    const isPdf = file.type === 'application/pdf' || /\.pdf$/i.test(file.name);
    const isImg = file.type.startsWith('image/');
    if (!isPdf && !isImg) continue;
    const bytes = new Uint8Array(await file.arrayBuffer());
    let meta = { name: file.name, mime: file.type || (isPdf ? 'application/pdf' : 'image/jpeg'), kind: isPdf ? 'pdf' : 'foto', size: file.size };
    let thumb = null;
    if (isImg) {
      try { const t = await makeThumb(file); thumb = t.thumb; meta = { ...meta, w: t.w, h: t.h }; } catch { /* formato sem decodificador: guarda sem miniatura */ }
    }
    await sp.addItem(folder, meta, bytes, thumb);
  }
  say(`${files.length} ${files.length === 1 ? 'item guardado' : 'itens guardados'} no Forte`);
}

// ---------- ações ----------

function ask(dialog) {
  return new Promise((resolve) => { st.dialog = { ...dialog, resolve }; render(); });
}

const actions = {
  drawer: () => { st.drawer = true; render(); },
  closeDrawer: () => { st.drawer = false; render(); },
  soon: () => say('Chega numa próxima versão'),
  about: () => { st.drawer = false; ask({ icon: ICON_G, title: 'Nider Galeria', text: `Versão ${VERSION}`, ok: 'Fechar', cancel: false }); },
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
  folder: (el) => { st.folder = el.dataset.id; render(); },
  up: () => { st.folder = null; render(); },
  settings: () => { st.screen = 'settings'; render(); },
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
  item: (id) => {
    const it = st.space.index.items.find((i) => i.id === id);
    st.sheet = { title: it.name, options: [
      { label: 'Apagar do Forte', danger: true, run: async () => {
        if (!(await ask({ title: 'Apagar este item?', text: 'Ele some do Forte para sempre.', ok: 'Apagar', danger: true }))) return;
        await st.space.deleteItem(id);
        say('Item apagado');
      } },
    ] };
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

let holdTimer = 0;
let held = false;
$app.addEventListener('pointerdown', (ev) => {
  const el = ev.target.closest('[data-hold]');
  held = false;
  if (!el) return;
  holdTimer = setTimeout(() => { held = true; navigator.vibrate?.(20); holds[el.dataset.hold](el.dataset.id); }, 480);
});
for (const t of ['pointerup', 'pointercancel', 'pointerleave', 'scroll']) $app.addEventListener(t, () => clearTimeout(holdTimer), true);
$app.addEventListener('contextmenu', (ev) => ev.preventDefault());
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
  if (isActive) { setTimeout(() => { st.picking = false; }, 1500); return; }
  if (st.space && !st.picking && st.space.index.settings.lockOnLeave !== false) lock(true);
});

App.addListener('backButton', () => {
  if (pswp) return pswp.close();
  if (pdfClose) return pdfClose();
  if (st.dialog) return actions.dlgCancel();
  if (st.sheet) { st.sheet = null; return render(); }
  if (st.pin) return actions.cancelPin();
  if (st.drawer) return actions.closeDrawer();
  if (st.screen === 'settings') return actions.closeSettings();
  if (st.folder) return actions.up();
  if (st.space) return lock(false);
  App.minimizeApp();
});

render();
