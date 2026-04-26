/* ─── Config ───────────────────────────────────────────── */
const REFRESH_SEC = 30;
const CHART_COLORS = ['#2563eb','#7c3aed','#16a34a','#ea580c','#0f766e','#db2777','#4f46e5','#92400e','#0369a1','#854d0e'];

/* ─── Filter state ─────────────────────────────────────── */
let activeFilters = {};          // committed filters (after Apply)

/* ─── Chart instances ──────────────────────────────────── */
let charts = {};

/* ─── Countdown state ──────────────────────────────────── */
let countdownVal = REFRESH_SEC;
let countdownTimer = null;

/* ══════════════════════════════════════════════════════════
   FILTER HELPERS
══════════════════════════════════════════════════════════ */

/** Read the current UI filter values (not yet committed) */
function readFilterInputs() {
  return {
    from:      document.getElementById('fFrom').value || null,
    to:        document.getElementById('fTo').value   || null,
    platform:  document.getElementById('fPlatform').value  || null,
    eventName: document.getElementById('fEventName').value || null,
    userId:    document.getElementById('fUserId').value.trim() || null,
  };
}

/** Build a URLSearchParams string (returns '' or '?k=v&…') */
function buildQS(extra = {}) {
  const f = { ...activeFilters, ...extra };
  const p = new URLSearchParams();
  if (f.from)      p.set('from',      f.from);
  if (f.to)        p.set('to',        f.to);
  if (f.platform)  p.set('platform',  f.platform);
  if (f.eventName) p.set('eventName', f.eventName);
  if (f.userId)    p.set('userId',    f.userId);
  const s = p.toString();
  return s ? '?' + s : '';
}

/** Add a fixed extra param (e.g. limit) to an existing QS string safely */
function withParam(qs, key, val) {
  const sep = qs ? '&' : '?';
  return `${qs}${sep}${key}=${encodeURIComponent(val)}`;
}

/** Update the active-filter count badge */
function updateFilterBadge() {
  const count = Object.values(activeFilters).filter(Boolean).length;
  const badge = document.getElementById('activeFiltersCount');
  const bar   = document.getElementById('filterBar');
  if (count > 0) {
    badge.textContent = `${count} filter${count > 1 ? 's' : ''} active`;
    badge.style.display = 'inline-block';
    bar.classList.add('active');
  } else {
    badge.style.display = 'none';
    bar.classList.remove('active');
  }
}

/** Populate a <select> from an array of string options */
function populateSelect(id, values, allLabel) {
  const sel = document.getElementById(id);
  const current = sel.value;
  sel.innerHTML = `<option value="">${allLabel}</option>`;
  values.forEach(v => {
    const opt = document.createElement('option');
    opt.value = v; opt.textContent = v;
    if (v === current) opt.selected = true;
    sel.appendChild(opt);
  });
}

/* ══════════════════════════════════════════════════════════
   FETCH HELPERS
══════════════════════════════════════════════════════════ */

async function fetchJson(url) {
  const res = await fetch(url, { headers: { 'Accept': 'application/json' } });
  if (!res.ok) throw new Error(`${res.status} — ${url}`);
  return res.json();
}

function esc(v) {
  if (v == null) return '—';
  return String(v).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function fmt(ts) {
  if (!ts) return '—';
  const d = new Date(ts);
  return isNaN(d) ? esc(ts) : d.toLocaleString();
}

function hideLoader(id)       { const e = document.getElementById(id); if (e) e.classList.add('hidden'); }
function showLoader(id)       { const e = document.getElementById(id); if (e) e.classList.remove('hidden'); }
function setBadge(id, text)   { const e = document.getElementById(id); if (e) e.textContent = text; }
function destroyChart(key)    { if (charts[key]) { charts[key].destroy(); delete charts[key]; } }

/* ══════════════════════════════════════════════════════════
   CHART BUILDERS
══════════════════════════════════════════════════════════ */

function buildDailyChart(rows) {
  destroyChart('daily');
  hideLoader('dailyLoading');
  const sorted = [...rows].sort((a,b) => String(a.date).localeCompare(String(b.date)));
  const labels = sorted.map(r => r.date);
  const values = sorted.map(r => Number(r.clicks));
  setBadge('dailyBadge', values.length ? `${values.reduce((a,b)=>a+b,0)} total` : 'No data');
  if (!labels.length) return;

  charts.daily = new Chart(document.getElementById('dailyClicksChart'), {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: 'Clicks', data: values,
        borderColor: '#2563eb', backgroundColor: 'rgba(37,99,235,0.10)',
        borderWidth: 2, tension: 0.3, fill: true,
        pointRadius: values.length > 30 ? 0 : 4, pointHoverRadius: 6
      }]
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      plugins: { legend: { display: false }, tooltip: { mode: 'index', intersect: false } },
      scales: {
        x: { grid: { display: false }, ticks: { maxTicksLimit: 10, font: { size: 11 } } },
        y: { beginAtZero: true, grid: { color: '#f0f0f0' }, ticks: { font: { size: 11 } } }
      }
    }
  });
}

