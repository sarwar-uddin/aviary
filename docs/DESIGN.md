# Aviary design v2: "a field guide you hold"

The v1 screens work but they're flat. They read as a plain Material list on beige. v2 should feel like a well-made printed guide: big plates, confident type, small printed details, and a little craft in every screen.

## Principles
1. **Photos are the hero.** Use bigger images, full-bleed where it makes sense, and rounded plates with a hairline frame. Text sits beside or under the plates, never crowding them.
2. **Printed-guide details.**
   - **Plate numbers:** species are "No. 706", using `sort + 1` in small caps.
   - **Section headers:** small caps with a hairline rule.
   - **Dividers:** an ornamental `❦`-style rule between sections (see `ui/common/Ornaments.kt`).
   - **Names:** the Latin name always in italics.
3. **Colour is quiet but meaningful.**
   - Paper and ink everywhere.
   - Moss marks what's selected or primary.
   - Each season keeps its colour (`seasonColor`): resident is moss, winter is sky, summer is ochre, passage is fern.
   - Rust only for threatened birds.
   - No grey Material defaults: every container uses `Field.colors`.
4. **Hierarchy through type.**
   - Serif display for names and screen titles.
   - Sans for UI labels.
   - The Bangla name is always present, and large in moss on hero moments.
5. **Calm motion.** Crossfades, gentle scale on press, and a parallax hero image on the bird page. No bouncing.
6. **Edge-to-edge.**
   - Hero photos run under the status bar. Use `WindowInsets.statusBars` for the padding of overlaid buttons.
   - The system nav bar is paper-coloured. MainActivity handles this.
7. **Light on the realme C30** (720x1600, low-end).
   - Thumbnails at maxPx 300 or less in lists, stable keys.
   - No blur, no big shadows. Use hairline borders and tonal surfaces instead.

## Shared pieces (`ui/common/`)
- `Badges.kt`: Tag, SeasonTag, IucnTag, StatusTag, SciNameText, seasonColor.
- `BirdImage.kt`: images. Pass `placeholder = false` when layering over a thumbnail.
- `Ornaments.kt`: SectionHeader, OrnamentDivider, PlateNumber, SeasonDot.

## Screens
- **Birds:**
  - A collapsing header with "Aviary".
  - A **Bird of the day** hero card: large plate, names, season, deterministic by date, chosen from species with a season and a photo.
  - A pill search field and moss-filled selected chips.
  - Family headers with an ornament.
  - List rows: plate thumbnail, names, and season dot plus tag.
  - "Plates" grid: museum-label cards.
- **This month:** a hero showing the month name in big serif, a count, and an at-a-glance bar of season proportions, then the sections.
- **Bird page:**
  - A full-bleed parallax hero with a paper fade at the bottom.
  - Names overlapping the fade.
  - An "At a glance" 2x2 grid: In Bangladesh (season or status), Red List, Family, Plate no.
  - Field notes with ochre numbered marks, About, Your sightings, and the family row.
- **My log:** keep the notebook card; align it with the new headers and chips.
