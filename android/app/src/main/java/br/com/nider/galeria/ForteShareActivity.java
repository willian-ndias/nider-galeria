package br.com.nider.galeria;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

/**
 * "Compartilhar → Ocultar": guarda no Forte sem pedir senha (ForteInbox) e volta para o
 * app de origem. A original some da galeria: sem confirmação quando o app tem
 * "Gerenciar mídia".
 */
public class ForteShareActivity extends ComponentActivity {

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
                PublicKey inbox = ForteInbox.key(this);
                if (inbox == null) {
                    message = "Abra o Nider Galeria e crie a senha do Forte primeiro";
                } else {
                    int saved = 0;
                    for (Uri u : uris) {
                        if (ForteInbox.store(this, inbox, u)) {
                            saved++;
                            Uri m = Midia.findOriginal(this, u);
                            if (m != null) media.add(m);
                        }
                    }
                    message = saved == 0 ? "Só fotos e PDFs podem ser ocultados" : saved == 1 ? "Ocultado" : saved + " itens ocultados";
                    if (saved > 0 && media.size() < saved) {
                        message += media.isEmpty() ? ", mas a original continua na galeria" : ", mas " + (saved - media.size()) + " original(is) continua(m) na galeria";
                    }
                }
            } catch (Exception e) {
                message = "Não consegui ocultar";
            }
            final String msg = message;
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                if (!removeOriginals(media)) finish();
            });
        }).start();
    }

    static List<Uri> sharedUris(Intent intent) {
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
}
