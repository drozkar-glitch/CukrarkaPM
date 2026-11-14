package cz.mares.cukrarkapm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CukrarkaBarvy = lightColorScheme(
    primary = Color(0xFFAD1457),
    onPrimary = Color.White,
    secondary = Color(0xFF2E7D32),
    background = Color(0xFFFFF5F8),
    surface = Color.White
)

@Composable
fun App() {
    MaterialTheme(colorScheme = CukrarkaBarvy) {
        val ulozenaRelace = remember { VaultStorage.nactiUlozenouRelaci() }
        var aktualniUcet by remember { mutableStateOf(ulozenaRelace?.first) }
        var dataCukrarny by remember {
            mutableStateOf(ulozenaRelace?.second ?: VzorovaData.vytvorVychoziData())
        }
        var vybranaZalozka by remember { mutableStateOf(0) }
        var stavSynchronizace by remember { mutableStateOf("☁️ Uloženo") }

        LaunchedEffect(aktualniUcet?.nick) {
            val ucet = aktualniUcet
            if (ucet != null && !ucet.jeUkazkovyRezim) {
                VaultStorage.stahniNejnovsiDataZCloudu(ucet) { zCloudu ->
                    if (zCloudu != null && zCloudu.posledniUlozeniMs >= dataCukrarny.posledniUlozeniMs) {
                        dataCukrarny = zCloudu
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFF5F8))
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val prihlaseny = aktualniUcet
            if (prihlaseny == null) {
                AuthScreen(
                    priPrihlaseni = { ucet, nactenaData ->
                        aktualniUcet = ucet
                        dataCukrarny = nactenaData
                        vybranaZalozka = 0
                    }
                )
            } else {
                val ulozZmenu: (DataCukrarny) -> Unit = { novaData ->
                    dataCukrarny = novaData
                    if (!prihlaseny.jeUkazkovyRezim) {
                        stavSynchronizace = "⏳ Ukládám..."
                        VaultStorage.ulozRelaciAData(prihlaseny, novaData) { uspech ->
                            stavSynchronizace = if (uspech) "☁️ Uloženo" else "📱 Uloženo v mobilu"
                        }
                    }
                }

                Scaffold(
                    containerColor = Color(0xFFFFF5F8),
                    topBar = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🧁 Cukrářka PM",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp,
                                color = Color(0xFFAD1457)
                            )
                            Text(
                                text = if (prihlaseny.jeUkazkovyRezim) "👀 Ukázka" else stavSynchronizace,
                                fontSize = 12.sp,
                                color = Color(0xFF6D4C41),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    bottomBar = {
                        Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                            NavigationBar(containerColor = Color.White) {
                                NavigationBarItem(
                                    selected = vybranaZalozka == 0,
                                    onClick = { vybranaZalozka = 0 },
                                    icon = { Text("📋", fontSize = 20.sp) },
                                    label = { Text("Přehled", fontWeight = FontWeight.Bold) }
                                )
                                NavigationBarItem(
                                    selected = vybranaZalozka == 1,
                                    onClick = { vybranaZalozka = 1 },
                                    icon = { Text("🗓️", fontSize = 20.sp) },
                                    label = { Text("Objednávky", fontWeight = FontWeight.Bold) }
                                )
                                NavigationBarItem(
                                    selected = vybranaZalozka == 2,
                                    onClick = { vybranaZalozka = 2 },
                                    icon = { Text("🍰", fontSize = 20.sp) },
                                    label = { Text("Recepty", fontWeight = FontWeight.Bold) }
                                )
                                NavigationBarItem(
                                    selected = vybranaZalozka == 3,
                                    onClick = { vybranaZalozka = 3 },
                                    icon = { Text("📦", fontSize = 20.sp) },
                                    label = { Text("Sklad", fontWeight = FontWeight.Bold) }
                                )
                            }
                            Text(
                                text = "© 2026 Created by Pavel Mareš",
                                fontSize = 10.sp,
                                color = Color(0xFF8D6E63),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                            )
                        }
                    }
                ) { vnitrniOkraje ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vnitrniOkraje)
                    ) {
                        when (vybranaZalozka) {
                            0 -> DashboardScreen(
                                ucet = prihlaseny,
                                data = dataCukrarny,
                                priZmeneDat = ulozZmenu,
                                priObnoveniZCloudu = {
                                    VaultStorage.stahniNejnovsiDataZCloudu(prihlaseny) { zCloudu ->
                                        if (zCloudu != null) dataCukrarny = zCloudu
                                    }
                                },
                                priOdhlaseni = {
                                    VaultStorage.odhlasitLokalne()
                                    aktualniUcet = null
                                },
                                priSmazaniUctu = {
                                    VaultStorage.smazUcetZCloudu(prihlaseny) {
                                        aktualniUcet = null
                                    }
                                }
                            )
                            1 -> OrdersAndClientsScreen(
                                data = dataCukrarny,
                                priZmeneDat = ulozZmenu
                            )
                            2 -> RecipesScreen(
                                data = dataCukrarny,
                                priZmeneDat = ulozZmenu
                            )
                            3 -> WarehouseScreen(
                                data = dataCukrarny,
                                priZmeneDat = ulozZmenu
                            )
                        }
                    }
                }
            }
        }
    }
}
