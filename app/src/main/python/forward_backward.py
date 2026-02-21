import numpy as np

def compute_emission_probabilities(obs_continue, obs_log_dwell, p, mu, sigma):
    """
    Computes the emission probabilities B for all states and all time steps in a session.
    obs_continue: array of shape (T,)
    obs_log_dwell: array of shape (T,)
    p: array of shape (2,) [P(continue | State=0), P(continue | State=1)]
    mu: array of shape (2,)
    sigma: array of shape (2,)
    
    Returns: B of shape (2, T)
    """
    T = len(obs_continue)
    B = np.zeros((2, T))
    
    for state in [0, 1]:
        # Bernoulli for continue
        p_c = p[state] if obs_continue[0] == 1 else (1.0 - p[state])
        # Gaussian for log_dwell
        p_d = (1.0 / (np.sqrt(2 * np.pi) * sigma[state])) * np.exp(-0.5 * ((obs_log_dwell[0] - mu[state]) / sigma[state])**2)
        
        # We process each timestep
        for t in range(T):
            b_c = p[state] if obs_continue[t] == 1 else (1.0 - p[state])
            b_d = (1.0 / (np.sqrt(2 * np.pi) * sigma[state])) * np.exp(-0.5 * ((obs_log_dwell[t] - mu[state]) / sigma[state])**2)
            B[state, t] = b_c * b_d
            
    return B

def forward_backward(A, pi, B):
    """
    Computes the forward (alpha) and backward (beta) probabilities with scaling to prevent underflow.
    
    A: Transition matrix (2, 2)
    pi: Initial state distribution (2,)
    B: Emission probabilities (2, T)
    
    Returns:
    alpha: (2, T)
    beta: (2, T)
    c: (T,) scale factors
    gamma: (2, T)
    xi: (T-1, 2, 2)
    log_likelihood: float
    """
    T = B.shape[1]
    
    alpha = np.zeros((2, T))
    beta = np.zeros((2, T))
    c = np.zeros(T)
    
    # Forward Pass
    alpha[:, 0] = pi * B[:, 0]
    c[0] = 1.0 / np.sum(alpha[:, 0]) if np.sum(alpha[:, 0]) > 0 else 1.0
    alpha[:, 0] = alpha[:, 0] * c[0]
    
    for t in range(1, T):
        alpha[:, t] = np.dot(alpha[:, t-1], A) * B[:, t]
        c[t] = 1.0 / np.sum(alpha[:, t]) if np.sum(alpha[:, t]) > 0 else 1.0
        alpha[:, t] = alpha[:, t] * c[t]
        
    # Backward Pass
    beta[:, T-1] = 1.0 * c[T-1]
    for t in range(T-2, -1, -1):
        beta[:, t] = np.dot(A, B[:, t+1] * beta[:, t+1]) * c[t]
        
    # Calculate Gamma
    gamma = np.zeros((2, T))
    for t in range(T):
        denom = np.sum(alpha[:, t] * beta[:, t])
        gamma[:, t] = (alpha[:, t] * beta[:, t]) / denom if denom > 0 else 0.5
        
    # Calculate Xi
    xi = np.zeros((T-1, 2, 2))
    for t in range(T-1):
        denom = np.sum(np.outer(alpha[:, t], B[:, t+1] * beta[:, t+1]) * A)
        if denom > 0:
            for i in range(2):
                for j in range(2):
                    xi[t, i, j] = (alpha[i, t] * A[i, j] * B[j, t+1] * beta[j, t+1]) / denom
                    
    log_likelihood = -np.sum(np.log(c))
    
    return alpha, beta, c, gamma, xi, log_likelihood
