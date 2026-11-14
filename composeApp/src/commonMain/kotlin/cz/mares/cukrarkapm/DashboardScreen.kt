package cz.mares.cukrarkapm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    ucet: UcetCukrarky,
    data: DataCukrarny,
    priZmeneDat: (DataCukrarny) -> Unit,
    priObnoveniZCloudu: () -> Unit,
    priOdhlaseni: () -> Unit,
    priSmazaniUctu: () -> Unit
) {
    val malinova = Color(0xFFAD1457)
    var otevreneNastaveni by remember { mutableStateOf(false) }
    var potvrditSmazaniUctu by remember { mutableStateOf(false) }

    var nazevDilny by remember(data.nastaveni.nazevDilny) { mutableStateOf(data.nastaveni.nazevDilny) }
    var hodinovaText by remember(data.nastaveni.hodinovaOdmenaKc) {
        mutableStateOf(formatJednoducheCislo(data.nastaveni.hodinovaOdmenaKc))
    }
    var troubaText by remember(data.nastaveni.cenaHodinyTroubyKc) {
        mutableStateOf(formatJednoducheCislo(data.nastaveni.cenaHodinyTroubyKc))
    }
    var ucetBankyText by remember(data.nastaveni.bankovniUcet) {
        mutableStateOf(data.nastaveni.bankovniUcet)
    }

    val aktivniObjednavky = remember(data.objednavky) {
        data.objednavky.filter { it.stav != StavObjednavky.HOTOVO }
    }
    val celkovyCistyVydelek = remember(aktivniObjednavky, data) {
        aktivniObjednavky.sumOf { CalculationEngine.spocitejVydelekObjednavky(it, data).cistyVydelekKc }
    }
    val planPeceni = remember(data) { CalculationEngine.spocitejPlanPeceni(data) }
    val nakupniListek = remember(data) { CalculationEngine.spocitejNakupniListek(data) }
    val zdrazeneSuroviny = remember(data.sklad) { data.sklad.filter { it.zdraziloProcent > 0 } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = malinova),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = data.nastaveni.nazevDilny,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = if (ucet.jeUkazkovyRezim) {
                                "Ukázkový režim (pro uložení do cloudu se přihlas)"
                            } else {
                                "🔐 Účet: ${ucet.nick} (soukromý šifrovaný trezor)"
                            },
                            color = Color(0xFFF8BBD0),
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = { otevreneNastaveni = !otevreneNastaveni },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text(if (otevreneNastaveni) "Skrýt ⚙️" else "⚙️ Nastavení")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Rozpracované zakázky", color = Color(0xFFFCE4EC), fontSize = 12.sp)
                        Text("${aktivniObjednavky.size}× k upečení", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Čistý výdělek ze zakázek", color = Color(0xFFFCE4EC), fontSize = 12.sp)
                        Text(formatKc(celkovyCistyVydelek), color = Color(0xFFFFF59D), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    }
                }
            }
        }

        if (otevreneNastaveni) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("⚙️ Nastavení tvé cukrárny", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)

                    OutlinedTextField(
                        value = nazevDilny,
                        onValueChange = { nazevDilny = it },
                        label = { Text("Název tvé výrobny") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hodinovaText,
                            onValueChange = { hodinovaText = it },
                            label = { Text("Odměna za hodinu (Kč)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = troubaText,
                            onValueChange = { troubaText = it },
                            label = { Text("1 hod pečení trouby (Kč)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = ucetBankyText,
                        onValueChange = { ucetBankyText = it },
                        label = { Text("Číslo účtu pro QR platby (např. 123456789/0800)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            priZmeneDat(
                                data.copy(
                                    nastaveni = NastaveniCukrarny(
                                        nazevDilny = nazevDilny.trim().ifBlank { "Moje sladká výrobna" },
                                        hodinovaOdmenaKc = parseCeskeCislo(hodinovaText).coerceAtLeast(0.0),
                                        cenaHodinyTroubyKc = parseCeskeCislo(troubaText).coerceAtLeast(0.0),
                                        bankovniUcet = ucetBankyText.trim()
                                    )
                                )
                            )
                            otevreneNastaveni = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("💾 Uložit nastavení", fontWeight = FontWeight.Bold)
                    }

                    OddelovacRadek()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!ucet.jeUkazkovyRezim) {
                            OutlinedButton(onClick = priObnoveniZCloudu, modifier = Modifier.weight(1f)) {
                                Text("☁️ Načíst z cloudu")
                            }
                        }
                        OutlinedButton(onClick = priOdhlaseni, modifier = Modifier.weight(1f)) {
                            Text(if (ucet.jeUkazkovyRezim) "🔐 Přihlásit se" else "🚪 Odhlásit")
                        }
                    }

                    if (!ucet.jeUkazkovyRezim) {
                        if (!potvrditSmazaniUctu) {
                            TextButton(onClick = { potvrditSmazaniUctu = true }) {
                                Text("🗑️ Smazat můj účet a všechna uložená data", color = Color(0xFFC62828))
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Opravdu trvale smazat účet?", color = Color(0xFFC62828), modifier = Modifier.weight(1f))
                                Button(
                                    onClick = priSmazaniUctu,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                                ) {
                                    Text("Ano, smazat")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (zdrazeneSuroviny.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🔴 Hlídač cen surovin:", fontWeight = FontWeight.ExtraBold, color = Color(0xFFC62828))
                    Spacer(Modifier.height(4.dp))
                    zdrazeneSuroviny.forEach { pol ->
                        Text(
                            text = "• ${pol.nazev} zdražilo o ${pol.zdraziloProcent} % (teď ${formatJednoducheCislo(pol.cenaZaBaleniKc)} Kč, dříve ${formatJednoducheCislo(pol.predchoziCenaBaleniKc)} Kč)",
                            fontSize = 13.sp,
                            color = Color(0xFFB71C1C)
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("👩‍🍳 Co teď napéct (součet všech zakázek)", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                if (planPeceni.isEmpty()) {
                    Text("Zatím nemáš žádné rozpracované objednávky k pečení.", fontSize = 13.sp, color = Color(0xFF6D4C41))
                } else {
                    planPeceni.forEach { pol ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color(0xFFFFF8FA), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                val celkemText = if (pol.jeNaVahu) {
                                    formatMnozstviSkladu(pol.celkemPotrebaGramyNeboKusy, MernaJednotka.GRAM)
                                } else {
                                    "${formatJednoducheCislo(pol.celkemPotrebaGramyNeboKusy)} ks"
                                }
                                Text(
                                    text = "${pol.nazevReceptu}: celkem $celkemText",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "👉 Zadělej ${formatJednoducheCislo(pol.doporuceneDavkyTesta)}× dávku (přesně ${formatJednoducheCislo(pol.pocetDavekTestaPresne)})",
                                    fontSize = 13.sp,
                                    color = malinova,
                                    fontWeight = FontWeight.SemiBold
                                )
                                pol.rozpisNaplni.forEach { napln ->
                                    val mnNapln = if (pol.jeNaVahu) {
                                        "${napln.potrebnaHmotnostGramy.roundToInt()} g"
                                    } else {
                                        "${formatJednoducheCislo(napln.potrebnaHmotnostGramy)} ks"
                                    }
                                    Text("   • ${napln.variantaNazev}: $mnNapln", fontSize = 12.sp, color = Color(0xFF4E342E))
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val odhadNakupu = nakupniListek.sumOf { it.odhadovanaCenaNakupuKc }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🛒 Nákupní lístek do obchodu", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    if (nakupniListek.isNotEmpty()) {
                        Text("cca ${formatKc(odhadNakupu)}", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (nakupniListek.isEmpty()) {
                    Text("🟢 Všechny suroviny i krabice na objednávky máš doma na skladě!", color = Color(0xFF2E7D32), fontSize = 13.sp)
                } else {
                    nakupniListek.forEach { nakup ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🟠 ${nakup.pocetBaleniKoupit}× ${nakup.polozkaSkladu.nazev} (chybí ${formatMnozstviSkladu(nakup.chybiDokoupitJednotek, nakup.polozkaSkladu.jednotka)})",
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(formatKc(nakup.odhadovanaCenaNakupuKc), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