function buildTopProductsChart(rows) {
  destroyChart('products');
  hideLoader('productsLoading');
  const labels = rows.map(r => r.asin || '?');
  const values = rows.map(r => Number(r.clicks));
  setBadge('productsBadge', rows.length ? `${rows.length} ASINs` : 'No data');
  if (!labels.length) return;

  charts.products = new Chart(document.getElementById('topProductsChart'), {
    type: 'bar',
    data: {
      labels,
      datasets: [{
        label: 'Clicks', data: values,
        backgroundColor: CHART_COLORS.slice(0, labels.length),
        borderRadius: 6, borderSkipped: false
      }]
    },
    options: {
      responsive: true, maintainAspectRatio: false, indexAxis: 'y',
      plugins: {
        legend: { display: false },
        tooltip: { callbacks: { label: ctx => ` ${ctx.parsed.x} clicks` } }
      },
      scales: {
        x: { beginAtZero: true, grid: { color: '#f0f0f0' }, ticks: { font: { size: 11 } } },
        y: { grid: { display: false }, ticks: { font: { size: 11 } } }
      }
    }
  });
}

function buildEventNamesChart(rows) {
  destroyChart('events');
  hideLoader('eventsLoading');
  const labels = rows.map(r => r.eventName || 'UNKNOWN');
  const values = rows.map(r => Number(r.count));
  const total  = values.reduce((a,b) => a+b, 0);
  setBadge('eventsBadge', total ? `${total} events` : 'No data');
  if (!labels.length) return;

  charts.events = new Chart(document.getElementById('eventNamesChart'), {
    type: 'doughnut',
    data: {
      labels,
      datasets: [{
        data: values,
        backgroundColor: CHART_COLORS.slice(0, labels.length),
        borderWidth: 2, borderColor: '#fff', hoverOffset: 6
      }]
    },
    options: {
      responsive: true, maintainAspectRatio: false, cutout: '62%',
      plugins: {
        legend: { position: 'bottom', labels: { boxWidth: 12, font: { size: 11 }, padding: 10 } },
        tooltip: {
          callbacks: {
            label: ctx => {
              const pct = total ? ((ctx.parsed / total) * 100).toFixed(1) : 0;
              return ` ${ctx.parsed} (${pct}%)`;
            }
          }
        }
      }
    }
  });
}

/* ══════════════════════════════════════════════════════════
   TABLE
══════════════════════════════════════════════════════════ */

function trendHtml(t) {
  if (!t) return '—';
  const cls  = (t==='UP'||t==='RISING') ? 'trend-up' : (t==='DOWN'||t==='FALLING') ? 'trend-down' : 'trend-stable';
  const icon = (t==='UP'||t==='RISING') ? '↑' : (t==='DOWN'||t==='FALLING') ? '↓' : '→';
  return `<span class="${cls}">${icon} ${esc(t)}</span>`;
}

function renderTable(events) {
  const tbody = document.getElementById('recentEventsTbody');
  setBadge('recentBadge', `${events.length} shown`);
  if (!events.length) {
    tbody.innerHTML = '<tr><td colspan="8" class="empty-row">No events match the current filters</td></tr>';
    return;
  }
  tbody.innerHTML = events.map(ev => {
    const p = ev.payload || {};
    return `<tr>
      <td>${fmt(ev.timestamp)}</td>
      <td><span class="event-pill">${esc(ev.eventName)}</span></td>
      <td><code>${esc(p.asin)}</code></td>
      <td style="max-width:160px;overflow:hidden;text-overflow:ellipsis" title="${esc(p.userId)}">${esc(p.userId)}</td>
      <td>${esc(p.platform)}</td>
      <td>${esc(p.version)}</td>
      <td>${p.score != null ? Number(p.score).toFixed(1) : '—'}</td>
      <td>${trendHtml(p.trend)}</td>
    </tr>`;
  }).join('');
}

