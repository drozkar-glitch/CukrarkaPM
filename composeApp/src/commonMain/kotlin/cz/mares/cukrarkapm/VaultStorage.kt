package cz.mares.cukrarkapm

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

enum class TypVysledkuAuth {
    USPECH,
    POTVRDIT_NOVY_PIN,
    CHYBA
}

data class VysledekAuth(
    val typ: TypVysledkuAuth,
    val zprava: String,
    val ucet: UcetCukrarky? = null,
    val data: DataCukrarny? = null
)

@OptIn(ExperimentalEncodingApi::class)
object VaultStorage {
    private const val FIREBASE_ZAKLAD = "https://mares-games-default-rtdb.europe-west1.firebasedatabase.app/cukrarka_pm"
    private const val KLIC_ULOZENY_NICK = "cukrarka_nick_v1"
    private const val KLIC_ULOZENY_PIN = "cukrarka_pin_v1"
    private const val KLIC_LOKALNI_TREZOR = "cukrarka_trezor_v1"

    fun vytvorPrazdnaData(casMs: Long = aktualniCasMs()): DataCukrarny {
        return DataCukrarny(
            nastaveni = NastaveniCukrarny(),
            sklad = emptyList(),
            recepty = emptyList(),
            klientky = emptyList(),
            objednavky = emptyList(),
            posledniUlozeniMs = casMs
        )
    }

    fun cistyKlicNicku(nick: String): String {
        val zaklad = nick.trim().lowercase()
        val sb = StringBuilder()
        for (ch in zaklad) {
            when {
                ch in 'a'..'z' || ch in '0'..'9' -> sb.append(ch)
                ch == 'á' -> sb.append('a')
                ch == 'č' -> sb.append('c')
                ch == 'ď' -> sb.append('d')
                ch == 'é' || ch == 'ě' -> sb.append('e')
                ch == 'í' -> sb.append('i')
                ch == 'ň' -> sb.append('n')
                ch == 'ó' -> sb.append('o')
                ch == 'ř' -> sb.append('r')
                ch == 'š' -> sb.append('s')
                ch == 'ť' -> sb.append('t')
                ch == 'ú' || ch == 'ů' -> sb.append('u')
                ch == 'ý' -> sb.append('y')
                ch == 'ž' -> sb.append('z')
                ch == '_' || ch == '-' -> sb.append('_')
            }
        }
        return sb.toString().take(28)
    }

    fun nactiUlozenouRelaci(): Pair<UcetCukrarky, DataCukrarny>? {
        val nick = nactiLokalniText(KLIC_ULOZENY_NICK)?.trim().orEmpty()
        val pin = nactiLokalniText(KLIC_ULOZENY_PIN)?.trim().orEmpty()
        if (nick.isEmpty() || pin.isEmpty()) return null
        val ucet = UcetCukrarky(nick = nick, pin = pin, jeUkazkovyRezim = false)
        val zasifrovano = nactiLokalniText(KLIC_LOKALNI_TREZOR).orEmpty()
        val desifrovano = if (zasifrovano.isNotEmpty()) desifrujText(zasifrovano, cistyKlicNicku(nick), pin) else null
        val data = if (desifrovano != null) rozbalData(desifrovano) else vytvorPrazdnaData()
        return ucet to data
    }

    fun odhlasitLokalne() {
        ulozLokalniText(KLIC_ULOZENY_NICK, "")
        ulozLokalniText(KLIC_ULOZENY_PIN, "")
    }

