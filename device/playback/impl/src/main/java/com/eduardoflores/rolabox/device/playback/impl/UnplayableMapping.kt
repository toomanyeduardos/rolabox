package com.eduardoflores.rolabox.device.playback.impl

import androidx.media3.common.PlaybackException
import com.eduardoflores.rolabox.common.util.NonFatalReport
import com.eduardoflores.rolabox.device.playback.api.PlaybackError

/** Media3's error codes as the kinds the user can tell apart. `PlaybackException` stops here (ADR-023, rule 10). */
internal fun PlaybackException.toUnplayable(): PlaybackError.Unplayable = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackError.Unplayable.Missing

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    -> PlaybackError.Unplayable.Damaged

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    -> PlaybackError.Unplayable.Unsupported

    in DRM_CODES -> PlaybackError.Unplayable.Protected

    in IO_CODES -> PlaybackError.Unplayable.Unreadable

    else -> PlaybackError.Unplayable.EngineFailed
}

// Media3 keeps each family of codes in its own range of a thousand.
private val IO_CODES = 2000..2999
private val DRM_CODES = 6000..6999

/** Whether a stream failed for the network or the server, which is the phone's state and not a bug (ADR-030). */
internal fun PlaybackException.isNetworkState(): Boolean = errorCode in NETWORK_CODES

private val NETWORK_CODES = setOf(
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
)

/**
 * Reports an item that couldn't be played to [report], unless it is the phone's state and not a bug: a deleted or
 * protected file, a stream that lost its network or a server that answered with an error (ADR-030). An Unplayable is a
 * data object, whose toString() is its name.
 */
internal fun reportUnplayable(report: NonFatalReport, unplayable: PlaybackError.Unplayable, error: PlaybackException) {
    if (unplayable != PlaybackError.Unplayable.Missing &&
        unplayable != PlaybackError.Unplayable.Protected &&
        !error.isNetworkState()
    ) {
        report.report("Unplayable.$unplayable ${error.errorCodeName}", error)
    }
}
