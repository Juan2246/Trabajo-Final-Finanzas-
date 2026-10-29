/* Lucide Icons · ISC License. See static/vendor/LUCIDE-LICENSE.txt. */
const iconShapes = {
  grid: '<rect width="7" height="9" x="3" y="3" rx="1" />\n  <rect width="7" height="5" x="14" y="3" rx="1" />\n  <rect width="7" height="9" x="14" y="12" rx="1" />\n  <rect width="7" height="5" x="3" y="16" rx="1" />',
  users:
    '<path d="M18 21a8 8 0 0 0-16 0" />\n  <circle cx="10" cy="8" r="5" />\n  <path d="M22 20c0-3.37-2-6.5-4-8a5 5 0 0 0-.45-8.3" />',
  box: '<path d="M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z" />\n  <path d="M12 22V12" />\n  <polyline points="3.29 7 12 12 20.71 7" />\n  <path d="m7.5 4.27 9 5.15" />',
  bag: '<path d="M16 10a4 4 0 0 1-8 0" />\n  <path d="M3.103 6.034h17.794" />\n  <path d="M3.4 5.467a2 2 0 0 0-.4 1.2V20a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6.667a2 2 0 0 0-.4-1.2l-2-2.667A2 2 0 0 0 17 2H7a2 2 0 0 0-1.6.8z" />',
  wallet:
    '<path d="M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1" />\n  <path d="M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4" />',
  file: '<path d="M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z" />\n  <path d="M14 2v5a1 1 0 0 0 1 1h5" />\n  <path d="M10 9H8" />\n  <path d="M16 13H8" />\n  <path d="M16 17H8" />',
  store:
    '<path d="M15 21v-5a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v5" />\n  <path d="M17.774 10.31a1.12 1.12 0 0 0-1.549 0 2.5 2.5 0 0 1-3.451 0 1.12 1.12 0 0 0-1.548 0 2.5 2.5 0 0 1-3.452 0 1.12 1.12 0 0 0-1.549 0 2.5 2.5 0 0 1-3.77-3.248l2.889-4.184A2 2 0 0 1 7 2h10a2 2 0 0 1 1.653.873l2.895 4.192a2.5 2.5 0 0 1-3.774 3.244" />\n  <path d="M4 10.95V19a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8.05" />',
  clock: '<circle cx="12" cy="12" r="10" />\n  <path d="M12 6v6h4" />',
  settings:
    '<path d="M14 17H5" />\n  <path d="M19 7h-9" />\n  <circle cx="17" cy="17" r="3" />\n  <circle cx="7" cy="7" r="3" />',
  help: '<circle cx="12" cy="12" r="10" />\n  <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />\n  <path d="M12 17h.01" />',
  plus: '<path d="M5 12h14" />\n  <path d="M12 5v14" />',
  arrow: '<path d="M5 12h14" />\n  <path d="m12 5 7 7-7 7" />',
  down: '<path d="M12 15V3" />\n  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />\n  <path d="m7 10 5 5 5-5" />',
  search: '<path d="m21 21-4.34-4.34" />\n  <circle cx="11" cy="11" r="8" />',
  logout:
    '<path d="m16 17 5-5-5-5" />\n  <path d="M21 12H9" />\n  <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />',
  check: '<path d="M20 6 9 17l-5-5" />',
  shield:
    '<path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />\n  <path d="m9 12 2 2 4-4" />',
  trend:
    '<path d="M12 16v5" />\n  <path d="M16 14.639V21" />\n  <path d="M20 10.656V21" />\n  <path d="m22 3-8.646 8.646a.5.5 0 0 1-.708 0L9.354 8.354a.5.5 0 0 0-.707 0L2 15" />\n  <path d="M4 18.463V21" />\n  <path d="M8 14.656V21" />',
  menu: '<path d="M4 5h16" />\n  <path d="M4 12h16" />\n  <path d="M4 19h16" />',
  close: '<path d="M18 6 6 18" />\n  <path d="m6 6 12 12" />',
  edit: '<path d="M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />\n  <path d="M18.375 2.625a1 1 0 0 1 3 3l-9.013 9.014a2 2 0 0 1-.853.505l-2.873.84a.5.5 0 0 1-.62-.62l.84-2.873a2 2 0 0 1 .506-.852z" />',
  printer:
    '<path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2" />\n  <path d="M6 9V3a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v6" />\n  <rect x="6" y="14" width="12" height="8" rx="1" />',
  refresh:
    '<path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8" />\n  <path d="M21 3v5h-5" />\n  <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16" />\n  <path d="M8 16H3v5" />',
  lock: '<circle cx="12" cy="16" r="1" />\n  <rect x="3" y="10" width="18" height="12" rx="2" />\n  <path d="M7 10V7a5 5 0 0 1 10 0v3" />',
  chevron: '<path d="m9 18 6-6-6-6" />',
  info: '<circle cx="12" cy="12" r="10" />\n  <path d="M12 16v-4" />\n  <path d="M12 8h.01" />',
  coin: '<path d="M13.744 17.736a6 6 0 1 1-7.48-7.48" />\n  <path d="M15 6h1v4" />\n  <path d="m6.134 14.768.866-.5 2 3.464" />\n  <circle cx="16" cy="8" r="6" />',
  eye: '<path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0" />\n  <circle cx="12" cy="12" r="3" />',
  eyeOff:
    '<path d="M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49" />\n  <path d="M14.084 14.158a3 3 0 0 1-4.242-4.242" />\n  <path d="M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143" />\n  <path d="m2 2 20 20" />',
  chevronDown: '<path d="m6 9 6 6 6-6" />',
  arrowUpRight: '<path d="M7 7h10v10" />\n  <path d="M7 17 17 7" />',
  calendar:
    '<path d="M8 2v3" />\n  <path d="M16 2v3" />\n  <rect x="3" y="3" width="18" height="18" rx="2" />\n  <path d="M3 9h18" />\n  <path d="M8 13h.01" />\n  <path d="M12 13h.01" />\n  <path d="M16 13h.01" />\n  <path d="M8 17h.01" />\n  <path d="M12 17h.01" />\n  <path d="M16 17h.01" />',
  sliders:
    '<path d="M10 5H3" />\n  <path d="M12 19H3" />\n  <path d="M14 3v4" />\n  <path d="M16 17v4" />\n  <path d="M21 12h-9" />\n  <path d="M21 19h-5" />\n  <path d="M21 5h-7" />\n  <path d="M8 10v4" />\n  <path d="M8 12H3" />',
  mail: '<path d="m22 7-8.991 5.727a2 2 0 0 1-2.009 0L2 7" />\n  <rect x="2" y="4" width="20" height="16" rx="2" />',
  key: '<path d="M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z" />\n  <circle cx="16.5" cy="7.5" r=".5" fill="currentColor" />',
  trash:
    '<path d="M10 11v6" />\n  <path d="M14 11v6" />\n  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />\n  <path d="M3 6h18" />\n  <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />',
  logo: '<path d="M12 5v16" />\n  <path d="m16 12 2 2 4-4" />\n  <path d="M22 6V5a2 2 0 00-1.999-2L16 3.002A5 5 0 0012 5a5 5 0 00-4-2H4a2 2 0 00-2 2v12a2 2 0 001.999 2H8a5 5 0 014 2 5 5 0 014-2h4.001A2 2 0 0022 17v-1.344" />',
};
