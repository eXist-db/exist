'use strict';
/* eXist-db JMH benchmark dashboard rendering engine.
 * Shared by dev/bench/core/index.html and dev/bench/indexes/index.html.
 * Reads window.BENCHMARK_DATA (written by benchmark-action/github-action-benchmark
 * into the sibling data.js) and renders a grouped, searchable, dark-mode-aware
 * dashboard using Chart.js v4.
 */
(function () {
  // Qualitative palette (12 colors), reused per chart by series index.
  const PALETTE = [
    '#4493f8', '#f85149', '#3fb950', '#d29922', '#a371f7', '#39c5cf',
    '#db61a2', '#8b949e', '#58a6ff', '#e3b341', '#ff7b72', '#56d364',
  ];

  const NOISE_BAND = 3; // percent; changes smaller than this are shown as "stable"

  function el(tag, attrs, children) {
    const e = document.createElement(tag);
    if (attrs) {
      for (const [k, v] of Object.entries(attrs)) {
        if (k === 'class') e.className = v;
        else if (k === 'text') e.textContent = v;
        else if (k === 'title') e.title = v;
        else e.setAttribute(k, v);
      }
    }
    (children || []).forEach((c) => e.appendChild(c));
    return e;
  }

  function slugify(s) {
    return s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
  }

  // "pkg.pkg.ClassName.methodName ( {"k":"v"} )" -> {shortClass, pkg, method, paramsObj}
  function parseBenchName(name) {
    const m = name.match(/^(.*?)\s*\(\s*(\{[\s\S]*\})\s*\)\s*$/);
    let qualified = name.trim();
    let paramsObj = null;
    if (m) {
      qualified = m[1].trim();
      try {
        paramsObj = JSON.parse(m[2]);
      } catch (_) {
        paramsObj = null;
      }
    }
    const lastDot = qualified.lastIndexOf('.');
    const cls = lastDot === -1 ? '' : qualified.slice(0, lastDot);
    const method = lastDot === -1 ? qualified : qualified.slice(lastDot + 1);
    const clsLastDot = cls.lastIndexOf('.');
    const shortClass = clsLastDot === -1 ? cls : cls.slice(clsLastDot + 1);
    const pkg = clsLastDot === -1 ? '' : cls.slice(0, clsLastDot);
    return { shortClass, pkg, fullClass: cls, method, paramsObj };
  }

  function paramLabel(paramsObj, keys) {
    if (!paramsObj) return null;
    const useKeys = keys || Object.keys(paramsObj);
    if (useKeys.length === 0) return null;
    return useKeys.map((k) => `${k}=${paramsObj[k]}`).join(', ');
  }

  // Only the param keys that actually differ across a method's series are worth
  // showing in the legend — constant keys (e.g. verificationMode=STRICT on every
  // series) just add noise.
  function varyingParamKeys(paramsObjList) {
    const withParams = paramsObjList.filter(Boolean);
    if (withParams.length === 0) return [];
    const allKeys = new Set();
    withParams.forEach((p) => Object.keys(p).forEach((k) => allKeys.add(k)));
    return [...allKeys].filter((k) => {
      const values = new Set(withParams.map((p) => p[k]));
      return values.size > 1;
    });
  }

  // Higher value == better performance for this unit?
  function biggerIsBetter(unit) {
    if (!unit) return false;
    const u = unit.toLowerCase();
    if (u.startsWith('ops')) return true; // ops/s, ops/ms, ...
    return false; // */op (ms/op, us/op, ns/op, B/op, ...) and anything else: smaller is better
  }

  // Positive = improvement, negative = regression, regardless of metric direction.
  function pctChange(prevVal, currVal, unit) {
    if (prevVal === 0) return null;
    return biggerIsBetter(unit)
      ? ((currVal - prevVal) / prevVal) * 100
      : ((prevVal - currVal) / prevVal) * 100;
  }

  function badgeFor(pct) {
    if (pct === null || !Number.isFinite(pct)) {
      return { cls: 'neutral', text: 'first run' };
    }
    if (pct > NOISE_BAND) return { cls: 'good', text: `+${pct.toFixed(1)}%` };
    if (pct < -NOISE_BAND) return { cls: 'bad', text: `${pct.toFixed(1)}%` };
    return { cls: 'neutral', text: `${pct >= 0 ? '+' : ''}${pct.toFixed(1)}% (noise)` };
  }

  // Collapse per-run entries into { className: { method: { series: [{label, unit, points:[{commit,date,value,range,extra}]}] } } }
  // Series are keyed by the raw JSON params string first; labels are computed afterwards
  // from only the param keys that actually vary within that method, to avoid repeating
  // constant params (e.g. verificationMode=STRICT) on every legend entry.
  function buildGroups(entries) {
    const classes = new Map();
    for (const run of entries) {
      const { commit, date } = run;
      for (const bench of run.benches) {
        const { shortClass, pkg, fullClass, method, paramsObj } = parseBenchName(bench.name);
        const seriesKey = paramsObj ? JSON.stringify(paramsObj) : method;

        if (!classes.has(shortClass)) classes.set(shortClass, { pkg, fullClass, methods: new Map() });
        const clsEntry = classes.get(shortClass);

        if (!clsEntry.methods.has(method)) clsEntry.methods.set(method, new Map());
        const seriesMap = clsEntry.methods.get(method);

        if (!seriesMap.has(seriesKey)) seriesMap.set(seriesKey, { paramsObj, unit: bench.unit, points: [] });
        seriesMap.get(seriesKey).points.push({
          commit, date,
          value: bench.value,
          range: bench.range,
          extra: bench.extra,
        });
      }
    }

    // Second pass: compute compact labels per method using only varying param keys.
    for (const clsEntry of classes.values()) {
      for (const [method, seriesMap] of clsEntry.methods) {
        const series = [...seriesMap.values()];
        const varyKeys = varyingParamKeys(series.map((s) => s.paramsObj));
        series.forEach((s) => {
          s.label = paramLabel(s.paramsObj, varyKeys) || method;
        });
      }
    }

    return classes;
  }

  function renderTooltipAfterTitle(point) {
    if (!point) return '';
    const c = point.commit;
    return `\n${c.message}\n\n${c.timestamp} committed by @${c.committer.username}\n`;
  }

  const allCharts = [];

  function cssVar(name) {
    return getComputedStyle(document.body).getPropertyValue(name).trim();
  }

  function makeChart(canvas, seriesList, unit) {
    let allValues = [];
    seriesList.forEach((s) => s.points.forEach((p) => allValues.push(p.value)));
    allValues = allValues.filter((v) => v > 0);
    const useLog = allValues.length > 1 && Math.max(...allValues) / Math.min(...allValues) > 15;

    const labels = seriesList[0].points.map((p) => p.commit.id.slice(0, 7));

    const datasets = seriesList.map((s, i) => {
      const color = PALETTE[i % PALETTE.length];
      return {
        label: s.label,
        data: s.points.map((p) => p.value),
        borderColor: color,
        backgroundColor: color + '33',
        pointRadius: s.points.length <= 15 ? 3 : 1.5,
        borderWidth: 2,
        tension: 0.15,
        spanGaps: true,
      };
    });

    const chart = new Chart(canvas, {
      type: 'line',
      data: { labels, datasets },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        scales: {
          x: {
            title: { display: true, text: 'commit', color: cssVar('--fg-muted') },
            ticks: { color: cssVar('--fg-muted') },
            grid: { color: cssVar('--chart-grid') },
          },
          y: {
            type: useLog ? 'logarithmic' : 'linear',
            title: { display: true, text: unit || '', color: cssVar('--fg-muted') },
            beginAtZero: !useLog,
            ticks: { color: cssVar('--fg-muted') },
            grid: { color: cssVar('--chart-grid') },
          },
        },
        plugins: {
          legend: {
            display: seriesList.length > 1,
            position: 'bottom',
            labels: { boxWidth: 10, boxHeight: 10, font: { size: 10 }, color: cssVar('--fg') },
          },
          tooltip: {
            callbacks: {
              afterTitle: (items) => {
                const item = items[0];
                const s = seriesList[item.datasetIndex];
                return renderTooltipAfterTitle(s.points[item.dataIndex]);
              },
              label: (item) => {
                const s = seriesList[item.datasetIndex];
                const p = s.points[item.dataIndex];
                let label = `${s.label}: ${item.formattedValue} ${s.unit}`;
                if (p.range) label += ` (${p.range})`;
                return label;
              },
              afterLabel: (item) => {
                const s = seriesList[item.datasetIndex];
                const p = s.points[item.dataIndex];
                return p.extra ? `\n${p.extra}` : '';
              },
            },
          },
        },
        onClick: (evt, activeEls, chart) => {
          if (!activeEls.length) return;
          const { datasetIndex, index } = activeEls[0];
          const s = seriesList[datasetIndex];
          const url = s.points[index].commit.url;
          window.open(url, '_blank');
        },
      },
    });
    allCharts.push(chart);
    return chart;
  }

  function refreshChartTheme() {
    const fgMuted = cssVar('--fg-muted');
    const grid = cssVar('--chart-grid');
    const fg = cssVar('--fg');
    allCharts.forEach((chart) => {
      chart.options.scales.x.title.color = fgMuted;
      chart.options.scales.x.ticks.color = fgMuted;
      chart.options.scales.x.grid.color = grid;
      chart.options.scales.y.title.color = fgMuted;
      chart.options.scales.y.ticks.color = fgMuted;
      chart.options.scales.y.grid.color = grid;
      if (chart.options.plugins.legend.labels) chart.options.plugins.legend.labels.color = fg;
      chart.update('none');
    });
  }

  function chartWorstBadge(seriesList) {
    let worst = null;
    let best = null;
    for (const s of seriesList) {
      if (s.points.length < 2) continue;
      const prev = s.points[s.points.length - 2];
      const curr = s.points[s.points.length - 1];
      const pct = pctChange(prev.value, curr.value, s.unit);
      if (pct === null) continue;
      if (worst === null || pct < worst) worst = pct;
      if (best === null || pct > best) best = pct;
    }
    if (worst === null) return badgeFor(null);
    if (worst < -NOISE_BAND) return badgeFor(worst);
    if (best !== null && best > NOISE_BAND) return badgeFor(best);
    return badgeFor(worst);
  }

  function render(rootEl, sidebarEl, data, suiteName) {
    const entries = data.entries[suiteName] || [];
    if (entries.length === 0) {
      rootEl.appendChild(el('div', { class: 'empty-state', text: 'No benchmark data recorded for this suite yet.' }));
      return;
    }

    const classes = buildGroups(entries);
    const sortedClassNames = [...classes.keys()].sort((a, b) => a.localeCompare(b));

    const tocList = el('ul', {});
    sortedClassNames.forEach((clsName) => {
      const clsEntry = classes.get(clsName);
      const anchor = 'cls-' + slugify(clsName);
      const count = clsEntry.methods.size;
      const link = el('a', { class: 'toc-link', href: '#' + anchor, 'data-search': clsName.toLowerCase() }, [
        el('span', { text: clsName }),
        el('span', { class: 'count', text: String(count) }),
      ]);
      tocList.appendChild(el('li', {}, [link]));
    });
    sidebarEl.appendChild(el('h2', { text: `Benchmarks (${sortedClassNames.length} classes)` }));
    sidebarEl.appendChild(tocList);

    sortedClassNames.forEach((clsName) => {
      const clsEntry = classes.get(clsName);
      const anchor = 'cls-' + slugify(clsName);
      const details = el('details', { class: 'bench-class', id: anchor, open: 'open', 'data-search': clsName.toLowerCase() });
      const summary = el('summary', {}, [
        el('span', { class: 'class-name', text: clsName }),
        el('span', { class: 'class-pkg', text: clsEntry.pkg }),
        el('span', { class: 'class-count', text: `${clsEntry.methods.size} chart${clsEntry.methods.size === 1 ? '' : 's'}` }),
      ]);
      details.appendChild(summary);

      const grid = el('div', { class: 'chart-grid' });
      const sortedMethods = [...clsEntry.methods.keys()].sort((a, b) => a.localeCompare(b));
      sortedMethods.forEach((methodName) => {
        const seriesMap = clsEntry.methods.get(methodName);
        const seriesList = [...seriesMap.values()].sort((a, b) => a.label.localeCompare(b.label, undefined, { numeric: true }));
        const unit = seriesList[0].unit;

        const badge = chartWorstBadge(seriesList);
        const searchText = (clsName + ' ' + methodName + ' ' + seriesList.map((s) => s.label).join(' ')).toLowerCase();

        const card = el('div', { class: 'chart-card', 'data-search': searchText });
        card.appendChild(el('div', { class: 'chart-card-header' }, [
          el('div', { class: 'chart-title', text: methodName }),
          el('span', { class: `badge ${badge.cls}`, title: 'Change vs. previous run', text: badge.text }),
        ]));
        const wrap = el('div', { class: 'chart-canvas-wrap' });
        const canvas = el('canvas');
        wrap.appendChild(canvas);
        card.appendChild(wrap);
        grid.appendChild(card);

        makeChart(canvas, seriesList, unit);
      });

      details.appendChild(grid);
      rootEl.appendChild(details);
    });
  }

  function wireSearch(inputEl) {
    inputEl.addEventListener('input', () => {
      const q = inputEl.value.trim().toLowerCase();
      document.querySelectorAll('details.bench-class').forEach((section) => {
        let sectionHasMatch = false;
        section.querySelectorAll('.chart-card').forEach((card) => {
          const match = !q || card.getAttribute('data-search').includes(q);
          card.classList.toggle('hidden', !match);
          if (match) sectionHasMatch = true;
        });
        section.classList.toggle('hidden', !sectionHasMatch);
        if (q && sectionHasMatch) section.open = true;
      });
      document.querySelectorAll('.toc-link').forEach((link) => {
        const match = !q || link.getAttribute('data-search').includes(q);
        link.classList.toggle('hidden', !match);
      });
    });
  }

  function wireTheme(toggleBtn) {
    const stored = localStorage.getItem('bench-theme');
    if (stored) document.documentElement.setAttribute('data-theme', stored);
    const isDark = () => {
      const attr = document.documentElement.getAttribute('data-theme');
      if (attr) return attr === 'dark';
      return window.matchMedia('(prefers-color-scheme: dark)').matches;
    };
    const sync = () => { toggleBtn.textContent = isDark() ? '☀️ Light' : '🌙 Dark'; };
    sync();
    toggleBtn.addEventListener('click', () => {
      const next = isDark() ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', next);
      localStorage.setItem('bench-theme', next);
      sync();
      refreshChartTheme();
    });
  }

  window.BenchDashboard = { render, wireSearch, wireTheme, slugify };
})();
