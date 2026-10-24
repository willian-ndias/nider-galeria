package br.com.nider.galeria;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.app.PendingIntent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import java.util.ArrayList;
import java.util.List;

/**
 * "Compartilhar → Nider Fotos": escolhe a pasta (em Pictures/Nider Galeria) e
 * Copiar ou Mover, sem senha. Copiar deixa a foto nos dois lugares; Mover tira
 * da pasta de origem. Volta para o app de onde veio.
 */
public class FotosShareActivity extends ComponentActivity {

    private ActivityResultLauncher<IntentSenderRequest> deleteLauncher;
    private List<Uri> uris;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        deleteLauncher = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), r -> finish());
        uris = ForteShareActivity.sharedUris(getIntent());
        if (uris.isEmpty()) {
            finish();
            return;
        }
        pickFolder();
    }

    // Folha de baixo no visual do Nider (grafite, cantos arredondados, linhas grandes).
    private static final int BG = 0xFF171D24, TEXT = 0xFFE9EDF2, MUTED = 0xFF9AA6B2, ACCENT = 0xFF4F8CF7, INK = 0xFF0B0E12;
    private Dialog sheet;
    private LinearLayout body;

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void openSheet() {
        sheet = new Dialog(this);
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(12), 0, dp(24));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BG);
        float r = dp(28);
        bg.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
        body.setBackground(bg);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        sheet.setContentView(scroll);
        Window w = sheet.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setDimAmount(0.6f);
        }
        sheet.setOnCancelListener(d -> finish());
        sheet.show();
    }

    private void clear(String title) {
        body.removeAllViews();
        View grip = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF3A4552);
        g.setCornerRadius(dp(2));
        grip.setBackground(g);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(40), dp(4));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.bottomMargin = dp(12);
        body.addView(grip, gp);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(TEXT);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(24), 0, dp(24), dp(8));
        body.addView(t);
    }

    private void row(String text, String sub, Runnable onClick) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(sub == null ? 56 : 68));
        r.setPadding(dp(24), dp(6), dp(24), dp(6));
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        r.setBackgroundResource(tv.resourceId);
        TextView a = new TextView(this);
        a.setText(text);
        a.setTextColor(TEXT);
        a.setTextSize(16);
        a.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        r.addView(a);
        if (sub != null) {
            TextView b = new TextView(this);
            b.setText(sub);
            b.setTextColor(MUTED);
            b.setTextSize(13);
            r.addView(b);
        }
        r.setOnClickListener(v -> onClick.run());
        body.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void pickFolder() {
        if (sheet == null) openSheet();
        clear(uris.size() == 1 ? "Guardar 1 item em" : "Guardar " + uris.size() + " itens em");
        for (String f : Midia.niderFolders(this)) row(f, null, () -> pickMode(f));
        row("+ Nova pasta", null, this::newFolder);
    }

    private void newFolder() {
        clear("Nova pasta");
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint("Ex.: Viagem 2026");
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setTextSize(16);
        GradientDrawable ib = new GradientDrawable();
        ib.setColor(0xFF0E1216);
        ib.setStroke(dp(1), 0xFF34404C);
        ib.setCornerRadius(dp(14));
        input.setBackground(ib);
        input.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        ip.setMargins(dp(24), dp(8), dp(24), dp(16));
        body.addView(input, ip);
        TextView ok = new TextView(this);
        ok.setText("Criar");
        ok.setTextColor(INK);
        ok.setTextSize(16);
        ok.setTypeface(Typeface.DEFAULT_BOLD);
        ok.setGravity(Gravity.CENTER);
        GradientDrawable ob = new GradientDrawable();
        ob.setColor(ACCENT);
        ob.setCornerRadius(dp(26));
        ok.setBackground(ob);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        op.setMargins(dp(24), 0, dp(24), 0);
        body.addView(ok, op);
        ok.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) pickMode(name);
        });
        input.requestFocus();
        if (sheet.getWindow() != null) sheet.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void pickMode(String folder) {
        clear("Em “" + folder + "”");
        row("Mover", "Sai da pasta de origem e fica só aqui", () -> run(folder, true));
        row("Copiar", "Fica nos dois lugares", () -> run(folder, false));
    }

    private void run(String folder, boolean move) {
        clear((move ? "Movendo" : "Copiando") + " para “" + folder + "”…");
        new Thread(() -> {
            int done = 0;
            List<Uri> originals = new ArrayList<>();
            for (Uri u : uris) {
                try {
                    Midia.copyToFolder(this, u, folder);
                    done++;
                    if (move) {
                        Uri o = Midia.findOriginal(this, u);
                        if (o != null) originals.add(o);
                    }
                } catch (Exception ignored) {
                    // Um arquivo que falha não impede os outros.
                }
            }
            String msg = done == 0 ? "Não consegui guardar. O Nider Fotos aceita fotos e vídeos"
                : (move ? "Movido para " : "Copiado para ") + folder + (done > 1 ? " (" + done + ")" : "");
            if (move && done > 0 && originals.size() < done) msg += ", mas a original continua na pasta de origem";
            final String text = msg;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (sheet != null) sheet.dismiss();
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
                if (originals.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    finish();
                    return;
                }
                try {
                    PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), originals);
                    deleteLauncher.launch(new IntentSenderRequest.Builder(pi.getIntentSender()).build());
                } catch (Exception e) {
                    finish();
                }
            });
        }).start();
    }
}
