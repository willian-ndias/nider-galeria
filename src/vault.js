// Cofre do Nider Forte.
//
// Cada espaço (o principal e o de disfarce) tem uma chave de dados aleatória
// (DEK). A senha gera, por PBKDF2, a chave que embrulha a DEK e um valor de
// conferência. Trocar senha só reembrulha a DEK: os arquivos não são
// recriptografados. O índice do principal guarda a DEK do disfarce, por isso o
// dono troca ou apaga a senha de disfarce sem precisar saber qual é.
//
// No disco nada diz qual espaço é qual: os dois ficam em pastas com id
// aleatório e a ordem no config.json é embaralhada.
import { Filesystem, Directory, Encoding } from '@capacitor/filesystem';

const ROOT = 'forte';
const ITERATIONS = 310000;
const enc = new TextEncoder();
const dec = new TextDecoder();

const b64 = (bytes) => {
  let s = '';
  const u = new Uint8Array(bytes);
  for (let i = 0; i < u.length; i += 0x8000) s += String.fromCharCode(...u.subarray(i, i + 0x8000));
  return btoa(s);
};
const unb64 = (text) => Uint8Array.from(atob(text), (c) => c.charCodeAt(0));
const rand = (n) => crypto.getRandomValues(new Uint8Array(n));
const uid = () => [...rand(12)].map((b) => b.toString(16).padStart(2, '0')).join('');

async function derive(pin, salt) {
  const base = await crypto.subtle.importKey('raw', enc.encode(pin), 'PBKDF2', false, ['deriveBits']);
  const bits = new Uint8Array(await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt, iterations: ITERATIONS }, base, 512));
  const kek = await crypto.subtle.importKey('raw', bits.slice(0, 32), 'AES-GCM', false, ['encrypt', 'decrypt']);
  const check = new Uint8Array(await crypto.subtle.digest('SHA-256', bits.slice(32))).slice(0, 16);
  return { kek, check };
}

const sameBytes = (a, b) => a.length === b.length && a.every((x, i) => x === b[i]);

async function seal(key, plain) {
  const iv = rand(12);
  const body = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, plain));
  const out = new Uint8Array(12 + body.length);
  out.set(iv);
  out.set(body, 12);
  return out;
}

async function open(key, blob) {
  return new Uint8Array(await crypto.subtle.decrypt({ name: 'AES-GCM', iv: blob.slice(0, 12) }, key, blob.slice(12)));
}

const dekKey = (raw) => crypto.subtle.importKey('raw', raw, 'AES-GCM', false, ['encrypt', 'decrypt']);

async function readText(path) {
  try {
    const r = await Filesystem.readFile({ path, directory: Directory.Data, encoding: Encoding.UTF8 });
    return r.data;
  } catch {
    return null;
  }
}
const writeText = (path, data) => Filesystem.writeFile({ path, data, directory: Directory.Data, encoding: Encoding.UTF8, recursive: true });
async function readBytes(path) {
  const r = await Filesystem.readFile({ path, directory: Directory.Data });
  return unb64(typeof r.data === 'string' ? r.data : await blobToB64(r.data));
}
const writeBytes = (path, bytes) => Filesystem.writeFile({ path, data: b64(bytes), directory: Directory.Data, recursive: true });
async function blobToB64(blob) {
  return b64(new Uint8Array(await blob.arrayBuffer()));
}
async function remove(path) {
  try { await Filesystem.deleteFile({ path, directory: Directory.Data }); } catch { /* já não existe */ }
}
async function removeDir(path) {
  try { await Filesystem.rmdir({ path, directory: Directory.Data, recursive: true }); } catch { /* já não existe */ }
}

async function readConfig() {
  const text = await readText(`${ROOT}/config.json`);
  return text ? JSON.parse(text) : { v: 1, spaces: [], fails: 0, wipe: false };
}
const writeConfig = (cfg) => writeText(`${ROOT}/config.json`, JSON.stringify(cfg));

