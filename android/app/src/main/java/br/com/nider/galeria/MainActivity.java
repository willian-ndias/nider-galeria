package br.com.nider.galeria;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NiderPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
