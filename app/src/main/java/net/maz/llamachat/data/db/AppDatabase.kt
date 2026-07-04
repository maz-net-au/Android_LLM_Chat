package net.maz.llamachat.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ConversationEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    companion object {
        /** v2 adds the per-conversation `userName` ({{user}} substitution). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN userName TEXT NOT NULL DEFAULT 'user'")
            }
        }

        /** v3 adds per-conversation sampling overrides, stored as a JSON blob. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN samplingJson TEXT NOT NULL DEFAULT '{}'")
            }
        }

        /** v4 adds the running conversation summary (the compacted older messages). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN summary TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v5 caches the last measured transcript token count so the context readout can
         *  show on reopen without re-running `/tokenize` (which forces the model to load).
         *  Existing rows default to 0 ("never measured"), refreshed after the next reply. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN tokenCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v6 caches the context window (`n_ctx`) alongside the token count, so the full
         *  "/ limit · %" readout also shows on reopen. Existing rows default to 0 (unknown). */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN contextLimit INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "llamachat.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
            }
    }
}
