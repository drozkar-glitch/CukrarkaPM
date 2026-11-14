package cz.mares.cukrarkapm

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

enum class SemaforVydelku {
    ZELENA,
    ORANZOVA,
    CERVENA
}

data class DetailKalkulace(
    val prodejniCenaJednotkyKc: Double,
    val jednotkaPopisek: String,
    val nakladSurovinJednotkyKc: Double,
    val nakladTroubyJednotkyKc: Double,
    val nakladPraceJednotkyKc: Double,
    val cistyVydelekPoSurovinachKc: Double,
    val ziskNadHodinovouOdmenuKc: Double,
    val celkovaVahaDavkyGramy: Double,
    val casPraceDavkyMin: Int,
    val casTroubyDavkyMin: Int,
    val alergeny: List<Alergen>,
    val semafor: SemaforVydelku,
    val lidskyVerdikt: String
)

data class VydelekObjednavky(
    val prodejniCenaCelkemKc: Double,
    val nakladSurovinATroubyKc: Double,
    val cistyVydelekKc: Double,
    val odhadCasuPraceMin: Int,
    val semafor: SemaforVydelku
)

data class RozpisNaplne(
    val variantaNazev: String,
    val potrebnaHmotnostGramy: Double
)

data class PolozkaPlanuPeceni(
    val receptId: String,
    val nazevReceptu: String,
    val jeNaVahu: Boolean,
    val celkemPotrebaGramyNeboKusy: Double,
    val pocetDavekTestaPresne: Double,
    val doporuceneDavkyTesta: Double,
    val rozpisNaplni: List<RozpisNaplne>
)

data class PolozkaNakupnihoListku(
    val polozkaSkladu: SkladovaPolozka,
    val potrebaNaObjednavky: Double,
    val chybiDokoupitJednotek: Double,
    val pocetBaleniKoupit: Int,
    val odhadovanaCenaNakupuKc: Double
)

object CalculationEngine {

