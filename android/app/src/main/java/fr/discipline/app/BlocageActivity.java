package fr.discipline.app;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

public class BlocageActivity extends Activity {

    static final String EXTRA_PAQUET = "paquet";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocage);

        String paquet = getIntent().getStringExtra(EXTRA_PAQUET);
        TextView texte = findViewById(R.id.texte_blocage);
        texte.setText(getString(R.string.message_blocage, nomLisible(paquet)));

        findViewById(R.id.bouton_fermer).setOnClickListener(v -> finish());
    }

    private String nomLisible(String paquet) {
        if (paquet == null) {
            return "";
        }
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(paquet, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return paquet;
        }
    }
}
