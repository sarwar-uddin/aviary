package com.github.sarwaruddin.aviary.model

/**
 * Shared data contracts. Mirrors data/SCHEMA.md (aviary.db).
 * Everyone codes against these; change them only in the integration pass.
 */

data class Family(
    val id: String,          // "anatidae"
    val name: String,        // "Anatidae"
    val commonName: String,  // "Ducks, geese, and waterfowl"
    val order: String,       // "Anseriformes"
    val blurb: String?,
    val sort: Int,
)

enum class BdStatus { REGULAR, ACCIDENTAL, INTRODUCED, EXTIRPATED;
    companion object {
        fun parse(s: String?) = entries.firstOrNull { it.name.equals(s, true) } ?: REGULAR
    }
}

/** When a bird is in Bangladesh. Curated; null = not yet known. */
enum class Season(val label: String, val labelBn: String) {
    RESIDENT("Resident", "স্থায়ী"),
    WINTER("Winter visitor", "শীতের পাখি"),
    SUMMER("Summer visitor", "গ্রীষ্মের পাখি"),
    PASSAGE("Passage migrant", "পরিযায়ী (পথচারী)"),
    VAGRANT("Vagrant", "বিরল আগন্তুক");

    /** Is this kind of bird expected in Bangladesh in [month] (1-12)? */
    fun presentIn(month: Int): Boolean = when (this) {
        RESIDENT -> true
        WINTER -> month >= 10 || month <= 3
        SUMMER -> month in 3..9
        PASSAGE -> month in 3..5 || month in 9..11
        VAGRANT -> false
    }

    companion object {
        fun parse(s: String?) = entries.firstOrNull { it.name.equals(s, true) }
    }
}

/** IUCN global Red List category. */
enum class Iucn(val code: String, val label: String, val threatened: Boolean) {
    LC("LC", "Least concern", false),
    NT("NT", "Near threatened", false),
    VU("VU", "Vulnerable", true),
    EN("EN", "Endangered", true),
    CR("CR", "Critically endangered", true),
    EW("EW", "Extinct in the wild", true),
    EX("EX", "Extinct", true),
    DD("DD", "Data deficient", false),
    NE("NE", "Not evaluated", false);

    companion object {
        fun parse(s: String?) = entries.firstOrNull { it.code == s }
    }
}

/**
 * Where to get a picture: a bundled asset (offline) and/or a URL (online, cached).
 * Loaders try [asset] first, then [url].
 */
data class ImageRef(val asset: String?, val url: String?)

data class Photo(
    val small: ImageRef,     // ~330px: lists, cards (asset = bundled thumbnail, first photo only)
    val large: ImageRef,     // ~960px: detail / viewer (online)
    val original: String?,
    val credit: Credit,
)

data class Credit(
    val author: String?,
    val license: String?,
    val licenseUrl: String?,
    val pageUrl: String?,    // Commons file page; link it in the credits
    val source: String,      // "commons" | "xeno-canto"
)

/** One row in lists. Cheap to load for all ~830 species. */
data class BirdSummary(
    val id: String,
    val sort: Int,
    val familyId: String,
    val nameEn: String,
    val nameBn: String?,
    val nameSci: String,
    val status: BdStatus,
    val season: Season?,
    val iucn: Iucn?,
    val thumb: ImageRef?,
)

/** A sound recording (xeno-canto or Commons). Streams online, cached after first play. */
data class Recording(
    val url: String,
    val sonogram: String?,   // spectrogram image (xeno-canto)
    val type: String?,       // "song", "call", "flight call, alarm call"...
    val country: String?,
    val locality: String?,
    val length: String?,     // "0:27"
    val credit: Credit,
)

data class Bird(
    val summary: BirdSummary,
    val family: Family,
    val descriptionEn: String?,   // short Wikipedia description, e.g. "species of bird"
    val summaryEn: String?,       // Wikipedia intro, paragraphs separated by "\n\n"
    val summaryBn: String?,
    val facts: List<String>,
    val photos: List<Photo>,      // ordered, best first
    val wikiEn: String?,          // article title -> https://en.wikipedia.org/wiki/<title>
    val wikiBn: String?,
    val ebirdCode: String?,
    val recordings: List<Recording> = emptyList(),
) {
    val id get() = summary.id
}

data class BirdFilter(
    val query: String = "",               // matches English, Bangla, scientific names
    val familyId: String? = null,
    val seasons: Set<Season> = emptySet(), // empty = any
    val threatenedOnly: Boolean = false,
    val includeRare: Boolean = false,      // accidental + extirpated hidden by default
)

/** A birdwatch log entry. */
data class Sighting(
    val id: Long = 0,
    val speciesId: String,
    val timeMillis: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val place: String? = null,
    val count: Int = 1,
    val notes: String = "",
)

data class LifeListEntry(
    val bird: BirdSummary,
    val firstSeenMillis: Long,
    val sightings: Int,
)
