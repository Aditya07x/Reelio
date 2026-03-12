// ============================================================
// Reélio ALSE Lab — app.js  (v4.0 — Full Overhaul)
// ============================================================

const CONFIG = {
    DOOM_THRESHOLD: 0.55,
    BINGE_REELS: 60,
    INTERRUPT_REELS: 5,
    AMBER_THRESHOLD: 0.35,
};

// Scenario presets — override collectParams() for stress-test scenarios
const SCENARIO_PRESETS = {
    ad_heavy: { ad_rate: 43, sessions: 60 },
    survey_sparse: { mood_before_fill_rate: 60, post_session_rating_fill_rate: 40, regret_score_fill_rate: 40, mood_after_fill_rate: 40, comparative_rating_fill_rate: 13, actual_vs_intended_fill_rate: 13 },
    false_positive_trap: { dwell_mu: 9, speed_mu: 5, exit_rate_base: 0.02, rewatch_rate_base: 0.03, sessions: 40 },
    exit_conflict_extreme: { exit_rate_base: 0.5, session_reels_mu: 13, sessions: 30 },
    regime_shift: { sessions: 60 },
    cold_start_race: { sessions: 10 },
    flood_corrupted: { simulate_flood_bug: true, flood_probability: 15, sessions: 30 },
};

// ============================================================
// Chart Manager
// ============================================================
const ChartManager = {
    _charts: {},
    init(id, config) {
        this.destroy(id);
        const ctx = document.getElementById(id)?.getContext('2d');
        if (!ctx) return null;
        this._charts[id] = new Chart(ctx, config);
        return this._charts[id];
    },
    get(id) { return this._charts[id]; },
    update(id, labels, data, datasetIdx = 0) {
        const c = this._charts[id];
        if (!c) return;
        c.data.labels = labels;
        c.data.datasets[datasetIdx].data = data;
        c.update('none');
    },
    destroy(id) {
        if (this._charts[id]) { this._charts[id].destroy(); delete this._charts[id]; }
    },
    destroyAll() {
        Object.keys(this._charts).forEach(id => this.destroy(id));
    },
};

// ============================================================
// State
// ============================================================
let csvColumns = [], csvRows = [];
let currentSessionId = '';
let liveLabels = [], liveData = [];

Chart.defaults.color = '#64748b';
Chart.defaults.borderColor = '#1e293b';

