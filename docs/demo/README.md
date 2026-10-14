# Fractal demo

A real anautomat run on the dedicated Android 16 emulator, using ChatGPT sign-in and phone-side screen observations and accessibility actions.

## Goal

See [goal.txt](goal.txt) for the exact goal. It authorizes installing Python from Termux repositories, creating files in `~/fractal-lab`, and starting a loopback-only server. The agent created the [HTML/JavaScript app](generated-index.html), verified the rendered fractal in Chrome, zoomed twice, and reset it. Completion was reported at step 32.

## Setup and recording

- anautomat, Chrome, and official Termux v0.118.3 were installed before the take. The Termux APK was checked against the release checksum. ChatGPT sign-in and model selection were already complete.
- A separate smoke test checked terminal input with `printf ANAUTOMAT_IME_OK`; that harmless marker remains in terminal scrollback. No fractal app or Python installation was prepared in advance.
- The goal draft was loaded before recording. The run was started by tapping **Run goal**. No PC control action was sent during goal execution. The floating menu was opened after completion to show the result.
- The PC recorded the emulator through ADB. The full recording joins two screenrecord segments with a short capture gap at the boundary; it includes setup idle time and the completion menu. This is a single goal execution, not a montage of retries.
- The edited MP4 starts with a result preview, then plays the recorded execution at labeled 12x and 3x speeds. The GIF doubles that edit's playback speed. No generated frames or substituted terminal output are used.
- After completion, the generated HTML was fetched through a temporary ADB port forward for inspection. That forward did not participate in inference or goal actions and was removed immediately.

## Assets

[Edited video](https://github.com/fuba/anautomat/releases/download/v0.1.2/anautomat-fractal-demo.mp4) · [Full recording](https://github.com/fuba/anautomat/releases/download/v0.1.2/anautomat-fractal-full.mp4)

The server binds to `127.0.0.1:8000`. Rendering and interaction run locally in Chrome. Reproducing the agent run requires internet access for ChatGPT inference and the initial Termux package download. Terminal input requires Android 13 or later. Results and step counts vary by model and device.