    fun prihlasNeboZalozUcet(
        zadanyNick: String,
        zadanyPin: String,
        potvrzenyPin: String,
        vysledek: (VysledekAuth) -> Unit
    ) {
        val klicNicku = cistyKlicNicku(zadanyNick)
        val cistyPin = zadanyPin.trim().filter { it.isDigit() }
        if (klicNicku.length < 3) {
            vysledek(VysledekAuth(TypVysledkuAuth.CHYBA, "Přezdívka musí mít alespoň 3 písmena nebo číslice."))
            return
        }
        if (cistyPin.length < 4) {
            vysledek(VysledekAuth(TypVysledkuAuth.CHYBA, "PIN musí mít alespoň 4 číslice."))
            return
        }

        val otiskPinu = vytvorOtisk(klicNicku, cistyPin, "auth_pin_v1")
        val idTrezoru = vytvorOtisk(klicNicku, cistyPin, "vault_slot_v1")
        val urlNicku = "$FIREBASE_ZAKLAD/nicks/$klicNicku.json"

        httpPozadavek("GET", urlNicku, null) { uspech, odpoved ->
            if (!uspech) {
                vysledek(VysledekAuth(TypVysledkuAuth.CHYBA, "Nepodařilo se spojit s úložištěm. Zkontroluj připojení k internetu."))
                return@httpPozadavek
            }
            val orez = odpoved.trim()
            if (orez.isEmpty() || orez == "null") {
                if (potvrzenyPin.isEmpty()) {
                    vysledek(
                        VysledekAuth(
                            TypVysledkuAuth.POTVRDIT_NOVY_PIN,
                            "Přezdívka je volná! Zadej prosím stejný PIN ještě jednou pro kontrolu."
                        )
                    )
                    return@httpPozadavek
                }
                if (potvrzenyPin.trim() != cistyPin) {
                    vysledek(VysledekAuth(TypVysledkuAuth.POTVRDIT_NOVY_PIN, "Zadané PINy se neshodují, zkus to prosím znovu."))
                    return@httpPozadavek
                }
                val cas = aktualniCasMs()
                val jsonNicku = """{"verifier":"$otiskPinu","displayNick":"${escapujJson(zadanyNick.trim())}","created":$cas}"""
                httpPozadavek("PUT", urlNicku, jsonNicku) { okZapis, _ ->
                    if (!okZapis) {
                        vysledek(VysledekAuth(TypVysledkuAuth.CHYBA, "Nepodařilo se založit účet v cloudu."))
                        return@httpPozadavek
                    }
                    val ucet = UcetCukrarky(nick = zadanyNick.trim(), pin = cistyPin, jeUkazkovyRezim = false)
                    val prazdnaCukrarna = vytvorPrazdnaData(cas)
                    ulozRelaciAData(ucet, prazdnaCukrarna) {
                        vysledek(VysledekAuth(TypVysledkuAuth.USPECH, "Cukrárna založena!", ucet, prazdnaCukrarna))
                    }
                }
            } else {
                val ulozenyOtisk = vyctiJsonHodnotu(orez, "verifier").orEmpty()
                if (ulozenyOtisk != otiskPinu) {
                    vysledek(VysledekAuth(TypVysledkuAuth.CHYBA, "Tato přezdívka už je obsazená a zadaný PIN nesouhlasí."))
                    return@httpPozadavek
                }
                val zobrazovanyNick = vyctiJsonHodnotu(orez, "displayNick")?.takeIf { it.isNotBlank() } ?: zadanyNick.trim()
                val urlTrezoru = "$FIREBASE_ZAKLAD/vaults/$idTrezoru.json"
                httpPozadavek("GET", urlTrezoru, null) { okTrezor, odpovedTrezoru ->
                    val ucet = UcetCukrarky(nick = zobrazovanyNick, pin = cistyPin, jeUkazkovyRezim = false)
                    val zasifrovanyPayload = if (okTrezor) vyctiJsonHodnotu(odpovedTrezoru, "payload") else null
                    val cistyObsah = if (!zasifrovanyPayload.isNullOrBlank()) {
                        desifrujText(zasifrovanyPayload, klicNicku, cistyPin)
                    } else null
                    val nactenaData = if (cistyObsah != null) rozbalData(cistyObsah) else vytvorPrazdnaData()
                    ulozLokalniKopie(ucet, nactenaData)
                    vysledek(VysledekAuth(TypVysledkuAuth.USPECH, "Vítej zpátky!", ucet, nactenaData))
                }
            }
        }
    }

    fun ulozRelaciAData(ucet: UcetCukrarky, data: DataCukrarny, poUlozeni: (Boolean) -> Unit = {}) {
        val aktualizovana = data.copy(posledniUlozeniMs = aktualniCasMs())
        if (ucet.jeUkazkovyRezim) {
            poUlozeni(true)
            return
        }
        val sifrovano = ulozLokalniKopie(ucet, aktualizovana)
        val klicNicku = cistyKlicNicku(ucet.nick)
        val idTrezoru = vytvorOtisk(klicNicku, ucet.pin, "vault_slot_v1")
        val telo = """{"payload":"$sifrovano","updated":${aktualizovana.posledniUlozeniMs}}"""
        httpPozadavek("PUT", "$FIREBASE_ZAKLAD/vaults/$idTrezoru.json", telo) { ok, _ ->
            poUlozeni(ok)
        }
    }

