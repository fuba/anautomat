Unify the app and floating-menu goal controls using one shared component. Both now show the same GOAL field, microphone, primary play/pause button, stop button and session status.

- Play starts a new goal, resumes a paused goal, or continues after the step limit.
- While running, the same primary button becomes Pause.
- Stop ends the session. Goal editing and voice input unlock after stopping, so Resume always refers to the displayed goal.
- Voice input is available in both surfaces; returning from voice input in the app no longer opens the floating menu.
- Sign-in cancellation is separate from ending a goal.

Also guard overlay creation before accessibility-service connection and preserve menu visibility during configuration changes. Tests exercise shared controls across start, pause, resume, budget continuation and stop. The APK uses the existing release signing key for in-place updates.
