package br.com.nider.galeria;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.json.JSONObject;

/**
 * "Compartilhar → Nider Forte": guarda sem pedir senha e volta para o app de origem.
 *
 * Cada arquivo é criptografado com uma chave AES nova, e essa chave é trancada
 * com a chave pública da caixa de entrada (config.json). Só a senha principal
 * abre a chave privada, então só ela vê o que chegou (pasta Recebidos). A
 * original some da galeria: sem confirmação quando o app tem "Gerenciar mídia".
 */
public class ForteShareActivity extends ComponentActivity {

    private static final Pattern MEDIA_URI = Pattern.compile("content://media/[^/\\s]+/(images|video|file)/media/\\d+");
    private final SecureRandom random = new SecureRandom();
    private ActivityResultLauncher<IntentSenderRequest> deleteLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        deleteLauncher = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), r -> finish());
        List<Uri> uris = sharedUris(getIntent());
        if (uris.isEmpty()) {
            finish();
            return;
        }
        new Thread(() -> {
            String message;
            List<Uri> media = new ArrayList<>();
            try {
                PublicKey inbox = inboxKey();
                if (inbox == null) {
                    message = "Abra o Nider Galeria e crie a senha do Forte primeiro";
                } else {
                    int saved = 0;
                    for (Uri u : uris) {
                        if (store(inbox, u)) {
                            saved++;
                            String m = mediaUri(u);
                            if (m != null) media.add(Uri.parse(m));
                        }
                    }
                    message = saved == 0 ? "O Forte aceita fotos e PDFs" : saved == 1 ? "Guardado no Forte" : saved + " itens guardados no Forte";
                }
            } catch (Exception e) {
                message = "Não consegui guardar no Forte";
            }
            final String msg = message;
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                if (!removeOriginals(media)) finish();
            });
        }).start();
    }

    private static List<Uri> sharedUris(Intent intent) {
        List<Uri> uris = new ArrayList<>();
        if (intent == null) return uris;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) uris.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) uris.addAll(list);
        }
        return uris;
    }

    /** Devolve true quando abriu o pedido de apagar (a tela fecha no retorno). */
    private boolean removeOriginals(List<Uri> media) {
        if (media.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        try {
            PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), media);
            deleteLauncher.launch(new IntentSenderRequest.Builder(pi.getIntentSender()).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private PublicKey inboxKey() throws Exception {
        File cfg = new File(getFilesDir(), "forte/config.json");
        if (!cfg.exists()) return null;
        String pub = new JSONObject(new String(readAll(new java.io.FileInputStream(cfg)), StandardCharsets.UTF_8)).optString("inbox", "");
        if (pub.isEmpty()) return null;
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(pub, Base64.NO_WRAP)));
    }

    private boolean store(PublicKey inbox, Uri uri) throws Exception {
        ContentResolver cr = getContentResolver();
        String name = displayName(cr, uri);
        String mime = cr.getType(uri);
        if (mime == null) mime = name.toLowerCase().endsWith(".pdf") ? "application/pdf" : "image/jpeg";
        boolean pdf = mime.equals("application/pdf");
        boolean image = mime.startsWith("image/");
        if (!pdf && !image) return false;
        byte[] bytes;
        try (InputStream in = cr.openInputStream(uri)) {
            bytes = readAll(in);
        }
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey key = kg.generateKey();
        JSONObject meta = new JSONObject();
        meta.put("name", name);
        meta.put("mime", mime);
        meta.put("kind", pdf ? "pdf" : "foto");
        meta.put("size", bytes.length);
        byte[] thumb = null;
        if (image) {
            BitmapFactory.Options b = new BitmapFactory.Options();
            b.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, b);
            if (b.outWidth > 0) {
                meta.put("w", b.outWidth);
                meta.put("h", b.outHeight);
                int sample = 1;
                while (Math.max(b.outWidth, b.outHeight) / (sample * 2) >= 360) sample *= 2;
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
                if (bmp != null) {
                    float r = Math.min(1f, 360f / Math.max(bmp.getWidth(), bmp.getHeight()));
                    Bitmap small = Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(bmp.getWidth() * r)), Math.max(1, Math.round(bmp.getHeight() * r)), true);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    small.compress(Bitmap.CompressFormat.JPEG, 82, out);
                    thumb = out.toByteArray();
                }
            }
        }
        File dir = new File(getFilesDir(), "forte/inbox/" + System.currentTimeMillis() + "-" + random.nextInt(1_000_000));
        dir.mkdirs();
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, inbox, new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        write(new File(dir, "data.bin"), seal(key, bytes));
        if (thumb != null) write(new File(dir, "thumb.bin"), seal(key, thumb));
        write(new File(dir, "meta.bin"), seal(key, meta.toString().getBytes(StandardCharsets.UTF_8)));
        // A chave vai por último: a caixa de entrada só lê pastas que têm key.bin.
        write(new File(dir, "key.bin"), rsa.doFinal(key.getEncoded()));
        return true;
    }

    /** Mesmo formato do app: 12 bytes de IV seguidos do texto cifrado AES-GCM. */
    private byte[] seal(SecretKey key, byte[] plain) throws Exception {
        byte[] iv = new byte[12];
        random.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] body = c.doFinal(plain);
        byte[] out = new byte[12 + body.length];
        System.arraycopy(iv, 0, out, 0, 12);
        System.arraycopy(body, 0, out, 12, body.length);
        return out;
    }

    private static void write(File f, byte[] data) throws Exception {
        try (FileOutputStream os = new FileOutputStream(f)) {
            os.write(data);
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        in.close();
        return out.toByteArray();
    }

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
}