    fun stahniNejnovsiDataZCloudu(ucet: UcetCukrarky, vysledek: (DataCukrarny?) -> Unit) {
        if (ucet.jeUkazkovyRezim) {
            vysledek(null)
            return
        }
        val klicNicku = cistyKlicNicku(ucet.nick)
        val idTrezoru = vytvorOtisk(klicNicku, ucet.pin, "vault_slot_v1")
        httpPozadavek("GET", "$FIREBASE_ZAKLAD/vaults/$idTrezoru.json", null) { ok, odpoved ->
            if (!ok) {
                vysledek(null)
                return@httpPozadavek
            }
            val payload = vyctiJsonHodnotu(odpoved, "payload")
            val cisty = if (!payload.isNullOrBlank()) desifrujText(payload, klicNicku, ucet.pin) else null
            val data = if (cisty != null) rozbalData(cisty) else null
            if (data != null) ulozLokalniKopie(ucet, data)
            vysledek(data)
        }
    }

    fun smazUcetZCloudu(ucet: UcetCukrarky, hotovo: (Boolean) -> Unit) {
        odhlasitLokalne()
        ulozLokalniText(KLIC_LOKALNI_TREZOR, "")
        if (ucet.jeUkazkovyRezim) {
            hotovo(true)
            return
        }
        val klicNicku = cistyKlicNicku(ucet.nick)
        val idTrezoru = vytvorOtisk(klicNicku, ucet.pin, "vault_slot_v1")
        httpPozadavek("DELETE", "$FIREBASE_ZAKLAD/vaults/$idTrezoru.json", null) { _, _ ->
            httpPozadavek("DELETE", "$FIREBASE_ZAKLAD/nicks/$klicNicku.json", null) { ok, _ ->
                hotovo(ok)
            }
        }
    }

    fun vyhledejProduktPodleEanu(surovyKod: String, vysledek: (PolozkaKataloguEan?, RozpoznanyCarovyKod) -> Unit) {
        val infoKod = analyzujCeskyCarovyKod(surovyKod)
        val klic = infoKod.vyhledavaciKlic
        if (klic.length < 6) {
            vysledek(null, infoKod)
            return
        }
        httpPozadavek("GET", "$FIREBASE_ZAKLAD/ean_catalog/$klic.json", null) { okFb, odpFb ->
            val orezFb = odpFb.trim()
            if (okFb && orezFb.isNotEmpty() && orezFb != "null") {
                val nazev = vyctiJsonHodnotu(orezFb, "nazev").orEmpty()
                if (nazev.isNotBlank()) {
                    val jednText = vyctiJsonHodnotu(orezFb, "jednotka").orEmpty()
                    val jedn = MernaJednotka.entries.firstOrNull { it.name == jednText } ?: MernaJednotka.GRAM
                    val baleni = vyctiJsonHodnotu(orezFb, "velikostBaleni")?.toDoubleOrNull()
                        ?: infoKod.odvozenaVahaGramy ?: 250.0
                    val jeObal = vyctiJsonHodnotu(orezFb, "jeObal") == "true"
                    val alText = vyctiJsonHodnotu(orezFb, "alergeny").orEmpty()
                    val alergeny = alText.split(",").mapNotNull { kod ->
                        Alergen.entries.firstOrNull { it.name == kod.trim() }
                    }
                    vysledek(PolozkaKataloguEan(infoKod.cistyKod, nazev, jedn, baleni, jeObal, alergeny), infoKod)
                    return@httpPozadavek
                }
            }
            val offUrl = "https://world.openfoodfacts.org/api/v2/product/$klic.json?fields=product_name,product_name_cs,quantity,allergens_tags"
            httpPozadavek("GET", offUrl, null) { okOff, odpOff ->
                if (!okOff || odpOff.isBlank()) {
                    vysledek(null, infoKod)
                    return@httpPozadavek
                }
                val ceskyNazev = vyctiJsonHodnotu(odpoved = odpOff, klic = "product_name_cs")?.trim().orEmpty()
                val obecnyNazev = vyctiJsonHodnotu(odpoved = odpOff, klic = "product_name")?.trim().orEmpty()
                val vyslednyNazev = ceskyNazev.ifEmpty { obecnyNazev }
                if (vyslednyNazev.isEmpty()) {
                    vysledek(null, infoKod)
                    return@httpPozadavek
                }
                val mnozstviText = vyctiJsonHodnotu(odpOff, "quantity").orEmpty()
                val (jednotka, velikost) = rozparsujVelikostBaleni(mnozstviText, infoKod.odvozenaVahaGramy)
                val alergeny = rozparsujAlergenyOff(odpOff)
                vysledek(
                    PolozkaKataloguEan(
                        ean = infoKod.cistyKod,
                        nazev = vyslednyNazev,
                        jednotka = jednotka,
                        velikostBaleni = velikost,
                        jeObal = false,
                        alergeny = alergeny
                    ),
                    infoKod
                )
            }
        }
    }

