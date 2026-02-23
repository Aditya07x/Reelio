import numpy as np
import pandas as pd
import json
import os
import argparse
from forward_backward import compute_emission_probabilities, forward_backward
from viterbi import viterbi

def initialize_parameters():
    """
    Initializes HMM parameters enforcing inertia/doomscrolling domain assumptions.
    
    Returns:
    A: Transition Matrix (2,2)
    pi: Initial state (2,)
    p: Bernoulli probability of 'Continue' given state (2,)
    mu: Gaussian mean for log(dwellSec) given state (2,)
    sigma: Gaussian std for log(dwellSec) given state (2,)
    """
    # State 0 = Casual (Lower persistence, lower dwell, lower continue probability)
    # State 1 = Capture/Doomscrolling (Higher persistence, higher dwell, higher continue probability)
    
    A = np.array([
        [0.80, 0.20],  # P(0->0), P(0->1)
        [0.10, 0.90]   # P(1->0), P(1->1): Stronger capture inertia
    ])
    
    pi = np.array([0.9, 0.1])  # Assume sessions start in casual state usually
    
    # Bernoulli p for "Continue" emission. 
    # Doomscrolling implies continuation is almost guaranteed.
    p = np.array([0.6, 0.95]) 
    
    # Gaussian parameters for log(dwellSec). Will be updated by data. 
    # State 1 should ideally have a higher mean in the end.
    mu = np.array([1.5, 2.5]) 
    sigma = np.array([0.5, 0.5])
    
    return A, pi, p, mu, sigma

def _schema_skip(path, encoding):
    """Return 1 if the first line is a SCHEMA_VERSION metadata line, else 0."""
    try:
        with open(path, 'r', encoding=encoding) as f:
            first = f.readline().strip().lstrip('\ufeff')
        return 1 if first.startswith('SCHEMA_VERSION') else 0
    except Exception:
        return 0

def load_and_preprocess_data(csv_input):
    """
    Loads telemetry, normalizes dwellSec via log transform.
    Groups into a list of sessions.
    """
    import io
    if isinstance(csv_input, str) and '\n' in csv_input:
        df = pd.read_csv(io.StringIO(csv_input))
    else:
        try:
            df = pd.read_csv(csv_input, encoding='utf-8', skiprows=_schema_skip(csv_input, 'utf-8'))
        except UnicodeDecodeError:
            df = pd.read_csv(csv_input, encoding='utf-16', skiprows=_schema_skip(csv_input, 'utf-16'))
        
    # Required columns: SessionNum, ReelIndex, startTime, Continue, dwellSec, timePeriod, ...
    
    # 1. Sort chronologically
    df['StartTime'] = pd.to_datetime(df['StartTime'])
    if 'StartTime' in df.columns:
        df['date'] = df['StartTime'].dt.date.astype(str)
    df = df.sort_values(by=['SessionNum', 'StartTime'])
    
    # 2. Normalize Dwell (Add tiny epsilon to avoid log(0))
    df['log_dwell'] = np.log(np.maximum(df['DwellTime'], 1e-3))
    
    # 3. Format 'Continue' boolean strictly to 0 or 1.
    # We will enforce 'Continue' to be 1 for all reels except the very last reel in each Session.
    # This perfectly mirrors the actual user experience: they "continued" until they "stopped".
    df['Continue'] = 1
    # Find the indices of the last row in each session group and set Continue = 0
    last_indices = df.groupby('SessionNum').tail(1).index
    df.loc[last_indices, 'Continue'] = 0
    
    sessions = []
    for session_id, group in df.groupby('SessionNum'):
        continue_vals = group['Continue'].values if 'Continue' in group.columns else np.ones(len(group))
        sessions.append({
            'session_id': session_id,
            'n_reels': len(group),
            'continue': continue_vals,
            'log_dwell': group['log_dwell'].values,
            'max_streak': group['SessionLength'].max() if 'SessionLength' in group.columns else 0,
            'avg_dwell': group['DwellTime'].mean(),
            'timePeriod': group['TimePeriod'].iloc[0] if 'TimePeriod' in group.columns else 'Unknown',
            'date': group['date'].iloc[0] if 'date' in group.columns else 'Unknown'
        })
        
    return sessions

