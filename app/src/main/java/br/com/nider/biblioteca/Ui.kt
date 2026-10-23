@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package br.com.nider.biblioteca

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Ink = Color(0xFF12171C)
private val Card = Color(0xFF1C232B)
private val Paper = Color(0xFFEEF3F6)

class BibliotecaModel(app: Application) : AndroidViewModel(app) {
    private val library = VaultStore.library(app)
    private val vault = VaultStore.vault(app)
    var tab by mutableStateOf(0)
    var albumId by mutableStateOf("")
    var albums by mutableStateOf(emptyList<Album>())
    var photos by mutableStateOf(emptyList<Photo>())
    var open by mutableStateOf<OpenSpace?>(null)
    var docs by mutableStateOf(emptyList<Doc>())
    var viewer by mutableStateOf<Viewer?>(null)
    var importing by mutableStateOf(false)
    var handedOff by mutableStateOf(false)

    fun reloadLibrary() {
        val list = library.albums()
        albums = list
        if (albumId.isEmpty() || list.none { it.id == albumId }) albumId = list.first().id
        photos = library.photos(albumId)
    }

    fun addAlbum(name: String) {
        if (name.isBlank()) return
        albumId = library.addAlbum(name).id
        reloadLibrary()
    }

    fun addPhotos(pairs: List<Pair<String, ByteArray>>) {
        pairs.forEach { (name, bytes) -> library.addPhoto(albumId, name, bytes) }
        reloadLibrary()
    }

    fun deletePhoto(id: String) {
        library.delete(id)
        reloadLibrary()
    }

    fun photoFile(id: String) = library.file(id)

    fun vaultReady() = vault.ready()

    fun createVault(real: String, decoy: String): Int? {
        if (real.length < 4 || decoy.length < 4) return R.string.senha_curta
        if (real == decoy) return R.string.senhas_iguais
        vault.create(real.toCharArray(), decoy.toCharArray())
        return null
    }

    fun enter(password: String): Boolean {
        val space = vault.unlock(password.toCharArray()) ?: return false
        open = space
        docs = vault.list(space)
        return true
    }

    fun lock() {
        open = null
        docs = emptyList()
        viewer = null
        if (!handedOff) wipeExport()
    }

    fun wipeExport() {
        val cache = getApplication<Application>().cacheDir
        File(cache, "export").deleteRecursively()
        File(cache, "ver.pdf").delete()
    }

    fun returned() {
        if (!handedOff) return
        handedOff = false
        wipeExport()
    }

    fun addDocs(pairs: List<Triple<String, String, ByteArray>>) {
        val space = open ?: return
        pairs.forEach { (name, mime, bytes) -> vault.add(space, name, mime, bytes) }
        docs = vault.list(space)
    }

    fun readDoc(id: String): ByteArray = vault.read(open!!, id)

    fun deleteDoc(id: String) {
        val space = open ?: return
        vault.delete(space, id)
        docs = vault.list(space)
    }
}

sealed class Viewer {
    data class PhotoZoom(val index: Int) : Viewer()
    data class DocImage(val id: String, val name: String) : Viewer()
    data class DocPdf(val id: String, val name: String) : Viewer()
}

@Composable
fun BibliotecaApp(model: BibliotecaModel = viewModel()) {
    val life = LocalLifecycleOwner.current
    DisposableEffect(life, model.tab) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !model.importing) model.lock()
            if (event == Lifecycle.Event.ON_START) model.returned()
        }
        life.lifecycle.addObserver(obs)
        onDispose { life.lifecycle.removeObserver(obs) }
    }
    BackHandler(enabled = model.viewer != null || model.open != null) {
        if (model.viewer != null) model.viewer = null else model.lock()
    }
    MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Card, primary = Paper, onPrimary = Ink)) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                if (model.viewer == null) {
                    NavigationBar(containerColor = Card, modifier = Modifier.navigationBarsPadding()) {
                        NavigationBarItem(
                            selected = model.tab == 0,
                            onClick = { model.lock(); model.tab = 0 },
                            label = { Text(stringResource(R.string.aba_fotos)) },
                            icon = { Text("▣") },
                        )
                        NavigationBarItem(
                            selected = model.tab == 1,
                            onClick = { model.tab = 1 },
                            label = { Text(stringResource(R.string.aba_forte)) },
                            icon = { Text("▣") },
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad).statusBarsPadding()) {
                if (model.tab == 0) Gallery(model) else Forte(model)
            }
        }
    }
}

