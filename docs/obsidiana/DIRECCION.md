# OBSIDIANA — brief (read fully, then work fast)

The user rejected the previous design line as dated. This is a NEW design system and a NEW design line, built from
zero for a white-label restaurant SaaS (Mexico, MXN, Mexican Spanish copy). Bar: a flagship commercial brand of 2026 —
visually and technically the most advanced you can make. Do NOT reuse the look of anything older (no cream/paper
backgrounds, no small soft cards, no timid type). There is NO platform name or logo anywhere: the product is 100%
white-label; the only brands shown are the fictional restaurants Brasa Demo, Nori Demo and Miga Demo.
"Obsidiana" is only the internal name of the system.

HARD DEADLINE: return by **07:42 UTC** (check `date -u +%H:%M` now and often). Publish as you go so something is
always visible. NEVER verify visually (no screenshots, no browser, no reading files back): the canvas type forbids it.
No git. Do not ask questions.

## Concept — "vidrio volcánico"
Deep obsidian surfaces; light is the material (glass layers, hairline highlights, one living accent that glows);
food photography is the hero and runs full-bleed; typography carries drama: an editorial serif for display next to a
precise grotesk for UI and a mono for numbers (order numbers, timers, money in dashboards). Dark-first; a clean light
theme exists for daylight contexts. Confident scale, generous space, bento grids, floating pill bars, big numerals.

## Tokens (exact)
Dark (default): canvas #07070A · surface #0E0E13 · raised #15151C · overlay #1D1D26 · line #262631 · line-strong #3A3A48 ·
ink #F5F5F7 · ink-2 #B4B4C2 · ink-3 #7C7C8E.
Light: canvas #F7F7F5 · surface #FFFFFF · raised #FFFFFF · sunken #EDEDEA · line #E2E2DD · line-strong #C9C9C2 ·
ink #0B0B0F · ink-2 #4B4B57 · ink-3 #6B6B78.
Accent (the tenant slot; platform default "Brasa"): #FF5B2E with on-accent #0B0B0F (dark text on the bright accent),
accent-soft = accent at 14% (dark) / 10% (light). Semantic dark/light: success #34D399/#0F9D6B, warning #FBBF24/#B45309,
danger #F87171/#DC2626, info #60A5FA/#2563EB.
Tenants: Brasa Demo — accent #FF5B2E, display Instrument Serif, radius default, dark. Nori Demo — accent #7CF5C4
(on-accent #06120D), display Inter Tight, sharp radius 4/6/10/14, dark. Miga Demo — accent #B8863B (on-accent #FFFFFF),
display Instrument Serif, soft radius 14/22/30/40, LIGHT theme.
Type: display/headings **Instrument Serif** 400 (italic for emphasis); UI **Inter Tight** 400/500/600/700; numbers
**JetBrains Mono** 500. Scale px/line/tracking: display 72/72 −3% · h1 44/48 −2% · h2 30/36 −1.5% · h3 20/26 600 ·
title 17/24 600 · body 15/23 · small 13/19 · label 11/14 600 caps +10%.
Radius 10 / 16 / 24 / 32 · pill 999. Spacing 4 8 12 16 24 32 48 64 96.
Glass: fill rgba(255,255,255,.06) + blur 24px saturate 140% + 1px border rgba(255,255,255,.10).
Glow (primary CTA only): 0 0 0 1px accent 30%, 0 16px 48px −12px accent 45%.
Motion: spring stiffness 380 / damping 30; durations 140 / 240 / 420 / 700 ms; easing cubic-bezier(.16,1,.3,1).
Patterns: shared-element photo → sheet, number ticker on totals, 40 ms staggered reveal, magnetic press (scale .97),
live pulse on real-time states, add button morphing into a stepper, cart pill that bumps.
Icons: inline stroke SVG 20px, stroke 1.6, round caps (class `icon` in the shared CSS). Never emoji.
Rules: no decorative gradient washes, no blue–purple gradients, no left-border cards, no fake status bars, no lorem
ipsum, no invented third-party logos (channel names as text pills). Text contrast ≥ 4.5:1 (use ink-2 for small text
on dark; never ink-3 below 13px). Touch targets ≥ 44px. Real `<button>`/`<a>`/`<input>` elements.

## Canvas (for canvas agents)
Canvas URL: https://claude.ai/artifact/RdxkgGMLhASQy4fiuhTFyc
Root folder (already exists): `/tmp/claude-0/-home-user-Saas-restaurant/2d1f6276-69a4-54db-981d-63b90fefa459/scratchpad/obsidiana/canvas`
— write your artboards at `<root>/project/<Name>.dc.html` with the Write tool. `project/canvas.json` (index, all 11
artboards with final names and sizes) and `project/obsidiana.css` (shared tokens + utilities: READ IT FIRST, it is
short) and `project/img/a.jpg … t.jpg` already exist locally; only the agent named "canvas-lead" publishes those.
Each artboard is one self-contained Design Component file with this exact skeleton:

```html
<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<title>Nombre de la pantalla</title>
<script src="./support.js"></script>
<link rel="stylesheet" href="obsidiana.css">
</head>
<body>
<x-dc>
<helmet>
<link href="https://fonts.googleapis.com/css2?family=Instrument+Serif:ital@0;1&family=Inter+Tight:wght@400;500;600;700&family=JetBrains+Mono:wght@500&display=swap" rel="stylesheet">
<style>body{margin:0}</style>
</helmet>
<div class="ob t-brasa col" style="width: 390px; height: 844px; overflow: hidden; position: relative">
  … markup …
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":390,"height":844}}'>
class Component extends DCLogic {
renderVals() { return {}; }
}
</script>
</body>
</html>
```
Rules that fail silently if broken: keep the `support.js` line exactly; close every non-void element; quote every
attribute; root element FIXED at the artboard's w×h from canvas.json (only `Sitio.dc.html` is a fluid PAGE: no px
width on the root, `max-width` container); `$preview` = w×h; `{{hole}}` is only a lookup into `renderVals()`; all UI
is markup inside `<x-dc>` (never script-built); style with inline `style="…"` plus the shared classes; photos as
`background-image:url(img/x.jpg)` set in an inline style on an element that also has class `ph` (solid fallback).
Photos available (img/): look at 2–3 with the Read tool if you need to pick (a–t are food and restaurant photos:
tacos, burger, bowls, salad, sushi, ramen, coffee, bread, cake, skewers, cocktails, interiors) — do not spend more
than one minute on this.
PUBLISHING (Artifact tool): your conversation has not touched the canvas, so first call
`Artifact {action:"read", url:<canvas>, path:"project/canvas.json"}` (if it is not served yet that is fine), then after
EACH artboard you write, publish it immediately:
`Artifact {action:"publish", url:<canvas>, root:"<root>", file_path:"<root>/project/<Name>.dc.html"}`
(add `files:{"project/Other.dc.html":"project/Other.dc.html"}` to send several). Send only your own files. If a publish
is refused, follow the refusal once and retry. Do not send canvas.json unless you are canvas-lead.

## Design system artifact (for the ds agent)
URL: https://claude.ai/artifact/BaJWVxX2Z17W1VTqRqxRdF — root folder
`/tmp/claude-0/-home-user-Saas-restaurant/2d1f6276-69a4-54db-981d-63b90fefa459/scratchpad/obsidiana/ds` (files under `<root>/project/…`).