def m_step(sessions, gammas, xis, p_old):
    """
    Updates the parameters based on the expected counts from the E-step (Baum-Welch).
    """
    A_new = np.zeros((2, 2))
    pi_new = np.zeros(2)
    p_numerator = np.zeros(2)
    p_denominator = np.zeros(2)
    mu_numerator = np.zeros(2)
    sigma_numerator = np.zeros(2)
    state_denominator = np.zeros(2)
    
    num_sessions = len(sessions)
    
    for s_idx, session in enumerate(sessions):
        gamma = gammas[s_idx]
        xi = xis[s_idx]
        T = session['n_reels']
        
        # Initial State
        pi_new += gamma[:, 0]
        
        # Transitions
        if T > 1:
            for i in range(2):
                for j in range(2):
                    A_new[i, j] += np.sum(xi[:, i, j])
                    
        # Emissions
        obs_continue = session['continue']
        obs_log_dwell = session['log_dwell']
        
        for state in [0, 1]:
            state_gamma_sum = np.sum(gamma[state, :])
            state_denominator[state] += state_gamma_sum
            
            # Bernoulli
            p_numerator[state] += np.sum(gamma[state, :] * obs_continue)
            
            # Gaussian
            mu_numerator[state] += np.sum(gamma[state, :] * obs_log_dwell)

    # Normalize updates with Laplace continuous smoothing to prevent NaN collapse
    pi_new = (pi_new + 1e-4) / (num_sessions + 2e-4)
    
    for i in range(2):
        row_sum = np.sum(A_new[i, :])
        if row_sum > 0:
            A_new[i, :] = (A_new[i, :] + 1e-4) / (row_sum + 2e-4)
        else:
            A_new[i, :] = [0.5, 0.5] # fallback
            
    p_new = np.zeros(2)
    mu_new = np.zeros(2)
    sigma_new = np.zeros(2)
    
    for state in [0, 1]:
        if state_denominator[state] > 1e-4:
            # Smooth Bernoulli to prevent 0 or 1 certainty collapse
            p_new[state] = (p_numerator[state] + 1e-2) / (state_denominator[state] + 2e-2)
            mu_new[state] = mu_numerator[state] / state_denominator[state]
        else:
            p_new[state] = p_old[state]
            mu_new[state] = 0.0 # Will be smoothed out
            
    # Calculate Variance
    for s_idx, session in enumerate(sessions):
        gamma = gammas[s_idx]
        obs_log_dwell = session['log_dwell']
        for state in [0, 1]:
            diff = obs_log_dwell - mu_new[state]
            sigma_numerator[state] += np.sum(gamma[state, :] * (diff ** 2))
            
    for state in [0, 1]:
        if state_denominator[state] > 1e-4:
            # Add base variance to prevent collapse to a Dirac delta
            sigma_new[state] = np.sqrt(sigma_numerator[state] / state_denominator[state]) + 1e-2
            sigma_new[state] = max(sigma_new[state], 0.1)
        else:
            sigma_new[state] = 0.5 # Safe fallback
            
    return A_new, pi_new, p_new, mu_new, sigma_new

def train_hmm(sessions, max_iter=15, tol=1e-3):
    A, pi, p, mu, sigma = initialize_parameters()
    
    prev_log_likelihood = -np.inf
    
    for iteration in range(max_iter):
        gammas = []
        xis = []
        total_log_likelihood = 0.0
        
        # E-Step: Calculate probabilities for each session
        for session in sessions:
            B = compute_emission_probabilities(session['continue'], session['log_dwell'], p, mu, sigma)
            # Ensure minimum viable likelihoods
            B = np.maximum(B, 1e-15)
            
            alpha, beta, c, gamma, xi, ll = forward_backward(A, pi, B)
            
            gammas.append(gamma)
            xis.append(xi)
            total_log_likelihood += ll
            
        print(f"Iteration {iteration+1}, Log-Likelihood: {total_log_likelihood:.2f}")
        
        # Convergence check
        if np.abs(total_log_likelihood - prev_log_likelihood) < tol:
            print("Converged.")
            break
            
        prev_log_likelihood = total_log_likelihood
        
        # M-Step: Update global parameters
        A, pi, p, mu, sigma = m_step(sessions, gammas, xis, p)
        
    return A, pi, p, mu, sigma, gammas

