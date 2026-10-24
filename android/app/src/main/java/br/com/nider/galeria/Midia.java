package br.com.nider.galeria;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ajudas de galeria usadas pelo app e pelas telas do Compartilhar. */
final class Midia {

    static final String NIDER_DIR = "Pictures/Nider Galeria/";
    private static final Pattern MEDIA_URI = Pattern.compile("content://media/[^/\\s]+/(images|video|file)/media/\\d+");
    private static final Pattern FILE_PATH = Pattern.compile("(/storage/[^?#]+|/sdcard/[^?#]+)");

    private Midia() {}

    /**
     * A foto original no catálogo do Android, para apagar ou mover. O Google Fotos
     * manda o endereço do catálogo; a Galeria da Xiaomi manda o caminho do arquivo,
     * que é procurado no catálogo (precisa da liberação de ver as fotos).
     */
    static Uri findOriginal(Context ctx, Uri uri) {
        if ("media".equals(uri.getAuthority())) return uri;
        String decoded = Uri.decode(uri.toString());
        Matcher m = MEDIA_URI.matcher(decoded);
        if (m.find()) return Uri.parse(m.group());
        String path = dataColumn(ctx, uri);
        if (path == null) {
            Matcher f = FILE_PATH.matcher(decoded);
            if (f.find()) path = f.group(1).replace("/sdcard/", "/storage/emulated/0/");
        }
        if (path == null) return null;
        Uri[] tables = {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Files.getContentUri("external"),
        };
        for (Uri table : tables) {
            try (Cursor c = ctx.getContentResolver().query(table, new String[] {MediaStore.MediaColumns._ID},
                    MediaStore.MediaColumns.DATA + "=?", new String[] {path}, null)) {
                if (c != null && c.moveToFirst()) return ContentUris.withAppendedId(table, c.getLong(0));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String dataColumn(Context ctx, Uri uri) {
        try (Cursor c = ctx.getContentResolver().query(uri, new String[] {MediaStore.MediaColumns.DATA}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String p = c.getString(0);
                if (p != null && p.startsWith("/")) return p;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static String displayName(ContentResolver cr, Uri uri) {
        try (Cursor c = cr.query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null) return c.getString(0);
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "arquivo";
    }

    static String mimeOf(ContentResolver cr, Uri uri, String name) {
        String mime = cr.getType(uri);
        if (mime != null) return mime;
        String n = name.toLowerCase();
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".mp4") || n.endsWith(".mov") || n.endsWith(".3gp")) return "video/mp4";
        if (n.endsWith(".png")) return "image/png";
        return "image/jpeg";
    }

    static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(in, out);
        in.close();
        return out.toByteArray();
    }

    static void copy(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buf = new byte[64 * 1024];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
    }

    /** Pastas do Nider Fotos (subpastas de Pictures/Nider Galeria). */
    static List<String> niderFolders(Context ctx) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try (Cursor c = ctx.getContentResolver().query(MediaStore.Files.getContentUri("external"),
                    new String[] {MediaStore.MediaColumns.RELATIVE_PATH},
                    MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?", new String[] {NIDER_DIR + "%"}, null)) {
                while (c != null && c.moveToNext()) {
                    String rel = c.getString(0);
                    if (rel == null) continue;
                    String rest = rel.substring(NIDER_DIR.length()).replaceAll("/+$", "");
                    if (!rest.isEmpty() && !rest.contains("/")) names.add(rest);
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>(names);
    }

    /** Copia uma foto ou vídeo para Pictures/Nider Galeria/<pasta>, mantendo a data da foto. */
    static Uri copyToFolder(Context ctx, Uri src, String folder) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw new Exception("Precisa do Android 10 ou mais novo");
        ContentResolver cr = ctx.getContentResolver();
        String name = displayName(cr, src);
        String mime = mimeOf(cr, src, name);
        boolean video = mime.startsWith("video/");
        if (!video && !mime.startsWith("image/")) throw new Exception("Só fotos e vídeos");
        Long taken = null;
        try (Cursor c = cr.query(src, new String[] {MediaStore.MediaColumns.DATE_TAKEN}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) taken = c.getLong(0);
        } catch (Exception ignored) {
        }
        Uri table = video
            ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, NIDER_DIR + folder.replaceAll("[\\\\/:*?\"<>|]", "_") + "/");
        if (taken != null) v.put(MediaStore.MediaColumns.DATE_TAKEN, taken);
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri item = cr.insert(table, v);
        if (item == null) throw new Exception("sem espaço");
        try (InputStream in = cr.openInputStream(src); OutputStream os = cr.openOutputStream(item)) {
            copy(in, os);
        } catch (Exception e) {
            cr.delete(item, null, null);
            throw e;
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        cr.update(item, done, null, null);
        return item;
    }
}
