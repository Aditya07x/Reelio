const { useState, useEffect, useMemo } = React;
const {
    AreaChart, Area,
    XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} = window.Recharts;

const COLORS = {
    cyan: '#22D3EE',
    amber: '#FBBF24',
    red: '#EF4444',
    green: '#10B981',
    slate_800: '#1E293B',
    slate_700: '#334155'
};

const CognitiveStabilityScore = ({ sessions, A11 }) => {
    const meanState = sessions.length > 0 ? sessions.reduce((acc, s) => acc + s.S_t, 0) / sessions.length : 0;
    const recoveryPower = 1.0 - A11;
    const score = Math.max(0, Math.min(100, Math.round((1.0 - meanState) * 80 + recoveryPower * 20)));

    let colorClass = "text-green-400";
    if (score < 40) colorClass = "text-red-400";
    else if (score < 70) colorClass = "text-amber-400";

    return (
        <div className="glass-card p-6 rounded-2xl flex items-center justify-between border-b-4 border-slate-700 hover:border-cyan-400">
            <div>
                <h2 className="text-xs font-semibold text-slate-400 uppercase tracking-widest mb-1">Index</h2>
                <div className="text-lg font-bold text-slate-200">Cognitive Stability</div>
            </div>
            <div className={`text-5xl font-black tracking-tighter ${colorClass}`}>{score}</div>
        </div>
    );
};

const TransitionMatrixVisualizer = ({ matrix }) => {
    if (!matrix || matrix.length < 2) return null;
    return (
        <div className="glass-card p-5 rounded-2xl h-full flex flex-col justify-between">
            <h2 className="text-sm font-semibold text-slate-300 uppercase tracking-wider mb-4">State Dynamics</h2>
            <div className="grid grid-cols-2 gap-2 flex-1">
                <div className="bg-slate-800/80 rounded-xl p-3 flex flex-col justify-center items-center relative overflow-hidden group">
                    <span className="text-xs text-slate-400 uppercase tracking-wider mb-1">Casual → Casual</span>
                    <span className="text-2xl font-bold text-cyan-400">{(matrix[0][0] * 100).toFixed(1)}%</span>
                </div>
                <div className="bg-slate-800/80 rounded-xl p-3 flex flex-col justify-center items-center relative overflow-hidden group">
                    <span className="text-xs text-slate-400 uppercase tracking-wider mb-1">Casual → Capture</span>
                    <span className="text-2xl font-bold text-amber-400">{(matrix[0][1] * 100).toFixed(1)}%</span>
                </div>
                <div className="bg-slate-800/80 rounded-xl p-3 flex flex-col justify-center items-center relative overflow-hidden group">
                    <span className="text-xs text-slate-400 uppercase tracking-wider mb-1">Capture → Casual</span>
                    <span className="text-2xl font-bold text-green-400">{(matrix[1][0] * 100).toFixed(1)}%</span>
                </div>
                <div className="bg-slate-800/80 rounded-xl p-3 flex flex-col justify-center items-center relative overflow-hidden group border border-red-500/20">
                    <span className="text-xs text-red-300 uppercase tracking-wider mb-1">Capture → Capture</span>
                    <span className="text-2xl font-bold text-red-400">{(matrix[1][1] * 100).toFixed(1)}%</span>
                </div>
            </div>
        </div>
    );
};

