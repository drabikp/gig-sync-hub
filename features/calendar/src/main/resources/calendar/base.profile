# Language-independent signals, for every band. Format: see ProfileFile.
# Tuned on a real band calendar (437 events); see docs/calendar-plan.md.

# Weekly calls, anniversaries, reminders.
REPEATING            -10
# Holidays and trips; a festival appearance is one or two days.
LONGER_THAN_DAYS      -3  3
# Absences are usually all-day events that don't block the time.
ALL_DAY_FREE          -1
# Gig titles are unique; "Rehearsal", "Travel home", "Call" repeat.
TITLE_REPEATED        -3  4
# The catalog (platform history) has a gig that day.
CATALOG_GIG_SAME_DAY   2
# A travel event that day goes there or arrives just before it.
TRAVEL_LEADS_TO        3

# Where the show time is in a day sheet (the event itself usually starts at arrival).
SHOWTIME_LABEL         0  showtime|show time|stage time|set time|show
