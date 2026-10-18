# Backlog

Ideas and pending items that were left for later, in order of priority.

## Player

- [ ] **"Tocando: origem · título" on the control panel.** When what plays comes from another
  screen (e.g. YouTube while on IPTV), the panel does not say what it is nor where it came from.
  A thin line "▶ YouTube · title" on the panel, tapping it goes to the source screen
  (`MainActivityDelegate.goToCurrent()`). Keep the panel global: hiding it or pausing on a screen
  change were rejected (no control left / breaks listening while browsing).
- [ ] **IPTV movie takes ~10 s of black screen before it starts** (seen in the screen recording
  of 17/10). Needs the Fermata.log of a slow start to know where the time goes.
- [ ] **A YouTube video stayed loading (00:00) at the end of the same recording.** Needs the log.

## ChatGPT

- [ ] **Sign in with the ChatGPT account** (usage billed to the user's plan, no API key). OpenAI
  opened it at DevDay (29/09/2026) for registered apps; it needs a client id from OpenAI. The
  requests already take their authorization from `ChatAddon.getAuthorization()`: the OAuth
  access token plugs in there.

## Settings

- [ ] Visual of the settings screens: one big card per row, repetitive. Group the rows, lighter
  rows, icons per section.

## Product (when it becomes Andro's own product)

- [ ] Remove the update check against the upstream Fermata and the link to the upstream project.
- [ ] License review (GPL-3.0 fork: keep it GPL and the credits, or rewrite clean-room).

## Build

- [ ] `:utils:testDebugUnitTest` always fails (8 `HttpHandlerTest`, inherited). It stops the CI
  test job before `:xtream` runs; run the xtream tests first or fix those 8.