    fun spocitejDetailReceptu(
        recept: Receptura,
        varianta: VariantaProduktu?,
        data: DataCukrarny,
        hloubka: Int = 0
    ): DetailKalkulace {
        val skladMapa = data.sklad.associateBy { it.id }
        val nastaveni = data.nastaveni

        if (recept.typ == TypReceptu.MIX_KRABICE && hloubka < 2) {
            var surovinyKrabiceKc = 0.0
            val alergeny = mutableSetOf<Alergen>()
            for (slozka in recept.suroviny) {
                val pol = skladMapa[slozka.polozkaId] ?: continue
                surovinyKrabiceKc += slozka.mnozstvi * pol.cenaZaJednotkuKc
                alergeny.addAll(pol.alergeny)
            }
            var nakladCukroviSurovinyKc = 0.0
            var nakladCukroviTroubaKc = 0.0
            var nakladCukroviPraceKc = 0.0
            var celkemVahaG = 0.0
            var soucetMinutPrace = recept.casPraceMin
            var soucetMinutTrouby = recept.casTroubyMin

            for (polMix in recept.polozkyMixu) {
                val podRecept = data.recepty.firstOrNull { it.id == polMix.receptId } ?: continue
                val podVar = podRecept.varianty.firstOrNull { it.id == polMix.variantaId }
                val podKalk = spocitejDetailReceptu(podRecept, podVar, data, hloubka + 1)
                val podilKg = polMix.hmotnostGramy / 1000.0
                nakladCukroviSurovinyKc += podKalk.nakladSurovinJednotkyKc * podilKg
                nakladCukroviTroubaKc += podKalk.nakladTroubyJednotkyKc * podilKg
                nakladCukroviPraceKc += podKalk.nakladPraceJednotkyKc * podilKg
                celkemVahaG += polMix.hmotnostGramy
                val podilDavky = polMix.hmotnostGramy / podKalk.celkovaVahaDavkyGramy.coerceAtLeast(1.0)
                soucetMinutPrace += (podKalk.casPraceDavkyMin * podilDavky).roundToInt()
                soucetMinutTrouby += (podKalk.casTroubyDavkyMin * podilDavky).roundToInt()
                alergeny.addAll(podKalk.alergeny)
            }

            val pocetKrabic = recept.vyteznostDavky.coerceAtLeast(1.0)
            val cenaProdej = varianta?.prodejniCenaKc ?: recept.zakladniProdejniCenaKc
            val surovinyJedn = (surovinyKrabiceKc + nakladCukroviSurovinyKc) / pocetKrabic
            val troubaJedn = (((recept.casTroubyMin / 60.0) * nastaveni.cenaHodinyTroubyKc) + nakladCukroviTroubaKc) / pocetKrabic
            val praceBaleniKc = ((recept.casPraceMin + (varianta?.extraCasPraceMin ?: 0)) / 60.0) * nastaveni.hodinovaOdmenaKc
            val praceJedn = (praceBaleniKc + nakladCukroviPraceKc) / pocetKrabic

            return sestavVysledekKalkulace(
                cenaProdej = cenaProdej,
                jednotkaPopisek = "1 ks",
                surovinyJedn = surovinyJedn,
                troubaJedn = troubaJedn,
                praceJedn = praceJedn,
                vahaDavkyG = celkemVahaG,
                casPraceMin = soucetMinutPrace,
                casTroubyMin = soucetMinutTrouby,
                alergeny = alergeny.toList()
            )
        }

        var nakladSurovinDavky = 0.0
        val alergeny = mutableSetOf<Alergen>()
        for (slozka in recept.suroviny) {
            val pol = skladMapa[slozka.polozkaId] ?: continue
            nakladSurovinDavky += slozka.mnozstvi * pol.cenaZaJednotkuKc
            alergeny.addAll(pol.alergeny)
        }
        if (varianta != null) {
            for (slozka in varianta.extraSurovinyNaDavku) {
                val pol = skladMapa[slozka.polozkaId] ?: continue
                nakladSurovinDavky += slozka.mnozstvi * pol.cenaZaJednotkuKc
                alergeny.addAll(pol.alergeny)
            }
        }

        val casPraceCelkem = recept.casPraceMin + (varianta?.extraCasPraceMin ?: 0)
        val casTroubyCelkem = recept.casTroubyMin
        val nakladTroubyDavky = (casTroubyCelkem / 60.0) * nastaveni.cenaHodinyTroubyKc
        val nakladPraceDavky = (casPraceCelkem / 60.0) * nastaveni.hodinovaOdmenaKc
        val prodejniCena = varianta?.prodejniCenaKc ?: recept.zakladniProdejniCenaKc

        return if (recept.typ == TypReceptu.CUKROVI_DAVKA) {
            val celkovaVahaDavky = (recept.vyteznostDavky + (varianta?.pridanaVahaGramy ?: 0.0)).coerceAtLeast(1.0)
            val prepoctovyKoeficientNaKg = 1000.0 / celkovaVahaDavky
            sestavVysledekKalkulace(
                cenaProdej = prodejniCena,
                jednotkaPopisek = "1 kg",
                surovinyJedn = nakladSurovinDavky * prepoctovyKoeficientNaKg,
                troubaJedn = nakladTroubyDavky * prepoctovyKoeficientNaKg,
                praceJedn = nakladPraceDavky * prepoctovyKoeficientNaKg,
                vahaDavkyG = celkovaVahaDavky,
                casPraceMin = casPraceCelkem,
                casTroubyMin = casTroubyCelkem,
                alergeny = alergeny.toList()
            )
        } else {
            val pocetKusu = recept.vyteznostDavky.coerceAtLeast(1.0)
            sestavVysledekKalkulace(
                cenaProdej = prodejniCena,
                jednotkaPopisek = "1 ks",
                surovinyJedn = nakladSurovinDavky / pocetKusu,
                troubaJedn = nakladTroubyDavky / pocetKusu,
                praceJedn = nakladPraceDavky / pocetKusu,
                vahaDavkyG = pocetKusu,
                casPraceMin = casPraceCelkem,
                casTroubyMin = casTroubyCelkem,
                alergeny = alergeny.toList()
            )
        }
    }

