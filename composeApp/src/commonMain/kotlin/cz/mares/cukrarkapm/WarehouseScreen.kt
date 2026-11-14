package cz.mares.cukrarkapm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

fun parseCeskeCislo(text: String): Double {
    return text.trim().replace(',', '.').replace(" ", "").toDoubleOrNull() ?: 0.0
}

fun formatJednoducheCislo(hodnota: Double): String {
    val desetiny = (hodnota * 100.0).roundToInt()
    return if (desetiny % 100 == 0) {
        (desetiny / 100).toString()
    } else {
        (desetiny / 100.0).toString().replace('.', ',')
    }
}

@Composable
fun VyberovyStitek(text: String, vybrano: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (vybrano) Color(0xFFAD1457) else Color(0xFFFCE4EC),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            color = if (vybrano) Color.White else Color(0xFF5D4037),
            fontWeight = if (vybrano) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun OddelovacRadek() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(1.dp)
            .background(Color(0xFFEEEEEE))
    )
}

@Composable
fun WarehouseScreen(
    data: DataCukrarny,
    priZmeneDat: (DataCukrarny) -> Unit
) {
    val malinova = Color(0xFFAD1457)
    var otevrenaCtecka by remember { mutableStateOf(false) }
    var hledaniKoduProbiha by remember { mutableStateOf(false) }
    var infoHlaska by remember { mutableStateOf("") }
    var filtrKategorie by remember { mutableStateOf(0) }
    var hledanyText by remember { mutableStateOf("") }

    var polozkaProDokoupeni by remember { mutableStateOf<SkladovaPolozka?>(null) }
    var predvyplnenaVahaGramy by remember { mutableStateOf<Double?>(null) }
    var upravovanaPolozka by remember { mutableStateOf<SkladovaPolozka?>(null) }
    var jeNovaPolozka by remember { mutableStateOf(false) }

    val filtrovanySklad = remember(data.sklad, filtrKategorie, hledanyText) {
        data.sklad.filter { pol ->
            val sediKat = when (filtrKategorie) {
                1 -> !pol.jeObal
                2 -> pol.jeObal
                3 -> pol.aktualniZasoba < pol.minimalniZasoba || pol.zdraziloProcent > 0
                else -> true
            }
            val sediText = hledanyText.isBlank() ||
                pol.nazev.contains(hledanyText.trim(), ignoreCase = true) ||
                pol.ean.contains(hledanyText.trim())
            sediKat && sediText
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { otevrenaCtecka = true },
                colors = ButtonDefaults.buttonColors(containerColor = malinova),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1.25f)
                    .height(54.dp)
            ) {
                Text("📷 Pípnout nákup", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            OutlinedButton(
                onClick = {
                    jeNovaPolozka = true
                    upravovanaPolozka = SkladovaPolozka(
                        id = "skl_${aktualniCasMs()}",
                        ean = "",
                        nazev = "",
                        jeObal = false,
                        jednotka = MernaJednotka.GRAM,
                        velikostBaleni = 250.0,
                        cenaZaBaleniKc = 0.0,
                        predchoziCenaBaleniKc = 0.0,
                        aktualniZasoba = 0.0,
                        minimalniZasoba = 250.0,
                        alergeny = emptyList()
                    )
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
            ) {
                Text("➕ Přidat ručně", fontWeight = FontWeight.Bold, color = malinova)
            }
        }

        if (hledaniKoduProbiha) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF8E1), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                CircularProgressIndicator(color = malinova)
                Text("Hledám naskenovaný produkt v databázi...", fontWeight = FontWeight.Medium)
            }
        }

        if (infoHlaska.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text(infoHlaska, color = Color(0xFF2E7D32), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = hledanyText,
            onValueChange = { hledanyText = it },
            label = { Text("🔍 Hledat surovinu, krabici nebo EAN") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VyberovyStitek("Vše (${data.sklad.size})", filtrKategorie == 0) { filtrKategorie = 0 }
            VyberovyStitek("🥣 Suroviny", filtrKategorie == 1) { filtrKategorie = 1 }
            VyberovyStitek("🎀 Krabice a obaly", filtrKategorie == 2) { filtrKategorie = 2 }
            VyberovyStitek("⚠️ Dochází / Zdražilo", filtrKategorie == 3) { filtrKategorie = 3 }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtrovanySklad, key = { it.id }) { pol ->
                val dochazi = pol.aktualniZasoba < pol.minimalniZasoba
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = (if (pol.jeObal) "🎀 " else "🥣 ") + pol.nazev,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF3E2723)
                                )
                                Text(
                                    text = "Balení ${formatMnozstviSkladu(pol.velikostBaleni, pol.jednotka)} za ${formatJednoducheCislo(pol.cenaZaBaleniKc)} Kč",
                                    fontSize = 13.sp,
                                    color = Color(0xFF6D4C41)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatMnozstviSkladu(pol.aktualniZasoba, pol.jednotka),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = if (dochazi) Color(0xFFE65100) else Color(0xFF2E7D32)
                                )
                                Text(
                                    text = if (dochazi) "🟠 Dochází!" else "🟢 Na skladě",
                                    fontSize = 12.sp,
                                    color = if (dochazi) Color(0xFFE65100) else Color(0xFF2E7D32)
                                )
                            }
                        }

                        if (pol.zdraziloProcent > 0 || pol.alergeny.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (pol.zdraziloProcent > 0) {
                                    Text(
                                        text = "🔴 Zdražilo o ${pol.zdraziloProcent} % (dříve ${formatJednoducheCislo(pol.predchoziCenaBaleniKc)} Kč)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFC62828)
                                    )
                                } else {
                                    Spacer(Modifier.height(1.dp))
                                }
                                if (pol.alergeny.isNotEmpty()) {
                                    Text(
                                        text = pol.alergeny.joinToString(" ") { it.ikona },
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    predvyplnenaVahaGramy = null
                                    polozkaProDokoupeni = pol
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Text("➕ Dokoupit do skladu", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    jeNovaPolozka = false
                                    upravovanaPolozka = pol
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("✏️ Upravit", color = malinova)
                            }
                        }
                    }
                }
            }
        }
    }

    if (otevrenaCtecka) {
        KamerovaCteckaDialog(
            priNacteniKodu = { surovyKod ->
                otevrenaCtecka = false
                val info = analyzujCeskyCarovyKod(surovyKod)
                val nalezenaVeSkladu = data.sklad.firstOrNull { pol ->
                    pol.ean.isNotBlank() && (
                        pol.ean == info.cistyKod ||
                            analyzujCeskyCarovyKod(pol.ean).vyhledavaciKlic == info.vyhledavaciKlic
                        )
                }
                if (nalezenaVeSkladu != null) {
                    predvyplnenaVahaGramy = info.odvozenaVahaGramy
                    polozkaProDokoupeni = nalezenaVeSkladu
                    infoHlaska = "Poznala jsem: ${nalezenaVeSkladu.nazev}"
                } else {
                    hledaniKoduProbiha = true
                    VaultStorage.vyhledejProduktPodleEanu(surovyKod) { nalezenyKatalog, rozKod ->
                        hledaniKoduProbiha = false
                        jeNovaPolozka = true
                        if (nalezenyKatalog != null) {
                            infoHlaska = "Načteno z katalogu: ${nalezenyKatalog.nazev}. Doplň jen cenu z účtenky."
                            upravovanaPolozka = SkladovaPolozka(
                                id = "skl_${aktualniCasMs()}",
                                ean = rozKod.cistyKod,
                                nazev = nalezenyKatalog.nazev,
                                jeObal = nalezenyKatalog.jeObal,
                                jednotka = nalezenyKatalog.jednotka,
                                velikostBaleni = rozKod.odvozenaVahaGramy ?: nalezenyKatalog.velikostBaleni,
                                cenaZaBaleniKc = 0.0,
                                predchoziCenaBaleniKc = 0.0,
                                aktualniZasoba = rozKod.odvozenaVahaGramy ?: nalezenyKatalog.velikostBaleni,
                                minimalniZasoba = nalezenyKatalog.velikostBaleni,
                                alergeny = nalezenyKatalog.alergeny
                            )
                        } else {
                            infoHlaska = "Nový čárový kód (${rozKod.cistyKod}). Napiš prosím název a příště už ho poznám sama."
                            val vychoziBaleni = rozKod.odvozenaVahaGramy ?: 250.0
                            upravovanaPolozka = SkladovaPolozka(
                                id = "skl_${aktualniCasMs()}",
                                ean = rozKod.cistyKod,
                                nazev = "",
                                jeObal = false,
                                jednotka = MernaJednotka.GRAM,
                                velikostBaleni = vychoziBaleni,
                                cenaZaBaleniKc = 0.0,
                                predchoziCenaBaleniKc = 0.0,
                                aktualniZasoba = vychoziBaleni,
                                minimalniZasoba = vychoziBaleni,
                                alergeny = emptyList()
                            )
                        }
                    }
                }
            },
            priZavreni = { otevrenaCtecka = false }
        )
    }

    polozkaProDokoupeni?.let { pol ->
        RychleNaskladneniDialog(
            polozka = pol,
            vahovyKodGramy = predvyplnenaVahaGramy,
            priUlozeni = { upravena ->
                val novySklad = data.sklad.map { if (it.id == upravena.id) upravena else it }
                priZmeneDat(data.copy(sklad = novySklad))
                polozkaProDokoupeni = null
                infoHlaska = "Přidáno do skladu: ${upravena.nazev}"
            },
            priZavreni = { polozkaProDokoupeni = null }
        )
    }

    upravovanaPolozka?.let { pol ->
        EditorSkladovePolozkyDialog(
            polozka = pol,
            jeNova = jeNovaPolozka,
            priUlozeni = { ulozena ->
                val novySklad = if (jeNovaPolozka) {
                    data.sklad + ulozena
                } else {
                    data.sklad.map { if (it.id == ulozena.id) ulozena else it }
                }
                VaultStorage.ulozDoSdilenehoKataloguEan(ulozena)
                priZmeneDat(data.copy(sklad = novySklad))
                upravovanaPolozka = null
                infoHlaska = "Uloženo: ${ulozena.nazev}"
            },
            priSmazani = {
                val novySklad = data.sklad.filterNot { it.id == pol.id }
                priZmeneDat(data.copy(sklad = novySklad))
                upravovanaPolozka = null
            },
            priZavreni = { upravovanaPolozka = null }
        )
    }
}

