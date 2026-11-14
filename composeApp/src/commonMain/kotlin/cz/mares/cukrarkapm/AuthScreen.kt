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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AuthScreen(
    priPrihlaseni: (UcetCukrarky, DataCukrarny) -> Unit
) {
    var nick by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var potvrzeniPinu by remember { mutableStateOf("") }
    var vyzadujePotvrzeni by remember { mutableStateOf(false) }
    var zobrazitPin by remember { mutableStateOf(false) }
    var nacitaSe by remember { mutableStateOf(false) }
    var stavovaZprava by remember { mutableStateOf("") }
    var jeChyba by remember { mutableStateOf(false) }

    val malinova = Color(0xFFAD1457)
    val svetleRuzova = Color(0xFFFFF5F8)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(svetleRuzova)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "🧁", fontSize = 54.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Cukrářka PM",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = malinova
            )
            Text(
                text = "Recepty, hlídání cen surovin, cukroví a objednávky",
                fontSize = 14.sp,
                color = Color(0xFF5D4037),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Přihlášení k tvé soukromé cukrárně",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF3E2723)
                    )
                    Text(
                        text = "Zadej svoji přezdívku a číselný PIN. Stačí jednou — tvoje recepty i klientky se zašifrují a nikdo jiný se k nim nedostane.",
                        fontSize = 13.sp,
                        color = Color(0xFF6D4C41)
                    )

                    OutlinedTextField(
                        value = nick,
                        onValueChange = {
                            nick = it
                            vyzadujePotvrzeni = false
                            stavovaZprava = ""
                        },
                        label = { Text("Tvoje přezdívka (např. JanaPece)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { novy ->
                            pin = novy.filter { it.isDigit() }.take(8)
                            stavovaZprava = ""
                        },
                        label = { Text("Tvůj číselný PIN (4 až 8 číslic)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = if (zobrazitPin) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (vyzadujePotvrzeni) {
                        OutlinedTextField(
                            value = potvrzeniPinu,
                            onValueChange = { novy ->
                                potvrzeniPinu = novy.filter { it.isDigit() }.take(8)
                            },
                            label = { Text("Zadej PIN ještě jednou pro kontrolu") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = if (zobrazitPin) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "💡 PIN si dobře zapamatuj — šifrují se jím tvoje recepty při přenosu na jiný telefon.",
                            fontSize = 12.sp,
                            color = Color(0xFFE65100)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { zobrazitPin = !zobrazitPin }) {
                            Text(if (zobrazitPin) "🙈 Skrýt PIN" else "👁️ Ukázat PIN")
                        }
                    }

                    if (stavovaZprava.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (jeChyba) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = stavovaZprava,
                                color = if (jeChyba) Color(0xFFC62828) else Color(0xFF2E7D32),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Button(
                        onClick = {
                            nacitaSe = true
                            stavovaZprava = ""
                            VaultStorage.prihlasNeboZalozUcet(
                                zadanyNick = nick,
                                zadanyPin = pin,
                                potvrzenyPin = if (vyzadujePotvrzeni) potvrzeniPinu else ""
                            ) { vysl ->
                                nacitaSe = false
                                when (vysl.typ) {
                                    TypVysledkuAuth.USPECH -> {
                                        if (vysl.ucet != null && vysl.data != null) {
                                            priPrihlaseni(vysl.ucet, vysl.data)
                                        }
                                    }
                                    TypVysledkuAuth.POTVRDIT_NOVY_PIN -> {
                                        vyzadujePotvrzeni = true
                                        jeChyba = false
                                        stavovaZprava = vysl.zprava
                                    }
                                    TypVysledkuAuth.CHYBA -> {
                                        jeChyba = true
                                        stavovaZprava = vysl.zprava
                                    }
                                }
                            }
                        },
                        enabled = !nacitaSe,
                        colors = ButtonDefaults.buttonColors(containerColor = malinova),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        if (nacitaSe) {
                            CircularProgressIndicator(color = Color.White)
                        } else {
                            Text(
                                text = if (vyzadujePotvrzeni) "✨ Založit moji cukrárnu" else "🔐 Otevřít moji cukrárnu",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            priPrihlaseni(
                                UcetCukrarky(nick = "Ukázka", pin = "", jeUkazkovyRezim = true),
                                VzorovaData.vytvorVychoziData()
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "👀 Vyzkoušet ukázku nanečisto",
                            fontWeight = FontWeight.SemiBold,
                            color = malinova
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "© 2026 Created by Pavel Mareš",
                fontSize = 12.sp,
                color = Color(0xFF8D6E63)
            )
        }
    }
}