// ============================================================
// Tab switching
// ============================================================
function switchTab(tab, btn) {
    document.getElementById('tab-charts').style.display = tab === 'charts' ? '' : 'none';
    document.getElementById('tab-csv').style.display = tab === 'csv' ? '' : 'none';
    qsa('.tab-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    if (tab === 'csv' && csvColumns.length === 0 && currentSessionId) loadCsv(currentSessionId);
}

// ============================================================
// Helpers
// ============================================================
function kv(key, val, cls) {
    return `<div class="kv"><span class="k">${key}</span><span class="v ${cls || ''}">${val}</span></div>`;
}
function colorClass(v, lo, hi) { return v >= hi ? 'hi' : v >= lo ? 'mid' : 'lo'; }
function kvGrid(el, pairs) {
    if (!el) return;
    el.innerHTML = pairs.map(([k, v, c]) => kv(k, v, c)).join('');
}
function setInner(id, html) {
    const el = document.getElementById(id);
    if (el) el.innerHTML = html;
}

// ============================================================
// Collect params
// ============================================================
function collectParams() {
    const v = id => document.getElementById(id)?.value;
    const f = id => parseFloat(v(id));
    const i = id => parseInt(v(id), 10);
    const iOrNull = id => { const x = v(id); return x ? parseInt(x, 10) : null; };
    const scenario = v('scenario');

    let params = {
        scenario, sessions: i('sessions'), seed: i('seed'), personality: v('personality'),
        session_reels_mu: f('session_reels_mu'), session_reels_sigma: f('session_reels_sigma'),
        dwell_mu: f('dwell_mu'), dwell_sigma: f('dwell_sigma'),
        speed_mu: f('speed_mu'), speed_sigma: f('speed_sigma'),
        gap_q10: f('gap_q10'), gap_q50: f('gap_q50'), gap_q90: f('gap_q90'),
        exit_rate_base: f('exit_rate_base'), rewatch_rate_base: f('rewatch_rate_base'),
        // Survey per-field
        survey_fill_pct: 40, // fallback
        mood_before_fill_rate: f('mood_before_fill_rate'),
        post_session_rating_fill_rate: f('post_session_rating_fill_rate'),
        regret_score_fill_rate: f('regret_score_fill_rate'),
        mood_after_fill_rate: f('mood_after_fill_rate'),
        comparative_rating_fill_rate: f('comparative_rating_fill_rate'),
        actual_vs_intended_fill_rate: f('actual_vs_intended_fill_rate'),
        delayed_regret_fill_rate: 0,
        override_regret: iOrNull('override_regret'),
        override_mood_before: iOrNull('override_mood_before'),
        override_comparative: iOrNull('override_comparative'),
        // Realism
        binge_probability: f('binge_probability'), burst_session_rate: f('burst_session_rate'),
        survey_fatigue_decay: f('survey_fatigue_decay') / 100.0,
        correlated_survey_skip: document.getElementById('correlated_survey_skip')?.checked || false,
        dishonesty_rate: f('dishonesty_rate'), interruption_rate: f('interruption_rate'),
        dwell_spike_rate: f('dwell_spike_rate'), weekend_multiplier: f('weekend_multiplier'),
        // Sensor & content
        lux_mode: v('lux_mode'), is_charging_rate: f('is_charging_rate'),
        ad_rate: f('ad_rate'), ad_skip_latency_mu: f('ad_skip_latency_mu'), ad_skip_latency_sigma: f('ad_skip_latency_sigma'),
        // Data bugs
        simulate_flood_bug: document.getElementById('simulate_flood_bug')?.checked || false,
        flood_probability: f('flood_probability'), flood_repeat_count: i('flood_repeat_count'),
        simulate_double_write: document.getElementById('simulate_double_write')?.checked || false,
        double_write_completion_factor: f('double_write_completion_factor'),
        // Feedback loops
        content_narrowing_rate: f('content_narrowing_rate') / 100.0,
        mood_carryover_strength: f('mood_carryover_strength') / 100.0,
        sleep_debt_enabled: document.getElementById('sleep_debt_enabled')?.checked || false,
        exit_conflict_shape: v('exit_conflict_shape'),
    };

    // Apply scenario presets (override specific params)
    if (SCENARIO_PRESETS[scenario]) {
        Object.assign(params, SCENARIO_PRESETS[scenario]);
    }

    return params;
}

// ============================================================
// Run simulation
// ============================================================
async function runSimulation() {
    const btn = document.getElementById('run-btn');
    const status = document.getElementById('status-text');
    btn.disabled = true; btn.innerHTML = '⏳ Running...'; status.textContent = '';

    const params = collectParams();
    currentSessionId = '';
    csvColumns = []; csvRows = [];
    liveLabels = []; liveData = [];

    qs('#pipeline-panel').style.display = '';
    qs('#empty-state').style.display = 'none';
    qs('#results-area').style.display = 'none';
    qs('#pp-bar').style.width = '0%';
    qs('#pp-progress').textContent = '0/0';
    initLiveChart(); initGammaChart();

    try {
        const resp = await fetch('/api/simulate-stream', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(params),
        });
        const reader = resp.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n'); buffer = lines.pop();
            for (const line of lines) {
                if (!line.startsWith('data: ')) continue;
                try { handleSSE(JSON.parse(line.slice(6).trim())); } catch (e) { }
            }
        }
    } catch (e) {
        status.textContent = '❌ ' + (e.message || '').substring(0, 200);
    } finally {
        btn.disabled = false; btn.innerHTML = '▶ Run';
    }
}

