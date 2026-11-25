package com.github.sarwaruddin.aviary.data

import com.github.sarwaruddin.aviary.model.BdStatus
import com.github.sarwaruddin.aviary.model.BirdFilter
import com.github.sarwaruddin.aviary.model.BirdSummary
import com.github.sarwaruddin.aviary.model.Iucn
import com.github.sarwaruddin.aviary.model.LifeListEntry
import com.github.sarwaruddin.aviary.model.Season
import com.github.sarwaruddin.aviary.model.Sighting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BirdSearchTest {
    private fun b(
        sort: Int, en: String, sci: String, bn: String? = null,
        status: BdStatus = BdStatus.REGULAR, season: Season? = null, iucn: Iucn? = null, fam: String = "f",
    ) = BirdSummary(sci.lowercase().replace(' ', '-'), sort, fam, en, bn, sci, status, season, iucn, null)

    private val robin = b(1, "Oriental Magpie-Robin", "Copsychus saularis", bn = "দোয়েল", season = Season.RESIDENT)
    private val tickell = b(2, "Tickell's Blue Flycatcher", "Cyornis tickelliae", season = Season.RESIDENT)
    private val magpie = b(3, "Red-billed Blue Magpie", "Urocissa erythroryncha", season = Season.WINTER, iucn = Iucn.LC)
    private val stork = b(4, "Greater Adjutant", "Leptoptilos dubius", status = BdStatus.EXTIRPATED, iucn = Iucn.EN, fam = "ciconiidae")
    private val vagrant = b(5, "Magpie Shrike", "Lanius x", status = BdStatus.ACCIDENTAL, season = Season.VAGRANT)
    private val all = listOf(vagrant, stork, magpie, tickell, robin)

    private fun ids(l: List<BirdSummary>) = l.map { it.nameEn }

    @Test fun normalizeAndLoose() {
        assertEquals("tickell's blue flycatcher", BirdSearch.normalize("  Tickell's   Blue FLYCATCHER "))
        assertEquals("tickellsblueflycatcher", BirdSearch.loose("Tickell’s Blue-Flycatcher"))
    }

    @Test fun blankQueryKeepsTaxonomicOrderAndHidesRare() {
        assertEquals(ids(listOf(robin, tickell, magpie)), ids(BirdSearch.apply(all, BirdFilter())))
        assertEquals(5, BirdSearch.apply(all, BirdFilter(includeRare = true)).size)
    }

    @Test fun prefixRanksBeforeWordPrefixAndSubstring() {
        val r = BirdSearch.apply(all, BirdFilter(query = "magpie", includeRare = true))
        // "Magpie Shrike" is a name prefix; robin/blue magpie match on a word.
        assertEquals(ids(listOf(vagrant, robin, magpie)), ids(r))
    }

    @Test fun looseMatchIgnoresHyphensAndApostrophes() {
        assertEquals(ids(listOf(tickell)), ids(BirdSearch.apply(all, BirdFilter(query = "tickells"))))
        assertEquals(ids(listOf(robin)), ids(BirdSearch.apply(all, BirdFilter(query = "magpierobin"))))
        assertEquals(ids(listOf(magpie)), ids(BirdSearch.apply(all, BirdFilter(query = "red billed"))))
    }

    @Test fun matchesScientificAndBangla() {
        assertEquals(ids(listOf(robin)), ids(BirdSearch.apply(all, BirdFilter(query = "COPSYCHUS"))))
        assertEquals(ids(listOf(robin)), ids(BirdSearch.apply(all, BirdFilter(query = "দোয়েল"))))
        assertNull(BirdSearch.score(robin, "heron"))
        assertEquals(0, BirdSearch.score(robin, "copsychus saularis"))
    }

    @Test fun facets() {
        assertEquals(ids(listOf(stork)), ids(BirdSearch.apply(all, BirdFilter(threatenedOnly = true, includeRare = true))))
        assertEquals(ids(listOf(stork)), ids(BirdSearch.apply(all, BirdFilter(familyId = "ciconiidae", includeRare = true))))
        assertEquals(ids(listOf(magpie)), ids(BirdSearch.apply(all, BirdFilter(seasons = setOf(Season.WINTER)))))
    }

    @Test fun aroundInMonth() {
        assertEquals(ids(listOf(robin, tickell, magpie)), ids(BirdSearch.aroundInMonth(all, 1)))
        assertEquals(ids(listOf(robin, tickell)), ids(BirdSearch.aroundInMonth(all, 7)))
    }

    @Test fun lifeList() {
        val s = listOf(
            Sighting(1, robin.id, 300), Sighting(2, robin.id, 100), Sighting(3, magpie.id, 200),
            Sighting(4, "unknown", 999),
        )
        val l = SqliteSightingRepository.lifeListOf(s, all.associateBy { it.id })
        assertEquals(listOf(LifeListEntry(magpie, 200, 1), LifeListEntry(robin, 100, 2)), l)
    }
}
