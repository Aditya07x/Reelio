import urllib.request, json

API = 'http://127.0.0.1:8000'

print("=== SSE Stream Test (15 sessions) ===")
payload = {"scenario": "worsening", "sessions": 15, "seed": 42, "binge_probability": 10.0, "interruption_rate": 10.0}
req = urllib.request.Request(f'{API}/api/simulate-stream', data=json.dumps(payload).encode(), headers={'Content-Type': 'application/json'})
resp = urllib.request.urlopen(req, timeout=300)
events = resp.read().decode()

lines = [l for l in events.split('\n') if l.startswith('data: ')]
session_events = []
has_complete = False
for l in lines:
    try:
        e = json.loads(l[6:])
        if e['type'] == 'session' and not e.get('skipped'):
            session_events.append(e)
        elif e['type'] == 'complete':
            has_complete = True
    except: pass

print(f"  Session events: {len(session_events)}")
print(f"  Complete event: {has_complete}")

if session_events:
    ev = session_events[-1]  # Last session
    print(f"\n--- Last Session (#{ev['session']}) ---")
    
    # Verify all pillar data is present
    pillars = [
        ('features', ['n_reels', 'mean_dwell', 'log_dwell', 'mean_speed', 'log_speed', 'exit_rate', 'rewatch_rate', 'entropy', 'trend', 'gap_min', 'fatigue_risk', 'mood_risk', 'stress_flag']),
        ('doom_scorer', ['doom_score', 'label', 'components']),
        ('baseline', ['dwell_mu', 'dwell_sig', 'speed_mu', 'session_len_mu', 'n_sessions_seen']),
        ('emission', ['mu_dwell_casual', 'mu_dwell_doom', 'sigma_dwell_casual', 'sigma_dwell_doom', 'p_rewatch_casual', 'p_rewatch_doom', 'p_exit_casual', 'p_exit_doom', 'rho_dwell_speed_casual', 'rho_dwell_speed_doom']),
        ('transition', ['A_casual_casual', 'A_casual_doom', 'A_doom_casual', 'A_doom_doom']),
        ('ctmc', ['q_01_pull', 'q_10_escape', 'A_gap']),
        ('hazard', ['h_doom', 'h_casual']),
        ('contextual_pi', ['pi_casual', 'pi_doom']),
        ('feature_weights', ['log_dwell', 'log_speed', 'rhythm_dissociation', 'rewatch_flag', 'exit_flag', 'swipe_incomplete']),
        ('gamma', ['mean_doom', 'max_doom', 'min_doom', 'first_reel_doom', 'last_reel_doom', 'doom_trajectory']),
        ('supervised', ['supervised_doom', 'label_confidence', 'running_disagreement', 'labeled_sessions_total']),
        ('regime', ['alert', 'alert_duration', 'doom_history_len']),
        ('confidence', ['overall', 'volume', 'separation', 'stability', 'supervision']),
        ('regret_calibration', ['n_samples', 'mean_predicted', 'mean_actual', 'systematic_bias', 'calibrated']),
    ]
    
    all_ok = True
    for pillar_name, expected_keys in pillars:
        if pillar_name not in ev:
            print(f"  ❌ Missing pillar: {pillar_name}")
            all_ok = False
            continue
        data = ev[pillar_name]
        missing = [k for k in expected_keys if k not in data]
        if missing:
            print(f"  ❌ {pillar_name}: missing keys {missing}")
            all_ok = False
        else:
            sample = {k: data[k] for k in expected_keys[:3]}
            print(f"  ✅ {pillar_name}: {sample}")
    
    # Also check top-level
    for k in ['S_t', 'raw_hmm_doom']:
        if k not in ev:
            print(f"  ❌ Missing top-level: {k}")
            all_ok = False
        else:
            print(f"  ✅ {k}: {ev[k]}")
    
    print(f"\n{'ALL PILLARS PRESENT ✅' if all_ok else '❌ Some pillars missing'}")
else:
    print("❌ No session events received")
