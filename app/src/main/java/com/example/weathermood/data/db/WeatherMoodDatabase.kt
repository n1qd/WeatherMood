package com.example.weathermood.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        FavoriteCityEntity::class,
        MoodRatingEntity::class,
        WeatherCacheEntity::class,
        SyncQueueEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class WeatherMoodDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun favoriteCityDao(): FavoriteCityDao
    abstract fun moodRatingDao(): MoodRatingDao
    abstract fun weatherCacheDao(): WeatherCacheDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        @Volatile private var instance: WeatherMoodDatabase? = null

        fun get(context: Context): WeatherMoodDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WeatherMoodDatabase::class.java,
                    "weathermood.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}




