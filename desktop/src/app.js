/* Bunc for Windows — renderer (UI, IPTV data, player). */
'use strict';

/* ================= Utilities ================= */
const $ = (s, root = document) => root.querySelector(s);

/** Create an element: el('div', {class:'x', onclick:fn}, child, 'text', …) */
function el(tag, attrs = {}, ...kids) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
    else if (k === 'class') e.className = v;
    else if (k === 'html') e.innerHTML = v;
    else e.setAttribute(k, v === true ? '' : v);
  }
  for (const k of kids.flat()) if (k !== null && k !== undefined && k !== false) e.append(k instanceof Node ? k : String(k));
  return e;
}

const store = {
  get(k, d) { try { const v = localStorage.getItem('bunc.' + k); return v === null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem('bunc.' + k, JSON.stringify(v)); } catch {} }
};

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const debounce = (fn, ms) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; };
const uid = () => Math.random().toString(36).slice(2) + Date.now().toString(36);
const hm = (ms) => new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
const norm = (s) => (s || '').normalize('NFD').replace(/\p{Mn}+/gu, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
const collator = new Intl.Collator(undefined, { sensitivity: 'base', numeric: true });

function dayLabel(ms) {
  const d = new Date(ms), t = new Date();
  const same = (a, b) => a.toDateString() === b.toDateString();
  if (same(d, t)) return 'Today';
  t.setDate(t.getDate() + 1);
  if (same(d, t)) return 'Tomorrow';
  return d.toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'numeric' });
}

/** Run async jobs with at most `n` at a time. */
function limiter(n) {
  let active = 0; const q = [];
  const next = () => {
    if (active >= n || !q.length) return;
    active++;
    const { fn, res, rej } = q.shift();
    fn().then(res, rej).finally(() => { active--; next(); });
  };
  return (fn) => new Promise((res, rej) => { q.push({ fn, res, rej }); next(); });
}

/* ================= State ================= */
const SECTIONS = [['LIVE', 'Live TV'], ['MOVIES', 'Movies'], ['SERIES', 'Series']];
let DEFAULT_UA = '';
const uaCandidates = () => [
  ['Windows', DEFAULT_UA],
  ['VLC', 'VLC/3.0.20 LibVLC/3.0.20'],
  ['Android', 'Dalvik/2.1.0 (Linux; U; Android 13; SM-S911B Build/TP1A.220624.014)'],
  ['okhttp', 'okhttp/4.12.0']
];

const S = {
  account: null,            // { id, type, name, host, user, pass, m3u, ua }
  m3u: [],                  // parsed playlist channels
  section: 'LIVE',
  category: '',
  cats: new Map(),          // section -> [{id,name}]
  content: new Map(),       // "SECTION:cat" -> [items]
  episodes: new Map(),
  query: ''
};

/* ================= Network ================= */
async function httpGet(url, ua) {
  const r = await window.bunc.get(url, ua || (S.account && S.account.ua) || DEFAULT_UA);
  if (r.status === 0) {
    const err = new Error(r.error && /ENOTFOUND|NAME_NOT_RESOLVED/i.test(r.error)
      ? "Can't find the server. Check the address and your internet connection."
      : 'Connection problem. Check your internet connection.');
    err.network = true; throw err;
  }
  return r;
}

async function getJson(url, ua) {
  const r = await httpGet(url, ua);
  if (!r.ok) { const e = new Error('HTTP ' + r.status); e.status = r.status; throw e; }
  try { return JSON.parse(r.text); } catch { const e = new Error('not Xtream data'); e.notJson = true; throw e; }
}

function normalizeHost(raw) {
  let h = (raw || '').trim();
  if (!/^https?:\/\//i.test(h)) h = 'http://' + h;
  h = h.split('/player_api.php')[0].split('/get.php')[0].split('?')[0];
  return h.replace(/\/+$/, '');
}
const enc = encodeURIComponent;

function api(action, extra = {}) {
  const a = S.account;
  const q = new URLSearchParams({ username: a.user, password: a.pass, ...(action ? { action } : {}), ...extra });
  return getJson(`${a.host}/player_api.php?${q}`);
}

/* ================= M3U ================= */
function parseM3u(text) {
  const out = []; let name = null, logo = null, group = 'Other', idx = 0;
  const attrRe = /([a-zA-Z0-9-]+)="([^"]*)"/g;
  for (let line of text.split(/\r?\n/)) {
    line = line.trim();
    if (!line) continue;
    if (/^#EXTINF/i.test(line)) {
      const attrs = {}; let m;
      attrRe.lastIndex = 0;
      while ((m = attrRe.exec(line))) attrs[m[1]] = m[2];
      logo = attrs['tvg-logo'] || null;
      group = (attrs['group-title'] || '').trim() || 'Other';
      let q = false, i = 0, title = '';
      for (; i < line.length; i++) { if (line[i] === '"') q = !q; else if (line[i] === ',' && !q) { title = line.slice(i + 1).trim(); break; } }
      name = title || attrs['tvg-name'] || 'Channel';
    } else if (/^#EXTGRP:/i.test(line)) {
      if (group === 'Other') group = line.slice(8).trim() || group;
    } else if (!line.startsWith('#')) {
      out.push({ id: idx++, name: name || line.split('/').pop(), logo, group, url: line, section: 'LIVE' });
      name = null; logo = null; group = 'Other';
    }
  }
  return out;
}

/* ================= Accounts / login ================= */
const profiles = {
  all: () => store.get('profiles', []),
  save(p) {
    const list = profiles.all();
    if (!p.id) p.id = uid();
    const i = list.findIndex((x) => x.id === p.id);
    if (i >= 0) list[i] = p; else list.push(p);
    store.set('profiles', list); store.set('last', p.id);
    return p;
  },
  remove(id) { store.set('profiles', profiles.all().filter((p) => p.id !== id)); }
};

