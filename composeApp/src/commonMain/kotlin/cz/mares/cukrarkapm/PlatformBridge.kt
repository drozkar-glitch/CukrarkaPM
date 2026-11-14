package cz.mares.cukrarkapm

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

expect fun aktualniCasMs(): Long

expect fun ulozLokalniText(klic: String, hodnota: String)

expect fun nactiLokalniText(klic: String): String?

expect fun httpPozadavek(
    metoda: String,
    url: String,
    teloJson: String?,
    vysledek: (uspech: Boolean, odpoved: String) -> Unit
)

expect fun otevriSmsZpravu(telefon: String, textZpravy: String)

@Composable
expect fun PlatebniQrObrazek(spaydText: String, modifier: Modifier)

@Composable
expect fun KamerovaCteckaDialog(
    priNacteniKodu: (String) -> Unit,
    priZavreni: () -> Unit
)