// ============================================================
// SSE Handler
// ============================================================
function handleSSE(ev) {
    const status = document.getElementById('status-text');
    switch (ev.type) {
        case 'status':
            qs('#pipeline-status').textContent = ev.message;
            status.textContent = ev.message; break;
        case 'generated':
            qs('#pipeline-status').textContent = `Generated ${ev.total_sessions} sessions (${ev.total_rows} rows)`;
            if (ev.session_id) currentSessionId = ev.session_id;
            if (ev.data_quality) renderDataQualityGlobal(ev.data_quality);
            break;
        case 'session': renderPillarData(ev); break;
        case 'complete': handleComplete(ev); break;
        case 'error': status.textContent = '❌ ' + (ev.message || '').substring(0, 200); break;
    }
}

// ============================================================
// Pillar rendering — split into focused functions
// ============================================================

function updateProgressBar(ev) {
    const pct = (ev.session / ev.total_sessions * 100).toFixed(0);
    qs('#pp-bar').style.width = pct + '%';
    qs('#pp-progress').textContent = `${ev.session}/${ev.total_sessions}`;
    qs('#pipeline-status').textContent = `Session ${ev.session}/${ev.total_sessions} — ${ev.n_reels} reels — ${(ev.start_time || '').substring(11, 16)}`;
}

function renderFeaturePanel(f) {
    kvGrid(qs('#pp-features'), [
        ['Reels', f.n_reels, f.n_reels >= CONFIG.BINGE_REELS ? 'hi' : ''],
        ['Dwell', f.mean_dwell + 's', colorClass(f.mean_dwell, 2, 4)],
        ['log(dwell)', f.log_dwell, ''],
        ['Speed', f.mean_speed, ''],
        ['log(speed)', f.log_speed, ''],
        ['Exits', f.exit_rate, colorClass(f.exit_rate, 0.05, 0.15)],
        ['Rewatch', f.rewatch_rate, colorClass(f.rewatch_rate, 0.05, 0.2)],
        ['Entropy', f.entropy, ''],
        ['Trend', f.trend, colorClass(-f.trend, 0, 0.5)],
        ['Gap', f.gap_min + 'm', f.gap_min < 5 ? 'hi' : ''],
        ['Fatigue', f.fatigue_risk, colorClass(f.fatigue_risk, 0.3, 0.7)],
        ['MoodRisk', f.mood_risk, colorClass(f.mood_risk, 0.3, 0.7)],
        ['Stress', f.stress_flag, f.stress_flag ? 'hi' : 'lo'],
        // Ad-aware
        ['Ad %', `${(f.ad_fraction * 100).toFixed(0)}%`, f.ad_fraction > 0.3 ? 'hi' : ''],
        ['Skip ms', f.avg_skip_latency_ms, f.avg_skip_latency_ms > 15000 ? 'hi' : ''],
        ['Ad dwell', f.ad_mean_dwell, ''],
        ['Org dwell', f.organic_mean_dwell, ''],
    ]);
}

function renderDoomScorer(ds) {
    const dsPct = Math.round(ds.doom_score * 100);
    qs('#pp-doom-bar').style.width = dsPct + '%';
    const el = qs('#pp-doom-val');
    el.textContent = ds.doom_score.toFixed(3);
    el.className = 'text-lg font-bold font-mono ' + (ds.doom_score >= CONFIG.DOOM_THRESHOLD ? 'text-red-400' : ds.doom_score >= CONFIG.AMBER_THRESHOLD ? 'text-amber-400' : 'text-green-400');
    kvGrid(qs('#pp-doom-comp'),
        Object.entries(ds.components).map(([k, v]) => [k.replace(/_/g, ' '), v.toFixed(3), colorClass(v, 0.15, 0.4)])
    );
}

