package br.com.nider.galeria;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.os.Bundle;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Size;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.PublicKey;
import org.json.JSONArray;
import org.json.JSONObject;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;
import android.Manifest;
import com.getcapacitor.PermissionState;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Ponte do Nider Galeria com o Android: liberar "Gerenciar mídia", apagar a
 * original da galeria e devolver um arquivo do Forte para a galeria do celular.
 * O "Compartilhar → Nider Forte" fica em ForteShareActivity.
 */
@CapacitorPlugin(
    name = "Nider",
    permissions = {
        @Permission(alias = "photos", strings = {Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}),
        @Permission(alias = "storage", strings = {Manifest.permission.READ_EXTERNAL_STORAGE})
    }
)
public class NiderPlugin extends Plugin {

    private ActivityResultLauncher<IntentSenderRequest> deleteLauncher;
    private PluginCall deleteCall;
    private int deleteSaved = -1;

    @Override
    public void load() {
        deleteLauncher = getActivity().registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
            if (deleteCall == null) return;
            JSObject out = new JSObject();
            out.put("deleted", result.getResultCode() == Activity.RESULT_OK);
            if (deleteSaved >= 0) out.put("saved", deleteSaved);
            deleteSaved = -1;
            deleteCall.resolve(out);
            deleteCall = null;
        });
    }

    private static void copy(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buf = new byte[64 * 1024];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
    }

    private String readAlias() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? "photos" : "storage";
    }

    private JSObject access() {
        JSObject out = new JSObject();
        out.put("read", getPermissionState(readAlias()) == PermissionState.GRANTED);
        out.put("manage", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(getContext()));
        return out;
    }

    /** read: pode ver as fotos; manage: pode apagar sem a confirmação do Android ("Gerenciar mídia"). */
    @PluginMethod
    public void mediaAccess(PluginCall call) {
        call.resolve(access());
    }

    @PluginMethod
    public void requestRead(PluginCall call) {
        if (getPermissionState(readAlias()) == PermissionState.GRANTED) {
            call.resolve(access());
            return;
        }
        requestPermissionForAlias(readAlias(), call, "readDone");
    }

    @PermissionCallback
    private void readDone(PluginCall call) {
        call.resolve(access());
    }

    /** Abre a tela do Android "Gerenciar mídia" deste app (liberar uma vez). */
    @PluginMethod
    public void openManageMedia(PluginCall call) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            call.resolve(new JSObject().put("opened", false));
            return;
        }
        Intent i = new Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:" + getContext().getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve(new JSObject().put("opened", true));
    }

    @PluginMethod
    public void deleteOriginals(PluginCall call) {
        JSArray arr = call.getArray("uris");
        List<Uri> uris = new ArrayList<>();
        try {
            for (int i = 0; i < arr.length(); i++) uris.add(Uri.parse(arr.getString(i)));
        } catch (Exception e) {
            call.reject("uris inválidas");
            return;
        }
        if (uris.isEmpty()) {
            call.resolve(new JSObject().put("deleted", false));
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PendingIntent pi = MediaStore.createDeleteRequest(getContext().getContentResolver(), uris);
            deleteCall = call;
            deleteLauncher.launch(new IntentSenderRequest.Builder(pi.getIntentSender()).build());
            return;
        }
        boolean all = true;
        for (Uri u : uris) {
            try {
                if (getContext().getContentResolver().delete(u, null, null) == 0) all = false;
            } catch (Exception e) {
                all = false;
            }
        }
        call.resolve(new JSObject().put("deleted", all));
    }

    @PluginMethod
    public void saveToGallery(PluginCall call) {
        String path = call.getString("path");
        String name = call.getString("name", "arquivo");
        String mime = call.getString("mime", "application/octet-stream");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            call.reject("Precisa do Android 10 ou mais novo");
            return;
        }
        boolean image = mime.startsWith("image/");
        boolean video = mime.startsWith("video/");
        Uri collection = image
            ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            : video ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            : MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, (image ? "Pictures" : video ? "Movies" : "Download") + "/Nider Galeria");
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        ContentResolver cr = getContext().getContentResolver();
        Uri item = null;
        try {
            item = cr.insert(collection, v);
            if (item == null) throw new Exception("sem espaço na galeria");
            try (InputStream in = new FileInputStream(path); OutputStream os = cr.openOutputStream(item)) {
                copy(in, os);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(item, done, null, null);
            call.resolve(new JSObject().put("uri", item.toString()));
        } catch (Exception e) {
            if (item != null) cr.delete(item, null, null);
            call.reject("Não consegui salvar na galeria");
        }
    }

    // ---------- galeria do celular ----------

    /** Todas as fotos e vídeos do celular, do mais novo para o mais antigo (só os dados, sem imagem). */
    @PluginMethod
    public void listMedia(PluginCall call) {
        try {
            call.resolve(new JSObject().put("items", queryMedia(false)));
        } catch (Exception e) {
            call.reject("Não consegui ler a galeria");
        }
    }

    /** O que está na lixeira do Android (some sozinho em 30 dias). */
    @PluginMethod
    public void listTrash(PluginCall call) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            call.resolve(new JSObject().put("items", new JSArray()));
            return;
        }
        try {
            call.resolve(new JSObject().put("items", queryMedia(true)));
        } catch (Exception e) {
            call.reject("Não consegui ler a lixeira");
        }
    }

    private JSArray queryMedia(boolean trashed) throws Exception {
        JSArray items = new JSArray();
        boolean r = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        List<String> cols = new ArrayList<>(java.util.Arrays.asList(
            MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION, MediaStore.MediaColumns.RELATIVE_PATH));
        if (r) {
            cols.add(MediaStore.MediaColumns.IS_FAVORITE);
            cols.add(MediaStore.MediaColumns.DATE_EXPIRES);
        }
        String sel = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + "," + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")";
        String order = MediaStore.MediaColumns.DATE_MODIFIED + " DESC";
        Uri table = MediaStore.Files.getContentUri("external");
        Cursor c;
        if (r) {
            Bundle args = new Bundle();
            args.putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, sel);
            args.putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, order);
            args.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, trashed ? MediaStore.MATCH_ONLY : MediaStore.MATCH_EXCLUDE);
            c = getContext().getContentResolver().query(table, cols.toArray(new String[0]), args, null);
        } else {
            c = getContext().getContentResolver().query(table, cols.toArray(new String[0]), sel, null, order);
        }
        try {
            while (c != null && c.moveToNext()) {
                boolean video = c.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
                Uri base = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                long taken = c.isNull(4) || c.getLong(4) == 0 ? c.getLong(5) * 1000 : c.getLong(4);
                JSONObject o = new JSONObject();
                o.put("uri", ContentUris.withAppendedId(base, c.getLong(0)).toString());
                o.put("video", video);
                o.put("name", c.getString(2));
                o.put("mime", c.getString(3));
                o.put("t", taken);
                o.put("w", c.getInt(6));
                o.put("h", c.getInt(7));
                o.put("b", c.getString(8));
                o.put("bn", c.getString(9) == null ? "Sem pasta" : c.getString(9));
                o.put("d", c.isNull(10) ? 0 : c.getLong(10));
                o.put("rp", c.getString(11) == null ? "" : c.getString(11));
                if (r) {
                    o.put("fav", c.getInt(12) == 1);
                    if (!c.isNull(13)) o.put("exp", c.getLong(13) * 1000);
                }
                items.put(o);
            }
        } finally {
            if (c != null) c.close();
        }
        return items;
    }

    private void launchRequest(PluginCall call, PendingIntent pi, int saved) {
        deleteCall = call;
        deleteSaved = saved;
        deleteLauncher.launch(new IntentSenderRequest.Builder(pi.getIntentSender()).build());
    }

    /** Favoritar ou desfavoritar (o favorito é o do Android, vale para as outras galerias). */
    @PluginMethod
    public void favorite(PluginCall call) {
        List<Uri> uris = urisOf(call);
        if (uris.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            call.reject("Precisa do Android 11 ou mais novo");
            return;
        }
        boolean on = Boolean.TRUE.equals(call.getBoolean("on", true));
        launchRequest(call, MediaStore.createFavoriteRequest(getContext().getContentResolver(), uris, on), uris.size());
    }

    /** Tirar da lixeira do Android. */
    @PluginMethod
    public void untrash(PluginCall call) {
        List<Uri> uris = urisOf(call);
        if (uris.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            call.reject("Precisa do Android 11 ou mais novo");
            return;
        }
        launchRequest(call, MediaStore.createTrashRequest(getContext().getContentResolver(), uris, false), uris.size());
    }

    /** Apagar de vez (sem lixeira). */
    @PluginMethod
    public void deleteForever(PluginCall call) {
        List<Uri> uris = urisOf(call);
        if (uris.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            call.reject("Precisa do Android 11 ou mais novo");
            return;
        }
        launchRequest(call, MediaStore.createDeleteRequest(getContext().getContentResolver(), uris), uris.size());
    }

    /** Miniaturas em JPEG (data URL), por endereço. */
    @PluginMethod
    public void thumbs(PluginCall call) {
        JSArray arr = call.getArray("uris");
        JSObject out = new JSObject();
        ContentResolver cr = getContext().getContentResolver();
        for (int i = 0; arr != null && i < arr.length(); i++) {
            try {
                String u = arr.getString(i);
                Bitmap b;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    b = cr.loadThumbnail(Uri.parse(u), new Size(320, 320), null);
                } else {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = 8;
                    try (InputStream in = cr.openInputStream(Uri.parse(u))) {
                        b = BitmapFactory.decodeStream(in, null, o);
                    }
                }
                if (b == null) continue;
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.JPEG, 78, bo);
                out.put(u, "data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP));
            } catch (Exception ignored) {
                // Miniatura que falha fica em branco.
            }
        }
        call.resolve(out);
    }

    /** Copia a foto para o cache e devolve o caminho, para o visualizador abrir. */
    @PluginMethod
    public void viewPath(PluginCall call) {
        String u = call.getString("uri");
        File dir = new File(getContext().getCacheDir(), "ver");
        dir.mkdirs();
        File out = new File(dir, Integer.toHexString(u.hashCode()) + ".img");
        try {
            if (!out.exists()) {
                try (InputStream in = getContext().getContentResolver().openInputStream(Uri.parse(u)); OutputStream os = new FileOutputStream(out)) {
                    copy(in, os);
                }
            }
            call.resolve(new JSObject().put("path", out.getAbsolutePath()));
        } catch (Exception e) {
            call.reject("Não consegui abrir a foto");
        }
    }

    @PluginMethod
    public void clearView(PluginCall call) {
        File[] list = new File(getContext().getCacheDir(), "ver").listFiles();
        if (list != null) for (File f : list) f.delete();
        call.resolve();
    }

    /** Abre no app do celular (vídeo no player do Android). */
    @PluginMethod
    public void openExternal(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(call.getString("uri")), call.getString("mime", "video/*"));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("Nenhum app do celular abre este arquivo");
        }
    }

    /** Manda fotos do celular para o Forte (caixa de entrada, sem senha) e apaga a original. */
    @PluginMethod
    public void toForte(PluginCall call) {
        JSArray arr = call.getArray("uris");
        List<Uri> originals = new ArrayList<>();
        int saved = 0;
        try {
            PublicKey inbox = ForteInbox.key(getContext());
            if (inbox == null) {
                call.reject("Abra o Forte com a senha principal uma vez antes");
                return;
            }
            for (int i = 0; arr != null && i < arr.length(); i++) {
                Uri u = Uri.parse(arr.getString(i));
                if (ForteInbox.store(getContext(), inbox, u)) {
                    saved++;
                    originals.add(u);
                }
            }
        } catch (Exception e) {
            call.reject("Não consegui guardar no Forte");
            return;
        }
        if (originals.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            call.resolve(new JSObject().put("saved", saved).put("deleted", false));
            return;
        }
        final int count = saved;
        PendingIntent pi = MediaStore.createDeleteRequest(getContext().getContentResolver(), originals);
        deleteCall = call;
        deleteSaved = count;
        deleteLauncher.launch(new IntentSenderRequest.Builder(pi.getIntentSender()).build());
    }

    // ---------- ações da seleção na galeria ----------

    private static List<Uri> urisOf(PluginCall call) {
        List<Uri> out = new ArrayList<>();
        JSArray arr = call.getArray("uris");
        for (int i = 0; arr != null && i < arr.length(); i++) {
            try {
                out.add(Uri.parse(arr.getString(i)));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /** Abre o "Compartilhar" do Android com as fotos escolhidas. */
    @PluginMethod
    public void shareUris(PluginCall call) {
        ArrayList<Uri> uris = new ArrayList<>(urisOf(call));
        if (uris.isEmpty()) {
            call.resolve();
            return;
        }
        Intent i = uris.size() == 1
            ? new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.get(0))
            : new Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(i, null);
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(chooser);
        call.resolve();
    }

    @PluginMethod
    public void niderFolders(PluginCall call) {
        JSArray out = new JSArray();
        for (String f : Midia.niderFolders(getContext())) out.put(f);
        call.resolve(new JSObject().put("folders", out));
    }

    /** Copia ou move para Pictures/Nider Galeria/<pasta>. Mover apaga a original (pedido do Android ou silencioso com "Gerenciar mídia"). */
    @PluginMethod
    public void copyTo(PluginCall call) {
        String folder = call.getString("folder", "Nider");
        boolean move = Boolean.TRUE.equals(call.getBoolean("move", false));
        List<Uri> done = new ArrayList<>();
        for (Uri u : urisOf(call)) {
            try {
                Midia.copyToFolder(getContext(), u, folder);
                done.add(u);
            } catch (Exception ignored) {
            }
        }
        if (!move || done.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            call.resolve(new JSObject().put("saved", done.size()).put("deleted", false));
            return;
        }
        PendingIntent pi = MediaStore.createDeleteRequest(getContext().getContentResolver(), done);
        deleteCall = call;
        deleteSaved = done.size();
        deleteLauncher.launch(new IntentSenderRequest.Builder(pi.getIntentSender()).build());
    }

    /** Apagar da galeria: vai para a lixeira do Android (Android 11+), que guarda por 30 dias. */
    @PluginMethod
    public void trashUris(PluginCall call) {
        List<Uri> uris = urisOf(call);
        if (uris.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            call.reject("Precisa do Android 11 ou mais novo");
            return;
        }
        PendingIntent pi = MediaStore.createTrashRequest(getContext().getContentResolver(), uris, true);
        deleteCall = call;
        deleteSaved = uris.size();
        deleteLauncher.launch(new IntentSenderRequest.Builder(pi.getIntentSender()).build());
    }

    // ---------- atualização pelo app ----------

    private static final String RELEASES = "https://api.github.com/repos/willian-ndias/nider-galeria/releases?per_page=10";

    /** Compara versões do tipo 2.2.0-beta: os números primeiro; sem sufixo vale mais que com sufixo. */
    static int compareVersions(String a, String b) {
        String[] pa = a.split("-", 2), pb = b.split("-", 2);
        String[] na = pa[0].split("\\."), nb = pb[0].split("\\.");
        for (int i = 0; i < Math.max(na.length, nb.length); i++) {
            int x = i < na.length ? parse(na[i]) : 0, y = i < nb.length ? parse(nb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        if (pa.length == 1 && pb.length == 2) return 1;
        if (pa.length == 2 && pb.length == 1) return -1;
        return pa.length == 2 ? pa[1].compareTo(pb[1]) : 0;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    @PluginMethod
    public void checkUpdate(PluginCall call) {
        try {
            String current = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionName;
            HttpURLConnection con = (HttpURLConnection) new URL(RELEASES).openConnection();
            con.setRequestProperty("Accept", "application/vnd.github+json");
            con.setRequestProperty("User-Agent", "NiderGaleria");
            con.setConnectTimeout(10000);
            con.setReadTimeout(15000);
            String body = new String(Midia.readAll(con.getInputStream()), java.nio.charset.StandardCharsets.UTF_8);
            JSONArray rels = new JSONArray(body);
            for (int i = 0; i < rels.length(); i++) {
                JSONObject r = rels.getJSONObject(i);
                if (r.optBoolean("draft")) continue;
                JSONArray assets = r.optJSONArray("assets");
                for (int j = 0; assets != null && j < assets.length(); j++) {
                    JSONObject a = assets.getJSONObject(j);
                    if (!a.optString("name").matches("(?i)nider-galeria-.*\\.apk")) continue;
                    String latest = r.optString("tag_name").replaceFirst("^[vV]", "");
                    JSObject out = new JSObject();
                    out.put("current", current);
                    out.put("latest", latest);
                    out.put("newer", compareVersions(latest, current) > 0);
                    out.put("notes", r.optString("body"));
                    out.put("url", a.optString("browser_download_url"));
                    out.put("size", a.optLong("size"));
                    call.resolve(out);
                    return;
                }
            }
            call.resolve(new JSObject().put("current", current).put("newer", false));
        } catch (Exception e) {
            call.reject("Sem conexão com o GitHub");
        }
    }

    /** Baixa o APK novo (com progresso em "updateProgress") e abre o instalador do Android. */
    @PluginMethod
    public void installUpdate(PluginCall call) {
        File apk = new File(new File(getContext().getCacheDir(), "update"), "nider-galeria.apk");
        String url = call.getString("url");
        if (url != null) {
            try {
                apk.getParentFile().mkdirs();
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setRequestProperty("User-Agent", "NiderGaleria");
                con.setInstanceFollowRedirects(true);
                long total = con.getContentLengthLong();
                try (InputStream in = con.getInputStream(); OutputStream os = new FileOutputStream(apk)) {
                    byte[] buf = new byte[64 * 1024];
                    long got = 0;
                    int r, last = -1;
                    while ((r = in.read(buf)) > 0) {
                        os.write(buf, 0, r);
                        got += r;
                        int pct = total > 0 ? (int) (got * 100 / total) : -1;
                        if (pct != last) {
                            last = pct;
                            notifyListeners("updateProgress", new JSObject().put("pct", pct));
                        }
                    }
                }
            } catch (Exception e) {
                call.reject("O download falhou. Tente de novo");
                return;
            }
        }
        if (!apk.exists()) {
            call.reject("Baixe a versão nova antes");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getContext().getPackageManager().canRequestPackageInstalls()) {
            Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getContext().getPackageName()));
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(s);
            call.resolve(new JSObject().put("needsPermission", true));
            return;
        }
        Uri u = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", apk);
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve(new JSObject().put("installing", true));
    }
}
