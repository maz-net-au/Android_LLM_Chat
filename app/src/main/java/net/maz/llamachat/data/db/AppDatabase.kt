package net.maz.llamachat.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConversationEntity::class, GalleryItemEntity::class],
    version = 8,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun galleryDao(): GalleryDao

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

        /** v7 adds the generated-media gallery table (ComfyUI results). The SQL must
         *  match Room's expected schema for [GalleryItemEntity] exactly — there is no
         *  destructive-migration fallback. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gallery_items` (" +
                        "`id` INTEGER NOT NULL, `flowType` TEXT NOT NULL, " +
                        "`workflowName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`fileName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        /** v8 snapshots the generating request onto each gallery row (`workflowId`
         *  + `inputsJson`) so the prompt survives and regenerate works after a
         *  restart. Existing rows default to -1/'[]' (no recoverable request). */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE gallery_items ADD COLUMN workflowId INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE gallery_items ADD COLUMN inputsJson TEXT NOT NULL DEFAULT '[]'")
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
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                ).build().also { instance = it }
            }
    }
}
