package com.github.sarwaruddin.aviary.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.github.sarwaruddin.aviary.model.BirdFilter
import com.github.sarwaruddin.aviary.model.BirdSummary
import com.github.sarwaruddin.aviary.model.LifeListEntry
import com.github.sarwaruddin.aviary.model.Sighting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The user's birdwatch log in a small writable sightings.db. */
class SqliteSightingRepository(context: Context) : SightingRepository {
    private val helper = Helper(context.applicationContext)
    private val all = MutableStateFlow<List<Sighting>?>(null)   // null = not loaded yet
    private val mutex = Mutex()
    @Volatile private var birdsById: Map<String, BirdSummary>? = null

    override fun sightings(): Flow<List<Sighting>> = flow {
        if (all.value == null) refresh()
        emitAll(all.filterNotNull())
    }.flowOn(Dispatchers.IO)

    override fun sightingsOf(speciesId: String): Flow<List<Sighting>> =
        sightings().map { list -> list.filter { it.speciesId == speciesId } }.distinctUntilChanged()

    override fun lifeList(): Flow<List<LifeListEntry>> = sightings().map { list ->
        val birds = birdsById ?: AppGraph.birds.birds(BirdFilter(includeRare = true))
            .associateBy { it.id }.also { if (it.isNotEmpty()) birdsById = it }
        lifeListOf(list, birds)
    }.flowOn(Dispatchers.IO)

    override suspend fun add(s: Sighting): Long = write {
        it.insertOrThrow(TABLE, null, s.values())
    }

    override suspend fun update(s: Sighting) {
        write { it.update(TABLE, s.values(), "id = ?", arrayOf(s.id.toString())) }
    }

    override suspend fun delete(id: Long) {
        write { it.delete(TABLE, "id = ?", arrayOf(id.toString())) }
    }

    private suspend fun <T> write(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            block(helper.writableDatabase).also { all.value = query() }
        }
    }

    private suspend fun refresh(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (all.value == null) all.value = query()
        }
    }

    private fun query(): List<Sighting> = helper.readableDatabase.query(
        TABLE, null, null, null, null, null, "time_millis DESC, id DESC",
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(Sighting(
                id = c.getLong(c.col("id")),
                speciesId = c.getString(c.col("species_id")),
                timeMillis = c.getLong(c.col("time_millis")),
                lat = c.dbl("lat"), lon = c.dbl("lon"),
                place = c.getString(c.col("place")),
                count = c.getInt(c.col("count")),
                notes = c.getString(c.col("notes")).orEmpty(),
            ))
        }
    }

    private fun Sighting.values() = ContentValues().apply {
        put("species_id", speciesId)
        put("time_millis", timeMillis)
        put("lat", lat)
        put("lon", lon)
        put("place", place)
        put("count", count)
        put("notes", notes)
    }

    private fun Cursor.col(name: String) = getColumnIndexOrThrow(name)
    private fun Cursor.dbl(name: String): Double? = col(name).let { if (isNull(it)) null else getDouble(it) }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "sightings.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE $TABLE (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    species_id TEXT NOT NULL,
                    time_millis INTEGER NOT NULL,
                    lat REAL, lon REAL, place TEXT,
                    count INTEGER NOT NULL DEFAULT 1,
                    notes TEXT NOT NULL DEFAULT ''
                )""",
            )
            db.execSQL("CREATE INDEX idx_sighting_species ON $TABLE(species_id, time_millis)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        private const val TABLE = "sighting"

        /** Pure: one entry per species seen, newest first-seen first. Unknown species are skipped. */
        fun lifeListOf(sightings: List<Sighting>, birds: Map<String, BirdSummary>): List<LifeListEntry> =
            sightings.groupBy { it.speciesId }.mapNotNull { (sp, list) ->
                birds[sp]?.let { LifeListEntry(it, list.minOf { s -> s.timeMillis }, list.size) }
            }.sortedByDescending { it.firstSeenMillis }
    }
}
