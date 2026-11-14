package cz.mares.cukrarkapm

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.EnumMap
import java.util.concurrent.Executors

object AndroidAppKontext {
    var appContext: Context? = null
}

private fun Context.najdiLifecycleOwner(): LifecycleOwner? {
    var aktualni: Context? = this
    while (aktualni != null) {
        if (aktualni is LifecycleOwner) return aktualni
        aktualni = (aktualni as? ContextWrapper)?.baseContext
    }
    return null
}

actual fun aktualniCasMs(): Long = System.currentTimeMillis()

actual fun ulozLokalniText(klic: String, hodnota: String) {
    val ctx = AndroidAppKontext.appContext ?: return
    ctx.getSharedPreferences("cukrarka_pm_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString(klic, hodnota)
        .apply()
}

actual fun nactiLokalniText(klic: String): String? {
    val ctx = AndroidAppKontext.appContext ?: return null
    return ctx.getSharedPreferences("cukrarka_pm_prefs", Context.MODE_PRIVATE)
        .getString(klic, null)
}

actual fun httpPozadavek(
    metoda: String,
    url: String,
    teloJson: String?,
    vysledek: (uspech: Boolean, odpoved: String) -> Unit
) {
    val hlavniVlakno = Handler(Looper.getMainLooper())
    Thread {
        var spojeni: HttpURLConnection? = null
        try {
            spojeni = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = metoda
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "CukrarkaPM/1.0")
                if (teloJson != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    outputStream.use { proud ->
                        proud.write(teloJson.toByteArray(Charsets.UTF_8))
                    }
                }
            }
            val kod = spojeni.responseCode
            val proud = if (kod in 200..299) spojeni.inputStream else spojeni.errorStream
            val textOdpovedi = proud?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val ok = kod in 200..299
            hlavniVlakno.post { vysledek(ok, textOdpovedi) }
        } catch (e: Exception) {
            hlavniVlakno.post { vysledek(false, e.message.orEmpty()) }
        } finally {
            spojeni?.disconnect()
        }
    }.start()
}

actual fun otevriSmsZpravu(telefon: String, textZpravy: String) {
    val ctx = AndroidAppKontext.appContext ?: return
    try {
        val cisteCislo = telefon.trim()
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(cisteCislo)}")).apply {
            putExtra("sms_body", textZpravy)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    } catch (_: Exception) {
    }
}