    fun ulozDoSdilenehoKataloguEan(polozka: SkladovaPolozka) {
        val infoKod = analyzujCeskyCarovyKod(polozka.ean)
        if (infoKod.vyhledavaciKlic.length < 6 || polozka.nazev.isBlank()) return
        val alSeznam = polozka.alergeny.joinToString(",") { it.name }
        val json = """{"nazev":"${escapujJson(polozka.nazev)}","jednotka":"${polozka.jednotka.name}","velikostBaleni":${polozka.velikostBaleni},"jeObal":${polozka.jeObal},"alergeny":"$alSeznam"}"""
        httpPozadavek("PUT", "$FIREBASE_ZAKLAD/ean_catalog/${infoKod.vyhledavaciKlic}.json", json) { _, _ -> }
    }

    private fun ulozLokalniKopie(ucet: UcetCukrarky, data: DataCukrarny): String {
        val klicNicku = cistyKlicNicku(ucet.nick)
        val zabaleno = zabalData(data)
        val sifrovano = zasifrujText(zabaleno, klicNicku, ucet.pin)
        ulozLokalniText(KLIC_ULOZENY_NICK, ucet.nick)
        ulozLokalniText(KLIC_ULOZENY_PIN, ucet.pin)
        ulozLokalniText(KLIC_LOKALNI_TREZOR, sifrovano)
        return sifrovano
    }

    private fun b64Koduj(text: String): String = Base64.UrlSafe.encode(text.encodeToByteArray())

    private fun b64Dekoduj(kod: String): String = try {
        if (kod.isEmpty()) "" else Base64.UrlSafe.decode(kod).decodeToString()
    } catch (_: Exception) {
        ""
    }

    private fun zabalData(data: DataCukrarny): String {
        val radky = mutableListOf<String>()
        val n = data.nastaveni
        radky += "SET|${b64Koduj(n.nazevDilny)}|${n.hodinovaOdmenaKc}|${n.cenaHodinyTroubyKc}|${b64Koduj(n.bankovniUcet)}|${data.posledniUlozeniMs}"
        for (s in data.sklad) {
            val al = s.alergeny.joinToString(",") { it.name }
            radky += "SKL|${b64Koduj(s.id)}|${b64Koduj(s.ean)}|${b64Koduj(s.nazev)}|${s.jeObal}|${s.jednotka.name}|${s.velikostBaleni}|${s.cenaZaBaleniKc}|${s.predchoziCenaBaleniKc}|${s.aktualniZasoba}|${s.minimalniZasoba}|$al"
        }
        for (r in data.recepty) {
            val sur = r.suroviny.joinToString(";") { "${b64Koduj(it.polozkaId)}:${it.mnozstvi}" }
            val mix = r.polozkyMixu.joinToString(";") { "${b64Koduj(it.receptId)}:${b64Koduj(it.variantaId)}:${it.hmotnostGramy}" }
            radky += "REC|${b64Koduj(r.id)}|${b64Koduj(r.nazev)}|${r.typ.name}|${r.vyteznostDavky}|${r.casPraceMin}|${r.casTroubyMin}|${r.zakladniProdejniCenaKc}|$sur|$mix|${b64Koduj(r.postupPoznamka)}"
            for (v in r.varianty) {
                val exSur = v.extraSurovinyNaDavku.joinToString(";") { "${b64Koduj(it.polozkaId)}:${it.mnozstvi}" }
                radky += "VAR|${b64Koduj(r.id)}|${b64Koduj(v.id)}|${b64Koduj(v.nazevVarianty)}|${v.pridanaVahaGramy}|${v.extraCasPraceMin}|${v.prodejniCenaKc}|$exSur"
            }
        }
        for (k in data.klientky) {
            val al = k.hlidaneAlergie.joinToString(",") { it.name }
            radky += "KLI|${b64Koduj(k.id)}|${b64Koduj(k.jmeno)}|${b64Koduj(k.telefon)}|${b64Koduj(k.kontaktSite)}|$al|${b64Koduj(k.poznamka)}"
        }
        for (o in data.objednavky) {
            radky += "OBJ|${b64Koduj(o.id)}|${o.cisloZakazky}|${b64Koduj(o.klientkaId)}|${b64Koduj(o.terminPredani)}|${o.stav.name}|${o.zaplacenaZalohaKc}|${o.jeDoplaceno}|${o.odepsanoZeSkladu}|${b64Koduj(o.poznamka)}"
            for (p in o.polozky) {
                radky += "POL|${b64Koduj(o.id)}|${b64Koduj(p.receptId)}|${b64Koduj(p.variantaId)}|${b64Koduj(p.nazevSnapshot)}|${p.jeNaVahu}|${p.mnozstvi}|${p.jednCenaKc}"
            }
        }
        return radky.joinToString("\n")
    }

