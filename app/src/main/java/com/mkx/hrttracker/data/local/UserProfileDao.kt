package com.mkx.hrttracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query(
        """
        SELECT * FROM user_profile
        WHERE id = :id
        LIMIT 1
        """
    )
    suspend fun getProfile(id: String = UserProfileEntity.SINGLETON_ID): UserProfileEntity?

    @Query(
        """
        SELECT * FROM user_profile
        WHERE id = :id
        LIMIT 1
        """
    )
    fun observeProfile(id: String = UserProfileEntity.SINGLETON_ID): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query(
        """
        DELETE FROM user_profile
        """
    )
    suspend fun deleteProfile()
}
