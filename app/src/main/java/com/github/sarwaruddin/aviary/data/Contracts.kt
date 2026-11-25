package com.github.sarwaruddin.aviary.data

import android.content.Context
import com.github.sarwaruddin.aviary.model.Bird
import com.github.sarwaruddin.aviary.model.BirdFilter
import com.github.sarwaruddin.aviary.model.BirdSummary
import com.github.sarwaruddin.aviary.model.Family
import com.github.sarwaruddin.aviary.model.LifeListEntry
import com.github.sarwaruddin.aviary.model.Sighting
import kotlinx.coroutines.flow.Flow

/**
 * Read-only bird dataset, backed by the bundled assets/aviary.db
 * (copied to app storage on first run / when the bundled data_version changes).
 * All functions are main-safe (they switch to Dispatchers.IO).
 */
interface BirdRepository {
    suspend fun families(): List<Family>
    /** All species matching [filter], in taxonomic order (sort). */
    suspend fun birds(filter: BirdFilter = BirdFilter()): List<BirdSummary>
    suspend fun bird(id: String): Bird?
    /** Species expected in Bangladesh in [month] (1-12), by curated season. */
    suspend fun aroundInMonth(month: Int): List<BirdSummary>
    /** Bundled dataset info from the meta table (data_version, built_at, sources...). */
    suspend fun meta(): Map<String, String>
}

/** The user's own birdwatch log. Separate writable DB (sightings.db). */
interface SightingRepository {
    fun sightings(): Flow<List<Sighting>>                 // newest first
    fun sightingsOf(speciesId: String): Flow<List<Sighting>>
    fun lifeList(): Flow<List<LifeListEntry>>             // newest first-seen first
    suspend fun add(s: Sighting): Long
    suspend fun update(s: Sighting)
    suspend fun delete(id: Long)
}

/** Simple service locator, initialised in MainActivity. */
object AppGraph {
    lateinit var birds: BirdRepository
        private set
    lateinit var sightings: SightingRepository
        private set

    fun init(context: Context) {
        if (::birds.isInitialized) return
        val app = context.applicationContext
        birds = SqliteBirdRepository(app)
        sightings = SqliteSightingRepository(app)
        ImageLoader.init(app)
        AudioPlayer.init(app)
    }
}