function renderGammaPanel(g) {
    kvGrid(qs('#pp-gamma'), [
        ['raw mean', g.mean_doom, colorClass(g.mean_doom, CONFIG.AMBER_THRESHOLD, CONFIG.DOOM_THRESHOLD)],
        ['max', g.max_doom, colorClass(g.max_doom, CONFIG.AMBER_THRESHOLD, CONFIG.DOOM_THRESHOLD)],
        ['min', g.min_doom, ''],
        ['first reel', g.first_reel_doom, ''],
        ['last reel', g.last_reel_doom, colorClass(g.last_reel_doom, CONFIG.AMBER_THRESHOLD, CONFIG.DOOM_THRESHOLD)],
    ]);
    if (g.doom_trajectory) {
        ChartManager.update('chart-gamma-traj', g.doom_trajectory.map((_, i) => i), g.doom_trajectory);
    }
}

function renderEmissionPanel(em) {
    kvGrid(qs('#pp-emission'), [
        ['μ_dwell C', em.mu_dwell_casual, ''], ['μ_dwell D', em.mu_dwell_doom, ''],
        ['σ_dwell C', em.sigma_dwell_casual, ''], ['σ_dwell D', em.sigma_dwell_doom, ''],
        ['μ_speed C', em.mu_speed_casual, ''], ['μ_speed D', em.mu_speed_doom, ''],
        ['σ_speed C', em.sigma_speed_casual, ''], ['σ_speed D', em.sigma_speed_doom, ''],
        ['ρ_C', em.rho_dwell_speed_casual, ''], ['ρ_D', em.rho_dwell_speed_doom, ''],
        ['p_rwt C', em.p_rewatch_casual, ''], ['p_rwt D', em.p_rewatch_doom, colorClass(em.p_rewatch_doom, 0.1, 0.3)],
        ['p_exit C', em.p_exit_casual, ''], ['p_exit D', em.p_exit_doom, colorClass(em.p_exit_doom, 0.2, 0.5)],
    ]);
}

function renderTransitionPanel(tr, ct) {
    kvGrid(qs('#pp-transition'), [
        ['A[C→C]', tr.A_casual_casual, ''], ['A[C→D]', tr.A_casual_doom, colorClass(tr.A_casual_doom, 0.1, 0.3)],
        ['A[D→C]', tr.A_doom_casual, colorClass(tr.A_doom_casual, 0.2, 0.4)], ['A[D→D]', tr.A_doom_doom, ''],
        ['q₀₁ pull', ct.q_01_pull, ''], ['q₁₀ esc', ct.q_10_escape, ''],
    ]);
}

function renderContextPanel(cp, hz) {
    kvGrid(qs('#pp-context'), [
        ['π casual', cp.pi_casual, ''], ['π doom', cp.pi_doom, colorClass(cp.pi_doom, 0.3, 0.5)],
        ['h doom', hz.h_doom, ''], ['h casual', hz.h_casual, ''],
    ]);
}

function renderSupervisedPanel(sup) {
    setInner('pp-supervised', [
        kv('sup doom', sup.supervised_doom, colorClass(sup.supervised_doom, 0.3, CONFIG.DOOM_THRESHOLD)),
        kv('label conf', sup.label_confidence, sup.label_confidence >= 0.5 ? 'mid' : ''),
        kv('disagree', sup.running_disagreement, Math.abs(sup.running_disagreement) > 0.05 ? 'hi' : ''),
        kv('labeled', sup.labeled_sessions_total, ''),
    ].join(''));
}

function renderRegimePanel(reg) {
    setInner('pp-regime', [
        kv('alert', reg.alert ? '⚠ YES' : '✓ No', reg.alert ? 'hi' : 'lo'),
        kv('duration', reg.alert_duration, ''),
        kv('history', reg.doom_history_len, ''),
    ].join(''));
}

function renderConfidencePanel(conf) {
    setInner('pp-conf', [
        kv('overall', conf.overall, colorClass(conf.overall, 0.4, 0.7)),
        kv('volume', conf.volume, ''),
        kv('separation', conf.separation, ''),
        kv('stability', conf.stability, ''),
        kv('supervision', conf.supervision, ''),
    ].join(''));
}