@Composable
actual fun PlatebniQrObrazek(spaydText: String, modifier: Modifier) {
    val matice = remember(spaydText) {
        if (spaydText.isBlank()) null else try {
            val parametry = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 1)
            }
            QRCodeWriter().encode(spaydText, BarcodeFormat.QR_CODE, 1, 1, parametry)
        } catch (_: Exception) {
            null
        }
    }

    Canvas(modifier = modifier.background(Color.White)) {
        val m = matice ?: return@Canvas
        val pocet = m.width.coerceAtLeast(1)
        val krok = size.minDimension / pocet.toFloat()
        for (y in 0 until m.height) {
            for (x in 0 until m.width) {
                if (m.get(x, y)) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * krok, y * krok),
                        size = Size(krok + 0.6f, krok + 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
actual fun KamerovaCteckaDialog(
    priNacteniKodu: (String) -> Unit,
    priZavreni: () -> Unit
) {
    val kontext = LocalContext.current
    var maOpravneni by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(kontext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var rucniKod by remember { mutableStateOf("") }
    var jizPrecteno by remember { mutableStateOf(false) }

    val zadostOpravneni = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { povoleno ->
        maOpravneni = povoleno
    }

    LaunchedEffect(Unit) {
        if (!maOpravneni) {
            zadostOpravneni.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(onDismissRequest = priZavreni) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📷 Namiř kameru na čárový kód", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))

                if (maOpravneni) {
                    val exekutor = remember { Executors.newSingleThreadExecutor() }
                    val ctecka = remember {
                        val formaty = listOf(
                            BarcodeFormat.EAN_13,
                            BarcodeFormat.EAN_8,
                            BarcodeFormat.UPC_A,
                            BarcodeFormat.CODE_128,
                            BarcodeFormat.QR_CODE,
                            BarcodeFormat.DATA_MATRIX
                        )
                        val tipy = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                            put(DecodeHintType.POSSIBLE_FORMATS, formaty)
                            put(DecodeHintType.TRY_HARDER, true)
                        }
                        MultiFormatReader().apply { setHints(tipy) }
                    }

                    DisposableEffect(Unit) {
                        onDispose {
                            exekutor.shutdown()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .background(Color.Black, RoundedCornerShape(14.dp))
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val nahledView = PreviewView(ctx)
                                val vlastnik = ctx.najdiLifecycleOwner()
                                val poskytovatelFuture = ProcessCameraProvider.getInstance(ctx)
                                poskytovatelFuture.addListener({
                                    try {
                                        val poskytovatel = poskytovatelFuture.get()
                                        val nahled = Preview.Builder().build().also {
                                            it.setSurfaceProvider(nahledView.surfaceProvider)
                                        }
                                        val analyza = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()

                                        analyza.setAnalyzer(exekutor) { snimek ->
                                            if (!jizPrecteno) {
                                                try {
                                                    val rovina = snimek.planes[0]
                                                    val buffer = rovina.buffer
                                                    val krokRadku = rovina.rowStride
                                                    val sirka = snimek.width
                                                    val vyska = snimek.height
                                                    val yData = ByteArray(buffer.remaining())
                                                    buffer.get(yData)

                                                    val nalezenyText = zkusDekodovatSnimek(
                                                        ctecka = ctecka,
                                                        yData = yData,
                                                        krokRadku = krokRadku,
                                                        sirka = sirka,
                                                        vyska = vyska,
                                                        rotace = snimek.imageInfo.rotationDegrees
                                                    )
                                                    if (!nalezenyText.isNullOrBlank()) {
                                                        jizPrecteno = true
                                                        Handler(Looper.getMainLooper()).post {
                                                            priNacteniKodu(nalezenyText)
                                                        }
                                                    }
                                                } catch (_: Exception) {
                                                }
                                            }
                                            snimek.close()
                                        }

                                        if (vlastnik != null) {
                                            poskytovatel.unbindAll()
                                            poskytovatel.bindToLifecycle(
                                                vlastnik,
                                                CameraSelector.DEFAULT_BACK_CAMERA,
                                                nahled,
                                                analyza
                                            )
                                        }
                                    } catch (_: Exception) {
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                nahledView
                            }
                        )
                    }
                } else {
                    Text("Pro čtení z kamery povol přístup k fotoaparátu, nebo opiš čísla pod kódem ručně:")
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = rucniKod,
                    onValueChange = { rucniKod = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Nebo zadej čísla čárového kódu") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = priZavreni, modifier = Modifier.weight(1f)) {
                        Text("Zavřít")
                    }
                    Button(
                        onClick = { if (rucniKod.isNotBlank()) priNacteniKodu(rucniKod) },
                        enabled = rucniKod.length >= 6,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Použít kód")
                    }
                }
            }
        }
    }
}

private fun zkusDekodovatSnimek(
    ctecka: MultiFormatReader,
    yData: ByteArray,
    krokRadku: Int,
    sirka: Int,
    vyska: Int,
    rotace: Int
): String? {
    try {
        val zdroj = PlanarYUVLuminanceSource(yData, krokRadku, vyska, 0, 0, sirka, vyska, false)
        return ctecka.decodeWithState(BinaryBitmap(HybridBinarizer(zdroj))).text
    } catch (_: Exception) {
        ctecka.reset()
    }
    if (rotace == 90 || rotace == 270) {
        try {
            val otoceno = ByteArray(sirka * vyska)
            for (y in 0 until vyska) {
                val pocatekRadku = y * krokRadku
                for (x in 0 until sirka) {
                    val idxZdroj = pocatekRadku + x
                    if (idxZdroj < yData.size) {
                        otoceno[x * vyska + (vyska - y - 1)] = yData[idxZdroj]
                    }
                }
            }
            val zdrojOtoceny = PlanarYUVLuminanceSource(otoceno, vyska, sirka, 0, 0, vyska, sirka, false)
            return ctecka.decodeWithState(BinaryBitmap(HybridBinarizer(zdrojOtoceny))).text
        } catch (_: Exception) {
            ctecka.reset()
        }
    }
    return null
}