@Composable
private fun RychleNaskladneniDialog(
    polozka: SkladovaPolozka,
    vahovyKodGramy: Double?,
    priUlozeni: (SkladovaPolozka) -> Unit,
    priZavreni: () -> Unit
) {
    var pocetBaleni by remember { mutableStateOf(1) }
    var novaCenaText by remember { mutableStateOf(formatJednoducheCislo(polozka.cenaZaBaleniKc)) }

    Dialog(onDismissRequest = priZavreni) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("➕ Dokoupit do skladu", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Text(polozka.nazev, fontWeight = FontWeight.Bold, color = Color(0xFFAD1457), fontSize = 16.sp)

                if (vahovyKodGramy != null && vahovyKodGramy > 0.0) {
                    Text("Z váhového kódu načtena hmotnost: ${vahovyKodGramy.roundToInt()} g")
                } else {
                    Text("Kolik balení (${formatMnozstviSkladu(polozka.velikostBaleni, polozka.jednotka)}) jsi koupila?")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { if (pocetBaleni > 1) pocetBaleni-- }) { Text("−", fontSize = 20.sp) }
                        Text("$pocetBaleni× balení", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        OutlinedButton(onClick = { pocetBaleni++ }) { Text("+", fontSize = 20.sp) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(2, 5, 10).forEach { skok ->
                            OutlinedButton(
                                onClick = { pocetBaleni += skok },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+$skok")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = novaCenaText,
                    onValueChange = { novaCenaText = it },
                    label = { Text("Cena za 1 balení na účtence (Kč)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = priZavreni, modifier = Modifier.weight(1f)) {
                        Text("Zrušit")
                    }
                    Button(
                        onClick = {
                            val novaCena = parseCeskeCislo(novaCenaText).let {
                                if (it > 0.0) it else polozka.cenaZaBaleniKc
                            }
                            val prirustek = vahovyKodGramy ?: (pocetBaleni * polozka.velikostBaleni)
                            val predchoziCena = if (novaCena != polozka.cenaZaBaleniKc && polozka.cenaZaBaleniKc > 0.0) {
                                polozka.cenaZaBaleniKc
                            } else {
                                polozka.predchoziCenaBaleniKc
                            }
                            priUlozeni(
                                polozka.copy(
                                    cenaZaBaleniKc = novaCena,
                                    predchoziCenaBaleniKc = predchoziCena,
                                    aktualniZasoba = polozka.aktualniZasoba + prirustek
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Text("Přidat do skladu", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSkladovePolozkyDialog(
    polozka: SkladovaPolozka,
    jeNova: Boolean,
    priUlozeni: (SkladovaPolozka) -> Unit,
    priSmazani: () -> Unit,
    priZavreni: () -> Unit
) {
    var nazev by remember { mutableStateOf(polozka.nazev) }
    var ean by remember { mutableStateOf(polozka.ean) }
    var jeObal by remember { mutableStateOf(polozka.jeObal) }
    var jednotka by remember { mutableStateOf(polozka.jednotka) }
    var velikostText by remember { mutableStateOf(formatJednoducheCislo(polozka.velikostBaleni)) }
    var cenaText by remember { mutableStateOf(if (polozka.cenaZaBaleniKc > 0.0) formatJednoducheCislo(polozka.cenaZaBaleniKc) else "") }
    var zasobaText by remember { mutableStateOf(formatJednoducheCislo(polozka.aktualniZasoba)) }
    var minZasobaText by remember { mutableStateOf(formatJednoducheCislo(polozka.minimalniZasoba)) }
    var vybraneAlergeny by remember { mutableStateOf(polozka.alergeny.toSet()) }

    Dialog(onDismissRequest = priZavreni) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (jeNova) "Nová položka do skladu" else "Upravit položku skladu",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )

                OutlinedTextField(
                    value = nazev,
                    onValueChange = { nazev = it },
                    label = { Text("Název (např. Máslo 82% nebo Krabice 1 kg)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Je to krabice / podložka / obal?", fontSize = 14.sp)
                    Switch(
                        checked = jeObal,
                        onCheckedChange = {
                            jeObal = it
                            if (it) {
                                jednotka = MernaJednotka.KUS
                                velikostText = "1"
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MernaJednotka.entries.forEach { j ->
                        VyberovyStitek(j.popis, jednotka == j) { jednotka = j }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = velikostText,
                        onValueChange = { velikostText = it },
                        label = { Text("1 balení (${jednotka.zkratka})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cenaText,
                        onValueChange = { cenaText = it },
                        label = { Text("Cena balení (Kč)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = zasobaText,
                        onValueChange = { zasobaText = it },
                        label = { Text("Mám doma (${jednotka.zkratka})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minZasobaText,
                        onValueChange = { minZasobaText = it },
                        label = { Text("Hlásit pod (${jednotka.zkratka})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = ean,
                    onValueChange = { ean = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Čárový kód EAN (nepovinné)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!jeObal) {
                    Text("Alergeny v této surovině:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Alergen.entries.chunked(2).forEach { dvojice ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            dvojice.forEach { al ->
                                VyberovyStitek(
                                    text = "${al.ikona} ${al.nazev}",
                                    vybrano = al in vybraneAlergeny,
                                    onClick = {
                                        vybraneAlergeny = if (al in vybraneAlergeny) {
                                            vybraneAlergeny - al
                                        } else {
                                            vybraneAlergeny + al
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!jeNova) {
                        TextButton(onClick = priSmazani) {
                            Text("🗑️ Smazat", color = Color(0xFFC62828))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = priZavreni) {
                        Text("Zrušit")
                    }
                    Button(
                        onClick = {
                            if (nazev.isBlank()) return@Button
                            val novaCena = parseCeskeCislo(cenaText)
                            val predchozi = if (!jeNova && novaCena != polozka.cenaZaBaleniKc && polozka.cenaZaBaleniKc > 0.0) {
                                polozka.cenaZaBaleniKc
                            } else {
                                polozka.predchoziCenaBaleniKc
                            }
                            priUlozeni(
                                polozka.copy(
                                    nazev = nazev.trim(),
                                    ean = ean.trim(),
                                    jeObal = jeObal,
                                    jednotka = jednotka,
                                    velikostBaleni = parseCeskeCislo(velikostText).coerceAtLeast(1.0),
                                    cenaZaBaleniKc = novaCena,
                                    predchoziCenaBaleniKc = predchozi,
                                    aktualniZasoba = parseCeskeCislo(zasobaText).coerceAtLeast(0.0),
                                    minimalniZasoba = parseCeskeCislo(minZasobaText).coerceAtLeast(0.0),
                                    alergeny = if (jeObal) emptyList() else vybraneAlergeny.toList()
                                )
                            )
                        },
                        enabled = nazev.isNotBlank()
                    ) {
                        Text("Uložit", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