    private fun sestavVysledekKalkulace(
        cenaProdej: Double,
        jednotkaPopisek: String,
        surovinyJedn: Double,
        troubaJedn: Double,
        praceJedn: Double,
        vahaDavkyG: Double,
        casPraceMin: Int,
        casTroubyMin: Int,
        alergeny: List<Alergen>
    ): DetailKalkulace {
        val cistyPoSurovinach = cenaProdej - surovinyJedn - troubaJedn
        val nadOdmenuPrace = cistyPoSurovinach - praceJedn
        val (semafor, verdikt) = when {
            cistyPoSurovinach <= 0.0 -> SemaforVydelku.CERVENA to
                "Pozor, proděláváš! Suroviny a trouba stojí víc než prodejní cena."
            nadOdmenuPrace < 0.0 -> SemaforVydelku.ORANZOVA to
                "Z $jednotkaPopisek ti zůstane ${formatKc(cistyPoSurovinach)}, ale nevychází plná hodinová odměna."
            else -> SemaforVydelku.ZELENA to
                "Z $jednotkaPopisek ti po surovinách a troubě zůstane čistých ${formatKc(cistyPoSurovinach)}."
        }
        return DetailKalkulace(
            prodejniCenaJednotkyKc = cenaProdej,
            jednotkaPopisek = jednotkaPopisek,
            nakladSurovinJednotkyKc = surovinyJedn,
            nakladTroubyJednotkyKc = troubaJedn,
            nakladPraceJednotkyKc = praceJedn,
            cistyVydelekPoSurovinachKc = cistyPoSurovinach,
            ziskNadHodinovouOdmenuKc = nadOdmenuPrace,
            celkovaVahaDavkyGramy = vahaDavkyG,
            casPraceDavkyMin = casPraceMin,
            casTroubyDavkyMin = casTroubyMin,
            alergeny = alergeny,
            semafor = semafor,
            lidskyVerdikt = verdikt
        )
    }

    fun sestavKatalogProduktu(data: DataCukrarny): List<KatalogovyProdukt> {
        val vystup = mutableListOf<KatalogovyProdukt>()
        for (rec in data.recepty) {
            val jeVahove = rec.typ == TypReceptu.CUKROVI_DAVKA
            if (rec.varianty.isEmpty()) {
                val k = spocitejDetailReceptu(rec, null, data)
                vystup += KatalogovyProdukt(
                    receptId = rec.id,
                    variantaId = "",
                    celyNazev = rec.nazev,
                    typ = rec.typ,
                    jeNaVahu = jeVahove,
                    prodejniCenaKc = k.prodejniCenaJednotkyKc,
                    nakladSurovinAEnergiiKc = k.nakladSurovinJednotkyKc + k.nakladTroubyJednotkyKc,
                    nakladVcetnePraceKc = k.nakladSurovinJednotkyKc + k.nakladTroubyJednotkyKc + k.nakladPraceJednotkyKc,
                    cistyVydelekKc = k.cistyVydelekPoSurovinachKc,
                    alergeny = k.alergeny
                )
            } else {
                for (varianta in rec.varianty) {
                    val k = spocitejDetailReceptu(rec, varianta, data)
                    vystup += KatalogovyProdukt(
                        receptId = rec.id,
                        variantaId = varianta.id,
                        celyNazev = "${rec.nazev} — ${varianta.nazevVarianty}",
                        typ = rec.typ,
                        jeNaVahu = jeVahove,
                        prodejniCenaKc = k.prodejniCenaJednotkyKc,
                        nakladSurovinAEnergiiKc = k.nakladSurovinJednotkyKc + k.nakladTroubyJednotkyKc,
                        nakladVcetnePraceKc = k.nakladSurovinJednotkyKc + k.nakladTroubyJednotkyKc + k.nakladPraceJednotkyKc,
                        cistyVydelekKc = k.cistyVydelekPoSurovinachKc,
                        alergeny = k.alergeny
                    )
                }
                val kZaklad = spocitejDetailReceptu(rec, null, data)
                vystup += KatalogovyProdukt(
                    receptId = rec.id,
                    variantaId = "",
                    celyNazev = "${rec.nazev} (základ bez náplně)",
                    typ = rec.typ,
                    jeNaVahu = jeVahove,
                    prodejniCenaKc = kZaklad.prodejniCenaJednotkyKc,
                    nakladSurovinAEnergiiKc = kZaklad.nakladSurovinJednotkyKc + kZaklad.nakladTroubyJednotkyKc,
                    nakladVcetnePraceKc = kZaklad.nakladSurovinJednotkyKc + kZaklad.nakladTroubyJednotkyKc + kZaklad.nakladPraceJednotkyKc,
                    cistyVydelekKc = kZaklad.cistyVydelekPoSurovinachKc,
                    alergeny = kZaklad.alergeny
                )
            }
        }
        return vystup
    }

