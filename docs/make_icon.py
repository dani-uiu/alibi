"""Generates the Alibi app icon (docs/icon.svg and the Android vector layers).

Run from an empty folder: python make_icon.py
Outputs icon.svg, icon-mono.svg and ic_launcher_{foreground,background,monochrome}.xml;
copy the XML files to app/src/main/res/drawable/.
"""
import math, sys
# Geometry on Android's 108x108 adaptive-icon grid (safe zone: r=33 circle at 54,54)
BG_TOP, BG_BOT = "#5E62EA", "#3135A8"
WHITE = "#FFFFFF"
TEAL = "#7EE8EE"

def pin(cx, cy, r, d_ratio=2.15):
    """Teardrop pin: circle radius r at (cx,cy), tip straight below at distance d."""
    d = r * d_ratio
    a = math.acos(r / d)                      # tangent angle from vertical
    tx, ty = r * math.sin(a), r * math.cos(a)
    tip = (cx, cy + d)
    # soften the tip with a small quadratic so it isn't needle-sharp
    k = 0.18
    lx, ly = cx - tx, cy + ty
    rx_, ry_ = cx + tx, cy + ty
    q1 = (tip[0] + (rx_ - tip[0]) * k, tip[1] + (ry_ - tip[1]) * k)
    q2 = (tip[0] + (lx - tip[0]) * k, tip[1] + (ly - tip[1]) * k)
    f = lambda v: f"{v:.2f}"
    return (f"M{f(q1[0])},{f(q1[1])} L{f(rx_)},{f(ry_)} "
            f"A{f(r)},{f(r)} 0 1,0 {f(lx)},{f(ly)} "
            f"L{f(q2[0])},{f(q2[1])} Q{f(tip[0])},{f(tip[1])} {f(q1[0])},{f(q1[1])} Z")

def circle(cx, cy, r):
    return f"M{cx - r:.2f},{cy:.2f} a{r:.2f},{r:.2f} 0 1,0 {2*r:.2f},0 a{r:.2f},{r:.2f} 0 1,0 {-2*r:.2f},0 Z"

# Main pin (where the phone "is")
M = dict(cx=61.0, cy=44.0, r=13.5)
MAIN = pin(**M)
HOLE = circle(M["cx"], M["cy"], 5.2)
# Ghost pin (where you really are)
G = dict(cx=37.0, cy=61.0, r=8.6)
GHOST = pin(**G)
GHOST_DOT = circle(G["cx"], G["cy"], 3.0)
# Ground shadows
def ellipse(cx, cy, rx, ry):
    return f"M{cx - rx:.2f},{cy:.2f} a{rx:.2f},{ry:.2f} 0 1,0 {2*rx:.2f},0 a{rx:.2f},{ry:.2f} 0 1,0 {-2*rx:.2f},0 Z"
SHADOW = ellipse(M["cx"], M["cy"] + M["r"] * 2.15 + 1.6, 8.5, 2.2)
# Dotted hop from ghost head to main pin, along a gentle arc
DOTS = []
p0, p1, ctrl = (40.0, 48.5), (46.8, 33.5), (38.5, 36.0)
for i, t in enumerate([0.12, 0.42, 0.72]):
    x = (1-t)**2*p0[0] + 2*(1-t)*t*ctrl[0] + t**2*p1[0]
    y = (1-t)**2*p0[1] + 2*(1-t)*t*ctrl[1] + t**2*p1[1]
    DOTS.append(circle(x, y, 1.8 + 0.35 * i))

def svg(size=108, background=True, mono=False):
    fg = WHITE
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="{size}" height="{size}">']
    if background:
        parts.append(f'<defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">'
                     f'<stop offset="0" stop-color="{BG_TOP}"/><stop offset="1" stop-color="{BG_BOT}"/></linearGradient>'
                     f'<radialGradient id="glow" cx="0.62" cy="0.38" r="0.55"><stop offset="0" stop-color="#FFFFFF" stop-opacity="0.16"/>'
                     f'<stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/></radialGradient></defs>')
        parts.append('<rect width="108" height="108" fill="url(#bg)"/><rect width="108" height="108" fill="url(#glow)"/>')
    ghost_col = fg if mono else TEAL
    if not mono:
        parts.append(f'<path d="{SHADOW}" fill="#000000" fill-opacity="0.22"/>')
    parts.append(f'<path d="{GHOST}" fill="none" stroke="{ghost_col}" stroke-width="3.0" stroke-linejoin="round" stroke-opacity="{1 if mono else 0.9}"/>')
    parts.append(f'<path d="{GHOST_DOT}" fill="{ghost_col}" fill-opacity="{1 if mono else 0.9}"/>')
    for d in DOTS:
        parts.append(f'<path d="{d}" fill="{ghost_col}"/>')
    parts.append(f'<path d="{MAIN} {HOLE}" fill="{fg}" fill-rule="evenodd"/>')
    parts.append('</svg>')
    return "".join(parts)

