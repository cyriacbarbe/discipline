package fr.discipline.app;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Set;

public class SessionsActivity extends Activity {

    private Regles regles;
    private TextView texteRestantes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sessions);

        regles = new Regles(this);

        EditText champMax = findViewById(R.id.champ_session_max);
        champMax.setText(String.valueOf(regles.getSessionMaxParJour()));
        champMax.addTextChangedListener(champEntierVers(regles::setSessionMaxParJour));

        EditText champDuree = findViewById(R.id.champ_session_duree);
        champDuree.setText(String.valueOf(regles.getSessionDureeMinutes()));
        champDuree.addTextChangedListener(champEntierVers(regles::setSessionDureeMinutes));

        texteRestantes = findViewById(R.id.texte_sessions_restantes);

        TextView texteApplis = findViewById(R.id.texte_applis_session);
        texteApplis.setText(getString(R.string.applis_session_format, nomsApplisSession()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        texteRestantes.setText(getString(R.string.sessions_restantes_format,
                regles.getSessionsRestantesAujourdHui(), regles.getSessionMaxParJour()));
    }

    private String nomsApplisSession() {
        Set<String> applis = regles.getApplisSession();
        if (applis.isEmpty()) {
            return getString(R.string.aucune);
        }
        PackageManager pm = getPackageManager();
        StringBuilder texte = new StringBuilder();
        for (String paquet : applis) {
            if (texte.length() > 0) {
                texte.append(", ");
            }
            try {
                ApplicationInfo info = pm.getApplicationInfo(paquet, 0);
                texte.append(pm.getApplicationLabel(info));
            } catch (PackageManager.NameNotFoundException e) {
                texte.append(paquet);
            }
        }
        return texte.toString();
    }

    private interface Ecrivain {
        void ecrire(int valeur);
    }

    private TextWatcher champEntierVers(Ecrivain ecrivain) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String texte = s.toString().trim();
                if (texte.isEmpty()) {
                    return;
                }
                try {
                    ecrivain.ecrire(Integer.parseInt(texte));
                } catch (NumberFormatException e) {
                    // l'utilisateur est en train de saisir, on ignore
                }
            }
        };
    }
}
