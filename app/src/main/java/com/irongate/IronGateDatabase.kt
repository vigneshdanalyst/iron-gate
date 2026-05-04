package com.irongate

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String
)

@Entity(tableName = "lock_sessions")
data class LockSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startedAtMillis: Long,
    val endsAtMillis: Long,
    val isActive: Boolean,
    val isSpartan: Boolean,
    val unlockReason: String? = null
)

@Entity(tableName = "attempt_logs")
data class AttemptLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMillis: Long,
    val type: String,
    val packageName: String? = null,
    val details: String
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0,
    val spartanModeEnabled: Boolean = false,
    val cooldownSeconds: Int = 60,
    val trustedFriendEmail: String = ""
)

@Dao
interface IronGateDao {
    @Query("SELECT * FROM blocked_apps ORDER BY appName ASC")
    fun observeBlockedApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT packageName FROM blocked_apps")
    suspend fun getBlockedPackageNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlockedApps(apps: List<BlockedAppEntity>)

    @Query("DELETE FROM blocked_apps")
    suspend fun clearBlockedApps()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLockSession(session: LockSessionEntity): Long

    @Query("SELECT * FROM lock_sessions WHERE isActive = 1 ORDER BY id DESC LIMIT 1")
    fun observeActiveLockSession(): Flow<LockSessionEntity?>

    @Query("SELECT * FROM lock_sessions WHERE isActive = 1 ORDER BY id DESC LIMIT 1")
    suspend fun getActiveLockSession(): LockSessionEntity?

    @Query(
        "UPDATE lock_sessions SET isActive = 0, unlockReason = :reason " +
            "WHERE id = (SELECT id FROM lock_sessions WHERE isActive = 1 ORDER BY id DESC LIMIT 1)"
    )
    suspend fun closeActiveSession(reason: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttemptLog(log: AttemptLogEntity)

    @Query("SELECT * FROM attempt_logs ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int = 100): Flow<List<AttemptLogEntity>>

    @Query("SELECT * FROM attempt_logs ORDER BY timestampMillis DESC")
    suspend fun getAllLogs(): List<AttemptLogEntity>

    @Query("SELECT * FROM settings WHERE id = 0")
    fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 0")
    suspend fun getSettings(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: SettingsEntity)
}

@Database(
    entities = [
        BlockedAppEntity::class,
        LockSessionEntity::class,
        AttemptLogEntity::class,
        SettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class IronGateDatabase : RoomDatabase() {
    abstract fun dao(): IronGateDao

    companion object {
        @Volatile
        private var INSTANCE: IronGateDatabase? = null

        fun getInstance(context: Context): IronGateDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    IronGateDatabase::class.java,
                    "iron_gate.db"
                ).build().also { db ->
                    INSTANCE = db
                }
            }
        }
    }
}
