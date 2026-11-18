# ADR-032: Typing on the device: the phone's keyboard over the wheel, into a field that is a row of the display

- **Status:** Accepted
- **Date:** 2026-11-18
- **Author:** Eduardo Flores
- **Reviewers:** AI-assisted review
- **Supersedes:** [ADR-018](018-device-navigation.md) (rule 11, for the keyboard: a device screen may
  take text through the host) and [ADR-019](019-device-host-module.md) (rule 6, for text: a device
  screen may receive typed text through the host's contract, and a screen with a field takes MENU
  while a row other than the field is highlighted)

## Context

Search on the device (podcasts, frames 29–34 in `player-designs/device`) needs text. The designs
show the phone's keyboard where the wheel was, results that update as the user types, and the wheel
back on the results when the keyboard goes.

[ADR-018](018-device-navigation.md) doesn't allow it. The display isn't touched, every interaction
goes through the wheel, and its rule 11 says "A screen that needs touch or the keyboard is a full
screen… No screen mixes the two", which `TouchComponentInDeviceScreen` enforces. Its "What was not
decided" left text input open, with two candidates: a letter picker on the wheel, and a full screen
with the system keyboard. [ADR-019](019-device-host-module.md)'s rule 6 lists every input a device
screen may take, and text isn't one.

Neither candidate fits Search:

- **A letter picker** turns a ten-letter query into dozens of turns and presses. Results that update
  as the user types would update one slow letter at a time.
- **A full screen** replaces the device with a Material screen to type, and the results would then
  be on a screen that can't be touched and has no field. The user couldn't see the results change as
  they type, which is what makes search usable.

Some of the platform's behavior has to be designed around:

- **While the soft keyboard is shown, the system's back goes to the keyboard first,** and the
  keyboard closes itself. The app doesn't receive that back. A rule like "back with an empty field
  leaves Search" can't be built on a back handler, and [ADR-018](018-device-navigation.md)'s rule 9,
  which is enforced, says nothing on the device handles back anyway.
- **The keyboard is the user's,** with its own height, layout and size setting. Gboard at its largest
  is taller than the wheel on a short phone.
- **A hardware keyboard draws nothing.** The field still receives text, and there's no soft keyboard
  to close.
- **On a tall or large screen** the wheel can be visible beside or under the keyboard.

The app is edge to edge, with `adjustResize` (`MainActivity`, the manifest), so the keyboard is a
window inset that the device can read.

## Decision

We will let **any device screen take text**, through **the phone's keyboard drawn over the wheel**,
into **a field that is a row of the display**. The host owns the keyboard and when the user is
typing. The screen owns the text and what it does with it.

### Typing is a state of the device, and the host owns it

- **The device is either on the wheel or typing.** It starts on the wheel. A screen with a field
  can start typing, and typing ends when the keyboard goes. The host knows which state it is in, and
  the screen is told.
- **While typing, the keyboard is drawn over the wheel.** The device doesn't change its size or its
  layout, and the wheel stays where it is, under the keyboard. The display keeps its size and keeps
  showing the screen.
- **The display is never covered.** When the keyboard would cover part of the display, the whole
  device moves up just enough that the display's bottom edge is above the keyboard, and it moves back
  down when the keyboard goes. The top of the body may leave the window. The host reads the keyboard's
  inset to do it, and nothing else in the device does.
- **While typing, the wheel sends nothing.** No turn, center, MENU, hold MENU, ⏮, ⏭, ⏯ or hold
  reaches the host's routing. Where the wheel is visible beside the keyboard, a touch on it, or
  anywhere in the window outside the keyboard, ends typing and does nothing else. The next touch is
  the wheel's again. Playback keeps playing, and the phone's media keys and the notification still
  control it ([ADR-023](023-playback-engine.md)).
- **Typing ends when the keyboard goes,** whatever made it go: its action key, the system's back
  (which the keyboard takes), the keyboard's own hide button, or a touch outside it. The host sees
  the keyboard's inset go and ends typing. No back handler is involved, and ADR-018's rule 9 holds
  as written.
- **A hardware keyboard types into the same field,** and the device is in the same state: the wheel
  sends nothing and no soft keyboard is drawn. Enter and Esc end typing. The system's back leaves the
  app, as it does on every device screen (ADR-018, rule 9): with no soft keyboard there's nothing to
  take it first.
- **The host keeps the state across configuration changes and process death,** with the screen's
  entry: whether it is typing, and so whether the keyboard comes back.

### The field is a row of the display, and the screen owns the text

- **A screen has at most one field,** at the top of its list, and it is one of the list's rows: the
  wheel can highlight it, like any row.
- **The screen owns the text.** The query is the screen's state, kept by its ViewModel and saved
  with its entry. The host never reads it. What the text does (searching, naming) is the screen's.
- **Center on the highlighted field starts typing.** So does a screen that asks to type when it is
  first shown, such as Search, which opens with the keyboard up.
- **While typing, nothing is highlighted** (frame 30). When typing ends, the screen is told, and it
  highlights its first result. With no result, it highlights the field.