def decode_sessions(sessions, A, pi, p, mu, sigma, gammas):
    results = []
    
    for s_idx, session in enumerate(sessions):
        B = compute_emission_probabilities(session['continue'], session['log_dwell'], p, mu, sigma)
        B = np.maximum(B, 1e-15)
        
        path = viterbi(A, pi, B)
        gamma = gammas[s_idx]
        
        # S_t is the mean probability of being in State 1 across all reels in the session
        mean_gamma_1 = np.mean(gamma[1, :])
        dominant_state = 1 if mean_gamma_1 > 0.5 else 0
        
        # Calculate contiguous capture episodes
        capture_episodes = 0
        current_episode = 0
        for state in path:
            if state == 1:
                current_episode += 1
            else:
                if current_episode > 0:
                    capture_episodes += current_episode
                current_episode = 0
        if current_episode > 0:
            capture_episodes += current_episode
            
        avg_capture_length = capture_episodes / np.sum(np.diff(np.concatenate(([0], path, [0]))) == 1) if np.sum(path) > 0 else 0
        
        results.append({
            "sessionNum": int(session['session_id']),
            "S_t": float(mean_gamma_1),
            "dominantState": dominant_state,
            "nReels": int(session['n_reels']),
            "avgDwell": float(session['avg_dwell']),
            "maxStreak": int(session['max_streak']),
            "timePeriod": session['timePeriod'],
            "date": session.get('date', 'Unknown'),
            "avgCaptureLength": float(avg_capture_length)
        })
        
    return results

def run_hmm_from_string(csv_data):
    if not csv_data or not csv_data.strip():
        return json.dumps({"error": "Empty CSV data", "sessions": []})
        
    sessions = load_and_preprocess_data(csv_data)
    
    # Performance Optimization: Bound to the most recent sessions for mobile CPU
    if len(sessions) > 50:
        sessions = sessions[-50:]
    
    if len(sessions) > 0:
        A, pi, p, mu, sigma, gammas = train_hmm(sessions)
        decoded_sessions = decode_sessions(sessions, A, pi, p, mu, sigma, gammas)
        
        regime_stability = 1.0 / (1.0 - A[1, 1]) if (1.0 - A[1, 1]) > 0 else 999.0
        p_capture_timeline = np.concatenate([g[1, :] for g in gammas]).round(3).tolist()
        
        output_payload = {
            "model_parameters": {
                "transition_matrix": A.tolist(),
                "initial_state": pi.tolist(),
                "bernoulli_p": p.tolist(),
                "gaussian_mu": mu.tolist(),
                "gaussian_sigma": sigma.tolist(),
                "regime_stability_score": regime_stability
            },
            "sessions": decoded_sessions,
            "timeline": {
                "p_capture": p_capture_timeline
            }
        }
        return json.dumps(output_payload)
    else:
        return json.dumps({"error": "No sessions found", "sessions": []})

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run Baum-Welch HMM on Telemetry")
    parser.add_argument('--input', type=str, required=True, help="Path to input CSV")
    parser.add_argument('--output', type=str, required=True, help="Path to output JSON")
    args = parser.parse_args()
    
    sessions = load_and_preprocess_data(args.input)
    print(f"Loaded {len(sessions)} sessions.")
    
    if len(sessions) > 0:
        A, pi, p, mu, sigma, gammas = train_hmm(sessions)
        
        decoded_sessions = decode_sessions(sessions, A, pi, p, mu, sigma, gammas)
        
        output_payload = {
            "model_parameters": {
                "transition_matrix": A.tolist(),
                "initial_state": pi.tolist(),
                "bernoulli_p": p.tolist(),
                "gaussian_mu": mu.tolist(),
                "gaussian_sigma": sigma.tolist()
            },
            "sessions": decoded_sessions,
            "timeline": {
                "p_capture": np.concatenate([g[1, :] for g in gammas]).round(3).tolist()
            }
        }
        
        with open(args.output, 'w') as f:
            json.dump(output_payload, f, indent=4)
            
        print(f"HMM Inference complete. Results saved to {args.output}")
    else:
        print("No sessions found to model.")