function clearCaches() {
  S.cats.clear(); S.content.clear(); S.episodes.clear(); epgNow.clear(); epgFull.clear();
}

async function useAccount(acc) {
  S.account = acc; S.m3u = []; clearCaches();
  await window.bunc.setUA(acc.ua || DEFAULT_UA);
  if (acc.type === 'm3u') {
    const r = await httpGet(acc.m3u);
    if (!r.ok) throw new Error('Playlist could not be loaded (HTTP ' + r.status + ').');
    S.m3u = parseM3u(r.text);
    if (!S.m3u.length) throw new Error('No channels found in this playlist.');
  }
}

/** Xtream login: tries several client identities (some panels answer only certain apps). */
async function loginXtream(hostRaw, user, pass, name) {
  const host = normalizeHost(hostRaw);
  const notes = [];
  for (const [label, ua] of uaCandidates()) {
    try {
      const q = new URLSearchParams({ username: user, password: pass });
      const j = await getJson(`${host}/player_api.php?${q}`, ua);
      const ui = j && j.user_info;
      if (ui && (Number(ui.auth) === 1 || String(ui.status).toLowerCase() === 'active')) {
        const acc = { type: 'xtream', name: name || user, host, user, pass, ua };
        await useAccount(acc);
        return profiles.save(acc);
      }
      throw Object.assign(new Error('The server rejected this username or password.'), { final: true });
    } catch (e) {
      if (e.final || e.network) throw e;
      notes.push(`${label}: ${e.message}`);
    }
  }
  // No Xtream API: try the M3U playlist with the same login.
  const m3u = `${host}/get.php?username=${enc(user)}&password=${enc(pass)}&type=m3u_plus&output=ts`;
  for (const [label, ua] of uaCandidates()) {
    try {
      const r = await httpGet(m3u, ua);
      if (r.ok && parseM3u(r.text).length) {
        const acc = { type: 'm3u', name: name || user, m3u, ua };
        await useAccount(acc);
        return profiles.save(acc);
      }
      notes.push(`${label} playlist: HTTP ${r.status}`);
    } catch (e) {
      if (e.network) throw e;
      notes.push(`${label} playlist: ${e.message}`);
    }
  }
  throw new Error('The server refused every connection type Bunc tried. Check the server address with your provider.\n' + notes.join(' · '));
}

async function loginM3u(url, name) {
  const acc = { type: 'm3u', name: name || 'Playlist', m3u: url.trim(), ua: DEFAULT_UA };
  await useAccount(acc);
  return profiles.save(acc);
}

/* ================= Data ================= */
const isM3u = () => S.account && S.account.type === 'm3u';
const hasEpg = () => S.account && S.account.type === 'xtream';

async function categories(section) {
  if (S.cats.has(section)) return S.cats.get(section);
  let list = [];
  if (isM3u()) {
    if (section === 'LIVE') list = [...new Set(S.m3u.map((c) => c.group))].sort(collator.compare).map((g) => ({ id: g, name: g }));
  } else {
    const action = { LIVE: 'get_live_categories', MOVIES: 'get_vod_categories', SERIES: 'get_series_categories' }[section];
    const raw = await api(action);
    const seen = new Set();
    for (const c of Array.isArray(raw) ? raw : []) {
      const id = String(c.category_id ?? '').trim();
      if (!id || seen.has(id)) continue;
      seen.add(id);
      list.push({ id, name: String(c.category_name || 'Unnamed').trim() || 'Unnamed' });
    }
  }
  S.cats.set(section, list);
  return list;
}

async function content(section, cat) {
  const key = section + ':' + cat;
  if (S.content.has(key)) return S.content.get(key);
  let list = [];
  if (isM3u()) {
    if (section === 'LIVE') list = S.m3u.filter((c) => !cat || c.group === cat);
  } else {
    const action = { LIVE: 'get_live_streams', MOVIES: 'get_vod_streams', SERIES: 'get_series' }[section];
    const raw = await api(action, cat ? { category_id: cat } : {});
    const seen = new Set();
    for (const it of Array.isArray(raw) ? raw : []) {
      const id = Number(section === 'SERIES' ? it.series_id : it.stream_id);
      if (!Number.isFinite(id) || seen.has(id)) continue;
      seen.add(id);
      list.push({
        id, section,
        name: String(it.name || 'Untitled'),
        logo: section === 'SERIES' ? it.cover : it.stream_icon,
        ext: it.container_extension || null
      });
    }
  }
  S.content.set(key, list);
  return list;
}

async function episodes(seriesId) {
  if (S.episodes.has(seriesId)) return S.episodes.get(seriesId);
  const info = await api('get_series_info', { series_id: seriesId });
  const raw = info && info.episodes;
  let all = [];
  const add = (x) => { if (Array.isArray(x)) x.forEach(add); else if (x && typeof x === 'object' && x.id) all.push(x); };
  if (Array.isArray(raw)) raw.forEach(add); else if (raw && typeof raw === 'object') Object.values(raw).forEach(add);
  all = all.map((e) => ({
    id: String(e.id), season: Number(e.season) || 0, num: Number(e.episode_num) || 0,
    title: e.title || `Episode ${e.episode_num || ''}`, ext: e.container_extension || 'mp4'
  })).sort((a, b) => a.season - b.season || a.num - b.num);
  S.episodes.set(seriesId, all);
  return all;
}

function liveUrl(item) {
  if (isM3u()) return item.url;
  const a = S.account; return `${a.host}/live/${enc(a.user)}/${enc(a.pass)}/${item.id}.ts`;
}
function movieUrl(item) {
  const a = S.account; return `${a.host}/movie/${enc(a.user)}/${enc(a.pass)}/${item.id}.${item.ext || 'mp4'}`;
}
function episodeUrl(ep) {
  const a = S.account; return `${a.host}/series/${enc(a.user)}/${enc(a.pass)}/${ep.id}.${ep.ext || 'mp4'}`;
}
const liveItem = (c) => ({ title: c.name, url: liveUrl(c), streamId: isM3u() ? null : c.id, logo: c.logo });