    private fun rozbalData(obsah: String): DataCukrarny {
        var nastaveni = NastaveniCukrarny()
        var casUlozeni = aktualniCasMs()
        val sklad = mutableListOf<SkladovaPolozka>()
        val zakladReceptu = mutableListOf<Receptura>()
        val variantyPodleReceptu = mutableListOf<Pair<String, VariantaProduktu>>()
        val klientky = mutableListOf<Klientka>()
        val zakladObjednavek = mutableListOf<Objednavka>()
        val polozkyPodleObjednavky = mutableListOf<Pair<String, PolozkaObjednavky>>()

        for (radek in obsah.lines()) {
            if (radek.isBlank()) continue
            val p = radek.split("|")
            when (p.firstOrNull()) {
                "SET" -> if (p.size >= 6) {
                    nastaveni = NastaveniCukrarny(
                        nazevDilny = b64Dekoduj(p[1]).ifBlank { "Moje sladká výrobna" },
                        hodinovaOdmenaKc = p[2].toDoubleOrNull() ?: 250.0,
                        cenaHodinyTroubyKc = p[3].toDoubleOrNull() ?: 12.0,
                        bankovniUcet = b64Dekoduj(p[4])
                    )
                    casUlozeni = p[5].toLongOrNull() ?: casUlozeni
                }
                "SKL" -> if (p.size >= 12) {
                    val al = p[11].split(",").mapNotNull { kod -> Alergen.entries.firstOrNull { it.name == kod } }
                    sklad += SkladovaPolozka(
                        id = b64Dekoduj(p[1]),
                        ean = b64Dekoduj(p[2]),
                        nazev = b64Dekoduj(p[3]),
                        jeObal = p[4] == "true",
                        jednotka = MernaJednotka.entries.firstOrNull { it.name == p[5] } ?: MernaJednotka.GRAM,
                        velikostBaleni = p[6].toDoubleOrNull() ?: 1.0,
                        cenaZaBaleniKc = p[7].toDoubleOrNull() ?: 0.0,
                        predchoziCenaBaleniKc = p[8].toDoubleOrNull() ?: 0.0,
                        aktualniZasoba = p[9].toDoubleOrNull() ?: 0.0,
                        minimalniZasoba = p[10].toDoubleOrNull() ?: 0.0,
                        alergeny = al
                    )
                }
                "REC" -> if (p.size >= 11) {
                    val sur = p[8].split(";").mapNotNull { blok ->
                        val d = blok.split(":")
                        if (d.size == 2) SlozkaReceptu(b64Dekoduj(d[0]), d[1].toDoubleOrNull() ?: 0.0) else null
                    }
                    val mix = p[9].split(";").mapNotNull { blok ->
                        val d = blok.split(":")
                        if (d.size == 3) PolozkaMixKrabice(b64Dekoduj(d[0]), b64Dekoduj(d[1]), d[2].toDoubleOrNull() ?: 0.0) else null
                    }
                    zakladReceptu += Receptura(
                        id = b64Dekoduj(p[1]),
                        nazev = b64Dekoduj(p[2]),
                        typ = TypReceptu.entries.firstOrNull { it.name == p[3] } ?: TypReceptu.CUKROVI_DAVKA,
                        vyteznostDavky = p[4].toDoubleOrNull() ?: 1000.0,
                        casPraceMin = p[5].toIntOrNull() ?: 0,
                        casTroubyMin = p[6].toIntOrNull() ?: 0,
                        zakladniProdejniCenaKc = p[7].toDoubleOrNull() ?: 0.0,
                        suroviny = sur,
                        varianty = emptyList(),
                        polozkyMixu = mix,
                        postupPoznamka = b64Dekoduj(p[10])
                    )
                }
                "VAR" -> if (p.size >= 8) {
                    val exSur = p[7].split(";").mapNotNull { blok ->
                        val d = blok.split(":")
                        if (d.size == 2) SlozkaReceptu(b64Dekoduj(d[0]), d[1].toDoubleOrNull() ?: 0.0) else null
                    }
                    variantyPodleReceptu += b64Dekoduj(p[1]) to VariantaProduktu(
                        id = b64Dekoduj(p[2]),
                        nazevVarianty = b64Dekoduj(p[3]),
                        extraSurovinyNaDavku = exSur,
                        pridanaVahaGramy = p[4].toDoubleOrNull() ?: 0.0,
                        extraCasPraceMin = p[5].toIntOrNull() ?: 0,
                        prodejniCenaKc = p[6].toDoubleOrNull() ?: 0.0
                    )
                }
                "KLI" -> if (p.size >= 7) {
                    val al = p[5].split(",").mapNotNull { kod -> Alergen.entries.firstOrNull { it.name == kod } }
                    klientky += Klientka(
                        id = b64Dekoduj(p[1]),
                        jmeno = b64Dekoduj(p[2]),
                        telefon = b64Dekoduj(p[3]),
                        kontaktSite = b64Dekoduj(p[4]),
                        hlidaneAlergie = al,
                        poznamka = b64Dekoduj(p[6])
                    )
                }
                "OBJ" -> if (p.size >= 10) {
                    zakladObjednavek += Objednavka(
                        id = b64Dekoduj(p[1]),
                        cisloZakazky = p[2].toIntOrNull() ?: 101,
                        klientkaId = b64Dekoduj(p[3]),
                        terminPredani = b64Dekoduj(p[4]),
                        stav = StavObjednavky.entries.firstOrNull { it.name == p[5] } ?: StavObjednavky.PRIJATO,
                        polozky = emptyList(),
                        zaplacenaZalohaKc = p[6].toDoubleOrNull() ?: 0.0,
                        jeDoplaceno = p[7] == "true",
                        odepsanoZeSkladu = p[8] == "true",
                        poznamka = b64Dekoduj(p[9])
                    )
                }
                "POL" -> if (p.size >= 8) {
                    polozkyPodleObjednavky += b64Dekoduj(p[1]) to PolozkaObjednavky(
                        receptId = b64Dekoduj(p[2]),
                        variantaId = b64Dekoduj(p[3]),
                        nazevSnapshot = b64Dekoduj(p[4]),
                        jeNaVahu = p[5] == "true",
                        mnozstvi = p[6].toDoubleOrNull() ?: 1.0,
                        jednCenaKc = p[7].toDoubleOrNull() ?: 0.0
                    )
                }
            }
        }

        val recepty = zakladReceptu.map { rec ->
            rec.copy(varianty = variantyPodleReceptu.filter { it.first == rec.id }.map { it.second })
        }
        val objednavky = zakladObjednavek.map { obj ->
            obj.copy(polozky = polozkyPodleObjednavky.filter { it.first == obj.id }.map { it.second })
        }
        return DataCukrarny(nastaveni, sklad, recepty, klientky, objednavky, casUlozeni)
    }

