import numpy as np

def viterbi(A, pi, B):
    """
    Finds the most likely sequence of hidden states given the observations.
    
    A: Transition matrix (2, 2)
    pi: Initial state distribution (2,)
    B: Emission probabilities (2, T)
    
    Returns:
    path: (T,) array of most likely states (0 or 1)
    """
    T = B.shape[1]
    
    # Initialize the dynamic programming tables
    delta = np.zeros((2, T))
    psi = np.zeros((2, T), dtype=int)
    
    # We use log probabilities to avoid underflow
    log_A = np.log(np.maximum(A, 1e-15))
    log_pi = np.log(np.maximum(pi, 1e-15))
    log_B = np.log(np.maximum(B, 1e-15))
    
    # Initialization
    delta[:, 0] = log_pi + log_B[:, 0]
    
    # Recursion
    for t in range(1, T):
        for j in range(2):
            # Calculate the prob of arriving at state j at time t from state i
            transitions = delta[:, t-1] + log_A[:, j]
            # Maximize over i
            delta[j, t] = np.max(transitions) + log_B[j, t]
            psi[j, t] = np.argmax(transitions)
            
    # Termination and Path Backtracking
    path = np.zeros(T, dtype=int)
    path[T-1] = np.argmax(delta[:, T-1])
    
    for t in range(T-2, -1, -1):
        path[t] = psi[path[t+1], t+1]
        
    return path
