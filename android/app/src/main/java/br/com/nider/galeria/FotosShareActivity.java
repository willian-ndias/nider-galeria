package br.com.nider.galeria;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
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

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setOnCancelListener(d -> finish());
    }

    private void pickFolder() {
        List<String> folders = Midia.niderFolders(this);
        List<String> items = new ArrayList<>(folders);
        items.add("+ Nova pasta");
        String title = uris.size() == 1 ? "Guardar 1 foto em" : "Guardar " + uris.size() + " fotos em";
        dialog().setTitle(title)
            .setItems(items.toArray(new String[0]), (d, i) -> {
                if (i == folders.size()) newFolder();
                else pickMode(folders.get(i));
            })
            .show();
    }

    private void newFolder() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint("Ex.: Viagem 2026");
        FrameLayout box = new FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        dialog().setTitle("Nova pasta")
            .setView(box)
            .setPositiveButton("Criar", (d, w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) finish();
                else pickMode(name);
            })
            .setNegativeButton("Cancelar", (d, w) -> finish())
            .show();
    }

    private void pickMode(String folder) {
        dialog().setTitle("Em “" + folder + "”")
            .setItems(new String[] {"Copiar (fica nos dois lugares)", "Mover (sai da pasta de origem)"}, (d, i) -> run(folder, i == 1))
            .show();
    }

    private void run(String folder, boolean move) {
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
