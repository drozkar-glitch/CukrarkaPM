package cz.mares.cukrarkapm

import kotlin.math.abs
import kotlin.math.roundToInt

// Cukrarka PM: Sprava skladu surovin se skenerem carovych kodu, kalkulace receptur a vanocniho cukrovi,
// varianty naplni, hlidani cen a marzi, adresar klientek a evidence objednavek s QR platbou.

enum class MernaJednotka(
    val zkratka: String,
    val popis: String,
    val velkaZkratka: String,
    val nasobekVelke: Double
) {
    GRAM("g", "Gramy (g)", "kg", 1000.0),
    MILILITR("ml", "Mililitry (ml)", "l", 1000.0),
    KUS("ks", "Kusy (ks)", "ks", 1.0)
}

enum class Alergen(val nazev: String, val ikona: String) {
    LEPEK("Lepek (mouka)", "🌾"),
    VEJCE("Vejce", "🥚"),
    MLEKO("Mléko a máslo", "🥛"),
    ORECHY("Ořechy a mandle", "🌰"),
    ARASIDY("Arašídy", "🥜"),
    SOJA("Sója (čokoláda)", "🫘"),
    SEZAM("Sezam", "✨")
}

data class UcetCukrarky(
    val nick: String,
    val pin: String,
    val jeUkazkovyRezim: Boolean
)

data class PolozkaKataloguEan(
    val ean: String,
    val nazev: String,
    val jednotka: MernaJednotka,
    val velikostBaleni: Double,
    val jeObal: Boolean,
    val alergeny: List<Alergen>
)

data class RozpoznanyCarovyKod(
    val cistyKod: String,
    val vyhledavaciKlic: String,
    val jeVahovyKod: Boolean,
    val odvozenaVahaGramy: Double?
)

fun analyzujCeskyCarovyKod(vstup: String): RozpoznanyCarovyKod {
    val cisty = vstup.trim().filter { it.isDigit() }
    if (cisty.length == 13 && (cisty.startsWith("28") || cisty.startsWith("29"))) {
        val vaha = cisty.substring(7, 12).toIntOrNull()?.toDouble()
        val klicDruhu = cisty.substring(0, 6) + "0000000"
        if (vaha != null && vaha > 0.0) {
            return RozpoznanyCarovyKod(
                cistyKod = cisty,
                vyhledavaciKlic = klicDruhu,
                jeVahovyKod = true,
                odvozenaVahaGramy = vaha
            )
        }
    }
    return RozpoznanyCarovyKod(
        cistyKod = cisty,
        vyhledavaciKlic = cisty,
        jeVahovyKod = false,
        odvozenaVahaGramy = null
    )
}

data class SkladovaPolozka(
    val id: String,
    val ean: String,
    val nazev: String,
    val jeObal: Boolean,
    val jednotka: MernaJednotka,
    val velikostBaleni: Double,
    val cenaZaBaleniKc: Double,
    val predchoziCenaBaleniKc: Double,
    val aktualniZasoba: Double,
    val minimalniZasoba: Double,
    val alergeny: List<Alergen>
) {
    val cenaZaJednotkuKc: Double
        get() = if (velikostBaleni > 0.0) cenaZaBaleniKc / velikostBaleni else 0.0

    val zdraziloProcent: Int
        get() {
            if (predchoziCenaBaleniKc <= 0.0 || cenaZaBaleniKc <= predchoziCenaBaleniKc) return 0
            return (((cenaZaBaleniKc - predchoziCenaBaleniKc) / predchoziCenaBaleniKc) * 100.0).roundToInt()
        }
}

enum class TypReceptu(val titulek: String) {
    CUKROVI_DAVKA("🎄 Cukroví (na váhu v kg)"),
    DORT_KUSOVKA("🎂 Dort nebo zákusek (na kusy)"),
    MIX_KRABICE("📦 Mix krabice cukroví (na kusy)")
}

