package com.novarehab.service

import android.content.Context
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.RadioStation

object RadioBrowserService {

    private val fixedStations = listOf(
        RadioStation("Radio 1", "https://live.radio.si/Radio1"),
        RadioStation("Radio Center", "http://stream2.radiocenter.si:8000/center"),
        RadioStation("ROKS UA", "https://online.radioroks.ua/RadioROKS"),
        RadioStation("Kiss FM UA", "https://online.kissfm.ua/KissFM"),
        RadioStation("Nashe UA", "https://online.nasheradio.ua/NasheRadio"),
        RadioStation("Glasba", "music://local")
    )

    fun fetchStations(
        context: Context,
        onSuccess: (List<RadioStation>) -> Unit,
        onError: () -> Unit
    ) {
        PrefsManager(context).saveRadioStations(fixedStations)
        onSuccess(fixedStations)
    }
}
