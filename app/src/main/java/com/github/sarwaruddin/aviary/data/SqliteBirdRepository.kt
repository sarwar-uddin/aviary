package com.github.sarwaruddin.aviary.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.github.sarwaruddin.aviary.model.BdStatus
import com.github.sarwaruddin.aviary.model.Bird
import com.github.sarwaruddin.aviary.model.BirdFilter
import com.github.sarwaruddin.aviary.model.BirdSummary
import com.github.sarwaruddin.aviary.model.Credit
import com.github.sarwaruddin.aviary.model.Family
import com.github.sarwaruddin.aviary.model.ImageRef
import com.github.sarwaruddin.aviary.model.Iucn
import com.github.sarwaruddin.aviary.model.Photo
import com.github.sarwaruddin.aviary.model.Recording
import com.github.sarwaruddin.aviary.model.Season
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/** [BirdRepository] over the bundled assets/aviary.db, opened read-only with plain SQLite. */
class SqliteBirdRepository(context: Context) : BirdRepository {
    private val app = context.applicationContext
    private val mutex = Mutex()
    @Volatile private var db: SQLiteDatabase? = null
    @Volatile private var cache: Cache? = null

    private class Cache(
        val families: List<Family>,
        val familyById: Map<String, Family>,
        val birds: List<BirdSummary>,
        val meta: Map<String, String>,
    )

    override suspend fun families(): List<Family> = io { cache().families }

    override suspend fun birds(filter: BirdFilter): List<BirdSummary> =
        io { BirdSearch.apply(cache().birds, filter) }

    override suspend fun aroundInMonth(month: Int): List<BirdSummary> =
        io { BirdSearch.aroundInMonth(cache().birds, month) }

    override suspend fun meta(): Map<String, String> = io { cache().meta }

    override suspend fun bird(id: String): Bird? = io {
        val c = cache()
        val summary = c.birds.firstOrNull { it.id == id } ?: return@io null
        val d = db ?: return@io null
        d.rawQuery(
            "SELECT description_en, summary_en, summary_bn, facts, wiki_en, wiki_bn, ebird_code FROM species WHERE id = ?",
            arrayOf(id),
        ).use { cur ->
            if (!cur.moveToFirst()) return@io null
            Bird(
                summary = summary,
                family = c.familyById[summary.familyId]
                    ?: Family(summary.familyId, summary.familyId, "", "", null, Int.MAX_VALUE),
                descriptionEn = cur.str(0),
                summaryEn = cur.str(1),
                summaryBn = cur.str(2),
                facts = parseFacts(cur.str(3)),
                photos = photos(d, id),
                wikiEn = cur.str(4),
                wikiBn = cur.str(5),
                ebirdCode = cur.str(6),
                recordings = recordings(d, id),
            )
        }
    }

    private fun photos(d: SQLiteDatabase, id: String): List<Photo> = d.rawQuery(
        """SELECT local_path, small_url, thumb_url, url, author, license, license_url, page_url, source
           FROM media WHERE species_id = ? AND kind = 'photo' ORDER BY rank""",
        arrayOf(id),
    ).use { cur ->
        buildList {
            while (cur.moveToNext()) {
                val url = cur.str(3)
                add(Photo(
                    small = ImageRef(cur.str(0), cur.str(1)),
                    large = ImageRef(null, cur.str(2) ?: url),
                    original = url,
                    credit = Credit(cur.str(4), cur.str(5), cur.str(6), cur.str(7), cur.str(8) ?: "commons"),
                ))
            }
        }
    }

    /** xeno-canto recordings if there are any (best first), else Commons audio. */
    private fun recordings(d: SQLiteDatabase, id: String): List<Recording> = d.rawQuery(
        """SELECT url, thumb_url, extra, author, license, license_url, page_url, source
           FROM media WHERE species_id = ? AND kind = 'audio'
           ORDER BY (source = 'xeno-canto') DESC, rank""",
        arrayOf(id),
    ).use { cur ->
        val all = buildList {
            while (cur.moveToNext()) {
                val extra = cur.str(2)?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
                fun ex(k: String) = extra?.optString(k)?.takeIf { it.isNotBlank() && it != "null" }
                add(Recording(
                    url = cur.str(0) ?: continue,
                    sonogram = cur.str(1),
                    type = ex("call_type"),
                    country = ex("country"),
                    locality = ex("locality"),
                    length = ex("length"),
                    credit = Credit(cur.str(3), cur.str(4), cur.str(5), cur.str(6), cur.str(7) ?: "commons"),
                ))
            }
        }
        val xc = all.filter { it.credit.source == "xeno-canto" }
        if (xc.isNotEmpty()) xc else all.take(2)
    }

