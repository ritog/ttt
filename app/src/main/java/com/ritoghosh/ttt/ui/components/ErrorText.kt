/**
 * ErrorText — the user-facing wording for validation errors from :core.
 *
 * Depends on: core/model (TitleError, TagError), strings.xml.
 * Must not: contain the rules themselves; :core decides what is invalid, this only words it.
 */
package com.ritoghosh.ttt.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ritoghosh.ttt.R
import com.ritoghosh.ttt.core.model.TagError
import com.ritoghosh.ttt.core.model.TitleError

@Composable
fun titleErrorText(error: TitleError): String = when (error) {
    TitleError.EMPTY -> stringResource(R.string.error_title_empty)
    TitleError.TOO_LONG -> stringResource(R.string.error_title_too_long)
}

@Composable
fun tagErrorText(error: TagError): String = when (error) {
    TagError.EMPTY -> stringResource(R.string.error_tag_empty)
    TagError.TOO_LONG -> stringResource(R.string.error_tag_too_long)
    TagError.INVALID_CHARACTERS -> stringResource(R.string.error_tag_invalid)
}
