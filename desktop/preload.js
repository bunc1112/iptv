// Safe bridge between the page and the main process.
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('bunc', {
  get: (url, ua) => ipcRenderer.invoke('http-get', url, ua),
  setUA: (ua) => ipcRenderer.invoke('set-ua', ua),
  defaultUA: () => ipcRenderer.invoke('default-ua'),
  keepAwake: (on) => ipcRenderer.invoke('keep-awake', on),
  version: () => ipcRenderer.invoke('app-version'),
  toggleFullscreen: () => ipcRenderer.invoke('toggle-fullscreen'),
  setFullscreen: (on) => ipcRenderer.invoke('set-fullscreen', on),
  checkUpdate: () => ipcRenderer.invoke('update-check'),
  installUpdate: () => ipcRenderer.invoke('update-install'),
  onUpdate: (cb) => ipcRenderer.on('update', (_e, s) => cb(s))
});