function renderBaselinePanel(b) {
    setInner('pp-baseline', [
        kv('dwell μ', b.dwell_mu, ''),
        kv('dwell σ', b.dwell_sig, ''),
        kv('sess len', b.session_len_mu, ''),
        kv('n seen', b.n_sessions_seen, ''),
    ].join(''));
}

function renderDataQualitySession(dq) {
    if (!dq) return;
    kvGrid(qs('#pp-dq'), [
        ['rows pre', dq.rows_before_dedupe, ''],
        ['rows post', dq.rows_after_dedupe, ''],
        ['deduped', dq.deduped ? 'YES' : 'no', dq.deduped ? 'hi' : ''],
    ]);
}

function renderDataQualityGlobal(dq) {
    if (!dq) return;
    const sc = dq.survey_coverage || {};
    kvGrid(qs('#pp-dq'), [
        ['flood sess', dq.flood_sessions, dq.flood_sessions > 0 ? 'hi' : ''],
        ['2x write', dq.double_write_sessions, dq.double_write_sessions > 0 ? 'hi' : ''],
        ['ad frac', `${(dq.ad_row_fraction * 100).toFixed(0)}%`, dq.ad_row_fraction > 0.3 ? 'hi' : ''],
        ['skip ms', dq.mean_skip_latency_ms, ''],
        ['regret fill', sc.regret || '—', ''],
        ['mood fill', sc.mood || '—', ''],
        ['comp fill', sc.comparative || '—', ''],
    ]);
}

function updateLiveStDisplay(S_t, session) {
    const el = qs('#pp-st-val');
    el.textContent = S_t.toFixed(4);
    el.className = 'text-2xl font-bold font-mono ' +
        (S_t >= CONFIG.DOOM_THRESHOLD ? 'text-red-400' : S_t >= CONFIG.AMBER_THRESHOLD ? 'text-amber-400' : 'text-green-400');
    liveLabels.push(session);
    liveData.push(S_t);
    ChartManager.update('chart-live', liveLabels, liveData);
}

function renderPillarData(ev) {
    if (ev.skipped) {
        qs('#pipeline-status').textContent = ev.reason;
        return;
    }
    updateProgressBar(ev);
    renderFeaturePanel(ev.features);
    renderDoomScorer(ev.doom_scorer);
    renderGammaPanel(ev.gamma);
    renderEmissionPanel(ev.emission);
    renderTransitionPanel(ev.transition, ev.ctmc);
    renderContextPanel(ev.contextual_pi, ev.hazard);
    kvGrid(qs('#pp-fw'),
        Object.entries(ev.feature_weights).map(([k, v]) => [k.replace(/_/g, ' '), v.toFixed(3), v >= 0.25 ? 'mid' : ''])
    );
    renderSupervisedPanel(ev.supervised);
    renderRegimePanel(ev.regime);
    renderConfidencePanel(ev.confidence);
    renderBaselinePanel(ev.baseline);
    if (ev.data_quality) renderDataQualitySession(ev.data_quality);
    updateLiveStDisplay(ev.S_t, ev.session);
}

// ============================================================
// Mini charts
// ============================================================
function initLiveChart() {
    liveLabels = []; liveData = [];
    ChartManager.init('chart-live', {
        type: 'line', data: {
            labels: [], datasets: [
                { data: [], borderColor: '#818cf8', backgroundColor: 'rgba(129,140,248,0.08)', fill: true, tension: 0.3, pointRadius: 0, borderWidth: 1.5 },
                { data: [], borderColor: '#ef444480', borderDash: [3, 2], borderWidth: 1, pointRadius: 0 },
            ]
        },
        options: {
            responsive: true, maintainAspectRatio: false, animation: false,
            scales: { y: { min: 0, max: 1, ticks: { stepSize: 0.25, font: { size: 8 } }, grid: { color: '#1e293b' } }, x: { ticks: { font: { size: 7 }, maxTicksLimit: 8 }, grid: { display: false } } },
            plugins: { legend: { display: false } }
        },
    });
}