const HeatmapCalendar = ({ sessions }) => {
    const dailyMap = {};
    sessions.forEach(s => {
        if (!dailyMap[s.date]) dailyMap[s.date] = { sum: 0, count: 0 };
        dailyMap[s.date].sum += s.S_t;
        dailyMap[s.date].count += 1;
    });

    const dates = Object.keys(dailyMap).sort().slice(-14);

    return (
        <div className="glass-card p-5 rounded-2xl h-full">
            <h2 className="text-sm font-semibold text-slate-300 uppercase tracking-wider mb-4">Risk Heatmap (14 Days)</h2>
            <div className="flex flex-wrap gap-2">
                {dates.length === 0 ? <p className="text-slate-500 text-sm">No daily data available</p> : null}
                {dates.map(date => {
                    const avgSt = dailyMap[date].sum / dailyMap[date].count;
                    let bg = "bg-slate-800";
                    if (avgSt > 0.6) bg = "bg-red-500/80 shadow-[0_0_10px_rgba(239,68,68,0.5)]";
                    else if (avgSt > 0.4) bg = "bg-amber-500/80";
                    else if (avgSt > 0.2) bg = "bg-cyan-600/60";
                    else bg = "bg-cyan-900/40";

                    return (
                        <div key={date} className="flex flex-col items-center group relative cursor-pointer">
                            <div className={`w-10 h-10 rounded-lg ${bg} border border-slate-700 transition-transform transform group-hover:scale-110`}></div>
                            <span className="text-[10px] text-slate-500 mt-1">{date.slice(-5)}</span>
                            <div className="absolute bottom-full mb-2 bg-slate-800 text-xs px-2 py-1 rounded opacity-0 group-hover:opacity-100 pointer-events-none z-10 whitespace-nowrap shadow-xl border border-slate-700">
                                {date}: S_t = {(avgSt).toFixed(2)}
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
};

const TimelinePlayback = ({ timeline }) => {
    const [scrubIndex, setScrubIndex] = useState(0);
    if (!timeline || !timeline.p_capture) return null;

    const maxIdx = timeline.p_capture.length - 1;
    const chartData = timeline.p_capture.map((p, i) => ({
        idx: i, p: p, RiskZone: 0.5
    })).slice(Math.max(0, scrubIndex - 20), Math.min(maxIdx + 1, scrubIndex + 20));

    const currentState = timeline.p_capture[scrubIndex] > 0.5 ? "Captured" : "Casual";
    const stateColor = timeline.p_capture[scrubIndex] > 0.5 ? "text-red-400" : "text-cyan-400";

    return (
        <div className="glass-card p-5 rounded-2xl flex flex-col w-full h-96">
            <div className="flex justify-between items-end mb-4">
                <div>
                    <h2 className="text-sm font-semibold text-slate-300 uppercase tracking-wider">Behavior Timeline Playback</h2>
                    <p className="text-xs text-slate-400 font-mono mt-1">Reel Explorer • Zoom Window Active</p>
                </div>
                <div className="text-right">
                    <div className="text-xs uppercase text-slate-500 tracking-wider">Reel {scrubIndex} State</div>
                    <div className={`text-xl font-bold ${stateColor}`}>{currentState} ({(timeline.p_capture[scrubIndex] * 100).toFixed(0)}%)</div>
                </div>
            </div>

            <div className="flex-1 w-full min-h-0 relative">
                <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                        <defs>
                            <linearGradient id="colorCap" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="5%" stopColor={COLORS.cyan} stopOpacity={0.4} />
                                <stop offset="95%" stopColor={COLORS.cyan} stopOpacity={0.0} />
                            </linearGradient>
                            <linearGradient id="colorThreat" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="5%" stopColor={COLORS.red} stopOpacity={0.15} />
                                <stop offset="95%" stopColor={COLORS.red} stopOpacity={0} />
                            </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="3 3" stroke="#334155" vertical={false} />
                        <XAxis dataKey="idx" stroke="#94A3B8" tick={{ fontSize: 10 }} />
                        <YAxis stroke="#94A3B8" domain={[0, 1]} tick={{ fontSize: 10 }} />
                        <Area type="stepBefore" dataKey="RiskZone" stroke="none" fill="url(#colorThreat)" activeDot={false} />
                        <Area type="monotone" dataKey="p" stroke={COLORS.cyan} strokeWidth={3} fill="url(#colorCap)" represents="Capture Prob" />
                    </AreaChart>
                </ResponsiveContainer>
            </div>

            <div className="mt-4 flex items-center gap-4">
                <span className="text-xs text-slate-500 font-mono">0</span>
                <input type="range" min="0" max={maxIdx} value={scrubIndex} onChange={(e) => setScrubIndex(parseInt(e.target.value))}
                    className="flex-1 h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-cyan-400" />
                <span className="text-xs text-slate-500 font-mono">{maxIdx}</span>
            </div>
        </div>
    );
};

const TriggerInsights = ({ sessions, A11 }) => {
    if (!sessions || sessions.length === 0) return null;

    const periodRisk = {};
    sessions.forEach(s => {
        if (!periodRisk[s.timePeriod]) periodRisk[s.timePeriod] = { sum: 0, c: 0 };
        periodRisk[s.timePeriod].sum += s.S_t;
        periodRisk[s.timePeriod].c += 1;
    });

    let worstPeriod = "Unknown", worstRisk = 0;
    Object.keys(periodRisk).forEach(k => {
        let avg = periodRisk[k].sum / periodRisk[k].c;
        if (avg > worstRisk) { worstRisk = avg; worstPeriod = k; }
    });

    const halfLife = (Math.log(2) / 0.5).toFixed(1);

    return (
        <div className="glass-card p-5 rounded-2xl space-y-4">
            <h2 className="text-sm font-semibold text-slate-300 uppercase tracking-wider mb-2">Automated Behavioral Insights</h2>

            <div className="bg-slate-800/60 p-4 rounded-xl border border-slate-700/50 flex gap-3 items-start">
                <div className="text-amber-400 mt-0.5">⚠️</div>
                <div>
                    <div className="text-sm font-bold text-slate-200">Peak Vulnerability: {worstPeriod}</div>
                    <div className="text-xs text-slate-400 mt-1">Your capture probability averages {(worstRisk * 100).toFixed(1)}% during {worstPeriod} sessions. Settings limits here is recommended.</div>
                </div>
            </div>

            <div className="bg-slate-800/60 p-4 rounded-xl border border-slate-700/50 flex gap-3 items-start">
                <div className="text-cyan-400 mt-0.5">🧠</div>
                <div>
                    <div className="text-sm font-bold text-slate-200">Cognitive Recovery Rate</div>
                    <div className="text-xs text-slate-400 mt-1">If you close the app during a doomscroll, it takes approximately <strong>{halfLife} hours</strong> for your brain's latent capture probability to reset to baseline.</div>
                </div>
            </div>

            <div className="bg-slate-800/60 p-4 rounded-xl border border-slate-700/50 flex gap-3 items-start">
                <div className="text-red-400 mt-0.5">⛓️</div>
                <div>
                    <div className="text-sm font-bold text-slate-200">Scroll Inertia</div>
                    <div className="text-xs text-slate-400 mt-1">Once trapped, there is a <strong>{(A11 * 100).toFixed(1)}%</strong> chance you will continue to the next reel purely out of state inertia, overriding conscious intent.</div>
                </div>
            </div>
        </div>
    );
};

const Dashboard = () => {
    const [rawData, setRawData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        const handleData = (parsed) => {
            console.log("React executing JSON payload", parsed);
            if (parsed.error) {
                setError(parsed.error);
            } else if (parsed.sessions && parsed.sessions.length > 0) {
                setRawData(parsed);
                setError(null);
            } else {
                setError("No sufficient data yet. Scroll a few more reels!");
            }
            setLoading(false);
        };

        window.reactDataCallback = handleData;
        if (window.injectedJsonData) {
            handleData(window.injectedJsonData);
        }

        const timer = setTimeout(() => {
            if (!window.injectedJsonData) {
                setLoading(false);
                setError("Waiting for Tracker injection... (Make sure accessibility service is active and you have scrolled Reels)");
            }
        }, 3000);
        return () => clearTimeout(timer);
    }, []);

    if (loading) return <div className="min-h-screen flex items-center justify-center"><div className="w-10 h-10 border-4 border-slate-700 border-t-cyan-400 rounded-full animate-spin"></div></div>;
    if (error) return <div className="min-h-screen flex items-center justify-center flex-col gap-4 text-slate-300">⚠️ {error}</div>;
    if (!rawData) return <div className="min-h-screen flex items-center justify-center">Initializing Engine...</div>;

    const A = rawData.model_parameters.transition_matrix;

    return (
        <div className="max-w-7xl mx-auto p-4 md:p-8 space-y-6 fade-in text-slate-200">
            <header className="mb-8">
                <h1 className="text-3xl md:text-4xl font-black bg-gradient-to-r from-white to-slate-500 bg-clip-text text-transparent tracking-tighter">Behavioral Dynamics</h1>
                <p className="text-cyan-400 font-mono text-xs uppercase tracking-[0.2em] mt-2">Continuous Latent State Engine (V2)</p>
            </header>

            <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
                <div className="lg:col-span-1 space-y-6">
                    <CognitiveStabilityScore sessions={rawData.sessions} A11={A[1][1]} />
                    <div className="grid grid-cols-2 lg:grid-cols-1 gap-4">
                        <div className="glass-card p-4 rounded-xl border border-slate-700/50">
                            <div className="text-2xl font-bold">{rawData.timeline?.p_capture?.length || 0}</div>
                            <div className="text-xs text-slate-400 uppercase tracking-widest mt-1">Total Reels Analyzed</div>
                        </div>
                        <div className="glass-card p-4 rounded-xl border border-slate-700/50">
                            <div className="text-2xl font-bold">{(rawData.model_parameters.regime_stability_score || 0).toFixed(1)}</div>
                            <div className="text-xs text-slate-400 uppercase tracking-widest mt-1">Expected Capture Dur. (Reels)</div>
                        </div>
                    </div>
                </div>

                <div className="lg:col-span-3 space-y-6 flex flex-col">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6 h-64">
                        <div className="h-full"><TransitionMatrixVisualizer matrix={A} /></div>
                        <div className="h-full"><HeatmapCalendar sessions={rawData.sessions} /></div>
                    </div>
                </div>
            </div>

            <div className="w-full">
                <TimelinePlayback timeline={rawData.timeline} />
            </div>

            <div className="w-full">
                <TriggerInsights sessions={rawData.sessions} A11={A[1][1]} />
            </div>

            <footer className="pt-8 pb-4 text-center font-mono text-[10px] text-slate-600 uppercase tracking-widest">
                Doomscroll Behavioral Research V2 • Time-Decayed HMM Pipeline
            </footer>

            <style>{`
            .fade-in { animation: fadeIn 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards; }
            @keyframes fadeIn { from { opacity: 0; transform: scale(0.98) translateY(10px); } to { opacity: 1; transform: scale(1) translateY(0); } }
            input[type=range]::-webkit-slider-thumb { -webkit-appearance: none; height: 16px; width: 16px; border-radius: 50%; background: #22D3EE; cursor: pointer; box-shadow: 0 0 10px rgba(34,211,238,0.5); border: 2px solid #0F172A; }
            input[type=range] { margin: 8px 0; }
            `}</style>
        </div>
    );
};

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(<Dashboard />);