    private fun mirnePromichej(stav: LongArray) {
        repeat(6) {
            stav[0] = (stav[0] + stav[1]).rotateLeft(13) xor stav[3]
            stav[2] = (stav[2] + stav[3]).rotateLeft(17) xor stav[1]
            stav[1] = (stav[1] xor stav[2]).rotateLeft(29) + stav[0]
            stav[3] = (stav[3] xor stav[0]).rotateLeft(37) + stav[2]
        }
    }

    private fun odvodKlicovyStav(nick: String, pin: String, ucel: String): LongArray {
        val stav = longArrayOf(
            1315423911L,
            2654435761L,
            5242871319L,
            9753124683L
        )
        val vstup = "$ucel|${cistyKlicNicku(nick)}|${pin.trim()}".encodeToByteArray()
        for (i in vstup.indices) {
            stav[i and 3] = stav[i and 3] xor ((vstup[i].toLong() and 0xFFL) shl ((i % 7) * 8))
            mirnePromichej(stav)
        }
        repeat(2048) { kolo ->
            stav[0] = stav[0] xor (kolo.toLong() * 6364136223846793005L + 1442695040888963407L)
            mirnePromichej(stav)
        }
        return stav
    }

    private fun vytvorOtisk(nick: String, pin: String, ucel: String): String {
        val s = odvodKlicovyStav(nick, pin, ucel)
        return longNaHex(s[0] xor s[2]) + longNaHex(s[1] xor s[3])
    }