async function wrapSpace(pin, raw) {
  const salt = rand(16);
  const { kek, check } = await derive(pin, salt);
  return { salt: b64(salt), check: b64(check), wrapped: b64(await seal(kek, raw)) };
}

export class OpenSpace {
  constructor(id, key, index, raw) {
    this.id = id;
    this.key = key;
    this.index = index;
    this.raw = raw;
  }
  get primary() { return this.index.role === 'primary'; }

  async save() {
    await writeBytes(`${ROOT}/${this.id}/index.bin`, await seal(this.key, enc.encode(JSON.stringify(this.index))));
  }
  async addFolder(name) {
    const folder = { id: uid(), name };
    this.index.folders.push(folder);
    await this.save();
    return folder;
  }
  async renameFolder(id, name) {
    const f = this.index.folders.find((x) => x.id === id);
    if (f) { f.name = name; await this.save(); }
  }
  // Apagar a pasta manda o que estava nela para a lixeira.
  async deleteFolder(id) {
    const now = Date.now();
    for (const it of this.index.items) if (it.folder === id && !it.trashed) it.trashed = now;
    this.index.folders = this.index.folders.filter((x) => x.id !== id);
    await this.save();
  }
  async addItem(folder, meta, bytes, thumb) {
    const item = { id: uid(), folder, added: Date.now(), ...meta };
    await writeBytes(`${ROOT}/${this.id}/${item.id}.bin`, await seal(this.key, bytes));
    if (thumb) await writeBytes(`${ROOT}/${this.id}/${item.id}.t.bin`, await seal(this.key, thumb));
    this.index.items.push(item);
    await this.save();
    return item;
  }
  async read(id) { return open(this.key, await readBytes(`${ROOT}/${this.id}/${id}.bin`)); }
  async thumb(id) { return open(this.key, await readBytes(`${ROOT}/${this.id}/${id}.t.bin`)); }
  async dropFiles(id) {
    await remove(`${ROOT}/${this.id}/${id}.bin`);
    await remove(`${ROOT}/${this.id}/${id}.t.bin`);
  }
  async renameItem(id, name) {
    const it = this.index.items.find((x) => x.id === id);
    if (it) { it.name = name; await this.save(); }
  }
  // Lixeira: o item fica guardado (criptografado) e some de vez depois de 30 dias.
  async trash(ids) {
    const now = Date.now();
    for (const it of this.index.items) if (ids.includes(it.id)) it.trashed = now;
    await this.save();
  }
  async untrash(ids) {
    for (const it of this.index.items) {
      if (!ids.includes(it.id)) continue;
      delete it.trashed;
      if (!this.index.folders.some((f) => f.id === it.folder)) {
        let back = this.index.folders.find((f) => f.name === 'Recuperados');
        if (!back) { back = { id: uid(), name: 'Recuperados' }; this.index.folders.push(back); }
        it.folder = back.id;
      }
    }
    await this.save();
  }
  async deleteItems(ids) {
    for (const id of ids) await this.dropFiles(id);
    this.index.items = this.index.items.filter((x) => !ids.includes(x.id));
    await this.save();
  }
  async purgeTrash(days = 30) {
    const limit = Date.now() - days * 86400000;
    const old = this.index.items.filter((x) => x.trashed && x.trashed < limit).map((x) => x.id);
    if (old.length) await this.deleteItems(old);
  }
  async deleteItem(id) {
    await this.dropFiles(id);
    this.index.items = this.index.items.filter((x) => x.id !== id);
    await this.save();
  }
}

