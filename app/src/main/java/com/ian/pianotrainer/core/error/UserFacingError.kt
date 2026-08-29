package com.ian.pianotrainer.core.error

enum class ErrorCode {
    FILE_TOO_LARGE,
    DUPLICATE_FILE,
    INVALID_MIDI_FILE,
    STORAGE_READ_ERROR,
    STORAGE_WRITE_ERROR,
    BLUETOOTH_DISABLED,
    PERMISSION_DENIED,
    MIDI_DEVICE_DISCONNECTED,
    MIDI_OPEN_FAILED,
    AUDIO_RECORD_FAILED,
    AUDIO_PERMISSION_DENIED,
    MICROPHONE_IN_USE,
    BACKUP_EXPORT_FAILED,
    BACKUP_RESTORE_FAILED,
    INVALID_BACKUP_ZIP,
    DATABASE_ERROR,
    UNKNOWN_ERROR
}

data class UserFacingError(
    val code: ErrorCode,
    val userMessageResId: Int? = null,
    val fallbackMessage: String,
    val technicalDetail: String? = null,
    val isRecoverable: Boolean = true,
    val actionLabelResId: Int? = null
)

sealed interface UiEvent {
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null
    ) : UiEvent

    data class RequestPermission(val permission: String) : UiEvent
    data object OpenSystemSettings : UiEvent
    data class Navigate(val route: String) : UiEvent
    data object NavigateBack : UiEvent
}