    fun zkontrolujAlergieObjednavky(
        klientka: Klientka?,
        polozky: List<PolozkaObjednavky>,
        katalog: List<KatalogovyProdukt>
    ): List<Alergen> {
        if (klientka == null || klientka.hlidaneAlergie.isEmpty()) return emptyList()
        val nalezene = mutableSetOf<Alergen>()
        for (pol in polozky) {
            val kat = katalog.firstOrNull { it.receptId == pol.receptId && it.variantaId == pol.variantaId } ?: continue
            for (al in kat.alergeny) {
                if (al in klientka.hlidaneAlergie) nalezene += al
            }
        }
        return nalezene.toList()
    }

    fun spocitejVydelekObjednavky(objednavka: Objednavka, data: DataCukrarny): VydelekObjednavky {
        var nakladSurATrouba = 0.0
        var nakladPrace = 0.0
        var casMin = 0

        for (pol in objednavka.polozky) {
            val rec = data.recepty.firstOrNull { it.id == pol.receptId } ?: continue
            val varianta = rec.varianty.firstOrNull { it.id == pol.variantaId }
            val kalk = spocitejDetailReceptu(rec, varianta, data)
            val nasobek = if (pol.jeNaVahu) pol.mnozstvi / 1000.0 else pol.mnozstvi
            nakladSurATrouba += (kalk.nakladSurovinJednotkyKc + kalk.nakladTroubyJednotkyKc) * nasobek
            nakladPrace += kalk.nakladPraceJednotkyKc * nasobek
            val podilDavky = if (pol.jeNaVahu) {
                pol.mnozstvi / kalk.celkovaVahaDavkyGramy.coerceAtLeast(1.0)
            } else {
                pol.mnozstvi / rec.vyteznostDavky.coerceAtLeast(1.0)
            }
            casMin += (kalk.casPraceDavkyMin * podilDavky).roundToInt()
        }

        val prodej = objednavka.celkovaCenaKc
        val cisty = prodej - nakladSurATrouba
        val semafor = when {
            cisty <= 0.0 -> SemaforVydelku.CERVENA
            cisty < nakladPrace -> SemaforVydelku.ORANZOVA
            else -> SemaforVydelku.ZELENA
        }
        return VydelekObjednavky(
            prodejniCenaCelkemKc = prodej,
            nakladSurovinATroubyKc = nakladSurATrouba,
            cistyVydelekKc = cisty,
            odhadCasuPraceMin = casMin,
            semafor = semafor
        )
    }