@Composable
private fun Gallery(model: BibliotecaModel) {
    val context = LocalContext.current
    var askAlbum by remember { mutableStateOf(false) }
    var albumName by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val pairs = uris.mapNotNull { uri ->
            val name = displayName(context, uri)
            val mime = context.contentResolver.getType(uri)
            if (!isPhoto(name, mime)) return@mapNotNull null
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapNotNull null
            name to bytes
        }
        model.importing = false
        model.addPhotos(pairs)
    }
    LaunchedEffect(Unit) { model.reloadLibrary() }
    val zoom = model.viewer as? Viewer.PhotoZoom
    if (zoom != null && model.photos.isNotEmpty()) {
        val pages = remember(model.photos) {
            model.photos.map { runCatching { decode(model.photoFile(it.id), 1600) }.getOrElse { decodeBytes(ByteArray(0), 16) } }
        }
        FotoPager(pages, zoom.index) { model.viewer = null }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), color = Paper, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { askAlbum = true }) { Text(stringResource(R.string.nova_pasta)) }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            model.albums.forEach { album ->
                Text(
                    album.name,
                    color = if (album.id == model.albumId) Ink else Paper,
                    modifier = Modifier
                        .background(if (album.id == model.albumId) Paper else Card, RoundedCornerShape(20.dp))
                        .clickable { model.albumId = album.id; model.reloadLibrary() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Box(Modifier.weight(1f)) {
            if (model.photos.isEmpty()) {
                Text(stringResource(R.string.galeria_vazia), color = Paper.copy(alpha = 0.7f), modifier = Modifier.padding(16.dp))
            }
            LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(model.photos, key = { it.id }) { photo ->
                    val bmp = remember(photo.id) { runCatching { decode(model.photoFile(photo.id), 400) }.getOrNull() }
                    val abrir = Modifier
                        .height(120.dp)
                        .combinedClickable(
                            onClick = { model.viewer = Viewer.PhotoZoom(model.photos.indexOf(photo)) },
                            onLongClick = { model.deletePhoto(photo.id) },
                        )
                    if (bmp != null) {
                        Image(bitmap = bmp, contentDescription = photo.name, contentScale = ContentScale.Crop, modifier = abrir)
                    } else {
                        Box(abrir.background(Card))
                    }
                }
            }
            FloatingActionButton(
                onClick = { model.importing = true; pick.launch(arrayOf("image/*")) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = Paper,
                contentColor = Ink,
            ) { Text("+") }
        }
    }
    if (askAlbum) {
        AlertDialog(
            onDismissRequest = { askAlbum = false },
            title = { Text(stringResource(R.string.nova_pasta)) },
            text = { OutlinedTextField(albumName, { albumName = it }, label = { Text(stringResource(R.string.nome_da_pasta)) }) },
            confirmButton = {
                TextButton(onClick = { model.addAlbum(albumName); albumName = ""; askAlbum = false }) { Text(stringResource(R.string.criar)) }
            },
            dismissButton = { TextButton(onClick = { askAlbum = false }) { Text(stringResource(R.string.cancelar)) } },
        )
    }
}

@Composable
private fun Forte(model: BibliotecaModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var real by remember { mutableStateOf("") }
    var decoy by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val space = model.open
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val triples = uris.mapNotNull { uri ->
            val name = displayName(context, uri)
            val mime = kindOf(name, context.contentResolver.getType(uri))
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapNotNull null
            Triple(name, mime, bytes)
        }
        model.importing = false
        model.addDocs(triples)
    }
    when (val view = model.viewer) {
        is Viewer.DocImage -> {
            val bmp = remember(view.id) { runCatching { decodeBytes(model.readDoc(view.id), 1600) }.getOrNull() }
            if (bmp == null) {
                LaunchedEffect(view.id) { model.viewer = null }
            } else {
                FotoFrame(bmp) { model.viewer = null }
            }
            return
        }
        is Viewer.DocPdf -> {
            PdfPages(bytes = model.readDoc(view.id), onBack = { model.viewer = null })
            return
        }
        else -> Unit
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(stringResource(R.string.aba_forte), color = Paper, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (!model.vaultReady()) {
            Text(stringResource(R.string.setup_texto), color = Paper.copy(alpha = 0.75f))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(real, { real = it }, label = { Text(stringResource(R.string.primeira_senha)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(decoy, { decoy = it }, label = { Text(stringResource(R.string.segunda_senha)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Color(0xFFFF8A80), modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                val fail = model.createVault(real, decoy)
                error = fail?.let { context.getString(it) }
                if (fail == null) { real = ""; decoy = "" }
            }) { Text(stringResource(R.string.salvar_senhas)) }
            return
        }
        if (space == null) {
            Text(stringResource(R.string.digite_senha), color = Paper.copy(alpha = 0.75f))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.senha)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Color(0xFFFF8A80), modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                error = if (model.enter(password)) null else context.getString(R.string.senha_incorreta)
                password = ""
            }) { Text(stringResource(R.string.entrar)) }
            return
        }
        Text(stringResource(R.string.toque_segura), color = Paper.copy(alpha = 0.6f))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (model.docs.isEmpty()) {
                Text(stringResource(R.string.forte_vazio), color = Paper.copy(alpha = 0.7f), modifier = Modifier.padding(top = 24.dp))
            }
            LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(model.docs, key = { it.id }) { doc ->
                    if (doc.mime.startsWith("image/") || isPhoto(doc.name, doc.mime)) {
                        val bmp = remember(doc.id) { runCatching { decodeBytes(model.readDoc(doc.id), 400) }.getOrNull() }
                        val abrir = Modifier
                            .height(120.dp)
                            .combinedClickable(
                                onClick = { openDoc(context, model, doc, scope) },
                                onLongClick = { model.deleteDoc(doc.id) },
                            )
                        if (bmp != null) {
                            Image(bitmap = bmp, contentDescription = doc.name, contentScale = ContentScale.Crop, modifier = abrir)
                        } else {
                            Box(abrir.background(Card))
                        }
                    } else {
                        Column(
                            Modifier
                                .height(120.dp)
                                .background(Card, RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = { openDoc(context, model, doc, scope) },
                                    onLongClick = { model.deleteDoc(doc.id) },
                                )
                                .padding(12.dp),
                        ) {
                            Text(
                                if (doc.mime == "application/pdf" || doc.name.endsWith(".pdf", true)) stringResource(R.string.tipo_pdf) else stringResource(R.string.tipo_arquivo),
                                color = Paper.copy(alpha = 0.6f),
                            )
                            Text(doc.name, color = Paper, maxLines = 3)
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { model.importing = true; pick.launch(arrayOf("*/*")) },
                modifier = Modifier.align(Alignment.BottomEnd),
                containerColor = Paper,
                contentColor = Ink,
            ) { Text("+") }
        }
    }
}

private fun kindOf(name: String, mime: String?): String {
    if (isPhoto(name, mime)) return if (mime?.startsWith("image/") == true) mime else "image/jpeg"
    if (mime == "application/pdf" || name.endsWith(".pdf", true)) return "application/pdf"
    return mime ?: "application/octet-stream"
}

private fun openDoc(context: android.content.Context, model: BibliotecaModel, doc: Doc, scope: kotlinx.coroutines.CoroutineScope) {
    when (kindOf(doc.name, doc.mime)) {
        "application/pdf" -> model.viewer = Viewer.DocPdf(doc.id, doc.name)
        else -> if (kindOf(doc.name, doc.mime).startsWith("image/")) {
            model.viewer = Viewer.DocImage(doc.id, doc.name)
        } else scope.launch {
            val bytes = withContext(Dispatchers.IO) { model.readDoc(doc.id) }
            val dir = File(context.cacheDir, "export").apply { mkdirs() }
            val out = File(dir, doc.name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            out.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", out)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, doc.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            model.handedOff = true
            try {
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.abrir_com)))
            } catch (_: Exception) {
                model.handedOff = false
                model.wipeExport()
                Toast.makeText(context, context.getString(R.string.sem_app), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun PdfPages(bytes: ByteArray, onBack: () -> Unit) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(bytes) {
        pages = withContext(Dispatchers.IO) { renderPdf(context.cacheDir, bytes) }
    }
    Column(Modifier.fillMaxSize().background(Ink)) {
        TextButton(onClick = onBack, modifier = Modifier.statusBarsPadding()) {
            Text(stringResource(R.string.fechar), color = Paper)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(pages) { page ->
                Image(page, contentDescription = null, modifier = Modifier.fillMaxWidth().padding(8.dp), contentScale = ContentScale.FillWidth)
            }
        }
    }
}

private fun renderPdf(cache: File, bytes: ByteArray): List<ImageBitmap> {
    val file = File(cache, "ver.pdf")
    file.writeBytes(bytes)
    return try {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val out = mutableListOf<ImageBitmap>()
        val count = renderer.pageCount.coerceAtMost(40)
        for (i in 0 until count) {
            val page = renderer.openPage(i)
            val width = 1080
            val height = (page.height * (width.toFloat() / page.width)).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            out.add(bmp.asImageBitmap())
        }
        renderer.close()
        pfd.close()
        out
    } finally {
        file.delete()
    }
}

private fun decode(file: File, max: Int): ImageBitmap = decodeBytes(file.readBytes(), max)

private fun decodeBytes(bytes: ByteArray, max: Int): ImageBitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > max) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        ?: Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
    return bmp.asImageBitmap()
}

private fun isPhoto(name: String, mime: String?): Boolean {
    if (mime?.startsWith("image/") == true) return true
    val n = name.lowercase()
    return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".heic") || n.endsWith(".heif")
}

private fun displayName(context: android.content.Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) return c.getString(0) ?: "arquivo"
    }
    return "arquivo"
}
