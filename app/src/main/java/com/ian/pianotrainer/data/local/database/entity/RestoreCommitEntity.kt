package com.ian.pianotrainer.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "restore_commits")
data class RestoreCommitEntity(
    @PrimaryKey val operationId: String,
    val committedAt: Long
)
