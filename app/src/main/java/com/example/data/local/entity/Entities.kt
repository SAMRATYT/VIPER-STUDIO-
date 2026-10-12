package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scenes")
data class SceneEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isDefault: Boolean,
    val layersJson: String
)

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val durationSeconds: Long,
    val fileSizeBytes: Long,
    val resolution: String,
    val timestamp: Long
)

@Entity(tableName = "stream_profiles")
data class StreamProfileEntity(
    @PrimaryKey val id: String,
    val platformName: String,
    val streamKey: String,
    val rtmpUrl: String,
    val streamTitle: String,
    val streamDescription: String,
    val resolutionLabel: String,
    val fps: Int,
    val bitrateKbps: Int
)
