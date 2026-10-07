package `in`.mite.rain.platform

import android.content.Context
import androidx.room.*
import `in`.mite.rain.core.CsvGraphLoader
import `in`.mite.rain.core.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** Versioned, validated asset snapshot. Re-import automatically when packaged CSVs change. */
@Entity(tableName = "floor_snapshots")
data class FloorSnapshot(@PrimaryKey val id: Int = 1, val digest: String, val rooms: String, val edges: String)
@Dao
interface FloorDao {
    @Query("SELECT * FROM floor_snapshots WHERE id = 1") suspend fun get(): FloorSnapshot?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(snapshot: FloorSnapshot)
}
@Database(entities = [FloorSnapshot::class], version = 1, exportSchema = true)
abstract class RainDatabase : RoomDatabase() { abstract fun floors(): FloorDao }

class GraphRepository(private val context: Context) {
    suspend fun load(): Graph = withContext(Dispatchers.IO) {
        val rooms = context.assets.open("rooms.csv").bufferedReader().use { it.readText() }
        val edges = context.assets.open("edges.csv").bufferedReader().use { it.readText() }
        val digest = MessageDigest.getInstance("SHA-256").digest((rooms + "\u0000" + edges).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val db = Room.databaseBuilder(context.applicationContext, RainDatabase::class.java, "rain.db").build()
        try {
            val cached = db.floors().get()
            if (cached?.digest == digest) CsvGraphLoader.load(cached.rooms, cached.edges)
            else CsvGraphLoader.load(rooms, edges).also { db.floors().put(FloorSnapshot(digest = digest, rooms = rooms, edges = edges)) }
        } finally { db.close() }
    }
}