data class SlozkaReceptu(
    val polozkaId: String,
    val mnozstvi: Double
)

data class VariantaProduktu(
    val id: String,
    val nazevVarianty: String,
    val extraSurovinyNaDavku: List<SlozkaReceptu>,
    val pridanaVahaGramy: Double,
    val extraCasPraceMin: Int,
    val prodejniCenaKc: Double
)

data class PolozkaMixKrabice(
    val receptId: String,
    val variantaId: String,
    val hmotnostGramy: Double
)

data class Receptura(
    val id: String,
    val nazev: String,
    val typ: TypReceptu,
    val vyteznostDavky: Double,
    val casPraceMin: Int,
    val casTroubyMin: Int,
    val zakladniProdejniCenaKc: Double,
    val suroviny: List<SlozkaReceptu>,
    val varianty: List<VariantaProduktu>,
    val polozkyMixu: List<PolozkaMixKrabice>,
    val postupPoznamka: String
)

data class Klientka(
    val id: String,
    val jmeno: String,
    val telefon: String,
    val kontaktSite: String,
    val hlidaneAlergie: List<Alergen>,
    val poznamka: String
)

enum class StavObjednavky(val popisek: String) {
    PRIJATO("📝 Přijato"),
    PECU("👩‍🍳 Peču"),
    HOTOVO("✅ Hotovo")
}

data class PolozkaObjednavky(
    val receptId: String,
    val variantaId: String,
    val nazevSnapshot: String,
    val jeNaVahu: Boolean,
    val mnozstvi: Double,
    val jednCenaKc: Double
) {
    val celkovaCenaKc: Double
        get() = if (jeNaVahu) (mnozstvi / 1000.0) * jednCenaKc else mnozstvi * jednCenaKc
}

data class Objednavka(
    val id: String,
    val cisloZakazky: Int,
    val klientkaId: String,
    val terminPredani: String,
    val stav: StavObjednavky,
    val polozky: List<PolozkaObjednavky>,
    val zaplacenaZalohaKc: Double,
    val jeDoplaceno: Boolean,
    val odepsanoZeSkladu: Boolean,
    val poznamka: String
) {
    val celkovaCenaKc: Double
        get() = polozky.sumOf { it.celkovaCenaKc }

    val zbyvaDoplatitKc: Double
        get() = if (jeDoplaceno) 0.0 else (celkovaCenaKc - zaplacenaZalohaKc).coerceAtLeast(0.0)
}

data class NastaveniCukrarny(
    val nazevDilny: String = "Moje sladká výrobna",
    val hodinovaOdmenaKc: Double = 250.0,
    val cenaHodinyTroubyKc: Double = 12.0,
    val bankovniUcet: String = ""
)

data class DataCukrarny(
    val nastaveni: NastaveniCukrarny,
    val sklad: List<SkladovaPolozka>,
    val recepty: List<Receptura>,
    val klientky: List<Klientka>,
    val objednavky: List<Objednavka>,
    val posledniUlozeniMs: Long
)

data class KatalogovyProdukt(
    val receptId: String,
    val variantaId: String,
    val celyNazev: String,
    val typ: TypReceptu,
    val jeNaVahu: Boolean,
    val prodejniCenaKc: Double,
    val nakladSurovinAEnergiiKc: Double,
    val nakladVcetnePraceKc: Double,
    val cistyVydelekKc: Double,
    val alergeny: List<Alergen>
)

fun formatKc(castka: Double): String {
    val zaokr = castka.roundToInt()
    val znamenko = if (zaokr < 0) "-" else ""
    val absText = abs(zaokr).toString()
    val sb = StringBuilder()
    for (i in absText.indices) {
        if (i > 0 && (absText.length - i) % 3 == 0) sb.append(' ')
        sb.append(absText[i])
    }
    return "$znamenko$sb Kč"
}