- **MENU on a result highlights the field.** That is "MENU returns to typing" in the designs: the
  field is one center away. **MENU on the highlighted field goes back one screen,** the host's MENU,
  as on any screen. A screen with a field takes MENU through the host's contract while a row other
  than the field is highlighted, as a screen in a mode of its own does (ADR-019, rule 6).
- **An empty field is the same.** The keyboard goes, the field is the only row and is highlighted,
  center types again and MENU leaves. There's no second behavior for an empty field.
- **The keyboard's action key** is the screen's to name (Search, Done). It always ends typing, and it
  calls the screen first, so a screen can act on it (search now, without waiting for a debounce).

So the controls on a screen with a field are:

| Control | Typing | On the wheel, the field highlighted | On the wheel, a row highlighted |
| --- | --- | --- | --- |
| Keyboard | Types into the field | - | - |
| Action key, back, hide, a touch outside | Ends typing: the first result is highlighted, or the field | - | - |
| Turn | - | Moves the highlight to the rows | Moves the highlight, the field included |
| Center | - | Starts typing | Opens or plays the row, as on any list |
| MENU | - | Goes back one screen | Highlights the field |
| Hold MENU, ⏮, ⏭, ⏯, holds | - | As on any list (ADR-018) | As on any list (ADR-018) |
| System's back | Taken by the keyboard: ends typing | Leaves the app (ADR-018, rule 9) | Leaves the app (ADR-018, rule 9) |

### Who owns what

- **`:common:designsystem`** has the field's look: `DeviceTextField`, a row of the display with its
  search glyph, the query, a caret while typing, its hint, and its highlight. It wraps Compose's
  `BasicTextField`, which is the one place the text is edited. It knows nothing about the host
  ([ADR-015](015-design-system-owns-visual-language.md)). It is a device component, so it is named in
  `DeviceComponentOutsideDesignSystem` ([ADR-018](018-device-navigation.md), rule 12).
- **`:device:host`** has the typing state, the keyboard (showing it, hiding it, reading its inset),
  moving the device up, ending typing on a touch outside, stopping the wheel's events while typing, and
  the contract a screen uses: a field that the screen places in its list, given the screen's text
  state, its hint, its short hint and its action, which says when typing starts and ends. Its names
  are device/02's.