    // ---- loading ----

    private suspend fun cache(): Cache = cache ?: mutex.withLock {
        cache ?: try {
            load().also { cache = it }
        } catch (e: Exception) {
            Log.e(TAG, "Could not load aviary.db", e)
            Cache(emptyList(), emptyMap(), emptyList(), emptyMap())   // not cached: retry next call
        }
    }

    private fun load(): Cache {
        val d = db ?: SQLiteDatabase.openDatabase(
            prepareDb().path, null, SQLiteDatabase.OPEN_READONLY,
        ).also { db = it }

        val families = d.rawQuery(
            "SELECT id, name, common_name, order_name, blurb, sort FROM family ORDER BY sort", null,
        ).use { cur ->
            buildList {
                while (cur.moveToNext()) add(Family(
                    cur.getString(0), cur.getString(1), cur.getString(2), cur.getString(3),
                    cur.str(4), cur.getInt(5),
                ))
            }
        }

        val thumbs = HashMap<String, ImageRef>()
        d.rawQuery(
            "SELECT species_id, local_path, small_url FROM media WHERE kind = 'photo' ORDER BY species_id, rank", null,
        ).use { cur ->
            while (cur.moveToNext()) {
                val sp = cur.getString(0)
                if (sp !in thumbs) thumbs[sp] = ImageRef(cur.str(1), cur.str(2))
            }
        }

        val birds = d.rawQuery(
            "SELECT id, sort, family_id, name_en, name_bn, name_sci, bd_status, season, iucn FROM species ORDER BY sort", null,
        ).use { cur ->
            buildList {
                while (cur.moveToNext()) {
                    val id = cur.getString(0)
                    add(BirdSummary(
                        id = id, sort = cur.getInt(1), familyId = cur.getString(2),
                        nameEn = cur.getString(3), nameBn = cur.str(4), nameSci = cur.getString(5),
                        status = BdStatus.parse(cur.str(6)), season = Season.parse(cur.str(7)),
                        iucn = Iucn.parse(cur.str(8)), thumb = thumbs[id],
                    ))
                }
            }
        }

        return Cache(families, families.associateBy { it.id }, birds, readMeta(d))
    }

    /**
     * Makes sure databases/aviary.db holds the bundled dataset. The asset is re-extracted only
     * when the APK changed (PackageInfo.lastUpdateTime), and swapped in only when its
     * meta.data_version differs from the installed copy.
     */
    private fun prepareDb(): File {
        val target = app.getDatabasePath(DB_NAME)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val apkTime = runCatching {
            @Suppress("DEPRECATION")
            app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime
        }.getOrDefault(-1L)
        if (target.exists() && prefs.getLong(KEY_APK_TIME, 0L) == apkTime) return target

        target.parentFile?.mkdirs()
        val fresh = File(target.parentFile, "$DB_NAME.new")
        app.assets.open(DB_NAME).use { input -> fresh.outputStream().use { input.copyTo(it) } }
        val bundled = dataVersion(fresh)
        val installed = if (target.exists()) dataVersion(target) else null
        if (installed == null || installed != bundled) {
            SQLiteDatabase.deleteDatabase(target)   // also removes -journal / -wal leftovers
            if (!fresh.renameTo(target)) {
                fresh.copyTo(target, overwrite = true); fresh.delete()
            }
            Log.i(TAG, "Installed aviary.db data_version=$bundled (was $installed)")
        } else {
            fresh.delete()
        }
        prefs.edit().putLong(KEY_APK_TIME, apkTime).apply()
        return target
    }

    private fun dataVersion(f: File): String? = runCatching {
        SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY).use { readMeta(it)["data_version"] }
    }.getOrNull()

    private fun readMeta(d: SQLiteDatabase): Map<String, String> =
        d.rawQuery("SELECT key, value FROM meta", null).use { cur ->
            buildMap { while (cur.moveToNext()) put(cur.getString(0), cur.str(1).orEmpty()) }
        }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        private const val TAG = "AviaryDb"
        private const val DB_NAME = "aviary.db"
        private const val PREFS = "aviary_db"
        private const val KEY_APK_TIME = "asset_apk_time"

        internal fun parseFacts(json: String?): List<String> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val a = JSONArray(json)
                (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
            }.getOrDefault(emptyList())
        }
    }
}

private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)
