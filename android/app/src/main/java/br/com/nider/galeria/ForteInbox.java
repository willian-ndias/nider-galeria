package br.com.nider.galeria;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.json.JSONObject;

/**
 * Caixa de entrada do Forte: guarda sem senha. Cada arquivo leva uma chave AES
 * nova, trancada com a chave pública RSA-OAEP (config.json, campo "inbox"). Só a
 * senha principal abre a privada; ao abrir o Forte, tudo vai para Recebidos.
 */
final class ForteInbox {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ForteInbox() {}

    static PublicKey key(Context ctx) throws Exception {
        File cfg = new File(ctx.getFilesDir(), "forte/config.json");
        if (!cfg.exists()) return null;
        String pub = new JSONObject(new String(Midia.readAll(new FileInputStream(cfg)), StandardCharsets.UTF_8)).optString("inbox", "");
        if (pub.isEmpty()) return null;
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(pub, Base64.NO_WRAP)));
    }

    /** Guarda uma foto ou PDF. false quando o tipo não é aceito. */
    static boolean store(Context ctx, PublicKey inbox, Uri uri) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        String name = Midia.displayName(cr, uri);
        String mime = Midia.mimeOf(cr, uri, name);
        boolean pdf = mime.equals("application/pdf");
        boolean image = mime.startsWith("image/");
        if (!pdf && !image) return false;
        byte[] bytes;
        try (InputStream in = cr.openInputStream(uri)) {
            bytes = Midia.readAll(in);
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
                thumb = thumbnail(bytes, b.outWidth, b.outHeight);
            }
        }
        File dir = new File(ctx.getFilesDir(), "forte/inbox/" + System.currentTimeMillis() + "-" + RANDOM.nextInt(1_000_000));
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

    private static byte[] thumbnail(byte[] bytes, int w, int h) {
        int sample = 1;
        while (Math.max(w, h) / (sample * 2) >= 360) sample *= 2;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        if (bmp == null) return null;
        float r = Math.min(1f, 360f / Math.max(bmp.getWidth(), bmp.getHeight()));
        Bitmap small = Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(bmp.getWidth() * r)), Math.max(1, Math.round(bmp.getHeight() * r)), true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        small.compress(Bitmap.CompressFormat.JPEG, 82, out);
        return out.toByteArray();
    }

    /** Mesmo formato do app: 12 bytes de IV seguidos do texto cifrado AES-GCM. */
    private static byte[] seal(SecretKey key, byte[] plain) throws Exception {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
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
}
