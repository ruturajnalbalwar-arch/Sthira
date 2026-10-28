package app.sthira.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.sthira.engine.CounterResult
import app.sthira.engine.createCounter
import java.io.IOException

/** What the chair-stand card says about the counter. */
enum class CounterBadge(val label: String) {
    Ready("Ready"),
    NotTuned("Counter not tuned"),
    Invalid("Counter settings invalid"),
    Missing("Counter settings missing"),
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
