package app.sthira.ui.common

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Text produced outside a composable (ViewModels, validators), resolved in the user's chosen
 * language only when it is drawn. Raw is for text that is never translated (file names, codes).
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText
    data class Raw(val text: String) : UiText

    fun resolve(context: Context): String = when (this) {
        is Res -> context.getString(id, *args.map { it.resolveArg(context) }.toTypedArray())
        is Plural -> context.resources.getQuantityString(id, count, *args.map { it.resolveArg(context) }.toTypedArray())
        is Raw -> text
    }
}

private fun Any.resolveArg(context: Context): Any = if (this is UiText) resolve(context) else this

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

@Composable
fun UiText.asString(): String = resolve(LocalContext.current)