    fun spocitejPlanPeceni(data: DataCukrarny): List<PolozkaPlanuPeceni> {
        val aktivni = data.objednavky.filter { it.stav != StavObjednavky.HOTOVO }
        if (aktivni.isEmpty()) return emptyList()

        val souctyVariant = mutableMapOf<String, Double>()

        fun pridejMnozstvi(recId: String, varId: String, mnozstvi: Double) {
            val klic = "$recId|$varId"
            souctyVariant[klic] = (souctyVariant[klic] ?: 0.0) + mnozstvi
        }

        for (obj in aktivni) {
            for (pol in obj.polozky) {
                val rec = data.recepty.firstOrNull { it.id == pol.receptId } ?: continue
                if (rec.typ == TypReceptu.MIX_KRABICE) {
                    val pocetKrabic = pol.mnozstvi
                    for (mixPol in rec.polozkyMixu) {
                        pridejMnozstvi(mixPol.receptId, mixPol.variantaId, mixPol.hmotnostGramy * pocetKrabic)
                    }
                } else {
                    pridejMnozstvi(pol.receptId, pol.variantaId, pol.mnozstvi)
                }
            }
        }

        val vysledek = mutableListOf<PolozkaPlanuPeceni>()
        for (rec in data.recepty) {
            if (rec.typ == TypReceptu.MIX_KRABICE) continue
            val zaznamyReceptu = souctyVariant.entries.filter { it.key.startsWith("${rec.id}|") }
            if (zaznamyReceptu.isEmpty()) continue

            var celkemMnozstvi = 0.0
            var celkemDavekTesta = 0.0
            val rozpis = mutableListOf<RozpisNaplne>()

            for ((klic, mnozstvi) in zaznamyReceptu) {
                val varId = klic.substringAfter("|")
                val varianta = rec.varianty.firstOrNull { it.id == varId }
                celkemMnozstvi += mnozstvi

                if (rec.typ == TypReceptu.CUKROVI_DAVKA) {
                    val vahaDavky = (rec.vyteznostDavky + (varianta?.pridanaVahaGramy ?: 0.0)).coerceAtLeast(1.0)
                    celkemDavekTesta += mnozstvi / vahaDavky
                    if (varianta != null) {
                        rozpis += RozpisNaplne(varianta.nazevVarianty, mnozstvi)
                    } else if (rec.varianty.isNotEmpty()) {
                        rozpis += RozpisNaplne("Základ bez náplně", mnozstvi)
                    }
                } else {
                    celkemDavekTesta += mnozstvi / rec.vyteznostDavky.coerceAtLeast(1.0)
                    if (varianta != null) {
                        rozpis += RozpisNaplne(varianta.nazevVarianty, mnozstvi)
                    }
                }
            }

            val zaokrPuldavky = ceil(celkemDavekTesta * 2.0) / 2.0
            vysledek += PolozkaPlanuPeceni(
                receptId = rec.id,
                nazevReceptu = rec.nazev,
                jeNaVahu = rec.typ == TypReceptu.CUKROVI_DAVKA,
                celkemPotrebaGramyNeboKusy = celkemMnozstvi,
                pocetDavekTestaPresne = celkemDavekTesta,
                doporuceneDavkyTesta = max(0.5, zaokrPuldavky),
                rozpisNaplni = rozpis
            )
        }
        return vysledek
    }

