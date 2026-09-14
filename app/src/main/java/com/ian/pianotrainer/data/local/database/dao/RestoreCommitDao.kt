package com.ian.pianotrainer.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ian.pianotrainer.data.local.database.entity.RestoreCommitEntity

@Dao
interface RestoreCommitDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(marker: RestoreCommitEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM restore_commits WHERE operationId = :operationId)")
    suspend fun isCommitted(operationId: String): Boolean

    @Query("DELETE FROM restore_commits WHERE operationId = :operationId")
    suspend fun delete(operationId: String)
}