fun formatMnozstviSkladu(hodnota: Double, jednotka: MernaJednotka): String {
    if (jednotka == MernaJednotka.KUS) {
        val desetiny = (hodnota * 10.0).roundToInt()
        return if (desetiny % 10 == 0) "${desetiny / 10} ks" else "${desetiny / 10.0} ks"
    }
    if (hodnota >= 1000.0) {
        val velke = (hodnota / 100.0).roundToInt() / 10.0
        return "$velke ${jednotka.velkaZkratka}"
    }
    return "${hodnota.roundToInt()} ${jednotka.zkratka}"
}

fun ceskyUcetNaIban(zadanyUcet: String): String {
    val cisty = zadanyUcet.trim().uppercase().replace(" ", "")
    if (cisty.isEmpty()) return ""
    if (cisty.startsWith("CZ") && cisty.length == 24) return cisty
    val castiLomitko = cisty.split("/")
    if (castiLomitko.size != 2) return ""
    val kodBanky = castiLomitko[1].filter { it.isDigit() }.padStart(4, '0')
    if (kodBanky.length != 4) return ""
    val castiPomlcka = castiLomitko[0].split("-")
    val predcisli = if (castiPomlcka.size == 2) castiPomlcka[0].filter { it.isDigit() } else ""
    val kmen = if (castiPomlcka.size == 2) castiPomlcka[1].filter { it.isDigit() } else castiLomitko[0].filter { it.isDigit() }
    if (kmen.isEmpty() || predcisli.length > 6 || kmen.length > 10) return ""
    val bban = kodBanky + predcisli.padStart(6, '0') + kmen.padStart(10, '0')
    val proKontrolu = bban + "123500"
    var zbytek = 0L
    for (znak in proKontrolu) {
        zbytek = (zbytek * 10L + (znak - '0')) % 97L
    }
    val kontrolniCisla = (98L - zbytek).toString().padStart(2, '0')
    return "CZ$kontrolniCisla$bban"
}

fun sestavSpaydRetzec(iban: String, castkaKc: Double, vsCislo: Int, zprava: String): String {
    val cistyIban = ceskyUcetNaIban(iban)
    if (cistyIban.isEmpty() || castkaKc <= 0.0) return ""
    val celaCastka = ((castkaKc * 100.0).roundToInt() / 100.0).toString()
    val bezpecnaZprava = zprava
        .replace("*", " ")
        .take(40)
        .trim()
    return "SPD*1.0*ACC:$cistyIban*AM:$celaCastka*CC:CZK*X-VS:$vsCislo*MSG:$bezpecnaZprava"
}

