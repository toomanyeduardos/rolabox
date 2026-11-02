package com.eduardoflores.rolabox.device.playback.impl

import androidx.media3.common.PlaybackException
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
