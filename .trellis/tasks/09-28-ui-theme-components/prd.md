# UI polish: theme and key components

## Goal
Make the existing VipLedger look more polished without changing navigation or business behavior. First pass only updates the shared theme and the shared design-system components that show up on most screens.

## Requirements
- Keep the green ledger identity. Soften the palette, add a warm off-white page background, and give cards a quieter elevation.
- Complete the Material 3 light and dark color schemes so surfaces, text, and containers are consistent.
- Amounts stay tabular and right-aligned. Make signed amounts slightly heavier.
- Transaction rows get a small type mark and a quieter subtitle, without changing click/long-click behavior.
- Empty states get a soft circular mark and a filled action when an action exists.
- Top bar uses a transparent container so it sits on the page background.
- Do not rewrite screens, copy, or feature logic in this pass.

## Acceptance
- `core:designsystem` compiles.
- Existing previews still compile against unchanged component signatures.
- No new user-visible hardcoded Chinese strings.
