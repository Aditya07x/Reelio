import json

with open('out_reelio.json', 'r') as f:
    data = json.load(f)

sessions = data['sessions']
A = data['model_parameters']['transition_matrix']

# from index.html:
mr = sessions[-1]
allSt = [s['S_t'] for s in sessions]
avgSt = sum(allSt) / len(sessions)

totalDwellSec = sum(s.get('avgDwell', 0) * s.get('nReels', 0) for s in sessions)
overallAvgDwell = sum(s.get('avgDwell', 0) for s in sessions) / max(len(sessions), 1)

sessionReelsSum = sum(s.get('nReels', 0) for s in sessions)
perReelCapture = data['timeline']['p_capture']
totalReelsAll = sessionReelsSum if sessionReelsSum > 0 else len(perReelCapture)

escapeRate = max(0.01, A[1][0])
avgNReels = totalReelsAll / max(len(sessions), 1)
pLostTrack = len([s for s in sessions if s['dominantState'] == 1]) / max(len(sessions), 1)

currentSessionDwellSec = mr.get('avgDwell', 0) * mr.get('nReels', 0)
currentSessionDuration = f"{(currentSessionDwellSec/60):.1f}m" if currentSessionDwellSec > 60 else f"{round(currentSessionDwellSec)}s"

doom_score = mr['S_t']
regime_stability = 1.0 / (escapeRate + 0.001)

model_confidence = data.get('model_confidence', min(0.95, len(sessions) / 20))

print("Dashboard Values:")
print(f"Current Intervention (Doom Score): {round(doom_score * 100)}%")
print(f"Reels: {totalReelsAll}")
print(f"Time (Current Session): {currentSessionDuration}")
print(f"Avg Dwell (Overall): {overallAvgDwell:.1f}s")
print(f"Sessions: {len(sessions)}")
print(f"Total Dwell Min: {(totalDwellSec/60):.1f}m" if totalDwellSec > 60 else f"{round(totalDwellSec)}s")
print(f"Model Confidence: {round(model_confidence * 100)}%")
print(f"Cognitive Stability (1 - S_t): {round((1 - doom_score) * 100)}%")

# Print doom components
doomComponents = {
    'length': min(1, avgNReels / 80),
    'volitional_conflict': pLostTrack,
    'rapid_reentry': min(1, A[0][1] * 3),
    'automaticity': min(1, A[1][1]),
    'dwell_collapse': min(1, max(0, 1 - (overallAvgDwell / 15))),
    'rewatch': min(1, mr['S_t']),
    'environment': 0.8 if mr['timePeriod'] in ["Late Night", "Night"] else (0.55 if mr['timePeriod'] == "Evening" else 0.3)
}
print(f"\nDoom Components:")
for k, v in doomComponents.items():
    print(f"  {k}: {round(v * 100)}%")
