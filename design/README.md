# anautomat identity

The replacement mark represents an observed screen: four open corners, an action point, and a short movement trace. It uses no initials, external fonts or third-party logo assets.

- Open corners suggest a screen without a heavy enclosing box.
- A 7-unit stroke preserves small-size legibility.
- The offset action point and trace suggest observation followed by action.
- The mark fits within the Android adaptive-icon safe circle.
- White (#FFFFFF) against black (#161616) provides a quiet, high-contrast identity.
- The floating control keeps a translucent circular surface; app launchers use an opaque adaptive background.

`android-use-mark.svg` is the mark master. `android-use-icon.svg` is its standalone app-icon presentation. Both are editable vectors without external fonts. Android uses generated VectorDrawable and adaptive-icon resources. The floating control draws the same VectorDrawable.

After editing the master SVG, run `scripts/sync-icon.ps1` to synchronize both the Android vector and standalone SVG before building. The Android system chooses the final launcher shape, including circles and rounded squares.

## Application and floating menu

Both surfaces use the shared `Ui.java` components: off-white background, black text, neutral-grey supporting text and a black primary action. Rounded input wells sit inside rounded cards with subtle borders. Medium-weight sans-serif headings establish hierarchy; small spaced labels organize Goal, connection and session details. Buttons have a 48dp minimum height and retain disabled and touch feedback states.

The app leads with the Goal composer, followed by account/model settings and activity. The floating menu uses the same composer and buttons, plus a compact session card and paired pause/resume actions. It scrolls when available screen space is limited.

## Copy constraints

Use text only for functional labels, actions, state, and necessary explanation. The composer label is GOAL. Do not add decorative text, rhetorical headings, taglines, YOUR GOAL, or DEVICE AGENT.

The interface palette is strictly monochrome. Use whitespace, type hierarchy, thin grey boundaries and black/white contrast for emphasis. Avoid decorative copy and coloured accents.
