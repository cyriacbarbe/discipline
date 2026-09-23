package fr.discipline.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class BlocageActivity extends Activity {

    static final String EXTRA_PAQUET = "paquet";

    private Regles regles;
    private String paquet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocage);

        regles = new Regles(this);
        paquet = getIntent().getStringExtra(EXTRA_PAQUET);
        TextView texte = findViewById(R.id.texte_blocage);
        texte.setText(getString(R.string.message_blocage, nomLisible(paquet)));

        findViewById(R.id.bouton_fermer).setOnClickListener(v -> finish());

        Button boutonSession = findViewById(R.id.bouton_session);
        int restantes = regles.getSessionsRestantesAujourdHui();
        if (paquet != null && regles.isAppliSession(paquet) && restantes > 0) {
            boutonSession.setText(getString(R.string.demarrer_session_format, restantes));
            boutonSession.setVisibility(View.VISIBLE);
            boutonSession.setOnClickListener(v -> demarrerSession());
        } else {
            boutonSession.setVisibility(View.GONE);
        }
    }

    private void demarrerSession() {
        regles.demarrerSession(paquet);
        Intent lancement = getPackageManager().getLaunchIntentForPackage(paquet);
        if (lancement != null) {
            lancement.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(lancement);
        }
        finish();
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
