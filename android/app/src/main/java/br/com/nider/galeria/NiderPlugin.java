package br.com.nider.galeria;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
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

    @Override
    public void load() {
        deleteLauncher = getActivity().registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
            if (deleteCall == null) return;
            JSObject out = new JSObject();
            out.put("deleted", result.getResultCode() == Activity.RESULT_OK);
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
}
