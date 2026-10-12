# Slovak / Czech words bands commonly use in their calendars. Compared without accents
# or case. Band-specific words — members' names, the band's own labels — belong in the
# band's profile file (gigsynchub.calendar.profile-file), not here.

# --- Not a gig ---------------------------------------------------------------
# What the event is, by the first word of the title: rehearsal, travel, call, meal,
# hotel, photo/video shoot, recording, training, meeting, phone call, day off …
TITLE_STARTS_WITH  -6  skuska|skusky|zkouska|zkousky|reh|cesta|presun|porada|call|obed|hotel|foto|fotenie|foceni|tocenie|natacen|nahravan|podcast|skolen|meeting|volat|volno|inventura|oprava|songwriting|stage generalka|vysetren|kapela chata|b-day
# Promo material, not a performance.
TITLE_CONTAINS     -4  pozvank|video|klip
# Someone can't come / is away / a private occasion.
TITLE_CONTAINS     -4  nemoze|nemozem|nemuze|nemuzu|dovolen|dovca|odcestovan|svadb|svatb|narodenin|oslav|konferenc|statnice|operac|mimo sr|mimo cr|vikendovy pobyt|chata|vylet
# Going to look at a festival, visiting someone.
TITLE_CONTAINS     -3  navstev
NOTES_CONTAIN      -3  pozriet|podivat

# --- A gig -------------------------------------------------------------------
# A day sheet in the notes.
NOTES_LABEL         5  showtime|show|soundcheck|zvukovka|prijazd|prichod|prijezd|arrival|doors|vykladka|dlzka hrania|delka hrani|cas predbezne|pokec|stav|spanie|ubytovanie
# What gigs are called.
TITLE_CONTAINS      3  fest|tour|koncert|klub|club|open air|openair|jarmok|slavnost|zraz|party|krst|majales|karneval|rokle|hradby|otvirak|rock
# Other bands on the bill.
TITLE_CONTAINS      1  +|s kapelou
# A fee or a contract.
NOTES_CONTAIN       1  cena|zmluv|smlouv|honorar|€|czk

# --- Roles -------------------------------------------------------------------
TRAVEL              0  cesta|presun
CANCELLED_TITLE     0  zrusen|cancel
CANCELLED_NOTES     0  koncert zrusen|koncert je zrusen|akcia zrusen|akce zrusen
TENTATIVE_TITLE     0  predbezne|?
TENTATIVE_NOTES     0  v jednani|nepotvrden
CONFIRMED_FIELD     0  stav=potvrd
SHOWTIME_LABEL      0  vystupenie|vystoupeni|zaciatok koncertu|zacatek koncertu|cas predbezne
