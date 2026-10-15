Retry temporary ChatGPT response failures without losing the active goal. Inference gets up to two automatic retries with exponential backoff, jitter and Retry-After support. Each retry observes the current screen; partial actions are discarded and device actions are never replayed by this mechanism.

When retries are exhausted, or an error needs user action, the goal pauses. Press the play icon in the control center or floating menu to resume with the same goal, action history, token meter and remaining step budget. Pause cancels pending retries. Resume is available while the current accessibility-service session stays alive.

Includes regression checks for bounded retries, error classification, server delays, manual resume and cancellation of stale callbacks. The APK uses the existing release signing key, so it can update the installed release in place.
