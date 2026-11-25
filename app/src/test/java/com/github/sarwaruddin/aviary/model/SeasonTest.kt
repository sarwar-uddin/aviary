package com.github.sarwaruddin.aviary.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SeasonTest {
    private fun months(s: Season) = (1..12).filter(s::presentIn)

    @Test fun presentIn() {
        assertEquals((1..12).toList(), months(Season.RESIDENT))
        assertEquals(listOf(1, 2, 3, 10, 11, 12), months(Season.WINTER))
        assertEquals((3..9).toList(), months(Season.SUMMER))
        assertEquals(listOf(3, 4, 5, 9, 10, 11), months(Season.PASSAGE))
        assertEquals(emptyList<Int>(), months(Season.VAGRANT))
    }

    @Test fun parse() {
        assertEquals(Season.WINTER, Season.parse("winter"))
        assertEquals(null, Season.parse(null))
        assertEquals(BdStatus.EXTIRPATED, BdStatus.parse("Extirpated"))
        assertEquals(BdStatus.REGULAR, BdStatus.parse("???"))
        assertEquals(Iucn.EN, Iucn.parse("EN"))
    }
}
