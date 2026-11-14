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
fun RecipesScreen(
    data: DataCukrarny,
    priZmeneDat: (DataCukrarny) -> Unit
) {
    val malinova = Color(0xFFAD1457)
    var filtrTypu by remember { mutableStateOf(0) }
    var upravovanyRecept by remember { mutableStateOf<Receptura?>(null) }
    var jeNovyRecept by remember { mutableStateOf(false) }

    var receptProVariantu by remember { mutableStateOf<Receptura?>(null) }
    var upravovanaVarianta by remember { mutableStateOf<VariantaProduktu?>(null) }
    var jeNovaVarianta by remember { mutableStateOf(false) }

    val filtrovaneRecepty = remember(data.recepty, filtrTypu) {
        data.recepty.filter { rec ->
            when (filtrTypu) {
                1 -> rec.typ == TypReceptu.CUKROVI_DAVKA
                2 -> rec.typ == TypReceptu.DORT_KUSOVKA
                3 -> rec.typ == TypReceptu.MIX_KRABICE
                else -> true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Button(
            onClick = {
                jeNovyRecept = true
                upravovanyRecept = Receptura(
                    id = "rec_${aktualniCasMs()}",
                    nazev = "",
                    typ = TypReceptu.CUKROVI_DAVKA,
                    vyteznostDavky = 1000.0,
                    casPraceMin = 45,
                    casTroubyMin = 25,
                    zakladniProdejniCenaKc = 850.0,
                    suroviny = emptyList(),
                    varianty = emptyList(),
                    polozkyMixu = emptyList(),
                    postupPoznamka = ""
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = malinova),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("➕ Nový recept, dort nebo vánoční krabice", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VyberovyStitek("Vše (${data.recepty.size})", filtrTypu == 0) { filtrTypu = 0 }
            VyberovyStitek("🎄 Cukroví", filtrTypu == 1) { filtrTypu = 1 }
            VyberovyStitek("🎂 Dorty a zákusky", filtrTypu == 2) { filtrTypu = 2 }
            VyberovyStitek("📦 Mix krabice", filtrTypu == 3) { filtrTypu = 3 }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtrovaneRecepty, key = { it.id }) { rec ->
                val kalk = CalculationEngine.spocitejDetailReceptu(rec, null, data)
                val (ikonaSemaforu, barvaPozadi, barvaTextu) = when (kalk.semafor) {
                    SemaforVydelku.ZELENA -> Triple("🟢", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    SemaforVydelku.ORANZOVA -> Triple("🟠", Color(0xFFFFF3E0), Color(0xFFE65100))
                    SemaforVydelku.CERVENA -> Triple("🔴", Color(0xFFFFEBEE), Color(0xFFC62828))
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
                                    text = rec.nazev,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = Color(0xFF3E2723)
                                )
                                Text(
                                    text = "${rec.typ.titulek} • 👩‍🍳 ${kalk.casPraceDavkyMin} min práce • 🔥 ${kalk.casTroubyDavkyMin} min trouba",
                                    fontSize = 12.sp,
                                    color = Color(0xFF6D4C41)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${formatKc(kalk.prodejniCenaJednotkyKc)} / ${kalk.jednotkaPopisek}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = malinova
                                )
                                Text(
                                    text = "Suroviny + trouba: ${formatKc(kalk.nakladSurovinJednotkyKc + kalk.nakladTroubyJednotkyKc)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF6D4C41)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(barvaPozadi, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "$ikonaSemaforu ${kalk.lidskyVerdikt}",
                                color = barvaTextu,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (kalk.alergeny.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Alergeny: " + kalk.alergeny.joinToString(", ") { "${it.ikona} ${it.nazev}" },
                                fontSize = 12.sp,
                                color = Color(0xFF5D4037)
                            )
                        }

                        if (rec.varianty.isNotEmpty()) {
                            OddelovacRadek()
                            Text(
                                text = "Příchutě a náplně pro objednávky:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF4E342E)
                            )
                            Spacer(Modifier.height(4.dp))
                            rec.varianty.forEach { varianta ->
                                val vKalk = CalculationEngine.spocitejDetailReceptu(rec, varianta, data)
                                val vIkona = when (vKalk.semafor) {
                                    SemaforVydelku.ZELENA -> "🟢"
                                    SemaforVydelku.ORANZOVA -> "🟠"
                                    SemaforVydelku.CERVENA -> "🔴"
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .background(Color(0xFFFFF8FA), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$vIkona ${varianta.nazevVarianty}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Prodej ${formatKc(vKalk.prodejniCenaJednotkyKc)}/${vKalk.jednotkaPopisek} • čistý výdělek ${formatKc(vKalk.cistyVydelekPoSurovinachKc)}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF5D4037)
                                        )
                                    }
                                    TextButton(onClick = {
                                        receptProVariantu = rec
                                        jeNovaVarianta = false
                                        upravovanaVarianta = varianta
                                    }) {
                                        Text("✏️ Upravit")
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (rec.typ != TypReceptu.MIX_KRABICE) {
                                OutlinedButton(
                                    onClick = {
                                        receptProVariantu = rec
                                        jeNovaVarianta = true
                                        upravovanaVarianta = VariantaProduktu(
                                            id = "var_${aktualniCasMs()}",
                                            nazevVarianty = "",
                                            extraSurovinyNaDavku = emptyList(),
                                            pridanaVahaGramy = if (rec.typ == TypReceptu.CUKROVI_DAVKA) 250.0 else 0.0,
                                            extraCasPraceMin = 15,
                                            prodejniCenaKc = rec.zakladniProdejniCenaKc
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Text("➕ Přidat náplň", color = malinova, fontWeight = FontWeight.Bold)
                                }
                            }
                            Button(
                                onClick = {
                                    jeNovyRecept = false
                                    upravovanyRecept = rec
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = malinova),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("✏️ Upravit recept")
                            }
                        }
                    }
                }
            }
        }
    }

    upravovanyRecept?.let { rec ->
        EditorReceptuDialog(
            recept = rec,
            jeNovy = jeNovyRecept,
            data = data,
            priUlozeni = { ulozeny ->
                val novySeznam = if (jeNovyRecept) {
                    data.recepty + ulozeny
                } else {
                    data.recepty.map { if (it.id == ulozeny.id) ulozeny else it }
                }
                priZmeneDat(data.copy(recepty = novySeznam))
                upravovanyRecept = null
            },
            priSmazani = {
                val novySeznam = data.recepty.filterNot { it.id == rec.id }
                priZmeneDat(data.copy(recepty = novySeznam))
                upravovanyRecept = null
            },
            priZavreni = { upravovanyRecept = null }
        )
    }

    val cilReceptVarianty = receptProVariantu
    val cilVarianta = upravovanaVarianta
    if (cilReceptVarianty != null && cilVarianta != null) {
        EditorVariantyDialog(
            recept = cilReceptVarianty,
            varianta = cilVarianta,
            jeNova = jeNovaVarianta,
            data = data,
            priUlozeni = { ulozenaVar ->
                val noveVarianty = if (jeNovaVarianta) {
                    cilReceptVarianty.varianty + ulozenaVar
                } else {
                    cilReceptVarianty.varianty.map { if (it.id == ulozenaVar.id) ulozenaVar else it }
                }
                val novySeznam = data.recepty.map {
                    if (it.id == cilReceptVarianty.id) cilReceptVarianty.copy(varianty = noveVarianty) else it
                }
                priZmeneDat(data.copy(recepty = novySeznam))
                upravovanaVarianta = null
                receptProVariantu = null
            },
            priSmazani = {
                val noveVarianty = cilReceptVarianty.varianty.filterNot { it.id == cilVarianta.id }
                val novySeznam = data.recepty.map {
                    if (it.id == cilReceptVarianty.id) cilReceptVarianty.copy(varianty = noveVarianty) else it
                }
                priZmeneDat(data.copy(recepty = novySeznam))
                upravovanaVarianta = null
                receptProVariantu = null
            },
            priZavreni = {
                upravovanaVarianta = null
                receptProVariantu = null
            }
        )
    }
}

@Composable
private fun EditorReceptuDialog(
    recept: Receptura,
    jeNovy: Boolean,
    data: DataCukrarny,
    priUlozeni: (Receptura) -> Unit,
    priSmazani: () -> Unit,
    priZavreni: () -> Unit
) {
    var nazev by remember { mutableStateOf(recept.nazev) }
    var typ by remember { mutableStateOf(recept.typ) }
    var vyteznostText by remember { mutableStateOf(formatJednoducheCislo(recept.vyteznostDavky)) }
    var praceMinText by remember { mutableStateOf(recept.casPraceMin.toString()) }
    var troubaMinText by remember { mutableStateOf(recept.casTroubyMin.toString()) }
    var cenaText by remember { mutableStateOf(formatJednoducheCislo(recept.zakladniProdejniCenaKc)) }
    var suroviny by remember { mutableStateOf(recept.suroviny) }
    var polozkyMixu by remember { mutableStateOf(recept.polozkyMixu) }
    var poznamka by remember { mutableStateOf(recept.postupPoznamka) }

    var vybranaSurovinaId by remember { mutableStateOf(data.sklad.firstOrNull()?.id.orEmpty()) }
    var mnozstviSurovinyText by remember { mutableStateOf("250") }

    val cukroviProMix = remember(data.recepty) {
        val moznosti = mutableListOf<Triple<String, String, String>>()
        data.recepty.filter { it.typ == TypReceptu.CUKROVI_DAVKA && it.id != recept.id }.forEach { r ->
            if (r.varianty.isEmpty()) {
                moznosti += Triple(r.id, "", r.nazev)
            } else {
                r.varianty.forEach { v ->
                    moznosti += Triple(r.id, v.id, "${r.nazev} — ${v.nazevVarianty}")
                }
            }
        }
        moznosti
    }
    var indexVybranehoMixu by remember { mutableStateOf(0) }
    var gramyDoMixuText by remember { mutableStateOf("250") }

    val docasnyRecept = recept.copy(
        nazev = nazev,
        typ = typ,
        vyteznostDavky = parseCeskeCislo(vyteznostText).coerceAtLeast(1.0),
        casPraceMin = praceMinText.filter { it.isDigit() }.toIntOrNull() ?: 0,
        casTroubyMin = troubaMinText.filter { it.isDigit() }.toIntOrNull() ?: 0,
        zakladniProdejniCenaKc = parseCeskeCislo(cenaText),
        suroviny = suroviny,
        polozkyMixu = polozkyMixu,
        postupPoznamka = poznamka
    )
    val zivaKalk = CalculationEngine.spocitejDetailReceptu(docasnyRecept, null, data)

    Dialog(onDismissRequest = priZavreni) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (jeNovy) "Nový recept / krabice" else "Upravit recept",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TypReceptu.entries.forEach { t ->
                        VyberovyStitek(t.titulek, typ == t) {
                            typ = t
                            vyteznostText = if (t == TypReceptu.CUKROVI_DAVKA) "1000" else "1"
                        }
                    }
                }

                OutlinedTextField(
                    value = nazev,
                    onValueChange = { nazev = it },
                    label = { Text("Název (např. Linecké cukroví nebo Čoko dort)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = vyteznostText,
                        onValueChange = { vyteznostText = it },
                        label = {
                            Text(if (typ == TypReceptu.CUKROVI_DAVKA) "Gramů z 1 dávky" else "Kusů z dávky")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cenaText,
                        onValueChange = { cenaText = it },
                        label = {
                            Text(if (typ == TypReceptu.CUKROVI_DAVKA) "Cena za 1 kg (Kč)" else "Cena za 1 ks (Kč)")
                        },
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
                        value = praceMinText,
                        onValueChange = { praceMinText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("👩‍🍳 Čas práce (min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = troubaMinText,
                        onValueChange = { troubaMinText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("🔥 Trouba peče (min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (typ == TypReceptu.MIX_KRABICE && cukroviProMix.isNotEmpty()) {
                    OddelovacRadek()
                    Text("🎄 Druhy cukroví v této krabici:", fontWeight = FontWeight.Bold)
                    polozkyMixu.forEachIndexed { idx, pMix ->
                        val rNazev = data.recepty.firstOrNull { it.id == pMix.receptId }?.let { r ->
                            val v = r.varianty.firstOrNull { it.id == pMix.variantaId }
                            if (v != null) "${r.nazev} (${v.nazevVarianty})" else r.nazev
                        } ?: "Cukroví"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("• $rNazev: ${pMix.hmotnostGramy.roundToInt()} g", fontSize = 13.sp)
                            TextButton(onClick = {
                                polozkyMixu = polozkyMixu.filterIndexed { i, _ -> i != idx }
                            }) { Text("✖") }
                        }
                    }
                    Text(
                        text = "Vyber druh pro přidání (celkem v krabici: ${polozkyMixu.sumOf { it.hmotnostGramy }.roundToInt()} g):",
                        fontSize = 12.sp
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cukroviProMix.forEachIndexed { idx, pol ->
                            VyberovyStitek(pol.third, indexVybranehoMixu == idx) { indexVybranehoMixu = idx }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = gramyDoMixuText,
                            onValueChange = { gramyDoMixuText = it },
                            label = { Text("Gramů v krabici") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            val vybr = cukroviProMix.getOrNull(indexVybranehoMixu) ?: return@Button
                            val g = parseCeskeCislo(gramyDoMixuText)
                            if (g > 0.0) {
                                polozkyMixu = polozkyMixu + PolozkaMixKrabice(vybr.first, vybr.second, g)
                            }
                        }) {
                            Text("+ Přidat druh")
                        }
                    }
                }

                OddelovacRadek()
                Text(
                    text = if (typ == TypReceptu.MIX_KRABICE) "🎀 Krabice a obal ze skladu:" else "🥣 Suroviny a krabice ze skladu:",
                    fontWeight = FontWeight.Bold
                )
                suroviny.forEach { slozka ->
                    val pol = data.sklad.firstOrNull { it.id == slozka.polozkaId }
                    if (pol != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• ${pol.nazev}: ${formatMnozstviSkladu(slozka.mnozstvi, pol.jednotka)} (${formatKc(slozka.mnozstvi * pol.cenaZaJednotkuKc)})",
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                suroviny = suroviny.filterNot { it.polozkaId == slozka.polozkaId }
                            }) { Text("✖") }
                        }
                    }
                }

                if (data.sklad.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        data.sklad.forEach { pol ->
                            VyberovyStitek(pol.nazev, vybranaSurovinaId == pol.id) {
                                vybranaSurovinaId = pol.id
                                mnozstviSurovinyText = if (pol.jednotka == MernaJednotka.KUS) "1" else "250"
                            }
                        }
                    }
                    val vybrPol = data.sklad.firstOrNull { it.id == vybranaSurovinaId }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = mnozstviSurovinyText,
                            onValueChange = { mnozstviSurovinyText = it },
                            label = { Text("Množství (${vybrPol?.jednotka?.zkratka ?: "g"})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            val mn = parseCeskeCislo(mnozstviSurovinyText)
                            if (vybranaSurovinaId.isNotBlank() && mn > 0.0) {
                                val bezStare = suroviny.filterNot { it.polozkaId == vybranaSurovinaId }
                                suroviny = bezStare + SlozkaReceptu(vybranaSurovinaId, mn)
                            }
                        }) {
                            Text("+ Přidat")
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF3E5F5), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Živý přepočet: suroviny a trouba vyjdou na ${formatKc(zivaKalk.nakladSurovinJednotkyKc + zivaKalk.nakladTroubyJednotkyKc)} / ${zivaKalk.jednotkaPopisek}. ${zivaKalk.lidskyVerdikt}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedTextField(
                    value = poznamka,
                    onValueChange = { poznamka = it },
                    label = { Text("Poznámka k postupu a teplotě (nepovinné)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!jeNovy) {
                        TextButton(onClick = priSmazani) {
                            Text("🗑️ Smazat", color = Color(0xFFC62828))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = priZavreni) { Text("Zrušit") }
                    Button(
                        onClick = {
                            if (nazev.isNotBlank()) {
                                priUlozeni(docasnyRecept.copy(nazev = nazev.trim()))
                            }
                        },
                        enabled = nazev.isNotBlank()
                    ) {
                        Text("Uložit recept", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorVariantyDialog(
    recept: Receptura,
    varianta: VariantaProduktu,
    jeNova: Boolean,
    data: DataCukrarny,
    priUlozeni: (VariantaProduktu) -> Unit,
    priSmazani: () -> Unit,
    priZavreni: () -> Unit
) {
    var nazevVarianty by remember { mutableStateOf(varianta.nazevVarianty) }
    var extraSuroviny by remember { mutableStateOf(varianta.extraSurovinyNaDavku) }
    var pridanaVahaText by remember { mutableStateOf(formatJednoducheCislo(varianta.pridanaVahaGramy)) }
    var extraPraceText by remember { mutableStateOf(varianta.extraCasPraceMin.toString()) }
    var cenaText by remember { mutableStateOf(formatJednoducheCislo(varianta.prodejniCenaKc)) }

    var vybranaSurovinaId by remember { mutableStateOf(data.sklad.firstOrNull { !it.jeObal }?.id.orEmpty()) }
    var mnozstviNaplneText by remember { mutableStateOf("300") }

    val docasnaVar = varianta.copy(
        nazevVarianty = nazevVarianty,
        extraSurovinyNaDavku = extraSuroviny,
        pridanaVahaGramy = parseCeskeCislo(pridanaVahaText),
        extraCasPraceMin = extraPraceText.filter { it.isDigit() }.toIntOrNull() ?: 0,
        prodejniCenaKc = parseCeskeCislo(cenaText)
    )
    val zivaKalk = CalculationEngine.spocitejDetailReceptu(recept, docasnaVar, data)

    Dialog(onDismissRequest = priZavreni) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${recept.nazev}: náplň / varianta",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )

                OutlinedTextField(
                    value = nazevVarianty,
                    onValueChange = { nazevVarianty = it },
                    label = { Text("Název varianty (např. plněné Nutellou)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Surovina na náplň ze skladu (na 1 dávku):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                extraSuroviny.forEach { slozka ->
                    val pol = data.sklad.firstOrNull { it.id == slozka.polozkaId }
                    if (pol != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("• ${pol.nazev}: ${formatMnozstviSkladu(slozka.mnozstvi, pol.jednotka)}", fontSize = 13.sp)
                            TextButton(onClick = {
                                extraSuroviny = extraSuroviny.filterNot { it.polozkaId == slozka.polozkaId }
                            }) { Text("✖") }
                        }
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    data.sklad.filter { !it.jeObal }.forEach { pol ->
                        VyberovyStitek(pol.nazev, vybranaSurovinaId == pol.id) {
                            vybranaSurovinaId = pol.id
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = mnozstviNaplneText,
                        onValueChange = { mnozstviNaplneText = it },
                        label = { Text("Množství náplně (g/ml/ks)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val mn = parseCeskeCislo(mnozstviNaplneText)
                        if (vybranaSurovinaId.isNotBlank() && mn > 0.0) {
                            extraSuroviny = extraSuroviny.filterNot { it.polozkaId == vybranaSurovinaId } +
                                SlozkaReceptu(vybranaSurovinaId, mn)
                        }
                    }) {
                        Text("+ Přidat náplň")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (recept.typ == TypReceptu.CUKROVI_DAVKA) {
                        OutlinedTextField(
                            value = pridanaVahaText,
                            onValueChange = { pridanaVahaText = it },
                            label = { Text("Přidá na váze (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = extraPraceText,
                        onValueChange = { extraPraceText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Čas plnění (min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = cenaText,
                    onValueChange = { cenaText = it },
                    label = { Text("Prodejní cena za ${zivaKalk.jednotkaPopisek} (Kč)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE8F5E9), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = zivaKalk.lidskyVerdikt,
                        fontSize = 12.sp,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium
                    )
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
                    OutlinedButton(onClick = priZavreni) { Text("Zrušit") }
                    Button(
                        onClick = {
                            if (nazevVarianty.isNotBlank()) {
                                priUlozeni(docasnaVar.copy(nazevVarianty = nazevVarianty.trim()))
                            }
                        },
                        enabled = nazevVarianty.isNotBlank()
                    ) {
                        Text("Uložit variantu", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