object VzorovaData {
    fun vytvorVychoziData(): DataCukrarny {
        val sklad = listOf(
            SkladovaPolozka("s_maslo", "8594007131089", "Máslo 82%", false, MernaJednotka.GRAM, 250.0, 64.9, 54.9, 1500.0, 1000.0, listOf(Alergen.MLEKO)),
            SkladovaPolozka("s_mouka", "8594001240015", "Hladká mouka pšeničná", false, MernaJednotka.GRAM, 1000.0, 21.9, 21.9, 4000.0, 2000.0, listOf(Alergen.LEPEK)),
            SkladovaPolozka("s_cukr", "8594001750125", "Cukr moučka", false, MernaJednotka.GRAM, 1000.0, 29.9, 29.9, 2500.0, 1000.0, emptyList()),
            SkladovaPolozka("s_vejce", "8594022920019", "Vejce čerstvá M (10 ks)", false, MernaJednotka.KUS, 10.0, 54.9, 49.9, 30.0, 20.0, listOf(Alergen.VEJCE)),
            SkladovaPolozka("s_orechy", "8594003320111", "Vlašské ořechy mleté", false, MernaJednotka.GRAM, 250.0, 69.0, 69.0, 500.0, 500.0, listOf(Alergen.ORECHY)),
            SkladovaPolozka("s_marm", "8594006660214", "Linecká marmeláda rybíz a jahoda", false, MernaJednotka.GRAM, 400.0, 49.9, 49.9, 800.0, 400.0, emptyList()),
            SkladovaPolozka("s_nutella", "8000500037560", "Nutella lískooříškový krém", false, MernaJednotka.GRAM, 400.0, 99.9, 89.9, 400.0, 400.0, listOf(Alergen.ORECHY, Alergen.MLEKO, Alergen.SOJA)),
            SkladovaPolozka("s_mascar", "8002580015209", "Mascarpone krémové", false, MernaJednotka.GRAM, 250.0, 59.9, 59.9, 750.0, 500.0, listOf(Alergen.MLEKO)),
            SkladovaPolozka("s_smetana", "8593807234112", "Smetana ke šlehání 33%", false, MernaJednotka.MILILITR, 250.0, 39.9, 39.9, 750.0, 500.0, listOf(Alergen.MLEKO)),
            SkladovaPolozka("s_coko", "5410976880110", "Hořká čokoláda 54% pecky", false, MernaJednotka.GRAM, 500.0, 189.0, 149.0, 500.0, 500.0, listOf(Alergen.SOJA)),
            SkladovaPolozka("o_krab1kg", "8595000010014", "Krabice na cukroví 1 kg s okénkem", true, MernaJednotka.KUS, 1.0, 26.0, 26.0, 12.0, 10.0, emptyList()),
            SkladovaPolozka("o_dort26", "8595000010021", "Dortová krabice a podložka 26 cm", true, MernaJednotka.KUS, 1.0, 62.0, 62.0, 5.0, 3.0, emptyList())
        )

        val recepty = listOf(
            Receptura(
                id = "r_linecke",
                nazev = "Linecké cukroví",
                typ = TypReceptu.CUKROVI_DAVKA,
                vyteznostDavky = 1050.0,
                casPraceMin = 45,
                casTroubyMin = 25,
                zakladniProdejniCenaKc = 850.0,
                suroviny = listOf(
                    SlozkaReceptu("s_mouka", 520.0),
                    SlozkaReceptu("s_maslo", 350.0),
                    SlozkaReceptu("s_cukr", 180.0),
                    SlozkaReceptu("s_vejce", 2.0)
                ),
                varianty = listOf(
                    VariantaProduktu(
                        id = "v_lin_marm",
                        nazevVarianty = "plněné marmeládou",
                        extraSurovinyNaDavku = listOf(SlozkaReceptu("s_marm", 300.0)),
                        pridanaVahaGramy = 250.0,
                        extraCasPraceMin = 20,
                        prodejniCenaKc = 860.0
                    ),
                    VariantaProduktu(
                        id = "v_lin_nut",
                        nazevVarianty = "plněné Nutellou",
                        extraSurovinyNaDavku = listOf(SlozkaReceptu("s_nutella", 300.0)),
                        pridanaVahaGramy = 250.0,
                        extraCasPraceMin = 20,
                        prodejniCenaKc = 950.0
                    )
                ),
                polozkyMixu = emptyList(),
                postupPoznamka = "Těsto nechat přes noc v chladu, péct na 170 °C dorůžova."
            ),
            Receptura(
                id = "r_rohlicky",
                nazev = "Vanilkové rohlíčky",
                typ = TypReceptu.CUKROVI_DAVKA,
                vyteznostDavky = 1150.0,
                casPraceMin = 70,
                casTroubyMin = 35,
                zakladniProdejniCenaKc = 920.0,
                suroviny = listOf(
                    SlozkaReceptu("s_mouka", 420.0),
                    SlozkaReceptu("s_maslo", 320.0),
                    SlozkaReceptu("s_orechy", 160.0),
                    SlozkaReceptu("s_cukr", 220.0),
                    SlozkaReceptu("s_vejce", 2.0)
                ),
                varianty = emptyList(),
                polozkyMixu = emptyList(),
                postupPoznamka = "Ještě teplé obalovat v moučkovém cukru s vanilkou."
            ),
            Receptura(
                id = "r_mix1kg",
                nazev = "Vánoční krabice 1 kg (mix)",
                typ = TypReceptu.MIX_KRABICE,
                vyteznostDavky = 1.0,
                casPraceMin = 10,
                casTroubyMin = 0,
                zakladniProdejniCenaKc = 940.0,
                suroviny = listOf(SlozkaReceptu("o_krab1kg", 1.0)),
                varianty = emptyList(),
                polozkyMixu = listOf(
                    PolozkaMixKrabice("r_linecke", "v_lin_marm", 350.0),
                    PolozkaMixKrabice("r_linecke", "v_lin_nut", 300.0),
                    PolozkaMixKrabice("r_rohlicky", "", 350.0)
                ),
                postupPoznamka = "Vyskládat na pergamen a převázat stuhou."
            ),
            Receptura(
                id = "r_dort_coko",
                nazev = "Čokoládový dort s mascarpone (26 cm)",
                typ = TypReceptu.DORT_KUSOVKA,
                vyteznostDavky = 1.0,
                casPraceMin = 110,
                casTroubyMin = 50,
                zakladniProdejniCenaKc = 1690.0,
                suroviny = listOf(
                    SlozkaReceptu("s_vejce", 6.0),
                    SlozkaReceptu("s_mouka", 240.0),
                    SlozkaReceptu("s_cukr", 220.0),
                    SlozkaReceptu("s_coko", 250.0),
                    SlozkaReceptu("s_mascar", 500.0),
                    SlozkaReceptu("s_smetana", 500.0),
                    SlozkaReceptu("o_dort26", 1.0)
                ),
                varianty = emptyList(),
                polozkyMixu = emptyList(),
                postupPoznamka = "Korpus 175 °C 50 min, promazat pařížským krémem s mascarpone."
            )
        )

        val klientky = listOf(
            Klientka("k_jana", "Jana Nováková", "+420 777 123 456", "IG @jananovakova", listOf(Alergen.ORECHY), "Pozor, syn má alergii na ořechy."),
            Klientka("k_petra", "Petra Svobodová", "+420 606 987 654", "FB Petra Svobodová", emptyList(), "Bere pravidelně cukroví i narozeninové dorty.")
        )

        val objednavky = listOf(
            Objednavka(
                id = "obj_1",
                cisloZakazky = 101,
                klientkaId = "k_petra",
                terminPredani = "20. 12. 2026 15:00",
                stav = StavObjednavky.PRIJATO,
                polozky = listOf(
                    PolozkaObjednavky("r_mix1kg", "", "Vánoční krabice 1 kg (mix)", false, 2.0, 940.0),
                    PolozkaObjednavky("r_linecke", "v_lin_nut", "Linecké cukroví — plněné Nutellou", true, 500.0, 950.0)
                ),
                zaplacenaZalohaKc = 500.0,
                jeDoplaceno = false,
                odepsanoZeSkladu = false,
                poznamka = "Zavolat hodinu předem."
            ),
            Objednavka(
                id = "obj_2",
                cisloZakazky = 102,
                klientkaId = "k_jana",
                terminPredani = "21. 11. 2026 10:00",
                stav = StavObjednavky.PECU,
                polozky = listOf(
                    PolozkaObjednavky("r_dort_coko", "", "Čokoládový dort s mascarpone (26 cm)", false, 1.0, 1690.0),
                    PolozkaObjednavky("r_linecke", "v_lin_marm", "Linecké cukroví — plněné marmeládou", true, 1000.0, 860.0)
                ),
                zaplacenaZalohaKc = 1000.0,
                jeDoplaceno = false,
                odepsanoZeSkladu = true,
                poznamka = "Nápis Všechno nejlepší Kubíku 8 let."
            )
        )

        return DataCukrarny(
            nastaveni = NastaveniCukrarny(),
            sklad = sklad,
            recepty = recepty,
            klientky = klientky,
            objednavky = objednavky,
            posledniUlozeniMs = 1763150000000L
        )
    }
}
