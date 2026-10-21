# 04 — Design system

The source of truth is board **17 Components** (`Components.dc.html`) on the [canvas](https://claude.ai/artifact/XrLDLiYD9C4ic1fQdtZCWd). The UI is dark only, because TV rooms are dark.

## Colour tokens

| Token | Hex | Use |
|---|---|---|
| `ground` | `#0B0E14` | App background, dark text on light fills |
| `panel` | `#0E121A` | Secondary column (groups, settings sections, detail side panel) |
| `surface` | `#141925` | Cards, setting rows, chips |
| `control` | `#1A2030` | Secondary buttons, inputs, badges, preview placeholder |
| `raised` | `#232B3D` | **Selected** (not focused) item, active tab |
| `inputFocus` | `#2A3347` | Focused text field |
| `track` | `#252C3D` | Progress track |
| `text` | `#E8EBF2` | Primary text; **focus fill** |
| `textDim` | `#B8BFCC` | Secondary text in lists |
| `muted` | `#9AA3B5` | Captions, meta, idle icons |
| `faint` | `#7F889A` | Counts, channel numbers |
| `onFocusSub` | `#3A4255` | Secondary text on the focus fill |
| `accent` | `#FFB547` | Primary actions, progress, now-line, rating, active rail icon |
| `live` | `#E5484D` | LIVE badge |
| `success` | `#3DD68C` / `#2E8A5B` | Status dot / done step |
| `danger` | `#FF8A8F` | Destructive text, error labels; `#3A1E24` error input fill |

The accent is user-selectable (Settings → Appearance) from `#FFB547`, `#4FB6FF`, `#5EE0A0` and `#FF7A90`. Text on accent is always `ground`.

## Typography

Manrope (UI) and JetBrains Mono (times, channel numbers, server URLs). Bundle both as `res/font` TTFs (OFL licence) instead of downloadable fonts, because many TV boxes lack Play Services.

| Style | Size / weight | Use |
|---|---|---|
| display | 40 / 800 | Login headline, movie title (44) |
| title | 26 / 800 | Screen titles, programme title in preview |
| section | 18 / 800 | Row headings ("Continue watching") |
| body | 15 / 600–700 | Channel names, rows, buttons (14–16) |
| caption | 13 / 500 | Meta, hints, counts |
| overline | 12 / 700, +0.12em, UPPERCASE | Column headers ("GROUPS") |
| mono | 13–22 / 500 | Clock, times, numbers |

## Shape & spacing

- Radii: 6 badges · 9–10 small controls/rows · 12 buttons/cards · 14–16 panels/cards · 18–20 big tiles/forms · full for pills, toggles and round buttons.
- Spacing: 4-step scale. Gaps are 2/4 within lists, 8–12 between controls, 16–20 between cards, 28 between sections. Screen padding is 28 vertical and 32–40 horizontal.
- Minimum focus or tap target is 44 dp. Prefer 48 on TV.
- Fixed widths: rail 72, group column 220, settings sections 260, preview pane 440.

## The focus rule

> No outlines, rings or accent side bars anywhere.

| State | Fill | Text |
|---|---|---|
| Idle | transparent (or `surface` for cards) | `text` / `textDim` |
| Selected (current group, active tab, current season) | `raised` (season pills: `accent`) | `text` |
| **Focused** (D-pad) | `text` `#E8EBF2` solid | `ground`, secondary `onFocusSub` |
| Disabled | `#2A3142` / `surface` | `faint` |

Progress bars inside a focused item switch to `ground` on a `#C9CED8` track. Logo badges switch to `#C9CED8`. Add a 100 ms colour animation. No scale-up, since it causes jitter in dense lists.

## Sizing

Boards are 1280×720. Android TV at 1080p is **960×540 dp**, so treat 1 board px = **0.75 dp** on TV. Express sizes through a single `val u: Dp` from `LocalConfiguration.screenWidthDp / 1280f` (`Dimens.u * 72` for the rail) so the same values work on tablets. Phones use the compact layout (bottom bar, single column) described in [01](01-architecture.md#focus--d-pad-strategy).

## Compose notes

```kotlin
object C { val ground = Color(0xFF0B0E14); val panel = Color(0xFF0E121A); /* … table above … */ }

@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    idle: Color = Color.Transparent,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(when { focused -> C.text; selected -> C.raised; else -> idle }, tween(100))
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape).background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, indication = null,
                interactionSource = remember { MutableInteractionSource() }),
    ) {
        CompositionLocalProvider(LocalContentColor provides if (focused) C.ground else C.text) { content(focused) }
    }
}
```

- `indication = null` removes the Material ripple and focus highlight, so FocusSurface draws all states itself.
- Content reads `LocalContentColor` and the `focused` flag for secondary colours.
- Apply `MaterialTheme(colorScheme = darkColorScheme(background = C.ground, …))` only so that stray Material components don't flash light colours.

## Component inventory

| Board 17 item | Composable |
|---|---|
| Primary / Secondary / Destructive / Icon button | `Btn(kind, text|icon)` on `FocusSurface` |
| Input default/focused/error | `Field(label, value, error)` with `BasicTextField` |
| Toggle | `Toggle(on)` (52×30 track, 22 knob) |
| Tab switch | `Segmented(options, selected)` |
| Season pill | `Pill(selected)` |
| List row (group, channel, setting) | `GroupRow`, `ChannelRow`, `SettingRow` |
| Poster card | `PosterCard(title, image, rating, meta)` |
| Badge | `Badge(text, kind = Live|Rating|Neutral|InUse)` |
| Progress | `Progress(fraction, onFocus)` |
| Key hint | `KeyHint(key, label)` |
| Rail | `Rail(active, onSelect)`, icons as `ImageVector`s from the SVG paths in `Rail.dc.html` |