function initGammaChart() {
    ChartManager.init('chart-gamma-traj', {
        type: 'line', data: {
            labels: [], datasets: [{
                data: [], borderColor: '#a78bfa', fill: false, tension: 0.2, pointRadius: 0, borderWidth: 1,
            }]
        },
        options: {
            responsive: true, maintainAspectRatio: false, animation: false,
            scales: { y: { min: 0, max: 1, display: false }, x: { display: false } },
            plugins: { legend: { display: false } }
        },
    });
}

// ============================================================
// Complete handler
// ============================================================
function handleComplete(ev) {
    qs('#pipeline-panel').style.display = 'none';
    qs('#results-area').style.display = '';
    qs('#status-text').textContent = 'Done! CSV tab to inspect raw data.';
    if (ev.session_id) currentSessionId = ev.session_id;
    const cd = ev.chart_data, e = ev.evaluation;
    renderStChart(cd.sessions, cd.s_t);
    renderReelsChart(cd.sessions, cd.reels_per_session);
    renderMetrics(e, cd);
    renderChecks(e.checks);
}

function renderStChart(labels, values) {
    ChartManager.init('chart-st', {
        type: 'line', data: {
            labels, datasets: [
                { label: 'S_t', data: values, borderColor: '#818cf8', backgroundColor: 'rgba(129,140,248,0.06)', fill: true, tension: 0.3, pointRadius: 1.5, borderWidth: 2 },
                { label: 'Threshold', data: labels.map(() => CONFIG.DOOM_THRESHOLD), borderColor: '#ef4444', borderDash: [6, 4], borderWidth: 1, pointRadius: 0 },
            ]
        }, options: { responsive: true, scales: { y: { min: 0, max: 1, ticks: { stepSize: 0.1 }, grid: { color: '#1e293b' } }, x: { grid: { display: false } } }, plugins: { legend: { position: 'bottom', labels: { boxWidth: 8, font: { size: 9 } } } } }
    });
}

function renderReelsChart(labels, values) {
    ChartManager.init('chart-reels', {
        type: 'bar', data: {
            labels, datasets: [{
                data: values,
                backgroundColor: values.map(v => v >= CONFIG.BINGE_REELS ? 'rgba(239,68,68,0.5)' : v >= 30 ? 'rgba(234,179,8,0.3)' : 'rgba(16,185,129,0.3)'),
                borderColor: values.map(v => v >= CONFIG.BINGE_REELS ? '#ef4444' : v >= 30 ? '#eab308' : '#10b981'), borderWidth: 1, borderRadius: 2,
            }]
        }, options: { responsive: true, scales: { y: { beginAtZero: true, grid: { color: '#1e293b' } }, x: { grid: { display: false } } }, plugins: { legend: { display: false } } }
    });
}

function renderMetrics(ev, cd) {
    const grid = qs('#metrics-grid');
    grid.innerHTML = [
        { l: 'First Window', v: ev.mean_first_window?.toFixed(3) },
        { l: 'Last Window', v: ev.mean_last_window?.toFixed(3) },
        { l: 'Delta', v: ev.delta_last_minus_first?.toFixed(3), c: ev.delta_last_minus_first < -0.05 ? 'text-green-400' : ev.delta_last_minus_first > 0.05 ? 'text-red-400' : '' },
        { l: 'Confidence', v: ev.model_confidence?.toFixed(3) },
        { l: 'Sessions', v: ev.sessions_scored },
        { l: 'Fill Rate', v: `${qs('#regret_score_fill_rate')?.value || 40}%` },
        { l: 'Binge', v: (cd.reels_per_session?.filter(r => r >= CONFIG.BINGE_REELS).length || 0), c: 'text-amber-400' },
        { l: 'Interrupted', v: (cd.reels_per_session?.filter(r => r <= CONFIG.INTERRUPT_REELS).length || 0), c: 'text-blue-400' },
    ].map(i => `<div class="bg-gray-900/50 rounded-lg border border-gray-800/40 p-2"><div class="text-[9px] text-gray-500 uppercase">${i.l}</div><div class="text-lg font-bold ${i.c || 'text-gray-200'} mt-0.5 font-mono">${i.v ?? '—'}</div></div>`).join('');
}