- **The screen** (in its part's `:ui:impl`) has the text, the results, the highlight and what the
  action key does. It doesn't show or hide the keyboard, request focus or read the keyboard's inset.
  It places the host's field, and nothing else that edits text.

This adds no module and no dependency ([ADR-020](020-modules-by-product-area.md)): the host depends
on the design system as it already does, and a screen on the host.

### The field and large text

- **The field follows the system font scale** ([ADR-017](017-accessibility.md), rules 2 and 3). It
  is one line, as tall as its text.
- **The query scrolls sideways to keep the caret in view** while typing, as any text field does. On
  the wheel, a query that doesn't fit ends in an ellipsis: it is the user's text, which ADR-017's rule
  4 allows.
- **The hint is never cut.** It is the app's text, so ADR-017's rule 4 forbids an ellipsis. The
  screen gives a hint and a short hint, and the field shows the short one when the hint doesn't fit
  on one line ("Shows, creators, episodes" and "Search"). ADR-017 records it as its rule 13.
- **The keyboard is the system's,** and follows its own size and language settings. Rolabox doesn't
  draw or style a key.

### TalkBack

The field is an edit field to TalkBack: it reads its hint when empty, its text otherwise, and that it
is being edited while typing. That comes from `BasicTextField`'s own semantics, given the hint as its
label, in the design system. Reading the results with the wheel is part of the device's screen reader
gap, which [ADR-018](018-device-navigation.md) records and ADR-017's screen reader section will
close. ADR-017 records the field's part as its rule 13.

## Alternatives considered

- **A letter picker driven by the wheel.** Nothing leaves the device's idea, and it is what the
  player the device is drawn after did. But a query takes minutes, results that update as the user
  types update one letter at a time, and the phone has a keyboard the user already knows.
- **A full screen with the system keyboard,** as ADR-018 kept open. Rule 11 stays as written. But the
  results are on the device and the field is not, so the user types blind, and every search leaves
  the device and comes back.
- **The keyboard in place of the wheel,** with the wheel removed from the layout while typing. What
  the designs first showed. But it is a second layout of the device, the display moves when the wheel
  goes, and on a tall screen there would be empty space where the wheel was.
- **The display shrinks above the keyboard,** instead of the device moving up. The device stays where
  it is. But the display's proportions change while typing, and the list jumps by rows as the
  keyboard opens.
- **Overlap allowed,** with the keyboard covering what it covers. Nothing moves. But with Gboard at
  its largest on a short phone, the results are under the keyboard, which defeats results that update
  as the user types.
- **Back with an empty field leaves Search,** as the ticket asked. It saves a MENU. But the keyboard
  takes back while it is shown, so the app would have to guess from the field being empty when the
  keyboard went, or handle back against ADR-018's enforced rule 9. And an empty field would behave
  differently from a full one for the same gesture.
- **"Back with the keyboard closed, an empty field leaves Search",** reacting to the keyboard going
  rather than to back. It is buildable. But closing the keyboard by its hide button or by a touch
  outside would then leave Search too, which nobody asked for.
- **MENU returns to typing, literally:** MENU on the results opens the keyboard. What frame 31's
  note says. But the keyboard covers the wheel and MENU isn't available while typing, so the "second
  MENU" that leaves can't be pressed, and the user is trapped in Search.
- **A touch on the wheel beside the keyboard ends typing and counts as the wheel's event.** One touch
  fewer. But a stray MENU would leave Search while the user meant to close the keyboard.
- **Only Search may type.** Smaller. But naming a playlist will need the same thing, and a second
  screen would then need another ADR for a mechanism that names no feature.
- **The screen shows and hides the keyboard itself,** with Compose's keyboard controller and focus.
  No new contract. But every screen would decide what MENU and the wheel do while typing, which
  ADR-018 keeps in one place, and a screen could open the keyboard with no field on the display.
- **Revise ADR-018 in place,** as its "What was not decided" said. One navigation ADR. But ADR-018 is
  already the longest record, and this is a decision of its own with its own rules and checks, as
  playback (ADR-023) and podcasts (ADR-031) were.

## Consequences

- Search can be built on the device, and any later screen that needs text (naming a playlist) uses
  the same field and contract, with no new decision.
- The display is touched by nothing still: the keyboard is outside it, and the field is a row the
  wheel reaches. ADR-018's idea holds, with the phone's keyboard as a second input device that only
  the host turns on.
- Leaving Search after typing is: close the keyboard, MENU to the field, MENU again. One MENU more
  than a list.
- The device can move up while typing, and the top of the body can leave the window. That is a
  deliberate change of the device's position, and the goldens of device/02 show it.
- With a hardware keyboard, the system's back leaves the app while typing, where with a soft keyboard
  it only ends typing. Esc ends typing. That difference is the platform's, and the rule stays "nothing
  on the device handles back".
- On a large screen a touch on the wheel only closes the keyboard, so the first touch after typing is
  lost.
- Results stay unread by TalkBack with the wheel until the screen reader section exists, as on every
  other device screen.
- `:device:host` grows: the keyboard, its inset, the device's offset and the typing state are now its
  own, with their tests.

## Rules

1. `[convention]` A device screen may take text, through one field at the top of its list, which is
   a row the wheel can highlight. The keyboard is the phone's, drawn over the wheel. A screen that
   needs anything else operated by touch or the keyboard is still a full screen (ADR-018, rule 11).
2. `[enforced]` Only `:device:host` shows or hides the keyboard, gives the field focus or reads the
   keyboard's inset, and only through the host's field does a device screen place a text field. A
   device screen doesn't use Compose's keyboard controller, focus requesters, key events or the
   keyboard's inset, and doesn't call `DeviceTextField` itself.
3. `[convention]` While typing, the wheel sends no event, a touch outside the keyboard only ends
   typing, and nothing on the display is highlighted. The device keeps its size and layout, and moves
   up only as far as it takes to keep the display above the keyboard.
4. `[convention]` Typing ends when the keyboard goes, whatever made it go, and with a hardware
   keyboard on Enter or Esc. Nothing on the device handles the system's back (ADR-018, rule 9).
5. `[convention]` When typing ends the screen highlights its first result, or the field when there
   is none. Center on the field starts typing. MENU on a row highlights the field, and MENU on the
   field goes back one screen.
6. `[convention]` The screen owns the text and saves it with its entry, and the host owns whether the
   device is typing and saves it with the same entry.
7. `[convention]` The field is the design system's `DeviceTextField`. It follows the system font
   scale, its hint is never cut, and a short hint replaces it when it doesn't fit (ADR-017, rule 13).

**Conformance.** Rule 2 is checked by `KeyboardInDeviceScreen`, a Rolabox detekt rule. It uses the
device screen files of `TouchInputInDeviceScreen` (a file that imports `:device:host` or a device
component of the design system) and reports there a use of `LocalSoftwareKeyboardController`,
`SoftwareKeyboardController`, `LocalTextInputService`, `FocusRequester`, `focusRequester`,
`requestFocus`, `onKeyEvent`, `onPreviewKeyEvent`, `imePadding`, `imeNestedScroll`, `WindowInsets.ime`
or a call to `DeviceTextField`. `:device:host` and `:common:designsystem` implement them and are
excluded. ADR-018's `TouchComponentInDeviceScreen` keeps reporting `BasicTextField`, `TextField` and
the other components operated by the keyboard in a device screen: the host's field is the only way
in. Both run in `detekt`, part of `./gradlew check` and of the CI `build` job, and their unit tests
run in `./gradlew check` and `unitTest`. An exception is a `@Suppress` that names the rule. Rules 3
to 6 are behavior, with no syntax for a build check to read: device/02 covers them with unit tests
of the host's typing state and routing, and an instrumented test that the keyboard shows and hides
and the wheel comes back. Rule 7's large text is covered by the goldens of `DeviceTextField` in
light, dark and at 1.5x, empty with its hint and with a query. Rules 1, 3 to 6 are reviewed.