/* ================= EPG ================= */
const epgNow = new Map();   // id -> {until, list}
const epgFull = new Map();
const epgLimit = limiter(6);

function decodeEpg(s) {
  if (!s) return '';
  const t = String(s).trim();
  if (!/^[A-Za-z0-9+/=\s]+$/.test(t)) return t;
  try {
    const bin = atob(t.replace(/\s/g, ''));
    const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0));
    const txt = new TextDecoder('utf-8', { fatal: true }).decode(bytes).trim();
    return /[\u0000-\u0008\u000E-\u001F]/.test(txt) ? t : txt;
  } catch { return t; }
}

function parsePrograms(j) {
  const l = j && j.epg_listings;
  const arr = Array.isArray(l) ? l : l && typeof l === 'object' ? Object.values(l) : [];
  const seen = new Set();
  return arr.map((p) => {
    const start = p.start_timestamp ? Number(p.start_timestamp) * 1000 : Date.parse(String(p.start || '').replace(' ', 'T'));
    const end = p.stop_timestamp ? Number(p.stop_timestamp) * 1000 : Date.parse(String(p.end || '').replace(' ', 'T'));
    return { title: decodeEpg(p.title) || 'No information', desc: decodeEpg(p.description), start, end };
  }).filter((p) => Number.isFinite(p.start) && Number.isFinite(p.end) && p.end > p.start && !seen.has(p.start) && seen.add(p.start))
    .sort((a, b) => a.start - b.start);
}

async function nowNext(id) {
  const now = Date.now(), c = epgNow.get(id);
  if (c && c.until > now) return c.list;
  let list = [];
  try { list = parsePrograms(await epgLimit(() => api('get_short_epg', { stream_id: id, limit: 4 }))).filter((p) => p.end > now).slice(0, 3); } catch {}
  const until = list.length ? Math.min(Math.max(list[0].end, now + 60e3), now + 30 * 60e3) : now + 10 * 60e3;
  epgNow.set(id, { until, list });
  return list;
}
async function nowPlaying(id) {
  const now = Date.now();
  return (await nowNext(id)).find((p) => now >= p.start && now < p.end) || null;
}
async function fullEpg(id) {
  const now = Date.now(), c = epgFull.get(id);
  if (c && c.until > now) return c.list.filter((p) => p.end > now);
  let list = [];
  try { list = parsePrograms(await epgLimit(() => api('get_simple_data_table', { stream_id: id }))); } catch {}
  if (!list.some((p) => p.end > now)) {
    try { list = parsePrograms(await epgLimit(() => api('get_short_epg', { stream_id: id, limit: 100 }))); } catch {}
  }
  epgFull.set(id, { until: now + 30 * 60e3, list });
  return list.filter((p) => p.end > now);
}

/* ================= Folder order ================= */
const sortKey = (sec) => `sort.${S.account.id}.${sec}`;
const orderKey = (sec) => `order.${S.account.id}.${sec}`;
function sortedCats(sec, list) {
  const mode = store.get(sortKey(sec), 'server');
  if (mode === 'az') return [...list].sort((a, b) => collator.compare(a.name, b.name));
  if (mode === 'custom') {
    const pos = new Map(store.get(orderKey(sec), []).map((id, i) => [id, i]));
    return [...list].sort((a, b) => (pos.get(a.id) ?? 1e9) - (pos.get(b.id) ?? 1e9));
  }
  return list;
}

/* ================= Views ================= */
const app = $('#app');
let viewToken = 0; // stale async results must not draw over a newer view
function show(...nodes) { viewToken++; app.replaceChildren(...nodes); }

function logo() { return el('img', { class: 'logo', src: 'img/wordmark.png', alt: 'Bunc' }); }

