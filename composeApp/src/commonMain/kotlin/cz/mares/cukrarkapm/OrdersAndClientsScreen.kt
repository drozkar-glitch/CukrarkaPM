package cz.mares.cukrarkapm

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun OrdersAndClientsScreen(
    data: DataCukrarny,
    priZmeneDat: (DataCukrarny) -> Unit
) {
    val malinova = Color(0xFFAD1457)
    var aktivniPodsekce by remember { mutableStateOf(0) }
    var filtrObjednavek by remember { mutableStateOf(0) }

    var upravovanaObjednavka by remember { mutableStateOf<Objednavka?>(null) }
    var jeNovaObjednavka by remember { mutableStateOf(false) }
    var objednavkaProQr by remember { mutableStateOf<Objednavka?>(null) }

    var upravovanaKlientka by remember { mutableStateOf<Klientka?>(null) }
    var jeNovaKlientka by remember { mutableStateOf(false) }

    val katalogProduktu = remember(data) { CalculationEngine.sestavKatalogProduktu(data) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { aktivniPodsekce = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (aktivniPodsekce == 0) malinova else Color(0xFFF8BBD0),
                    contentColor = if (aktivniPodsekce == 0) Color.White else Color(0xFF4E342E)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Text("🗓️ Objednávky (${data.objednavky.size})", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { aktivniPodsekce = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (aktivniPodsekce == 1) malinova else Color(0xFFF8BBD0),
                    contentColor = if (aktivniPodsekce == 1) Color.White else Color(0xFF4E342E)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Text("👩 Klientky (${data.klientky.size})", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(10.dp))

        if (aktivniPodsekce == 0) {
            Button(
                onClick = {
                    val dalsiCislo = (data.objednavky.maxOfOrNull { it.cisloZakazky } ?: 100) + 1
                    jeNovaObjednavka = true
                    upravovanaObjednavka = Objednavka(
                        id = "obj_${aktualniCasMs()}",
                        cisloZakazky = dalsiCislo,
                        klientkaId = data.klientky.firstOrNull()?.id.orEmpty(),
                        terminPredani = "",
                        stav = StavObjednavky.PRIJATO,
                        polozky = emptyList(),
                        zaplacenaZalohaKc = 0.0,
                        jeDoplaceno = false,
                        odepsanoZeSkladu = false,
                        poznamka = ""
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("➕ Nová objednávka od klientky", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VyberovyStitek("Rozpracované", filtrObjednavek == 0) { filtrObjednavek = 0 }
                VyberovyStitek("✅ Hotové", filtrObjednavek == 1) { filtrObjednavek = 1 }
                VyberovyStitek("Všechny", filtrObjednavek == 2) { filtrObjednavek = 2 }
            }

            Spacer(Modifier.height(8.dp))

            val seznamObj = data.objednavky.filter {
                when (filtrObjednavek) {
                    0 -> it.stav != StavObjednavky.HOTOVO
                    1 -> it.stav == StavObjednavky.HOTOVO
                    else -> true
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(seznamObj, key = { it.id }) { obj ->
                    val klientka = data.klientky.firstOrNull { it.id == obj.klientkaId }
                    val vydelek = CalculationEngine.spocitejVydelekObjednavky(obj, data)
                    val kolizeAlergenu = CalculationEngine.zkontrolujAlergieObjednavky(
                        klientka = klientka,
                        polozky = obj.polozky,
                        katalog = katalogProduktu
                    )
                    val semaforIkona = when (vydelek.semafor) {
                        SemaforVydelku.ZELENA -> "🟢"
                        SemaforVydelku.ORANZOVA -> "🟠"
                        SemaforVydelku.CERVENA -> "🔴"
                    }

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
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
                                        text = "#${obj.cisloZakazky} • ${klientka?.jmeno ?: "Neznámá klientka"}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 17.sp,
                                        color = Color(0xFF3E2723)
                                    )
                                    Text(
                                        text = "📅 Předání: ${obj.terminPredani.ifBlank { "Neuvedeno" }}",
                                        fontSize = 13.sp,
                                        color = malinova,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = formatKc(obj.celkovaCenaKc),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }

                            if (kolizeAlergenu.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFFFEBEE), RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "🔴 POZOR ALERGIE! Klientka nesmí: " +
                                            kolizeAlergenu.joinToString(", ") { "${it.ikona} ${it.nazev}" },
                                        color = Color(0xFFC62828),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            OddelovacRadek()

                            obj.polozky.forEach { pol ->
                                val mnText = if (pol.jeNaVahu) {
                                    formatMnozstviSkladu(pol.mnozstvi, MernaJednotka.GRAM)
                                } else {
                                    "${formatJednoducheCislo(pol.mnozstvi)} ks"
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("• $mnText ${pol.nazevSnapshot}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Text(formatKc(pol.celkovaCenaKc), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            if (obj.poznamka.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text("💬 ${obj.poznamka}", fontSize = 12.sp, color = Color(0xFF5D4037))
                            }

                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFFF8FA), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "$semaforIkona Čistý výdělek po surovinách a troubě: ${formatKc(vydelek.cistyVydelekKc)} (práce cca ${vydelek.odhadCasuPraceMin} min)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF3E2723)
                                    )
                                    Text(
                                        text = if (obj.jeDoplaceno) {
                                            "✅ Celá částka je zaplacená"
                                        } else {
                                            "Záloha: ${formatKc(obj.zaplacenaZalohaKc)} • Zbývá doplatit: ${formatKc(obj.zbyvaDoplatitKc)}"
                                        },
                                        fontSize = 12.sp,
                                        color = if (obj.jeDoplaceno) Color(0xFF2E7D32) else Color(0xFFE65100)
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StavObjednavky.entries.forEach { st ->
                                    VyberovyStitek(st.popisek, obj.stav == st) {
                                        var upravenaObj = obj.copy(
                                            stav = st,
                                            jeDoplaceno = if (st == StavObjednavky.HOTOVO) true else obj.jeDoplaceno
                                        )
                                        var novaData = data.copy(
                                            objednavky = data.objednavky.map { if (it.id == obj.id) upravenaObj else it }
                                        )
                                        if ((st == StavObjednavky.PECU || st == StavObjednavky.HOTOVO) && !obj.odepsanoZeSkladu) {
                                            novaData = CalculationEngine.odepisSurovinyObjednavkyZeSkladu(upravenaObj, novaData)
                                        }
                                        priZmeneDat(novaData)
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!klientka?.telefon.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            val doplatekInfo = if (obj.zbyvaDoplatitKc > 0.0) {
                                                " Doplatek činí ${formatKc(obj.zbyvaDoplatitKc)}."
                                            } else ""
                                            otevriSmsZpravu(
                                                klientka!!.telefon,
                                                "Dobrý den, Vaše sladká objednávka č. ${obj.cisloZakazky} je připravena k vyzvednutí.$doplatekInfo Těším se na Vás!"
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("📱 SMS")
                                    }
                                }
                                OutlinedButton(
                                    onClick = { objednavkaProQr = obj },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("💳 QR platba")
                                }
                                Button(
                                    onClick = {
                                        jeNovaObjednavka = false
                                        upravovanaObjednavka = obj
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = malinova),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("✏️ Upravit")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Button(
                onClick = {
                    jeNovaKlientka = true
                    upravovanaKlientka = Klientka(
                        id = "kli_${aktualniCasMs()}",
                        jmeno = "",
                        telefon = "+420 ",
                        kontaktSite = "",
                        hlidaneAlergie = emptyList(),
                        poznamka = ""
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = malinova),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("➕ Přidat novou klientku", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(data.klientky, key = { it.id }) { kli ->
                    val zakazkyKlientky = data.objednavky.filter { it.klientkaId == kli.id }
                    val celkemUtrata = zakazkyKlientky.sumOf { it.celkovaCenaKc }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("👩 ${kli.jmeno}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    Text(
                                        text = listOf(kli.telefon, kli.kontaktSite).filter { it.isNotBlank() }.joinToString(" • "),
                                        fontSize = 13.sp,
                                        color = Color(0xFF6D4C41)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${zakazkyKlientky.size}× zakázka", fontWeight = FontWeight.Bold, color = malinova)
                                    Text(formatKc(celkemUtrata), fontSize = 12.sp)
                                }
                            }
                            if (kli.hlidaneAlergie.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "⚠️ Nesmí: " + kli.hlidaneAlergie.joinToString(", ") { "${it.ikona} ${it.nazev}" },
                                    fontSize = 12.sp,
                                    color = Color(0xFFC62828),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (kli.poznamka.isNotBlank()) {
                                Text("💬 ${kli.poznamka}", fontSize = 12.sp, color = Color(0xFF5D4037))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (kli.telefon.isNotBlank()) {
                                    OutlinedButton(onClick = { otevriSmsZpravu(kli.telefon, "Dobrý den, ") }) {
                                        Text("📱 Napsat SMS")
                                    }
                                }
                                OutlinedButton(onClick = {
                                    jeNovaKlientka = false
                                    upravovanaKlientka = kli
                                }) {
                                    Text("✏️ Upravit klientku")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    upravovanaObjednavka?.let { obj ->
        EditorObjednavkyDialog(
            objednavka = obj,
            jeNova = jeNovaObjednavka,
            data = data,
            katalog = katalogProduktu,
            priUlozeni = { ulozena, novaKlientka ->
                val aktualizovaneKlientky = if (novaKlientka != null) data.klientky + novaKlientka else data.klientky
                val novySeznam = if (jeNovaObjednavka) {
                    listOf(ulozena) + data.objednavky
                } else {
                    data.objednavky.map { if (it.id == ulozena.id) ulozena else it }
                }
                priZmeneDat(data.copy(klientky = aktualizovaneKlientky, objednavky = novySeznam))
                upravovanaObjednavka = null
            },
            priSmazani = {
                priZmeneDat(data.copy(objednavky = data.objednavky.filterNot { it.id == obj.id }))
                upravovanaObjednavka = null
            },
            priZavreni = { upravovanaObjednavka = null }
        )
    }

    objednavkaProQr?.let { obj ->
        QrPlatbaDialog(
            objednavka = obj,
            data = data,
            priUlozeniUctu = { novyUcet ->
                priZmeneDat(data.copy(nastaveni = data.nastaveni.copy(bankovniUcet = novyUcet)))
            },
            priZavreni = { objednavkaProQr = null }
        )
    }

    upravovanaKlientka?.let { kli ->
        EditorKlientkyDialog(
            klientka = kli,
            jeNova = jeNovaKlientka,
            priUlozeni = { ulozena ->
                val novySeznam = if (jeNovaKlientka) {
                    data.klientky + ulozena
                } else {
                    data.klientky.map { if (it.id == ulozena.id) ulozena else it }
                }
                priZmeneDat(data.copy(klientky = novySeznam))
                upravovanaKlientka = null
            },
            priSmazani = {
                priZmeneDat(data.copy(klientky = data.klientky.filterNot { it.id == kli.id }))
                upravovanaKlientka = null
            },
            priZavreni = { upravovanaKlientka = null }
        )
    }
}

@Composable
private fun EditorObjednavkyDialog(
    objednavka: Objednavka,
    jeNova: Boolean,
    data: DataCukrarny,
    katalog: List<KatalogovyProdukt>,
    priUlozeni: (Objednavka, Klientka?) -> Unit,
    priSmazani: () -> Unit,
    priZavreni: () -> Unit
) {
    var klientkaId by remember { mutableStateOf(objednavka.klientkaId) }
    var zakladaNovouKlientku by remember { mutableStateOf(data.klientky.isEmpty()) }
    var noveJmenoKlientky by remember { mutableStateOf("") }
    var novyTelefonKlientky by remember { mutableStateOf("+420 ") }

    var termin by remember { mutableStateOf(objednavka.terminPredani) }
    var polozky by remember { mutableStateOf(objednavka.polozky) }
    var zalohaText by remember { mutableStateOf(formatJednoducheCislo(objednavka.zaplacenaZalohaKc)) }
    var jeDoplaceno by remember { mutableStateOf(objednavka.jeDoplaceno) }
    var poznamka by remember { mutableStateOf(objednavka.poznamka) }
    var otevrenyKatalog by remember { mutableStateOf(false) }

    val vybranaKlientka = data.klientky.firstOrNull { it.id == klientkaId }
    val kolizeAlergenu = CalculationEngine.zkontrolujAlergieObjednavky(vybranaKlientka, polozky, katalog)

    Dialog(onDismissRequest = priZavreni) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (jeNova) "Nová objednávka č. ${objednavka.cisloZakazky}" else "Upravit objednávku č. ${objednavka.cisloZakazky}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("👩 Klientka:", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { zakladaNovouKlientku = !zakladaNovouKlientku }) {
                        Text(if (zakladaNovouKlientku) "Vybrat ze seznamu" else "+ Rychle nová klientka")
                    }
                }

                if (zakladaNovouKlientku) {
                    OutlinedTextField(
                        value = noveJmenoKlientky,
                        onValueChange = { noveJmenoKlientky = it },
                        label = { Text("Jméno nové klientky") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = novyTelefonKlientky,
                        onValueChange = { novyTelefonKlientky = it },
                        label = { Text("Telefon nebo IG přezdívka") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        data.klientky.forEach { k ->
                            VyberovyStitek(k.jmeno, klientkaId == k.id) { klientkaId = k.id }
                        }
                    }
                }

                OutlinedTextField(
                    value = termin,
                    onValueChange = { termin = it },
                    label = { Text("📅 Kdy si pro to přijde (např. 22. 12. v 15:00)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OddelovacRadek()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🍰 Objednané produkty:", fontWeight = FontWeight.Bold)
                    Button(
                        onClick = { otevrenyKatalog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAD1457))
                    ) {
                        Text("➕ Vybrat z nabídky")
                    }
                }

                if (polozky.isEmpty()) {
                    Text("Zatím není vybraný žádný dort ani cukroví.", fontSize = 13.sp, color = Color(0xFF6D4C41))
                } else {
                    polozky.forEachIndexed { index, pol ->
                        val mnPopis = if (pol.jeNaVahu) "${pol.mnozstvi.roundToInt()} g" else "${formatJednoducheCislo(pol.mnozstvi)} ks"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFF8FA), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pol.nazevSnapshot, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("$mnPopis • ${formatKc(pol.celkovaCenaKc)}", fontSize = 12.sp, color = Color(0xFF2E7D32))
                            }
                            TextButton(onClick = {
                                polozky = polozky.filterIndexed { i, _ -> i != index }
                            }) {
                                Text("✖")
                            }
                        }
                    }
                }

                if (kolizeAlergenu.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFEBEE), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "🔴 Pozor! Vybrané zboží obsahuje alergen klientky: " +
                                kolizeAlergenu.joinToString(", ") { it.nazev },
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Text(
                    text = "Celkem pro klientku: ${formatKc(polozky.sumOf { it.celkovaCenaKc })}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = Color(0xFF2E7D32)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = zalohaText,
                        onValueChange = { zalohaText = it },
                        label = { Text("Zaplacená záloha (Kč)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Vše zaplaceno", fontSize = 11.sp)
                        Switch(checked = jeDoplaceno, onCheckedChange = { jeDoplaceno = it })
                    }
                }

                OutlinedTextField(
                    value = poznamka,
                    onValueChange = { poznamka = it },
                    label = { Text("Poznámka / nápis na dort") },
                    modifier = Modifier.fillMaxWidth()
                )

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
                    OutlinedButton(onClick = priZavreni) { Text("Zrušit") }
                    Button(
                        onClick = {
                            val vytvorenaKli = if (zakladaNovouKlientku && noveJmenoKlientky.isNotBlank()) {
                                Klientka(
                                    id = "kli_${aktualniCasMs()}",
                                    jmeno = noveJmenoKlientky.trim(),
                                    telefon = novyTelefonKlientky.trim(),
                                    kontaktSite = "",
                                    hlidaneAlergie = emptyList(),
                                    poznamka = ""
                                )
                            } else null
                            val cilKlientkaId = vytvorenaKli?.id ?: klientkaId
                            priUlozeni(
                                objednavka.copy(
                                    klientkaId = cilKlientkaId,
                                    terminPredani = termin.trim(),
                                    polozky = polozky,
                                    zaplacenaZalohaKc = parseCeskeCislo(zalohaText),
                                    jeDoplaceno = jeDoplaceno,
                                    poznamka = poznamka.trim()
                                ),
                                vytvorenaKli
                            )
                        },
                        enabled = polozky.isNotEmpty() && (!zakladaNovouKlientku || noveJmenoKlientky.isNotBlank())
                    ) {
                        Text("Uložit", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (otevrenyKatalog) {
        VyberProduktuDialog(
            katalog = katalog,
            priPridani = { prod, mnozstvi ->
                val existujiciIndex = polozky.indexOfFirst {
                    it.receptId == prod.receptId && it.variantaId == prod.variantaId
                }
                polozky = if (existujiciIndex >= 0) {
                    polozky.mapIndexed { idx, p ->
                        if (idx == existujiciIndex) p.copy(mnozstvi = p.mnozstvi + mnozstvi) else p
                    }
                } else {
                    polozky + PolozkaObjednavky(
                        receptId = prod.receptId,
                        variantaId = prod.variantaId,
                        nazevSnapshot = prod.celyNazev,
                        jeNaVahu = prod.jeNaVahu,
                        mnozstvi = mnozstvi,
                        jednCenaKc = prod.prodejniCenaKc
                    )
                }
            },
            priZavreni = { otevrenyKatalog = false }
        )
    }
}

@Composable
private fun VyberProduktuDialog(
    katalog: List<KatalogovyProdukt>,
    priPridani: (KatalogovyProdukt, Double) -> Unit,
    priZavreni: () -> Unit
) {
    var hledani by remember { mutableStateOf("") }
    var filtr by remember { mutableStateOf(0) }
    var posledniPridano by remember { mutableStateOf("") }

    val vyfiltrovano = katalog.filter { p ->
        val sediKat = when (filtr) {
            1 -> p.typ == TypReceptu.CUKROVI_DAVKA
            2 -> p.typ == TypReceptu.MIX_KRABICE
            3 -> p.typ == TypReceptu.DORT_KUSOVKA
            else -> true
        }
        val sediText = hledani.isBlank() || p.celyNazev.contains(hledani.trim(), ignoreCase = true)
        sediKat && sediText
    }

    Dialog(onDismissRequest = priZavreni) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🍰 Vyber produkty do objednávky", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)

                OutlinedTextField(
                    value = hledani,
                    onValueChange = { hledani = it },
                    label = { Text("🔍 Hledat (např. linecké, nutella, dort)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VyberovyStitek("Vše", filtr == 0) { filtr = 0 }
                    VyberovyStitek("🎄 Cukroví", filtr == 1) { filtr = 1 }
                    VyberovyStitek("📦 Krabice", filtr == 2) { filtr = 2 }
                    VyberovyStitek("🎂 Dorty", filtr == 3) { filtr = 3 }
                }

                if (posledniPridano.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE8F5E9), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        Text("✅ Přidáno: $posledniPridano", color = Color(0xFF2E7D32), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                vyfiltrovano.forEach { prod ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8FA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(prod.celyNazev, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                text = if (prod.jeNaVahu) {
                                    "${formatKc(prod.prodejniCenaKc)} / 1 kg • čistý výdělek ${formatKc(prod.cistyVydelekKc)}"
                                } else {
                                    "${formatKc(prod.prodejniCenaKc)} / 1 ks • čistý výdělek ${formatKc(prod.cistyVydelekKc)}"
                                },
                                fontSize = 12.sp,
                                color = Color(0xFF5D4037)
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (prod.jeNaVahu) {
                                    listOf(250.0 to "+250 g", 500.0 to "+500 g", 1000.0 to "+1 kg").forEach { (g, popisek) ->
                                        Button(
                                            onClick = {
                                                priPridani(prod, g)
                                                posledniPridano = "$popisek ${prod.celyNazev}"
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(popisek, fontSize = 12.sp)
                                        }
                                    }
                                } else {
                                    listOf(1.0 to "+1 ks", 2.0 to "+2 ks").forEach { (ks, popisek) ->
                                        Button(
                                            onClick = {
                                                priPridani(prod, ks)
                                                posledniPridano = "$popisek ${prod.celyNazev}"
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(popisek, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = priZavreni,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Hotovo — vrátit se k objednávce", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun QrPlatbaDialog(
    objednavka: Objednavka,
    data: DataCukrarny,
    priUlozeniUctu: (String) -> Unit,
    priZavreni: () -> Unit
) {
    var ucetText by remember { mutableStateOf(data.nastaveni.bankovniUcet) }
    val vychoziCastka = if (objednavka.zbyvaDoplatitKc > 0.0) objednavka.zbyvaDoplatitKc else objednavka.celkovaCenaKc
    var castkaText by remember { mutableStateOf(formatJednoducheCislo(vychoziCastka)) }

    val castka = parseCeskeCislo(castkaText)
    val spayd = sestavSpaydRetzec(
        iban = ucetText,
        castkaKc = castka,
        vsCislo = objednavka.cisloZakazky,
        zprava = "Objednavka ${objednavka.cisloZakazky}"
    )

    Dialog(onDismissRequest = priZavreni) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("💳 QR platba pro klientku", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)

                OutlinedTextField(
                    value = ucetText,
                    onValueChange = {
                        ucetText = it
                        if (ceskyUcetNaIban(it).isNotEmpty()) {
                            priUlozeniUctu(it.trim())
                        }
                    },
                    label = { Text("Tvoje číslo účtu (např. 123456789/0800)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = castkaText,
                    onValueChange = { castkaText = it },
                    label = { Text("Částka k zaplacení (Kč)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (spayd.isNotEmpty()) {
                    PlatebniQrObrazek(
                        spaydText = spayd,
                        modifier = Modifier.size(210.dp).padding(6.dp)
                    )
                    Text(
                        text = "Klientka jen načte kód v mobilním bankovnictví (${formatKc(castka)}, VS ${objednavka.cisloZakazky}).",
                        fontSize = 12.sp,
                        color = Color(0xFF2E7D32)
                    )
                } else {
                    Text(
                        text = "Zadej platné české číslo účtu s kódem banky za lomítkem (např. 123456789/0800) a hned se vykreslí QR kód.",
                        fontSize = 13.sp,
                        color = Color(0xFFE65100)
                    )
                }

                Button(onClick = priZavreni, modifier = Modifier.fillMaxWidth()) {
                    Text("Zavřít")
                }
            }
        }
    }
}

@Composable
private fun EditorKlientkyDialog(
    klientka: Klientka,
    jeNova: Boolean,
    priUlozeni: (Klientka) -> Unit,
    priSmazani: () -> Unit,
    priZavreni: () -> Unit
) {
    var jmeno by remember { mutableStateOf(klientka.jmeno) }
    var telefon by remember { mutableStateOf(klientka.telefon) }
    var site by remember { mutableStateOf(klientka.kontaktSite) }
    var alergie by remember { mutableStateOf(klientka.hlidaneAlergie.toSet()) }
    var poznamka by remember { mutableStateOf(klientka.poznamka) }

    Dialog(onDismissRequest = priZavreni) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (jeNova) "Nová klientka" else "Upravit klientku",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                OutlinedTextField(
                    value = jmeno,
                    onValueChange = { jmeno = it },
                    label = { Text("Jméno a příjmení") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = telefon,
                    onValueChange = { telefon = it },
                    label = { Text("Telefonní číslo") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = site,
                    onValueChange = { site = it },
                    label = { Text("Přezdívka na Instagramu / Facebooku") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Hlídané alergie v rodině klientky:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Alergen.entries.chunked(2).forEach { dvojice ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        dvojice.forEach { al ->
                            VyberovyStitek("${al.ikona} ${al.nazev}", al in alergie) {
                                alergie = if (al in alergie) alergie - al else alergie + al
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = poznamka,
                    onValueChange = { poznamka = it },
                    label = { Text("Poznámka ke klientce") },
                    modifier = Modifier.fillMaxWidth()
                )

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
                    OutlinedButton(onClick = priZavreni) { Text("Zrušit") }
                    Button(
                        onClick = {
                            if (jmeno.isNotBlank()) {
                                priUlozeni(
                                    klientka.copy(
                                        jmeno = jmeno.trim(),
                                        telefon = telefon.trim(),
                                        kontaktSite = site.trim(),
                                        hlidaneAlergie = alergie.toList(),
                                        poznamka = poznamka.trim()
                                    )
                                )
                            }
                        },
                        enabled = jmeno.isNotBlank()
                    ) {
                        Text("Uložit", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
