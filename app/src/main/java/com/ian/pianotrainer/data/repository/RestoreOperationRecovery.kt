package com.ian.pianotrainer.data.repository

import android.content.Context
import com.ian.pianotrainer.data.local.database.PianoTrainerDatabase
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun interface RestoreMetadataPublishFaultInjector {
    fun afterTempFileSynced()
    companion object { val NONE = RestoreMetadataPublishFaultInjector { } }
}

internal enum class RestoreJournalState {
    PREPARED,
    PRESERVE_SONGS_INTENT, PRESERVE_SONGS_DONE,
    PRESERVE_RECORDINGS_INTENT, PRESERVE_RECORDINGS_DONE,
    PROMOTE_SONGS_INTENT, PROMOTE_SONGS_DONE,
    PROMOTE_RECORDINGS_INTENT, PROMOTE_RECORDINGS_DONE,
    FILES_SWAPPED
}

internal class RestoreOperationRecovery(
    private val context: Context,
    private val database: PianoTrainerDatabase,
    private val renamer: SongFileRenamer = SongFileRenamer.DEFAULT,
    private val metadataFaultInjector: RestoreMetadataPublishFaultInjector = RestoreMetadataPublishFaultInjector.NONE
) {
    suspend fun reconcile(operationDir: File) {
        val meta = readMeta(operationDir)
        val operationId = meta.required("operationId")
        if (database.restoreCommitDao().isCommitted(operationId)) finalizeCommitted(operationDir, meta)
        else rollbackUncommitted(operationDir, meta)
    }

    fun writeState(operationDir: File, state: RestoreJournalState) {
        val meta = readMeta(operationDir).toMutableMap()
        meta["state"] = state.name
        writeMetaAtomic(operationDir, meta)
    }

    fun create(
        operationDir: File,
        operationId: String,
        hadSongs: Boolean,
        oldSongsDigest: String,
        newSongsDigest: String,
        hadRecordings: Boolean,
        oldRecordingsDigest: String,
        newRecordingsDigest: String
    ) {
        writeMetaAtomic(operationDir, linkedMapOf(
            "kind" to "RESTORE", "operationId" to operationId, "state" to RestoreJournalState.PREPARED.name,
            "hadSongs" to hadSongs.toString(), "oldSongsDigest" to oldSongsDigest, "newSongsDigest" to newSongsDigest,
            "hadRecordings" to hadRecordings.toString(), "oldRecordingsDigest" to oldRecordingsDigest,
            "newRecordingsDigest" to newRecordingsDigest
        ))
    }

    private suspend fun finalizeCommitted(op: File, meta: Map<String, String>) {
        ensureCommittedResource(op, "Songs", meta.required("newSongsDigest"))
        ensureCommittedResource(op, "Recordings", meta.required("newRecordingsDigest"))
        if (!op.deleteRecursively() && op.exists()) throw IOException("Cannot clean committed restore journal; retained for retry")
        database.restoreCommitDao().delete(meta.required("operationId"))
    }

    private fun ensureCommittedResource(op: File, suffix: String, expectedDigest: String) {
        val active = File(context.filesDir, suffix.lowercase())
        val staged = File(op, "new$suffix")
        val activeDigest = treeDigest(active)
        if (activeDigest != expectedDigest) {
            if (!active.exists() && staged.exists() && renamer.rename(staged, active)) {
                // Re-check below.
            } else throw IOException("Committed restore $suffix files are ambiguous; all copies retained")
        }
        if (treeDigest(active) != expectedDigest) throw IOException("Committed restore $suffix hash mismatch; journal retained")
    }

    private fun rollbackUncommitted(op: File, meta: Map<String, String>) {
        rollbackResource(op, "Songs", meta.required("hadSongs").toBooleanStrict(), meta.required("oldSongsDigest"), meta.required("newSongsDigest"))
        rollbackResource(op, "Recordings", meta.required("hadRecordings").toBooleanStrict(), meta.required("oldRecordingsDigest"), meta.required("newRecordingsDigest"))
        if (!op.deleteRecursively() && op.exists()) throw IOException("Cannot clean rolled-back restore journal; retained for retry")
    }

    private fun rollbackResource(op: File, suffix: String, hadExisting: Boolean, oldDigest: String, newDigest: String) {
        val active = File(context.filesDir, suffix.lowercase())
        val old = File(op, "old$suffix")
        val fresh = File(op, "new$suffix")

        if (old.exists()) {
            if (treeDigest(old) != oldDigest) throw IOException("Preserved old $suffix hash mismatch; copies retained")
            if (active.exists()) {
                if (fresh.exists()) throw IOException("Ambiguous $suffix rollback topology; copies retained")
                if (treeDigest(active) != newDigest) throw IOException("Active $suffix is neither verified old nor new data")
                if (!renamer.rename(active, fresh)) throw IOException("Cannot move uncommitted $suffix aside; journal retained")
            }
            if (!renamer.rename(old, active)) throw IOException("Cannot restore old $suffix; journal retained")
        } else if (hadExisting) {
            if (!active.exists() || treeDigest(active) != oldDigest) throw IOException("Old $suffix location is ambiguous; journal retained")
            // PREPARED or preserve intent before rename: active library was never moved.
        } else if (active.exists()) {
            if (fresh.exists() || treeDigest(active) != newDigest) throw IOException("New $suffix location is ambiguous; journal retained")
            if (!renamer.rename(active, fresh)) throw IOException("Cannot undo promoted $suffix; journal retained")
        }

        if (hadExisting) {
            if (!active.exists() || treeDigest(active) != oldDigest) throw IOException("Rollback verification failed for $suffix")
        } else if (active.exists()) throw IOException("Rollback expected no active $suffix directory")
    }

    private fun readMeta(op: File): Map<String, String> {
        val primary = File(op, "operation.properties")
        val temp = File(op, "operation.properties.tmp")
        parseMeta(primary)?.let { return it }
        val recovered = parseMeta(temp)
            ?: throw IOException("Restore journal '${op.name}' has no readable primary or temporary metadata; data retained")
        publishAtomic(temp, primary)
        return recovered
    }

    private fun parseMeta(file: File): Map<String, String>? {
        if (!file.isFile) return null
        val values = runCatching { file.readLines().mapNotNull { line ->
            val index = line.indexOf('=')
            if (index > 0) line.substring(0, index) to line.substring(index + 1) else null
        }.toMap() }.getOrNull() ?: return null
        val required = setOf("kind", "operationId", "state", "hadSongs", "oldSongsDigest", "newSongsDigest", "hadRecordings", "oldRecordingsDigest", "newRecordingsDigest")
        return values.takeIf { values["kind"] == "RESTORE" && required.all(values::containsKey) && runCatching { RestoreJournalState.valueOf(values.getValue("state")) }.isSuccess }
    }

    private fun writeMetaAtomic(op: File, values: Map<String, String>) {
        val temp = File(op, "operation.properties.tmp")
        FileOutputStream(temp).use { output ->
            output.write(values.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" }.toByteArray())
            output.fd.sync()
        }
        metadataFaultInjector.afterTempFileSynced()
        val target = File(op, "operation.properties")
        publishAtomic(temp, target)
    }

    private fun publishAtomic(temp: File, target: File) {
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Exception) {
            throw IOException("Cannot atomically publish restore journal metadata; previous metadata remains readable", error)
        }
    }

    companion object {
        fun treeDigest(directory: File): String {
            if (!directory.exists()) return "ABSENT"
            val digest = MessageDigest.getInstance("SHA-256")
            directory.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(directory).invariantSeparatorsPath }.forEach { file ->
                digest.update(file.relativeTo(directory).invariantSeparatorsPath.toByteArray())
                digest.update(0)
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

private fun Map<String, String>.required(key: String): String = this[key] ?: throw IOException("Restore journal missing '$key'")