/* ══════════════════════════════════════════════════════════
   MAIN REFRESH
══════════════════════════════════════════════════════════ */

async function refreshDashboard() {
  document.getElementById('refreshBtn').disabled = true;

  // show loaders for all charts
  ['dailyLoading','productsLoading','eventsLoading'].forEach(showLoader);

  const qs       = buildQS();            // analytics-event filters
  const dateOnly = buildQS({ platform: null, eventName: null, userId: null }); // click filters (no payload fields)

  try {
    const [clicks, eventsCount, products, daily, topNames, recent] = await Promise.all([
      fetchJson(`/api/analytics/clicks${dateOnly}`),
      fetchJson(`/api/analytics/events/count${qs}`),
      fetchJson(`/api/analytics/top-products${dateOnly}`),
      fetchJson(`/api/analytics/daily${dateOnly}`),
      fetchJson(`/api/analytics/events/top-names${withParam(qs, 'limit', 8)}`),
      fetchJson(`/api/analytics/events/recent${withParam(qs, 'limit', 30)}`)
    ]);

    // KPIs
    document.getElementById('totalClicks').textContent    = clicks ?? '—';
    document.getElementById('totalEvents').textContent    = eventsCount ?? '—';
    document.getElementById('topAsin').textContent        = products[0]?.asin || '—';
    document.getElementById('topEventName').textContent   = topNames[0]?.eventName || '—';

    buildDailyChart(daily);
    buildTopProductsChart(products);
    buildEventNamesChart(topNames);
    renderTable(recent);

    document.getElementById('lastUpdated').textContent = `Synced ${new Date().toLocaleTimeString()}`;
  } catch (err) {
    console.error('Dashboard refresh failed:', err);
    document.getElementById('lastUpdated').textContent = `Sync failed: ${err.message}`;
    ['dailyLoading','productsLoading','eventsLoading'].forEach(id => {
      const el = document.getElementById(id);
      if (el) { el.textContent = 'Failed to load'; el.classList.remove('hidden'); }
    });
  } finally {
    document.getElementById('refreshBtn').disabled = false;
  }
}

/* ══════════════════════════════════════════════════════════
   FILTER POPULATION (from API)
══════════════════════════════════════════════════════════ */

async function loadFilterOptions() {
  try {
    const [platforms, names] = await Promise.all([
      fetchJson('/api/analytics/events/platforms'),
      fetchJson('/api/analytics/events/names')
    ]);
    populateSelect('fPlatform',  platforms, 'All platforms');
    populateSelect('fEventName', names,     'All events');
  } catch (e) {
    console.warn('Could not load filter options:', e.message);
  }
}

/* ══════════════════════════════════════════════════════════
   FILTER WIRE-UP
══════════════════════════════════════════════════════════ */

document.getElementById('applyBtn').addEventListener('click', () => {
  activeFilters = readFilterInputs();
  updateFilterBadge();
  refreshDashboard().then(startCountdown);
});

document.getElementById('clearBtn').addEventListener('click', () => {
  document.getElementById('fFrom').value      = '';
  document.getElementById('fTo').value        = '';
  document.getElementById('fPlatform').value  = '';
  document.getElementById('fEventName').value = '';
  document.getElementById('fUserId').value    = '';
  activeFilters = {};
  updateFilterBadge();
  refreshDashboard().then(startCountdown);
});

// Apply on Enter inside the userId field
document.getElementById('fUserId').addEventListener('keydown', e => {
  if (e.key === 'Enter') document.getElementById('applyBtn').click();
});

document.getElementById('refreshBtn').addEventListener('click', () => {
  refreshDashboard().then(startCountdown);
});

/* ══════════════════════════════════════════════════════════
   COUNTDOWN
══════════════════════════════════════════════════════════ */

function startCountdown() {
  clearInterval(countdownTimer);
  countdownVal = REFRESH_SEC;

  countdownTimer = setInterval(() => {
    countdownVal--;
    const el = document.getElementById('countdown');
    if (el) el.textContent = countdownVal > 0 ? `Refreshing in ${countdownVal}s` : 'Refreshing…';
    if (countdownVal <= 0) {
      clearInterval(countdownTimer);
      refreshDashboard().then(startCountdown);
    }
  }, 1000);
}

/* ══════════════════════════════════════════════════════════
   BOOT
══════════════════════════════════════════════════════════ */

loadFilterOptions();
refreshDashboard().then(startCountdown);
