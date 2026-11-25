package com.github.sarwaruddin.aviary.data

import com.github.sarwaruddin.aviary.model.BdStatus
import com.github.sarwaruddin.aviary.model.BirdFilter
import com.github.sarwaruddin.aviary.model.BirdSummary
import java.text.Normalizer
import java.util.Locale

/** Pure, Android-free filtering and search ranking over [BirdSummary] lists (unit-tested). */
object BirdSearch {
    private val WS = Regex("\\s+")
    private val LOOSE = Regex("[\\s\\-\\u2010\\u2011\\u2012\\u2013\\u2014'\\u2018\\u2019\\u02BC`]")
    private val WORD_SPLIT = Regex("[\\s\\-\\u2010\\u2011]+")

    /** NFC (so composed/decomposed Bangla letters compare equal), lowercase, trim, collapse whitespace. */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFC).lowercase(Locale.ROOT).trim().replace(WS, " ")

    /** [normalize], then drop hyphens, apostrophes and spaces: "Tickell's Blue-Flycatcher" -> "tickellsblueflycatcher". */
    fun loose(s: String): String = normalize(s).replace(LOOSE, "")

    /**
     * Match score for [b] against [query] (lower is better), or null if it doesn't match.
     * 0 exact name, 1 name prefix, 2 word prefix / loose prefix, 3 substring, 4 loose substring.
     */
    fun score(b: BirdSummary, query: String): Int? {
        val q = normalize(query)
        if (q.isEmpty()) return 0
        val lq = loose(q)
        val names = listOfNotNull(b.nameEn, b.nameBn, b.nameSci).map(::normalize)
        return when {
            names.any { it == q } -> 0
            names.any { it.startsWith(q) } -> 1
            names.any { n -> n.split(WORD_SPLIT).any { it.startsWith(q) } } -> 2
            lq.isNotEmpty() && names.any { loose(it).startsWith(lq) } -> 2
            names.any { it.contains(q) } -> 3
            lq.isNotEmpty() && names.any { loose(it).contains(lq) } -> 4
            else -> null
        }
    }

    /** Matches for [query], best first; ties (and a blank query) keep taxonomic order. */
    fun search(list: List<BirdSummary>, query: String): List<BirdSummary> {
        if (normalize(query).isEmpty()) return list.sortedBy { it.sort }
        return list.mapNotNull { b -> score(b, query)?.let { it to b } }
            .sortedWith(compareBy({ it.first }, { it.second.sort }))
            .map { it.second }
    }

    /** Everything in [filter] except the query. */
    fun matchesFacets(b: BirdSummary, f: BirdFilter): Boolean =
        (f.familyId == null || b.familyId == f.familyId) &&
            (f.seasons.isEmpty() || b.season in f.seasons) &&
            (!f.threatenedOnly || b.iucn?.threatened == true) &&
            (f.includeRare || (b.status != BdStatus.ACCIDENTAL && b.status != BdStatus.EXTIRPATED))

    fun apply(list: List<BirdSummary>, f: BirdFilter): List<BirdSummary> =
        search(list.filter { matchesFacets(it, f) }, f.query)

    /** Species expected in [month] by curated season, taxonomic order. */
    fun aroundInMonth(list: List<BirdSummary>, month: Int): List<BirdSummary> =
        list.filter { it.season?.presentIn(month) == true }.sortedBy { it.sort }
}