/* ---------- Profiles ---------- */
function viewProfiles(msg) {
  const list = profiles.all();
  if (!list.length) return viewLogin();
  const err = el('div', { class: 'error' }, msg || '');
  const wrap = el('div', { class: 'panel' }, logo(), el('div', { class: 'subtitle' }, 'Choose profile'));
  for (const p of list) {
    const sub = p.type === 'm3u' ? 'M3U playlist' : 'Xtream · ' + (p.host || '').replace(/^https?:\/\//, '');
    wrap.append(el('div', { class: 'profile' },
      el('button', { class: 'main', onclick: () => openProfile(p, err) }, el('b', {}, p.name || p.user || 'Profile'), el('span', { class: 'muted' }, sub)),
      el('button', { class: 'del', title: 'Remove profile', onclick: () => {
        if (confirm(`Remove profile “${p.name || p.user}”?`)) { profiles.remove(p.id); viewProfiles(); }
      } }, '🗑')));
  }
  wrap.append(el('button', { class: 'add', onclick: () => viewLogin(true) }, '＋  Add new profile'), err);
  show(el('div', { class: 'center' }, wrap));
}

async function openProfile(p, errEl) {
  errEl.textContent = 'Connecting…';
  try {
    await useAccount(p);
    store.set('last', p.id);
    viewHome();
  } catch (e) {
    errEl.textContent = e.message || "Couldn't connect.";
  }
}

/* ---------- Login ---------- */
function viewLogin(canGoBack) {
  let mode = 'xtream';
  const name = el('input', { class: 'input', placeholder: 'e.g. Living room' });
  const host = el('input', { class: 'input', placeholder: 'http://host:port', spellcheck: 'false' });
  const user = el('input', { class: 'input', spellcheck: 'false', autocomplete: 'off' });
  const pass = el('input', { class: 'input', type: 'password', autocomplete: 'off' });
  const m3u = el('input', { class: 'input', placeholder: 'http://…/get.php?username=…&password=…&type=m3u_plus', spellcheck: 'false' });
  const xBox = el('div', {}, el('label', { class: 'lbl' }, 'Server URL'), host, el('label', { class: 'lbl' }, 'Username'), user, el('label', { class: 'lbl' }, 'Password'), pass);
  const mBox = el('div', { class: 'hidden' }, el('label', { class: 'lbl' }, 'M3U playlist URL'), m3u);
  const err = el('div', { class: 'error' });
  const bx = el('button', { class: 'on', onclick: () => setMode('xtream') }, 'Xtream');
  const bm = el('button', { onclick: () => setMode('m3u') }, 'M3U');
  function setMode(m) {
    mode = m; bx.classList.toggle('on', m === 'xtream'); bm.classList.toggle('on', m === 'm3u');
    xBox.classList.toggle('hidden', m !== 'xtream'); mBox.classList.toggle('hidden', m !== 'm3u');
  }
  const go = el('button', { class: 'btn gold', style: 'width:100%;margin-top:22px;padding:14px;font-size:17px', onclick: submit }, 'Connect');
  async function submit() {
    err.textContent = ''; go.disabled = true; go.textContent = 'Connecting…';
    try {
      if (mode === 'xtream') {
        if (!host.value.trim() || !user.value.trim()) throw new Error('Enter the server URL and username.');
        await loginXtream(host.value, user.value.trim(), pass.value, name.value.trim());
      } else {
        if (!m3u.value.trim()) throw new Error('Enter the playlist URL.');
        await loginM3u(m3u.value, name.value.trim());
      }
      viewHome();
    } catch (e) {
      err.textContent = e.message || 'Connection failed.';
      go.disabled = false; go.textContent = 'Connect';
    }
  }
  for (const i of [name, host, user, pass, m3u]) i.addEventListener('keydown', (e) => { if (e.key === 'Enter') submit(); });
  const panel = el('div', { class: 'panel' }, logo(), el('div', { class: 'subtitle' }, 'Add a profile'),
    el('div', { class: 'seg' }, bx, bm),
    el('label', { class: 'lbl' }, 'Profile name (optional)'), name, xBox, mBox, err, go,
    canGoBack && profiles.all().length ? el('button', { class: 'btn', style: 'width:100%;margin-top:10px', onclick: () => viewProfiles() }, 'Back to profiles') : null);
  show(el('div', { class: 'center' }, panel));
  setTimeout(() => host.focus(), 50);
}

/* ---------- Home ---------- */
let homeEls = null;

function viewHome() {
  const tabs = el('div', { style: 'display:flex;gap:6px' });
  const search = el('input', { class: 'search', placeholder: 'Search channels, movies, series…', value: S.query });
  const sidebar = el('div', { class: 'sidebar' });
  const main = el('div', { class: 'content' });
  const header = el('div', { class: 'header' },
    el('img', { class: 'wm', src: 'img/wordmark.png', alt: 'Bunc' }), tabs, el('span', { class: 'grow' }), search,
    hasEpg() ? el('button', { class: 'icon-btn', title: 'TV Guide', onclick: () => openGuide() }, '📅 Guide') : null,
    el('button', { class: 'icon-btn', title: 'Settings', onclick: viewSettings }, '⚙'),
    el('button', { class: 'icon-btn', title: 'Switch profile', onclick: () => { S.account = null; viewProfiles(); } }, '👤'));
  homeEls = { tabs, search, sidebar, main };
  const sections = isM3u() ? SECTIONS.slice(0, 1) : SECTIONS;
  if (!sections.some(([k]) => k === S.section)) S.section = 'LIVE';
  for (const [key, label] of sections) {
    tabs.append(el('button', { class: 'tab' + (key === S.section ? ' on' : ''), 'data-sec': key, onclick: () => selectSection(key) }, label));
  }
  search.addEventListener('input', debounce(() => { S.query = search.value; S.query.trim().length >= 2 ? renderSearch() : loadSection(); }, 250));
  show(header, el('div', { class: 'body' }, sidebar, main));
  if (S.query.trim().length >= 2) renderSearch(); else loadSection();
}

function selectSection(key) {
  S.section = key; S.category = ''; S.query = '';
  homeEls.search.value = '';
  for (const b of homeEls.tabs.children) b.classList.toggle('on', b.dataset.sec === key);
  loadSection();
}

async function loadSection() {
  const { sidebar, main } = homeEls; const tok = viewToken; const sec = S.section;
  sidebar.replaceChildren(el('div', { class: 'spinner' }));
  main.replaceChildren();
  let cats;
  try { cats = sortedCats(sec, await categories(sec)); } catch (e) {
    if (tok !== viewToken) return;
    sidebar.replaceChildren(); main.replaceChildren(el('div', { class: 'empty' }, friendly(e))); return;
  }
  if (tok !== viewToken || sec !== S.section) return;
  const all = [{ id: '', name: 'All' }, ...cats];
  if (!all.some((c) => c.id === S.category)) S.category = cats[0] ? cats[0].id : '';
  sidebar.replaceChildren(...all.map((c) => el('button', {
    class: 'cat' + (c.id === S.category ? ' on' : ''), title: c.name, 'data-id': c.id,
    onclick: (ev) => { S.category = c.id; for (const b of sidebar.children) b.classList.toggle('on', b === ev.currentTarget); loadContent(); }
  }, c.name)));
  loadContent();
}

async function loadContent() {
  const { main } = homeEls; const tok = viewToken; const sec = S.section, cat = S.category;
  main.replaceChildren(el('div', { class: 'spinner' }));
  let items;
  try { items = await content(sec, cat); } catch (e) {
    if (tok === viewToken && sec === S.section && cat === S.category) main.replaceChildren(el('div', { class: 'empty' }, friendly(e)));
    return;
  }
  if (tok !== viewToken || sec !== S.section || cat !== S.category) return;
  if (!items.length) { main.replaceChildren(el('div', { class: 'empty' }, 'Nothing here yet')); return; }
  main.replaceChildren(grid(items, sec, (c) => openItem(c, items)));
  main.scrollTop = 0;
}

function friendly(e) {
  if (e && e.network) return e.message;
  if (e && e.status) return `The server answered with an error (HTTP ${e.status}).`;
  return "Couldn't load this list from the server.";
}

function openItem(c, list) {
  if (c.section === 'LIVE') {
    const live = list.filter((x) => x.section === 'LIVE');
    openPlayer(live.map(liveItem), Math.max(0, live.indexOf(c)), true);
  } else if (c.section === 'MOVIES') {
    openPlayer([{ title: c.name, url: movieUrl(c) }], 0, false);
  } else {
    viewEpisodes(c);
  }
}

/** A grid that renders in chunks as you scroll; live tiles load "now playing" when visible. */
function grid(items, sec, onClick) {
  const g = el('div', { class: 'grid ' + (sec === 'LIVE' ? 'live' : 'vod') });
  const showNow = sec === 'LIVE' && hasEpg();
  const nowObs = showNow ? new IntersectionObserver((entries) => {
    for (const en of entries) {
      if (!en.isIntersecting) continue;
      nowObs.unobserve(en.target);
      const id = Number(en.target.dataset.sid);
      nowPlaying(id).then((p) => { if (p) en.target.querySelector('.now').textContent = p.title; });
    }
  }, { rootMargin: '200px' }) : null;
  let shown = 0;
  const sentinel = el('div', { style: 'height:1px;grid-column:1/-1' });
  function more() {
    const frag = document.createDocumentFragment();
    for (const c of items.slice(shown, shown + 150)) {
      const art = el('div', { class: 'art' });
      if (c.logo) {
        const img = el('img', { src: c.logo, loading: 'lazy', alt: '', referrerpolicy: 'no-referrer' });
        img.onerror = () => art.replaceChildren(el('span', { class: 'ph' }, (c.name || '?').slice(0, 2).toUpperCase()));
        art.append(img);
      } else art.append(el('span', { class: 'ph' }, (c.name || '?').slice(0, 2).toUpperCase()));
      const card = el('div', { class: 'card', tabindex: 0, title: c.name, onclick: () => onClick(c), onkeydown: (e) => { if (e.key === 'Enter') onClick(c); } },
        art, el('div', { class: 't' }, c.name), showNow ? el('div', { class: 'now' }) : null);
      if (showNow) { card.dataset.sid = c.id; nowObs.observe(card); }
      frag.append(card);
    }
    shown = Math.min(items.length, shown + 150);
    g.insertBefore(frag, sentinel);
    if (shown >= items.length) moreObs.disconnect();
  }
  g.append(sentinel);
  const moreObs = new IntersectionObserver((e) => { if (e[0].isIntersecting) more(); }, { rootMargin: '600px' });
  more();
  moreObs.observe(sentinel);
  return g;
}

/* ---------- Search ---------- */
async function renderSearch() {
  const { main } = homeEls; const tok = viewToken; const q = S.query;
  main.replaceChildren(el('div', { class: 'spinner' }), el('div', { class: 'empty', style: 'margin-top:0' }, 'Searching the whole catalog…'));
  const secs = isM3u() ? ['LIVE'] : ['LIVE', 'MOVIES', 'SERIES'];
  const lists = await Promise.all(secs.map((s) => content(s, '').catch(() => [])));
  if (tok !== viewToken || q !== S.query) return;
  const words = norm(q).split(/\s+/).filter(Boolean);
  const match = (l) => l.filter((c) => { const n = norm(c.name); return words.every((w) => n.includes(w)); }).slice(0, 200);
  const frag = [];
  secs.forEach((s, i) => {
    const res = match(lists[i]);
    if (!res.length) return;
    frag.push(el('div', { class: 'section-title' }, `${SECTIONS.find((x) => x[0] === s)[1]} (${res.length})`));
    frag.push(grid(res, s, (c) => openItem(c, res)));
  });
  main.replaceChildren(...(frag.length ? frag : [el('div', { class: 'empty' }, `No results for “${q.trim()}”`)]));
}

/* ---------- Episodes ---------- */
async function viewEpisodes(series) {
  const list = el('div', {}, el('div', { class: 'spinner' }));
  show(el('div', { class: 'page' },
    el('button', { class: 'btn back', onclick: viewHome }, '‹ Back'),
    el('h2', {}, series.name), list));
  const tok = viewToken;
  let eps = [];
  try { eps = await episodes(series.id); } catch {}
  if (tok !== viewToken) return;
  if (!eps.length) { list.replaceChildren(el('div', { class: 'empty' }, 'No episodes found')); return; }
  let season = null; const out = [];
  for (const e of eps) {
    if (e.season !== season) { season = e.season; out.push(el('div', { class: 'section-title' }, season ? `Season ${season}` : 'Episodes')); }
    out.push(el('button', { class: 'ep', onclick: () => openPlayer([{ title: `${series.name} — ${e.title}`, url: episodeUrl(e) }], 0, false) },
      e.title, el('div', { class: 'muted' }, e.num ? `Episode ${e.num}` : '')));
  }
  list.replaceChildren(...out);
}

/* ---------- Settings ---------- */
async function viewSettings() {
  const sections = isM3u() ? SECTIONS.slice(0, 1) : SECTIONS;
  let sec = sections[0][0];
  const version = await window.bunc.version().catch(() => '?');
  const upd = el('span', { class: 'muted' });
  const body = el('div');
  const secChips = el('div', { class: 'chips' });
  show(el('div', { class: 'page' },
    el('button', { class: 'btn back', onclick: viewHome }, '‹ Back'),
    el('h2', {}, 'Settings'),
    el('div', { class: 'section-title' }, 'App'),
    el('div', { style: 'display:flex;gap:12px;align-items:center' }, `Version ${version}`,
      el('button', { class: 'btn small', onclick: async () => {
        upd.textContent = 'Checking…';
        const r = await window.bunc.checkUpdate();
        upd.textContent = r === 'dev' ? 'Updates work in the installed app.' : r === 'ok' ? '' : "Couldn't check for updates.";
      } }, 'Check for updates'), upd),
    el('div', { class: 'section-title' }, 'Folder order'), secChips, body));
  const tok = viewToken;
  function chips() {
    secChips.replaceChildren(...sections.map(([k, l]) => el('button', { class: 'chip' + (k === sec ? ' on' : ''), onclick: () => { sec = k; chips(); draw(); } }, l)));
  }
  async function draw() {
    body.replaceChildren(el('div', { class: 'spinner' }));
    let server = [];
    try { server = await categories(sec); } catch {}
    if (tok !== viewToken) return;
    const mode = store.get(sortKey(sec), 'server');
    const list = sortedCats(sec, server);
    const setMode = (m) => {
      if (m === 'custom') store.set(orderKey(sec), list.map((c) => c.id));
      store.set(sortKey(sec), m); draw();
    };
    const move = (from, to) => {
      const ids = list.map((c) => c.id);
      to = Math.max(0, Math.min(ids.length - 1, to));
      if (to === from) return;
      const [x] = ids.splice(from, 1); ids.splice(to, 0, x);
      store.set(orderKey(sec), ids); draw();
    };
    const custom = mode === 'custom';
    body.replaceChildren(
      el('div', { class: 'muted' }, 'Sort folders by'),
      el('div', { class: 'chips' },
        el('button', { class: 'chip' + (mode === 'server' ? ' on' : ''), onclick: () => setMode('server') }, 'Provider order'),
        el('button', { class: 'chip' + (mode === 'az' ? ' on' : ''), onclick: () => setMode('az') }, 'A–Z'),
        el('button', { class: 'chip' + (custom ? ' on' : ''), onclick: () => setMode('custom') }, 'My order (1, 2, 3…)')),
      custom ? el('div', { class: 'chips' },
        el('button', { class: 'chip', onclick: () => { store.set(orderKey(sec), [...server].sort((a, b) => collator.compare(a.name, b.name)).map((c) => c.id)); draw(); } }, 'Sort A–Z first'),
        el('button', { class: 'chip', onclick: () => { store.set(orderKey(sec), server.map((c) => c.id)); draw(); } }, 'Reset to provider order')) : null,
      el('div', { class: 'muted', style: 'margin:6px 0 10px' }, custom ? 'Type a position number or use ▲ ▼ to move a folder.' : 'This is the order folders appear in.'),
      ...list.map((c, i) => el('div', { class: 'folder' },
        custom ? el('input', { class: 'num', type: 'number', min: 1, max: list.length, value: i + 1,
          onchange: (e) => move(i, Number(e.target.value) - 1), onkeydown: (e) => { if (e.key === 'Enter') e.target.blur(); } })
          : el('span', { class: 'num' }, i + 1),
        el('span', { class: 'name' }, c.name),
        custom ? el('button', { disabled: i === 0, onclick: () => move(i, i - 1), title: 'Move up' }, '▲') : null,
        custom ? el('button', { disabled: i === list.length - 1, onclick: () => move(i, i + 1), title: 'Move down' }, '▼') : null)));
  }
  chips(); draw();
}

/* ---------- TV Guide ---------- */
let guideCtx = null;
async function openGuide(channels, start = 0, fromPlayer = false) {
  if (!channels) {
    const sec = S.section === 'LIVE' ? S.category : '';
    try { channels = (await content('LIVE', S.section === 'LIVE' ? sec : '')).map(liveItem); } catch { channels = []; }
  }
  guideCtx = { channels, sel: Math.min(start, Math.max(0, channels.length - 1)), fromPlayer };
  const chs = el('div', { class: 'chs' });
  const sched = el('div', { class: 'sched' });
  const clock = el('span', { class: 'muted', style: 'font-size:18px;font-weight:600' }, hm(Date.now()));
  const title = el('span', { class: 'gold', style: 'flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis' });
  show(el('div', { class: 'header' },
    el('button', { class: 'icon-btn', onclick: () => { guideCtx = null; viewHome(); } }, '‹ Back'),
    el('b', { style: 'font-size:20px;margin:0 12px' }, 'TV Guide'), title, clock),
    el('div', { class: 'guide' }, chs, sched));
  const tok = viewToken;
  const tick = setInterval(() => { if (tok !== viewToken) clearInterval(tick); else clock.textContent = hm(Date.now()); }, 30000);
  if (!channels.length) { sched.replaceChildren(el('div', { class: 'empty' }, 'No channels')); return; }

  const rows = channels.map((c, i) => {
    const now = el('div', { class: 'now' });
    const row = el('button', { class: 'gch', onclick: () => (i === guideCtx.sel ? openPlayer(channels, i, true) : select(i)),
      ondblclick: () => openPlayer(channels, i, true), title: 'Click: schedule · Double-click: watch' },
      c.logo ? el('img', { src: c.logo, loading: 'lazy', alt: '' }) : el('span', { style: 'width:46px' }),
      el('div', { class: 'n' }, el('div', {}, c.title), now));
    row._now = now; return row;
  });
  chs.replaceChildren(...rows);
  const nowObs = new IntersectionObserver((entries) => {
    for (const en of entries) if (en.isIntersecting) {
      nowObs.unobserve(en.target);
      const c = channels[rows.indexOf(en.target)];
      if (c && c.streamId != null) nowPlaying(c.streamId).then((p) => { if (p) en.target._now.textContent = p.title; });
    }
  });
  rows.forEach((r) => nowObs.observe(r));

  async function select(i) {
    guideCtx.sel = i;
    rows.forEach((r, j) => r.classList.toggle('on', j === i));
    rows[i].scrollIntoView({ block: 'nearest' });
    const c = channels[i];
    title.textContent = c.title;
    sched.replaceChildren(el('div', { class: 'spinner' }));
    const progs = c.streamId != null ? await fullEpg(c.streamId) : [];
    if (tok !== viewToken || guideCtx.sel !== i) return;
    if (!progs.length) { sched.replaceChildren(el('div', { class: 'empty' }, 'No guide information for this channel')); return; }
    const now = Date.now(); const out = []; let day = '';
    out.push(el('button', { class: 'btn gold', style: 'margin-bottom:6px', onclick: () => openPlayer(channels, i, true) }, '▶ Watch ' + c.title));
    for (const p of progs) {
      const d = dayLabel(p.start);
      if (d !== day) { day = d; out.push(el('div', { class: 'day' }, d)); }
      const on = now >= p.start && now < p.end;
      out.push(el('div', { class: 'prog' + (on ? ' on' : ''), onclick: () => openPlayer(channels, i, true) },
        el('div', { class: 'time' }, `${hm(p.start)} – ${hm(p.end)}`, on ? el('span', { class: 'badge' }, 'NOW') : null),
        el('div', { class: 'title' }, p.title),
        on ? el('div', { class: 'bar' }, el('div', { style: `width:${Math.round(((now - p.start) / (p.end - p.start)) * 100)}%` })) : null,
        p.desc ? el('div', { class: 'desc' }, p.desc) : null));
    }
    sched.replaceChildren(...out);
  }
  select(guideCtx.sel);
}

/* ================= Player ================= */
const P = {
  root: $('#player'), video: $('#video'), info: $('#pl-info'), status: $('#pl-status'), bar: $('#pl-bar'), spin: $('#pl-spinner'),
  items: [], index: 0, live: false, mp: null, hls: null,
  attempts: 0, retryTimer: null, lastPos: -1, lastProgress: 0, steadySince: 0, watchdog: null,
  infoTimer: null, idleTimer: null, epg: []
};

function openPlayer(items, index, live) {
  if (!items.length) return;
  Object.assign(P, { items, index, live, attempts: 0 });
  P.root.classList.remove('hidden');
  P.bar.querySelectorAll('[data-act=prev],[data-act=next],[data-act=guide],[data-act=info]').forEach((b) => {
    const a = b.dataset.act;
    b.classList.toggle('hidden', !live || ((a === 'prev' || a === 'next') && items.length < 2) || (a === 'guide' && !hasEpg()));
  });
  P.video.controls = !live;
  window.bunc.keepAwake(true);
  start();
  showInfo();
  clearInterval(P.watchdog);
  P.watchdog = setInterval(watchdog, 2000);
  P.root.focus();
}

function closePlayer() {
  stopStream();
  clearInterval(P.watchdog); clearTimeout(P.retryTimer);
  P.root.classList.add('hidden');
  P.info.classList.add('hidden'); P.status.classList.add('hidden'); P.bar.classList.add('hidden');
  window.bunc.keepAwake(false);
  window.bunc.setFullscreen(false);
}

function stopStream() {
  try { if (P.mp) { P.mp.pause(); P.mp.unload(); P.mp.detachMediaElement(); P.mp.destroy(); } } catch {}
  try { if (P.hls) P.hls.destroy(); } catch {}
  P.mp = null; P.hls = null;
  P.video.removeAttribute('src');
  try { P.video.load(); } catch {}
}

function kind(url) {
  const path = url.split('?')[0].toLowerCase();
  if (path.endsWith('.m3u8')) return 'hls';
  if (path.endsWith('.ts') || (P.live && !/\.(mp4|mkv|avi|mov|webm|m4v)$/.test(path))) return 'ts';
  return 'file';
}

function start(resumeAt) {
  const item = P.items[P.index]; if (!item) return;
  stopStream();
  P.lastPos = -1; P.lastProgress = Date.now(); P.steadySince = 0;
  P.spin.classList.remove('hidden');
  const v = P.video; const k = kind(item.url);
  if (k === 'ts' && window.mpegts && mpegts.isSupported()) {
    P.mp = mpegts.createPlayer({ type: 'mpegts', isLive: P.live, url: item.url }, {
      enableWorker: true, lazyLoad: false, liveBufferLatencyChasing: false,
      enableStashBuffer: true, stashInitialSize: 1024 * 1024, autoCleanupSourceBuffer: true
    });
    P.mp.on(mpegts.Events.ERROR, () => reconnect());
    P.mp.attachMediaElement(v); P.mp.load();
    v.play().catch(() => {});
  } else if (k === 'hls' && window.Hls && Hls.isSupported()) {
    P.hls = new Hls({ maxBufferLength: 30, liveSyncDurationCount: 3 });
    P.hls.on(Hls.Events.ERROR, (_e, d) => { if (d.fatal) reconnect(); });
    P.hls.loadSource(item.url); P.hls.attachMedia(v);
    v.play().catch(() => {});
  } else {
    v.src = item.url;
    if (resumeAt) v.currentTime = resumeAt;
    v.play().catch(() => {});
  }
}

function setStatus(t) { P.status.textContent = t || ''; P.status.classList.toggle('hidden', !t); }

function reconnect() {
  if (P.retryTimer || P.root.classList.contains('hidden')) return;
  if (!P.live && P.attempts >= 5) { setStatus('Playback failed. Press Esc and try again.'); P.spin.classList.add('hidden'); return; }
  P.attempts++;
  const wait = P.attempts <= 1 ? 500 : P.attempts === 2 ? 1500 : P.attempts <= 4 ? 3000 : 8000;
  setStatus(P.attempts > 6 ? 'Stream not responding — still trying…' : 'Reconnecting…');
  const resumeAt = P.live ? 0 : P.video.currentTime;
  P.retryTimer = setTimeout(() => { P.retryTimer = null; start(resumeAt); }, wait);
}

function watchdog() {
  const v = P.video, now = Date.now();
  if (P.retryTimer || v.paused) { P.lastProgress = now; P.steadySince = 0; return; }
  if (v.currentTime !== P.lastPos && v.readyState >= 3) {
    P.lastPos = v.currentTime; P.lastProgress = now;
    if (!P.steadySince) P.steadySince = now;
    if (now - P.steadySince > 15000) P.attempts = 0;
  } else {
    P.steadySince = 0;
    if (P.live && now - P.lastProgress > 12000) reconnect();
  }
}

const V = P.video;
V.addEventListener('playing', () => { P.spin.classList.add('hidden'); setStatus(''); });
V.addEventListener('waiting', () => P.spin.classList.remove('hidden'));
V.addEventListener('ended', () => { if (P.live) reconnect(); });
V.addEventListener('error', () => reconnect());

function zap(d) {
  if (!P.live || P.items.length < 2) return;
  P.index = (P.index + d + P.items.length) % P.items.length;
  P.attempts = 0; clearTimeout(P.retryTimer); P.retryTimer = null; setStatus('');
  start(); showInfo();
}

async function showInfo() {
  const item = P.items[P.index]; if (!item) return;
  const box = P.info;
  const render = (list) => {
    const now = Date.now();
    const cur = list.find((p) => now >= p.start && now < p.end);
    const next = list.find((p) => p.start >= (cur ? cur.end : now));
    const row = (lab, p, isNow) => el('div', {}, el('div', { class: 'row' },
      el('span', { class: 'lab' + (isNow ? ' now' : '') }, lab), el('span', { class: 'tm' }, `${hm(p.start)}–${hm(p.end)}`), el('span', { class: 'ttl' }, p.title)),
      isNow ? el('div', { class: 'bar' }, el('div', { style: `width:${Math.round(((now - p.start) / (p.end - p.start)) * 100)}%` })) : null);
    box.replaceChildren(el('div', { class: 'ch' }, item.title), cur ? row('NOW', cur, true) : null, next ? row('NEXT', next, false) : null,
      P.live && hasEpg() ? el('div', { class: 'muted', style: 'font-size:12px;margin-top:8px' }, 'G: TV guide · ↑↓: channels · F: fullscreen · Esc: back') : null);
  };
  render([]);
  box.classList.remove('hidden');
  clearTimeout(P.infoTimer);
  P.infoTimer = setTimeout(() => box.classList.add('hidden'), 6000);
  if (P.live && item.streamId != null && hasEpg()) {
    const idx = P.index;
    const list = await nowNext(item.streamId);
    if (idx === P.index && !box.classList.contains('hidden')) render(list);
  }
}

// Mouse movement shows the control bar; it hides after 3 s of no movement.
P.root.addEventListener('mousemove', () => {
  P.bar.classList.remove('hidden'); P.root.classList.remove('idle');
  clearTimeout(P.idleTimer);
  P.idleTimer = setTimeout(() => { P.bar.classList.add('hidden'); P.root.classList.add('idle'); }, 3000);
});
P.video.addEventListener('dblclick', () => window.bunc.toggleFullscreen());
P.video.addEventListener('click', () => { if (P.live) showInfo(); });
P.bar.addEventListener('click', (e) => {
  const a = e.target.closest('button') && e.target.closest('button').dataset.act;
  if (a === 'back') closePlayer();
  else if (a === 'prev') zap(-1);
  else if (a === 'next') zap(1);
  else if (a === 'info') showInfo();
  else if (a === 'guide') { const { items, index } = P; closePlayer(); openGuide(items, index, true); }
  else if (a === 'fs') window.bunc.toggleFullscreen();
  else if (a === 'mute') { P.video.muted = !P.video.muted; e.target.textContent = P.video.muted ? '🔇' : '🔊'; }
});
$('#pl-vol').addEventListener('input', (e) => { P.video.volume = Number(e.target.value); store.set('volume', P.video.volume); });
P.video.volume = store.get('volume', 1); $('#pl-vol').value = P.video.volume;

document.addEventListener('keydown', (e) => {
  if (P.root.classList.contains('hidden')) {
    if (e.key === 'Escape' && guideCtx) { guideCtx = null; viewHome(); }
    return;
  }
  const k = e.key;
  if (k === 'Escape' || k === 'Backspace') { e.preventDefault(); closePlayer(); }
  else if (P.live && (k === 'ArrowUp' || k === 'PageUp')) { e.preventDefault(); zap(-1); }
  else if (P.live && (k === 'ArrowDown' || k === 'PageDown')) { e.preventDefault(); zap(1); }
  else if (k === 'f' || k === 'F' || k === 'F11') { e.preventDefault(); window.bunc.toggleFullscreen(); }
  else if (k === 'i' || k === 'I' || (P.live && k === 'Enter')) showInfo();
  else if ((k === 'g' || k === 'G') && P.live && hasEpg()) { const { items, index } = P; closePlayer(); openGuide(items, index, true); }
  else if (k === 'm' || k === 'M') P.video.muted = !P.video.muted;
  else if (k === ' ' && !P.live) { e.preventDefault(); P.video.paused ? P.video.play() : P.video.pause(); }
});

/* ================= Updates ================= */
const banner = $('#update-banner');
window.bunc.onUpdate((s) => {
  if (s.state === 'downloading') {
    banner.replaceChildren(el('span', {}, `Downloading update${s.version ? ' ' + s.version : ''}… ${Math.round(s.percent || 0)}%`));
    banner.classList.remove('hidden');
  } else if (s.state === 'ready') {
    banner.replaceChildren(el('span', {}, `Bunc ${s.version} is ready.`),
      el('button', { class: 'btn gold small', onclick: () => window.bunc.installUpdate() }, 'Restart & update'),
      el('button', { class: 'btn small', onclick: () => banner.classList.add('hidden') }, 'Later'));
    banner.classList.remove('hidden');
  } else if (s.state === 'none' || s.state === 'error') {
    banner.classList.add('hidden');
  }
});

/* ================= Start ================= */
(async function boot() {
  DEFAULT_UA = await window.bunc.defaultUA().catch(() => navigator.userAgent);
  viewProfiles();
})();
