// Bunc for Windows — main process: window, network requests, keep-awake, updates.
const { app, BrowserWindow, ipcMain, net, session, shell, powerSaveBlocker } = require('electron');
const path = require('path');

let win = null;
let defaultUA = '';
let awakeId = null;

function createWindow() {
  win = new BrowserWindow({
    width: 1400,
    height: 860,
    minWidth: 960,
    minHeight: 600,
    backgroundColor: '#0A1130',
    title: 'Bunc',
    icon: path.join(__dirname, 'src', 'img', 'icon.png'),
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      // IPTV servers send no CORS headers; the player must be able to read their streams.
      webSecurity: false
    }
  });
  win.removeMenu();
  win.loadFile(path.join(__dirname, 'src', 'index.html'));
  // Links open in the normal browser, never inside the app.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/i.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.on('closed', () => { win = null; });
}

// Single instance: a second launch focuses the running window.
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (win) { if (win.isMinimized()) win.restore(); win.focus(); }
  });

  app.whenReady().then(() => {
    defaultUA = session.defaultSession.getUserAgent().replace(/\s*(Electron|bunc)\/\S+/gi, '');
    session.defaultSession.setUserAgent(defaultUA);
    createWindow();
    setupUpdates();
  });

  app.on('window-all-closed', () => app.quit());
}

// ---------- Network: API / playlist requests with a chosen client identity ----------
ipcMain.handle('http-get', async (_e, url, ua) => {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), 30000);
  try {
    const res = await net.fetch(url, {
      headers: { 'User-Agent': ua || defaultUA, Accept: '*/*' },
      redirect: 'follow',
      signal: ctrl.signal,
      cache: 'no-store'
    });
    const text = await res.text();
    return { ok: res.ok, status: res.status, url: res.url, text };
  } catch (e) {
    return { ok: false, status: 0, url, text: '', error: String((e && e.message) || e) };
  } finally {
    clearTimeout(t);
  }
});

// Identity used by the video player (streams, logos).
ipcMain.handle('set-ua', (_e, ua) => {
  session.defaultSession.setUserAgent(ua || defaultUA);
  return true;
});
ipcMain.handle('default-ua', () => defaultUA);

// Keep the display awake while watching.
ipcMain.handle('keep-awake', (_e, on) => {
  if (on && awakeId === null) awakeId = powerSaveBlocker.start('prevent-display-sleep');
  if (!on && awakeId !== null) { powerSaveBlocker.stop(awakeId); awakeId = null; }
  return true;
});

ipcMain.handle('app-version', () => app.getVersion());

ipcMain.handle('toggle-fullscreen', () => {
  if (win) win.setFullScreen(!win.isFullScreen());
  return win ? win.isFullScreen() : false;
});
ipcMain.handle('set-fullscreen', (_e, on) => {
  if (win) win.setFullScreen(!!on);
  return true;
});

// ---------- Automatic updates from GitHub releases ----------
let autoUpdater = null;
function send(state) {
  if (win) win.webContents.send('update', state);
}
function setupUpdates() {
  if (!app.isPackaged) return; // only the installed app can update itself
  try {
    autoUpdater = require('electron-updater').autoUpdater;
  } catch (e) {
    return;
  }
  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;
  autoUpdater.on('checking-for-update', () => send({ state: 'checking' }));
  autoUpdater.on('update-available', (i) => send({ state: 'downloading', version: i.version, percent: 0 }));
  autoUpdater.on('download-progress', (p) => send({ state: 'downloading', percent: p.percent }));
  autoUpdater.on('update-downloaded', (i) => send({ state: 'ready', version: i.version }));
  autoUpdater.on('update-not-available', () => send({ state: 'none' }));
  autoUpdater.on('error', (e) => send({ state: 'error', message: String((e && e.message) || e) }));
  setTimeout(() => autoUpdater.checkForUpdates().catch(() => {}), 4000);
  // Check again every 6 hours while the app stays open.
  setInterval(() => autoUpdater.checkForUpdates().catch(() => {}), 6 * 3600 * 1000);
}
ipcMain.handle('update-check', async () => {
  if (!autoUpdater) return 'dev';
  try { await autoUpdater.checkForUpdates(); return 'ok'; } catch (e) { return String((e && e.message) || e); }
});
ipcMain.handle('update-install', () => {
  if (autoUpdater) autoUpdater.quitAndInstall(false, true);
});
