package com.novarehab.ui

import com.novarehab.R
import com.novarehab.utils.CustomCommIcon

object CommunicationRepository {

    fun defaultItems(): List<CommunicationItem> = listOf(
        CommunicationItem(
            id = "piti",
            label = "ZEJNA",
            ttsText = "Zejna sem.",
            iconRes = R.drawable.comm_piti,
            children = listOf(
                CommunicationItem("voda", "VODA", "Zelim vodo.", R.drawable.comm_piti),
                CommunicationItem("caj", "CAJ", "Zelim caj.", R.drawable.comm_piti),
                CommunicationItem("sok", "SOK", "Zelim sok.", R.drawable.comm_piti)
            )
        ),
        CommunicationItem(
            id = "jesti",
            label = "LACNA",
            ttsText = "Lacna sem.",
            iconRes = R.drawable.comm_jesti,
            children = listOf(
                CommunicationItem("zajtrk", "ZAJTRK", "Zelim zajtrk.", R.drawable.comm_jesti),
                CommunicationItem("kosilo", "KOSILO", "Zelim kosilo.", R.drawable.comm_jesti),
                CommunicationItem("prigrizek", "PRIGRIZEK", "Zelim prigrizek.", R.drawable.comm_jesti)
            )
        ),
        CommunicationItem(
            id = "slabo",
            label = "SLABO",
            ttsText = "Ne pocutim se dobro.",
            iconRes = R.drawable.comm_slabo,
            children = listOf(
                CommunicationItem("bolecina", "BOLECINA", "Imam bolecino.", R.drawable.comm_bolecina),
                CommunicationItem("slabost", "SLABOST", "Slabo mi je.", R.drawable.comm_slabo),
                CommunicationItem("tesnoba", "TESNOBA", "Cutim tesnobo.", R.drawable.comm_tesnoba)
            )
        ),
        CommunicationItem(
            id = "pomoc",
            label = "POMOC",
            ttsText = "Potrebujem pomoc.",
            iconRes = R.drawable.comm_pomoc,
            children = listOf(
                CommunicationItem("pomoc_pridi", "PRIDI", "Prosim pridi k meni.", R.drawable.comm_pridi_sem),
                CommunicationItem("pomoc_dvigni", "DVIGNI ME", "Prosim dvigni me.", R.drawable.comm_pomoc),
                CommunicationItem("pomoc_polozaj", "POPRAVI POLOZAJ", "Prosim popravi moj polozaj.", R.drawable.comm_postelja)
            )
        ),
        CommunicationItem("kopalnica", "WC", "Potrebujem v kopalnico.", R.drawable.comm_kopalnica),
        CommunicationItem("dobro", "DOBRO", "Dobro se pocutim.", R.drawable.comm_dobro),
        CommunicationItem("utrujena", "UTRUJENA", "Utrujena sem, rada bi pocivala.", R.drawable.comm_utrujena),
        CommunicationItem("mraz", "MRAZ", "Mrzlo mi je.", R.drawable.comm_mraz),
        CommunicationItem("vroce", "VROCE", "Vroce mi je.", R.drawable.comm_vroce),
        CommunicationItem("hvala", "HVALA", "Hvala lepa.", R.drawable.comm_hvala),
        CommunicationItem("pridi_sem", "PRIDI", "Prosim pridi sem k meni.", R.drawable.comm_pridi_sem),
        CommunicationItem("pocakaj", "POCAKAJ", "Pocakaj prosim.", R.drawable.comm_pocakaj),
        CommunicationItem("zdravilo", "ZDRAVILO", "Cas je za zdravilo.", R.drawable.comm_zdravilo),
        CommunicationItem("telefon", "TELEFON", "Prinesite mi telefon.", R.drawable.comm_telefon),
        CommunicationItem("tv", "TV", "Vklopite televizijo.", R.drawable.comm_tv),
        CommunicationItem("postelja", "POSTELJA", "Rada bi lezala.", R.drawable.comm_postelja),
        CommunicationItem("okno", "OKNO", "Odprite okno.", R.drawable.comm_okno),
        CommunicationItem("vesela", "VESELA", "Vesela sem.", R.drawable.comm_vesela),
        CommunicationItem("zalostna", "ZALOSTNA", "Zalostna sem.", R.drawable.comm_zalostna),
        CommunicationItem("jezna", "JEZNA", "Jezna sem.", R.drawable.comm_jezna),
        CommunicationItem("strah", "STRAH", "Prestrasena sem.", R.drawable.comm_strah),
        CommunicationItem("tesnoba_sama", "TESNOBA", "Tesnobno se pocutim.", R.drawable.comm_tesnoba),
        CommunicationItem("objemi", "OBJEMI", "Bi me objel?", R.drawable.comm_objemi)
    )

    fun customItems(items: List<CustomCommIcon>): List<CommunicationItem> {
        return items
            .filter { it.text.isNotBlank() || it.title.isNotBlank() }
            .map { item ->
                CommunicationItem(
                    id = item.id,
                    label = item.title.ifBlank { item.text }.uppercase(),
                    ttsText = item.text.ifBlank { item.title },
                    iconRes = R.drawable.ic_contact_default
                )
            }
    }
}
