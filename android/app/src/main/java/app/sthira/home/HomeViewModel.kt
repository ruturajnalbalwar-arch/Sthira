package app.sthira.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.sthira.R
import app.sthira.engine.CounterResult
import app.sthira.engine.createCounter
import app.sthira.ui.common.UiText
import app.sthira.ui.common.uiText
import java.io.IOException

/** What the chair-stand card says about the counter. The label is resolved in the user's language when drawn. */
enum class CounterBadge(val label: UiText) {
    Ready(uiText(R.string.home_badge_ready)),
    NotTuned(uiText(R.string.home_badge_not_tuned)),
    Invalid(uiText(R.string.home_badge_invalid)),
    Missing(uiText(R.string.home_badge_missing)),
}

object HomeModel {
    fun badgeFor(result: CounterResult): CounterBadge = when (result) {
        is CounterResult.Ready -> CounterBadge.Ready
        is CounterResult.Untuned -> CounterBadge.NotTuned
        is CounterResult.Invalid -> CounterBadge.Invalid
    }
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    val counterBadge: CounterBadge = try {
        HomeModel.badgeFor(createCounter(app.assets.open("params.json").bufferedReader().use { it.readText() }))
    } catch (e: IOException) {
        CounterBadge.Missing
    }
}