function renderChecks(checks) {
    const a = qs('#checks-area');
    a.innerHTML = '<h3 class="text-[9px] font-semibold text-gray-500 mb-2 uppercase">Checks</h3>';
    if (!checks) return;
    for (const [k, p] of Object.entries(checks))
        a.innerHTML += `<div class="flex items-center justify-between py-1.5 border-b border-gray-800/40 last:border-0"><div class="flex items-center gap-1.5"><span>${p ? '✅' : '❌'}</span><span class="text-xs text-gray-300">${k.replace(/_/g, ' ')}</span></div><span class="text-[9px] font-mono font-bold ${p ? 'text-green-400' : 'text-red-400'}">${p ? 'PASS' : 'FAIL'}</span></div>`;
}

// ============================================================
// CSV
// ============================================================
async function loadCsv(sessionId) {
    try {
        const resp = await fetch(`/api/csv/${sessionId}`);
        if (!resp.ok) throw new Error('No CSV data yet');
        const data = await resp.json();
        csvColumns = data.columns; csvRows = data.rows;
        renderCsvTable(data);
        qs('#csv-info').textContent = `${data.total_rows} rows${data.truncated ? ' (500)' : ''}`;
    } catch (e) { qs('#csv-info').textContent = '⚠ ' + e.message; }
}

let csvEditTimer = null;
function renderCsvTable(data) {
    const thead = qs('#csv-table thead');
    const tbody = qs('#csv-table tbody');
    thead.innerHTML = '<tr>' + data.columns.map(c => `<th>${c}</th>`).join('') + '</tr>';
    tbody.innerHTML = data.rows.map((row, ri) =>
        '<tr>' + row.map((cell, ci) => `<td contenteditable="true" data-r="${ri}" data-c="${ci}">${cell ?? ''}</td>`).join('') + '</tr>'
    ).join('');
    tbody.querySelectorAll('td').forEach(td => {
        td.addEventListener('input', function () {
            clearTimeout(csvEditTimer);
            const r = +this.dataset.r, c = +this.dataset.c, v = this.textContent.trim();
            csvEditTimer = setTimeout(() => { csvRows[r][c] = v; }, 200);
        });
    });
}

function tableToCsvText() {
    if (!csvColumns.length) return '';
    return csvColumns.join(',') + '\n' + csvRows.map(row => row.map(c => {
        const s = String(c ?? ''); return s.includes(',') || s.includes('"') ? `"${s.replace(/"/g, '""')}"` : s;
    }).join(',')).join('\n');
}

function downloadCsv() {
    const csv = tableToCsvText(); if (!csv) return;
    const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
    a.download = `reelio_${currentSessionId || 'sim'}.csv`; a.click();
}

async function repredictFromTable() {
    const csv = tableToCsvText(); if (!csv) return;
    const info = qs('#csv-info'); info.textContent = '🔄 Re-running...';
    try {
        const resp = await fetch('/api/repredict', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ csv_text: csv, scenario: qs('#scenario')?.value || 'custom' }) });
        if (!resp.ok) throw new Error(await resp.text());
        const data = await resp.json();
        renderStChart(data.chart_data.sessions, data.chart_data.s_t);
        renderReelsChart(data.chart_data.sessions, data.chart_data.reels_per_session);
        info.textContent = `✅ ${data.sessions_scored} sessions. Confidence: ${data.model_confidence?.toFixed(3)}`;
        qs('.tab-btn').click();
    } catch (e) { info.textContent = '❌ ' + (e.message || '').substring(0, 200); }
}