    private fun zasifrujText(cistyText: String, nick: String, pin: String): String {
        val zaklad = odvodKlicovyStav(nick, pin, "cipher_v1")
        val sul = aktualniCasMs() xor (cistyText.length.toLong() shl 24)
        val bajty = cistyText.encodeToByteArray()
        val vystup = ByteArray(bajty.size)
        val proud = zaklad.copyOf()
        proud[0] = proud[0] xor sul
        mirnePromichej(proud)

        val mac = longArrayOf(zaklad[2] xor sul, zaklad[3] + bajty.size.toLong())
        for (i in bajty.indices) {
            if (i % 16 == 0) mirnePromichej(proud)
            val klicovyBajt = ((proud[(i / 8) % 2] ushr ((i % 8) * 8)) and 0xFFL).toInt()
            val sifBajt = (bajty[i].toInt() and 0xFF) xor klicovyBajt
            vystup[i] = sifBajt.toByte()
            mac[0] = (mac[0] + sifBajt.toLong() + i.toLong()).rotateLeft(11) xor proud[2]
            mac[1] = (mac[1] xor mac[0]).rotateLeft(23) + proud[3]
        }
        val sifrovanoB64 = Base64.UrlSafe.encode(vystup)
        return "v1:${longNaHex(sul)}:${longNaHex(mac[0])}${longNaHex(mac[1])}:$sifrovanoB64"
    }

    private fun desifrujText(zabaleno: String, nick: String, pin: String): String? {
        val casti = zabaleno.split(":")
        if (casti.size != 4 || casti[0] != "v1") return null
        val sul = hexNaLong(casti[1]) ?: return null
        val ocekavanyMac = casti[2]
        val sifBajty = try {
            Base64.UrlSafe.decode(casti[3])
        } catch (_: Exception) {
            return null
        }

        val zaklad = odvodKlicovyStav(nick, pin, "cipher_v1")
        val proud = zaklad.copyOf()
        proud[0] = proud[0] xor sul
        mirnePromichej(proud)

        val cisteBajty = ByteArray(sifBajty.size)
        val mac = longArrayOf(zaklad[2] xor sul, zaklad[3] + sifBajty.size.toLong())
        for (i in sifBajty.indices) {
            if (i % 16 == 0) mirnePromichej(proud)
            val sifBajt = sifBajty[i].toInt() and 0xFF
            mac[0] = (mac[0] + sifBajt.toLong() + i.toLong()).rotateLeft(11) xor proud[2]
            mac[1] = (mac[1] xor mac[0]).rotateLeft(23) + proud[3]
            val klicovyBajt = ((proud[(i / 8) % 2] ushr ((i % 8) * 8)) and 0xFFL).toInt()
            cisteBajty[i] = (sifBajt xor klicovyBajt).toByte()
        }
        val vypoctenyMac = longNaHex(mac[0]) + longNaHex(mac[1])
        if (vypoctenyMac != ocekavanyMac) return null
        return try {
            cisteBajty.decodeToString()
        } catch (_: Exception) {
            null
        }
    }

    private fun longNaHex(hodnota: Long): String {
        val znaky = "0123456789abcdef"
        val sb = StringBuilder(16)
        for (pos in 15 downTo 0) {
            val nib = ((hodnota ushr (pos * 4)) and 0xFL).toInt()
            sb.append(znaky[nib])
        }
        return sb.toString()
    }

