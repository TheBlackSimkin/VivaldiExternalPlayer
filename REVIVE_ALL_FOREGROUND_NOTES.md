# Revive All foreground-player failure notes

Captured from Candidate 5 device QA.

## User sequence
1. User taps **Revive All** from dashboard.
2. User waits while some videos become revived/ready, but other videos remain queued to be checked/revived.
3. User opens an already revived/ready video to watch while the remaining tabs are still in the revive queue.
4. The player blinking/buffering issue happens immediately after entering the video.
5. The issue stops when user goes back to the dashboard.

## Observed symptoms
- Disturbance affects the whole visible player experience, but not all UI elements at the same rhythm.
- The tab-count/status box changes, for example from a form like `5/23 check` to a bare total like `24`, then back to the checked/total wording. Sometimes the first number increases.
- The video buffers, tries to start, sometimes shows a fraction of a second, then the blinking causes it to start over again.
- User sees only the player and the described player/status changes, not the dashboard or Android recents/app-switch animation.
- The issue occurs whenever user enters a revived/ready tab while there are still tabs left to be revived.
- When returning to the dashboard, queued/revive states appear to be where they were when the user entered the video, but user has not confirmed whether any tab advanced while watching.

## Candidate 6 implication
Candidate 5 active-session suspension was insufficient. The problem appears tied to entering PlayerActivity while the Revive All queue still exists, not only to one already-running private-display session.

Recommended safer rule for Candidate 6:
- When PlayerActivity is foreground, Revive All must become a paused queue and must not start or continue protected private-display/WebView revive work.
- Resume queued Revive All work only after the user leaves PlayerActivity and returns to the dashboard/app foreground state.
- Keep individual user-triggered in-player Refresh source separate from bulk Revive All semantics.
