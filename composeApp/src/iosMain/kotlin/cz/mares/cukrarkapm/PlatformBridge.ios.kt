@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package cz.mares.cukrarkapm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.window.Dialog
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeCode128Code
import platform.AVFoundation.AVMetadataObjectTypeDataMatrixCode
import platform.AVFoundation.AVMetadataObjectTypeEAN13Code
import platform.AVFoundation.AVMetadataObjectTypeEAN8Code
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.*
import platform.Foundation.*
import platform.Foundation.setValue
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIView
import platform.UIKit.UIViewContentMode
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

actual fun aktualniCasMs(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

actual fun ulozLokalniText(klic: String, hodnota: String) {
    NSUserDefaults.standardUserDefaults.setObject(hodnota, forKey = klic)
}

actual fun nactiLokalniText(klic: String): String? {
    return NSUserDefaults.standardUserDefaults.stringForKey(klic)
}

actual fun httpPozadavek(
    metoda: String,
    url: String,
    teloJson: String?,
    vysledek: (uspech: Boolean, odpoved: String) -> Unit
) {
    val nsUrl = NSURL.URLWithString(url)
    if (nsUrl == null) {
        vysledek(false, "Neplatná URL")
        return
    }
    val pozadavek = NSMutableURLRequest.requestWithURL(nsUrl).apply {
        setHTTPMethod(metoda)
        setValue("application/json", forHTTPHeaderField = "Accept")
        setValue("CukrarkaPM/1.0", forHTTPHeaderField = "User-Agent")
        if (teloJson != null) {
            setValue("application/json; charset=UTF-8", forHTTPHeaderField = "Content-Type")
            val bajty = teloJson.encodeToByteArray()
            if (bajty.isNotEmpty()) {
                val dataTelo = bajty.usePinned { pripichnuto ->
                    NSData.dataWithBytes(pripichnuto.addressOf(0), bajty.size.toULong())
                }
                setHTTPBody(dataTelo)
            }
        }
    }

    val uloha = NSURLSession.sharedSession.dataTaskWithRequest(
        request = pozadavek,
        completionHandler = { data: NSData?, odpovedHttp: NSURLResponse?, chyba: NSError? ->
            val stavovyKod = (odpovedHttp as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
            val ok = chyba == null && stavovyKod in 200..299
            val text = if (data != null && data.length.toInt() > 0) {
                val delka = data.length.toInt()
                val zdrojBajtu = data.bytes
                if (zdrojBajtu != null) {
                    val pole = ByteArray(delka)
                    pole.usePinned { pripichnuto ->
                        memcpy(pripichnuto.addressOf(0), zdrojBajtu, data.length)
                    }
                    pole.decodeToString()
                } else {
                    ""
                }
            } else {
                chyba?.localizedDescription.orEmpty()
            }
            dispatch_async(dispatch_get_main_queue()) {
                vysledek(ok, text)
            }
        }
    )
    uloha.resume()
}

actual fun otevriSmsZpravu(telefon: String, textZpravy: String) {
    val cisteCislo = telefon.filter { it.isDigit() || it == '+' }
    val kodovanaZprava = buildString {
        for (b in textZpravy.encodeToByteArray()) {
            val v = b.toInt() and 0xFF
            if ((v in 48..57) || (v in 65..90) || (v in 97..122)) {
                append(v.toChar())
            } else {
                append('%')
                append("0123456789ABCDEF"[v ushr 4])
                append("0123456789ABCDEF"[v and 0x0F])
            }
        }
    }
    val nsUrl = NSURL.URLWithString("sms:$cisteCislo&body=$kodovanaZprava") ?: return
    UIApplication.sharedApplication.openURL(
        nsUrl,
        options = mapOf<Any?, Any>(),
        completionHandler = null
    )
}

@Composable
actual fun PlatebniQrObrazek(spaydText: String, modifier: Modifier) {
    UIKitView(
        modifier = modifier,
        factory = {
            UIImageView().apply {
                backgroundColor = UIColor.whiteColor
                contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
            }
        },
        update = { zobrazeni ->
            if (spaydText.isNotBlank()) {
                val bajty = spaydText.encodeToByteArray()
                val data = bajty.usePinned { prip ->
                    NSData.dataWithBytes(prip.addressOf(0), bajty.size.toULong())
                }
                val filtr = CIFilter.filterWithName("CIQRCodeGenerator")
                filtr?.setValue(data, forKey = "inputMessage")
                val vystup: CIImage? = filtr?.outputImage
                if (vystup != null) {
                    val zvetseny = vystup.imageByApplyingTransform(CGAffineTransformMakeScale(10.0, 10.0))
                    val kontext = CIContext.context()
                    val cgObr = kontext.createCGImage(zvetseny, fromRect = zvetseny.extent)
                    if (cgObr != null) {
                        zobrazeni.image = UIImage.imageWithCGImage(cgObr)
                        CGImageRelease(cgObr)
                    }
                }
            }
        }
    )
}

@Composable
actual fun KamerovaCteckaDialog(
    priNacteniKodu: (String) -> Unit,
    priZavreni: () -> Unit
) {
    var rucniKod by remember { mutableStateOf("") }
    var jizPrecteno by remember { mutableStateOf(false) }
    val zarizeniKamery = remember { AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) }
    val relaceKamery = remember { AVCaptureSession() }

    val delegatCtecky = remember {
        object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
            override fun captureOutput(
                output: AVCaptureOutput,
                didOutputMetadataObjects: List<*>,
                fromConnection: AVCaptureConnection
            ) {
                if (jizPrecteno) return
                for (objekt in didOutputMetadataObjects) {
                    val strojovyKod = objekt as? AVMetadataMachineReadableCodeObject
                    val prectenyText = strojovyKod?.stringValue
                    if (!prectenyText.isNullOrBlank()) {
                        jizPrecteno = true
                        relaceKamery.stopRunning()
                        dispatch_async(dispatch_get_main_queue()) {
                            priNacteniKodu(prectenyText)
                        }
                        break
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (relaceKamery.isRunning()) {
                relaceKamery.stopRunning()
            }
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

                if (zarizeniKamery != null) {
                    Box(
                        modifier = Modifier
                            .width(280.dp)
                            .height(210.dp)
                            .background(Color.Black, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        UIKitView(
                            modifier = Modifier.width(280.dp).height(210.dp),
                            factory = {
                                val kontejner = UIView(frame = CGRectMake(0.0, 0.0, 280.0, 210.0))
                                val vstup = AVCaptureDeviceInput.deviceInputWithDevice(zarizeniKamery, error = null)
                                if (vstup != null && relaceKamery.canAddInput(vstup)) {
                                    relaceKamery.addInput(vstup)
                                }
                                val vystupMetadat = AVCaptureMetadataOutput()
                                if (relaceKamery.canAddOutput(vystupMetadat)) {
                                    relaceKamery.addOutput(vystupMetadat)
                                    vystupMetadat.setMetadataObjectsDelegate(delegatCtecky, queue = dispatch_get_main_queue())
                                    vystupMetadat.metadataObjectTypes = listOf(
                                        AVMetadataObjectTypeEAN13Code,
                                        AVMetadataObjectTypeEAN8Code,
                                        AVMetadataObjectTypeCode128Code,
                                        AVMetadataObjectTypeQRCode,
                                        AVMetadataObjectTypeDataMatrixCode
                                    )
                                }
                                val vrstvaNahledu = AVCaptureVideoPreviewLayer(session = relaceKamery).apply {
                                    videoGravity = AVLayerVideoGravityResizeAspectFill
                                    frame = CGRectMake(0.0, 0.0, 280.0, 210.0)
                                }
                                kontejner.layer.addSublayer(vrstvaNahledu)
                                relaceKamery.startRunning()
                                kontejner
                            }
                        )
                    }
                } else {
                    Text("Kamera není na tomto zařízení dostupná, zadej čísla čárového kódu ručně:")
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
