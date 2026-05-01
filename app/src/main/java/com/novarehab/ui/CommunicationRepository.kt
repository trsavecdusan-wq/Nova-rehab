package com.novarehab.ui

import com.novarehab.R
import com.novarehab.utils.CustomCommIcon

object CommunicationRepository {

    fun defaultItems(): List<CommunicationItem> = listOf(
        CommunicationItem(
            id = "piti",
            label = "ŽEJNA",
            ttsText = "Žejna sem.",
            iconRes = R.drawable.comm_piti,
            children = listOf(
                CommunicationItem("voda", "VODA", "Želim vodo.", R.drawable.comm_piti),
                CommunicationItem("caj", "ČAJ", "Želim čaj.", R.drawable.comm_piti),
                CommunicationItem("sok", "SOK", "Želim sok.", R.drawable.comm_piti)
            )
        ),
        CommunicationItem(
            id = "jesti",
            label = "LAČNA",
            ttsText = "Lačna sem.",
            iconRes = R.drawable.comm_jesti,
            children = listOf(
                CommunicationItem("zajtrk", "ZAJTRK", "Želim zajtrk.", R.drawable.comm_jesti),
                CommunicationItem("kosilo", "KOSILO", "Želim kosilo.", R.drawable.comm_jesti),
                CommunicationItem("prigrizek", "PRIGRIZEK", "Želim prigrizek.", R.drawable.comm_jesti)
            )
        ),
        CommunicationItem(
            id = "slabo",
            label = "SLABO",
            ttsText = "Ne počutim se dobro.",
            iconRes = R.drawable.comm_slabo,
            children = listOf(
                CommunicationItem("bolecina", "BOLEČINA", "Imam bolečino.", R.drawable.comm_bolecina),
                CommunicationItem("slabost", "SLABOST", "Slabo mi je.", R.drawable.comm_slabo),
                CommunicationItem("tesnoba", "TESNOBA", "Čutim tesnobo.", R.drawable.comm_tesnoba)
            )
        ),
        CommunicationItem(
            id = "pomoc",
            label = "POMOČ",
            ttsText = "Potrebujem pomoč.",
            iconRes = R.drawable.comm_pomoc,
            children = listOf(
                CommunicationItem("pomoc_pridi", "PRIDI", "Prosim pridi k meni.", R.drawable.comm_pridi_sem),
                CommunicationItem("pomoc_dvigni", "DVIGNI ME", "Prosim dvigni me.", R.drawable.comm_pomoc),
                CommunicationItem("pomoc_polozaj", "POPRAVI POLOŽAJ", "Prosim popravi moj položaj.", R.drawable.comm_postelja)
            )
        ),
        CommunicationItem("kopalnica", "WC", "Potrebujem v kopalnico.", R.drawable.comm_kopalnica),
        CommunicationItem("dobro", "DOBRO", "Dobro se počutim.", R.drawable.comm_dobro),
        CommunicationItem("utrujena", "UTRUJENA", "Utrujena sem, rada bi počivala.", R.drawable.comm_utrujena),
        CommunicationItem("mraz", "MRAZ", "Mrzlo mi je.", R.drawable.comm_mraz),
        CommunicationItem("vroce", "VROČE", "Vroče mi je.", R.drawable.comm_vroce),
        CommunicationItem("hvala", "HVALA", "Hvala lepa.", R.drawable.comm_hvala),
        CommunicationItem("pridi_sem", "PRIDI", "Prosim pridi sem k meni.", R.drawable.comm_pridi_sem),
        CommunicationItem("pocakaj", "POČAKAJ", "Počakaj prosim.", R.drawable.comm_pocakaj),
        CommunicationItem("zdravilo", "ZDRAVILO", "Čas je za zdravilo.", R.drawable.comm_zdravilo),
        CommunicationItem("telefon", "TELEFON", "Prinesite mi telefon.", R.drawable.comm_telefon),
        CommunicationItem("tv", "TV", "Vklopite televizijo.", R.drawable.comm_tv),
        CommunicationItem("postelja", "POSTELJA", "Rada bi ležala.", R.drawable.comm_postelja),
        CommunicationItem("okno", "OKNO", "Odprite okno.", R.drawable.comm_okno),
        CommunicationItem("vesela", "VESELA", "Vesela sem.", R.drawable.comm_vesela),
        CommunicationItem("zalostna", "ŽALOSTNA", "Žalostna sem.", R.drawable.comm_zalostna),
        CommunicationItem("jezna", "JEZNA", "Jezna sem.", R.drawable.comm_jezna),
        CommunicationItem("strah", "STRAH", "Prestrašena sem.", R.drawable.comm_strah),
        CommunicationItem("tesnoba_sama", "TESNOBA", "Tesnobno se počutim.", R.drawable.comm_tesnoba),
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