export const vault = {
  async exists() {
    return (await readConfig()).spaces.length > 0;
  },

  // Primeira senha: cria o Forte principal.
  async createPrimary(pin) {
    const raw = rand(32);
    const id = uid();
    const index = { role: 'primary', folders: [{ id: uid(), name: 'Documentos' }, { id: uid(), name: 'Pessoal' }], items: [], settings: { lockOnLeave: true }, decoy: null };
    const space = new OpenSpace(id, await dekKey(raw), index, raw);
    await space.save();
    await writeConfig({ v: 1, spaces: [{ id, ...(await wrapSpace(pin, raw)) }], fails: 0, wipe: false });
    return space;
  },

  // Devolve o espaço que a senha abre, ou null. Errar conta para o "apagar após 10".
  async unlock(pin) {
    const cfg = await readConfig();
    for (const s of cfg.spaces) {
      const { kek, check } = await derive(pin, unb64(s.salt));
      if (!sameBytes(check, unb64(s.check))) continue;
      const raw = await open(kek, unb64(s.wrapped));
      const key = await dekKey(raw);
      const index = JSON.parse(dec.decode(await open(key, await readBytes(`${ROOT}/${s.id}/index.bin`))));
      if (cfg.fails) { cfg.fails = 0; await writeConfig(cfg); }
      return new OpenSpace(s.id, key, index, raw);
    }
    cfg.fails = (cfg.fails || 0) + 1;
    if (cfg.wipe && cfg.fails >= 10) await this.wipeAll();
    else await writeConfig(cfg);
    return null;
  },

  // O disco não diz qual espaço é o principal, então o "apagar após 10 erros"
  // apaga o Forte inteiro: o app volta a pedir a criação da senha.
  async wipeAll() {
    await removeDir(ROOT);
  },

  // A senha abriria outro espaço? Serve para não deixar as duas senhas iguais.
  async opensOther(pin, selfId) {
    const cfg = await readConfig();
    for (const s of cfg.spaces) {
      if (s.id === selfId) continue;
      const { check } = await derive(pin, unb64(s.salt));
      if (sameBytes(check, unb64(s.check))) return true;
    }
    return false;
  },

  async opensId(pin, id) {
    const s = (await readConfig()).spaces.find((x) => x.id === id);
    if (!s) return false;
    const { check } = await derive(pin, unb64(s.salt));
    return sameBytes(check, unb64(s.check));
  },

  // Troca a senha do espaço aberto (principal ou disfarce, cada um a sua).
  async changePin(space, pin) {
    const cfg = await readConfig();
    const s = cfg.spaces.find((x) => x.id === space.id);
    Object.assign(s, await wrapSpace(pin, space.raw));
    await writeConfig(cfg);
  },

  // Só o principal: cria ou troca a senha de disfarce.
  async setDecoy(primary, pin) {
    const cfg = await readConfig();
    let raw;
    let id = primary.index.decoy?.id;
    if (id && cfg.spaces.some((s) => s.id === id)) {
      raw = unb64(primary.index.decoy.raw);
      const s = cfg.spaces.find((x) => x.id === id);
      Object.assign(s, await wrapSpace(pin, raw));
    } else {
      raw = rand(32);
      id = uid();
      const index = {
        role: 'reserve',
        folders: [{ id: uid(), name: 'Receitas' }, { id: uid(), name: 'Paisagens' }],
        items: [], settings: { lockOnLeave: true, wipe: false }, decoy: null,
      };
      await new OpenSpace(id, await dekKey(raw), index, raw).save();
      cfg.spaces.push({ id, ...(await wrapSpace(pin, raw)) });
      cfg.spaces.sort(() => Math.random() - 0.5);
      primary.index.decoy = { id, raw: b64(raw) };
      await primary.save();
    }
    await writeConfig(cfg);
  },

  async removeDecoy(primary) {
    const id = primary.index.decoy?.id;
    if (!id) return;
    const cfg = await readConfig();
    cfg.spaces = cfg.spaces.filter((s) => s.id !== id);
    await writeConfig(cfg);
    await removeDir(`${ROOT}/${id}`);
    primary.index.decoy = null;
    await primary.save();
  },

  async getWipe() { return !!(await readConfig()).wipe; },
  async setWipe(on) {
    const cfg = await readConfig();
    cfg.wipe = on;
    await writeConfig(cfg);
  },
};
export { b64 as toBase64, unb64 as fromBase64 };
