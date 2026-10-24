package br.com.nider.galeria;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ponte do Nider Galeria com o Android: receber fotos e PDFs pelo "Compartilhar",
 * apagar a original da galeria (com a confirmação do sistema) e devolver um
 * arquivo do Forte para a galeria do celular.
 */
@CapacitorPlugin(
    name = "Nider",
    permissions = {
        @Permission(alias = "photos", strings = {Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}),
        @Permission(alias = "storage", strings = {Manifest.permission.READ_EXTERNAL_STORAGE})
    }
)
public class NiderPlugin extends Plugin {

    private static final Pattern MEDIA_URI = Pattern.compile("content://media/[^/\\s]+/(images|video|file)/media/\\d+");

    private final List<JSObject> pending = new ArrayList<>();
    private ActivityResultLauncher<IntentSenderRequest> deleteLauncher;
    private PluginCall deleteCall;

    @Override
    public void load() {
        deleteLauncher = getActivity().registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
            if (deleteCall == null) return;
            JSObject out = new JSObject();
            out.put("deleted", result.getResultCode() == Activity.RESULT_OK);
            deleteCall.resolve(out);
            deleteCall = null;
        });
        receive(getActivity().getIntent());
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        if (receive(intent)) notifyListeners("shared", new JSObject());
    }

    private boolean receive(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        List<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) uris.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) uris.addAll(list);
        } else {
            return false;
        }
        intent.setAction(null);
        File dir = new File(getContext().getCacheDir(), "shared");
        dir.mkdirs();
        ContentResolver cr = getContext().getContentResolver();
        int n = 0;
        for (Uri uri : uris) {
            try {
                String name = displayName(cr, uri);
                File out = new File(dir, (n++) + "_" + name.replaceAll("[^A-Za-z0-9._-]", "_"));
                try (InputStream in = cr.openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
                    copy(in, os);
                }
                JSObject f = new JSObject();
                f.put("path", out.getAbsolutePath());
                f.put("name", name);
                f.put("mime", cr.getType(uri));
                f.put("media", mediaUri(uri));
                pending.add(f);
            } catch (Exception ignored) {
                // Um arquivo que não abre não impede os outros.
            }
        }
        return !uris.isEmpty();
    }

    /** A foto compartilhada pela galeria ou pelo Google Fotos, como endereço da galeria do Android (para apagar). */
    private static String mediaUri(Uri uri) {
        String s = uri.toString();
        if ("media".equals(uri.getAuthority())) return s;
        Matcher m = MEDIA_URI.matcher(Uri.decode(s));
        return m.find() ? m.group() : null;
    }

    private static String displayName(ContentResolver cr, Uri uri) {
        try (Cursor c = cr.query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null) return c.getString(0);
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "arquivo";
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
    public void takeShared(PluginCall call) {
        JSArray files = new JSArray();
        for (JSObject f : pending) files.put(f);
        pending.clear();
        JSObject out = new JSObject();
        out.put("files", files);
        call.resolve(out);
    }

    @PluginMethod
    public void clearShared(PluginCall call) {
        File dir = new File(getContext().getCacheDir(), "shared");
        File[] list = dir.listFiles();
        if (list != null) for (File f : list) f.delete();
        call.resolve();
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
}