    private fun nasbiratSurovinyObjednavky(
        objednavka: Objednavka,
        data: DataCukrarny,
        cilovaMapa: MutableMap<String, Double>
    ) {
        fun pripisSlozku(polozkaId: String, kolik: Double) {
            cilovaMapa[polozkaId] = (cilovaMapa[polozkaId] ?: 0.0) + kolik
        }

        fun rozpadniRecept(rec: Receptura, varId: String, mnozstvi: Double, hloubka: Int) {
            if (hloubka > 2) return
            if (rec.typ == TypReceptu.MIX_KRABICE) {
                val nasobekKrabic = mnozstvi / rec.vyteznostDavky.coerceAtLeast(1.0)
                for (s in rec.suroviny) {
                    pripisSlozku(s.polozkaId, s.mnozstvi * nasobekKrabic)
                }
                for (mix in rec.polozkyMixu) {
                    val podRec = data.recepty.firstOrNull { it.id == mix.receptId } ?: continue
                    rozpadniRecept(podRec, mix.variantaId, mix.hmotnostGramy * mnozstvi, hloubka + 1)
                }
                return
            }

            val varianta = rec.varianty.firstOrNull { it.id == varId }
            val nasobekDavky = if (rec.typ == TypReceptu.CUKROVI_DAVKA) {
                val celkVaha = (rec.vyteznostDavky + (varianta?.pridanaVahaGramy ?: 0.0)).coerceAtLeast(1.0)
                mnozstvi / celkVaha
            } else {
                mnozstvi / rec.vyteznostDavky.coerceAtLeast(1.0)
            }

            for (s in rec.suroviny) {
                pripisSlozku(s.polozkaId, s.mnozstvi * nasobekDavky)
            }
            if (varianta != null) {
                for (s in varianta.extraSurovinyNaDavku) {
                    pripisSlozku(s.polozkaId, s.mnozstvi * nasobekDavky)
                }
            }
        }

        for (pol in objednavka.polozky) {
            val rec = data.recepty.firstOrNull { it.id == pol.receptId } ?: continue
            rozpadniRecept(rec, pol.variantaId, pol.mnozstvi, 0)
        }
    }

    fun spocitejNakupniListek(data: DataCukrarny): List<PolozkaNakupnihoListku> {
        val spotreba = mutableMapOf<String, Double>()
        for (obj in data.objednavky) {
            if (obj.stav != StavObjednavky.HOTOVO && !obj.odepsanoZeSkladu) {
                nasbiratSurovinyObjednavky(obj, data, spotreba)
            }
        }

        val listek = mutableListOf<PolozkaNakupnihoListku>()
        for (pol in data.sklad) {
            val potrebaNaZakazky = spotreba[pol.id] ?: 0.0
            val cilovaZasoba = max(potrebaNaZakazky, pol.minimalniZasoba)
            val chybi = cilovaZasoba - pol.aktualniZasoba
            if (chybi > 0.01) {
                val baleni = ceil(chybi / pol.velikostBaleni.coerceAtLeast(1.0)).toInt().coerceAtLeast(1)
                listek += PolozkaNakupnihoListku(
                    polozkaSkladu = pol,
                    potrebaNaObjednavky = potrebaNaZakazky,
                    chybiDokoupitJednotek = chybi,
                    pocetBaleniKoupit = baleni,
                    odhadovanaCenaNakupuKc = baleni * pol.cenaZaBaleniKc
                )
            }
        }
        return listek.sortedByDescending { it.odhadovanaCenaNakupuKc }
    }

    fun odepisSurovinyObjednavkyZeSkladu(objednavka: Objednavka, data: DataCukrarny): DataCukrarny {
        if (objednavka.odepsanoZeSkladu) return data
        val spotreba = mutableMapOf<String, Double>()
        nasbiratSurovinyObjednavky(objednavka, data, spotreba)

        val novySklad = data.sklad.map { pol ->
            val ubytek = spotreba[pol.id] ?: 0.0
            if (ubytek > 0.0) {
                pol.copy(aktualniZasoba = (pol.aktualniZasoba - ubytek).coerceAtLeast(0.0))
            } else {
                pol
            }
        }
        val noveObjednavky = data.objednavky.map {
            if (it.id == objednavka.id) it.copy(odepsanoZeSkladu = true) else it
        }
        return data.copy(sklad = novySklad, objednavky = noveObjednavky)
    }
}
