package com.leaf.reader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentEntity::class,
        FolderEntity::class,
        TagEntity::class,
        DocumentTagCrossRef::class,
        CollectionEntity::class,
        RecentEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        ReaderStateEntity::class,
        TextCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LeafDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao
    abstract fun collectionDao(): CollectionDao
    abstract fun recentDao(): RecentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
    abstract fun readerStateDao(): ReaderStateDao
    abstract fun textCacheDao(): TextCacheDao

    companion object {
        @Volatile
        private var instance: LeafDatabase? = null

        fun get(context: Context): LeafDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LeafDatabase::class.java,
                "leaf.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