    private fun hexNaLong(hex: String): Long? {
        if (hex.length != 16) return null
        var vysl = 0L
        for (ch in hex) {
            val cislice = when (ch) {
                in '0'..'9' -> ch - '0'
                in 'a'..'f' -> ch - 'a' + 10
                in 'A'..'F' -> ch - 'A' + 10
                else -> return null
            }
            vysl = (vysl shl 4) or cislice.toLong()
        }
        return vysl
    }

    private fun escapujJson(s: String): String = buildString {
        for (ch in s) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun vyctiJsonHodnotu(odpoved: String, klic: String): String? {
        val hledany = "\"$klic\""
        val idxKlic = odpoved.indexOf(hledany)
        if (idxKlic < 0) return null
        val dvojtecka = odpoved.indexOf(':', idxKlic + hledany.length)
        if (dvojtecka < 0) return null
        var i = dvojtecka + 1
        while (i < odpoved.length && odpoved[i].isWhitespace()) i++
        if (i >= odpoved.length) return null
        if (odpoved[i] == '"') {
            i++
            val sb = StringBuilder()
            while (i < odpoved.length) {
                val c = odpoved[i]
                if (c == '\\' && i + 1 < odpoved.length) {
                    val dalsi = odpoved[i + 1]
                    when (dalsi) {
                        '"', '\\', '/' -> { sb.append(dalsi); i += 2 }
                        'n' -> { sb.append('\n'); i += 2 }
                        't' -> { sb.append('\t'); i += 2 }
                        'u' -> if (i + 5 < odpoved.length) {
                            val kod = odpoved.substring(i + 2, i + 6).toIntOrNull(16)
                            if (kod != null) sb.append(kod.toChar())
                            i += 6
                        } else i += 2
                        else -> { sb.append(dalsi); i += 2 }
                    }
                } else if (c == '"') {
                    break
                } else {
                    sb.append(c)
                    i++
                }
            }
            return sb.toString()
        } else {
            val start = i
            while (i < odpoved.length && odpoved[i] !in charArrayOf(',', '}', ']', '\n', '\r')) i++
            val hodnota = odpoved.substring(start, i).trim()
            return if (hodnota == "null") null else hodnota
        }
    }

    private fun rozparsujVelikostBaleni(text: String, vahovyKodGramy: Double?): Pair<MernaJednotka, Double> {
        if (vahovyKodGramy != null && vahovyKodGramy > 0.0) {
            return MernaJednotka.GRAM to vahovyKodGramy
        }
        val maly = text.lowercase().replace(',', '.')
        val cisloText = StringBuilder()
        var nalezenaCislice = false
        for (ch in maly) {
            if (ch.isDigit() || (ch == '.' && nalezenaCislice)) {
                cisloText.append(ch)
                nalezenaCislice = true
            } else if (nalezenaCislice) {
                break
            }
        }
        val cislo = cisloText.toString().toDoubleOrNull() ?: return (MernaJednotka.GRAM to 250.0)
        return when {
            maly.contains("kg") -> MernaJednotka.GRAM to (cislo * 1000.0)
            maly.contains("ml") -> MernaJednotka.MILILITR to cislo
            maly.contains("cl") -> MernaJednotka.MILILITR to (cislo * 10.0)
            maly.contains(" l") || maly.endsWith("l") -> MernaJednotka.MILILITR to (cislo * 1000.0)
            maly.contains("ks") || maly.contains("pcs") -> MernaJednotka.KUS to cislo
            else -> MernaJednotka.GRAM to cislo
        }
    }

    private fun rozparsujAlergenyOff(json: String): List<Alergen> {
        val maly = json.lowercase()
        val vysl = mutableListOf<Alergen>()
        if (maly.contains("gluten") || maly.contains("wheat")) vysl += Alergen.LEPEK
        if (maly.contains("egg")) vysl += Alergen.VEJCE
        if (maly.contains("milk") || maly.contains("lactose")) vysl += Alergen.MLEKO
        if (maly.contains("nuts") || maly.contains("hazelnut") || maly.contains("almond") || maly.contains("walnut")) vysl += Alergen.ORECHY
        if (maly.contains("peanut")) vysl += Alergen.ARASIDY
        if (maly.contains("soy")) vysl += Alergen.SOJA
        if (maly.contains("sesame")) vysl += Alergen.SEZAM
        return vysl.distinct()
    }
}