if __name__ == "__main__":
    open("icon.svg", "w").write(svg(512))
    open("icon-foreground.svg", "w").write(svg(512, background=False))
    open("icon-mono.svg", "w").write(svg(512, background=False, mono=True))

# ---- Overall placement: centre the artwork and leave margin inside the safe zone
PIVOT = (50.7, 54.5)
SCALE = 0.86
SHIFT = (54 - PIVOT[0], 54 - PIVOT[1])
SVG_TRANSFORM = f'translate({54:.2f},{54:.2f}) scale({SCALE}) translate({-PIVOT[0]:.2f},{-PIVOT[1]:.2f})'

def svg_final(size=512, background=True, mono=False):
    raw = svg(size, background, mono)
    head, rest = raw.split('<path', 1) if not background else (None, None)
    # wrap every artwork path (everything after the background rects) in a transformed group
    marker = '<rect width="108" height="108" fill="url(#glow)"/>' if background else None
    if background:
        a, b = raw.split(marker, 1)
        return a + marker + f'<g transform="{SVG_TRANSFORM}">' + b.replace('</svg>', '</g></svg>')
    a, b = raw.split('>', 1)
    return a + '>' + f'<g transform="{SVG_TRANSFORM}">' + b.replace('</svg>', '</g></svg>')

def vector(paths, comment):
    group = (f'    <group\n        android:pivotX="{PIVOT[0]}"\n        android:pivotY="{PIVOT[1]}"\n'
             f'        android:scaleX="{SCALE}"\n        android:scaleY="{SCALE}"\n'
             f'        android:translateX="{SHIFT[0]:.2f}"\n        android:translateY="{SHIFT[1]:.2f}">\n')
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- {comment} Generated from docs/icon.svg; 108x108 adaptive-icon grid. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
            + group + "".join(paths) + '    </group>\n</vector>\n')

def p(d, fill=None, alpha=None, stroke=None, width=None, evenodd=False):
    attrs = [f'android:pathData="{d}"']
    if fill: attrs.append(f'android:fillColor="{fill}"')
    if alpha is not None: attrs.append(f'android:fillAlpha="{alpha}"')
    if stroke:
        attrs += [f'android:strokeColor="{stroke}"', f'android:strokeWidth="{width}"', 'android:strokeLineJoin="round"']
    if evenodd: attrs.append('android:fillType="evenOdd"')
    return '        <path\n            ' + '\n            '.join(attrs) + ' />\n'

def fg_paths(mono):
    ghost = WHITE if mono else TEAL
    out = []
    if not mono:
        out.append(p(SHADOW, fill="#000000", alpha="0.22"))
    out.append(p(GHOST, stroke=ghost if mono else "#E67EE8EE", width="3.0"))
    out.append(p(GHOST_DOT, fill=ghost if mono else "#E67EE8EE"))
    for d in DOTS:
        out.append(p(d, fill=ghost))
    out.append(p(f"{MAIN} {HOLE}", fill=WHITE, evenodd=True))
    return out

BACKGROUND_XML = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Alibi launcher background: indigo gradient with a soft highlight behind the pin. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0" android:startY="0"
                android:endX="108" android:endY="108">
                <item android:offset="0" android:color="{BG_TOP}" />
                <item android:offset="1" android:color="{BG_BOT}" />
            </gradient>
        </aapt:attr>
    </path>
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="67" android:centerY="41"
                android:gradientRadius="59">
                <item android:offset="0" android:color="#29FFFFFF" />
                <item android:offset="1" android:color="#00FFFFFF" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''

if __name__ == "__main__":
    open("icon.svg", "w").write(svg_final(512))
    open("icon-mono.svg", "w").write(svg_final(512, background=False, mono=True))
    open("ic_launcher_foreground.xml", "w").write(vector(fg_paths(False), "Alibi launcher foreground: a pin where you appear to be, and a ghost pin where you are."))
    open("ic_launcher_monochrome.xml", "w").write(vector(fg_paths(True), "Alibi themed-icon layer (Android 13+): single colour, tinted by the system."))
    open("ic_launcher_background.xml", "w").write(BACKGROUND_XML)
