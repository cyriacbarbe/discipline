package fr.discipline.app;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {

    private Regles regles;
    private TextView etatService;
    private TextView etatUtilisation;
    private Button boutonDebut;
    private Button boutonFin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        regles = new Regles(this);

        etatService = findViewById(R.id.etat_service);
        findViewById(R.id.bouton_activer_service).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        etatUtilisation = findViewById(R.id.etat_utilisation);
        findViewById(R.id.bouton_autoriser_utilisation).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));

        findViewById(R.id.bouton_sessions).setOnClickListener(v ->
                startActivity(new Intent(this, SessionsActivity.class)));
        findViewById(R.id.bouton_nfc).setOnClickListener(v ->
                startActivity(new Intent(this, NfcActivity.class)));

        Switch interrupteurCreneau = findViewById(R.id.interrupteur_creneau);
        interrupteurCreneau.setChecked(regles.isCreneauActif());
        interrupteurCreneau.setOnCheckedChangeListener((bouton, coche) -> {
            regles.setCreneauActif(coche);
            rafraichirBoutonsCreneau();
        });

        boutonDebut = findViewById(R.id.bouton_debut);
        boutonFin = findViewById(R.id.bouton_fin);
        boutonDebut.setOnClickListener(v -> choisirHeure(true));
        boutonFin.setOnClickListener(v -> choisirHeure(false));
        rafraichirBoutonsCreneau();

        ListView listeApplications = findViewById(R.id.liste_applications);
        listeApplications.setAdapter(new ListeApplicationsAdapter(this,
                Applications.installees(getPackageManager(), getPackageName()), regles));
    }

    private void verifierMiseAJour() {
        Button bouton = findViewById(R.id.bouton_mise_a_jour);
        new Thread(() -> {
            try {
                String lien = MiseAJour.lienSiDisponible();
                if (lien != null) {
                    runOnUiThread(() -> {
                        bouton.setOnClickListener(v ->
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(lien))));
                        bouton.setVisibility(View.VISIBLE);
                    });
                }
            } catch (Exception e) {
                // hors ligne ou aucune Release publiée : pas de bouton
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        verifierMiseAJour();
        etatService.setText(serviceAccessibiliteActif() ? R.string.service_actif : R.string.service_inactif);
        etatUtilisation.setText(new Usage(this).permissionAccordee()
                ? R.string.acces_utilisation_actif : R.string.acces_utilisation_inactif);
    }

    private void choisirHeure(boolean debut) {
        int minutesActuelles = debut ? regles.getDebutMinutes() : regles.getFinMinutes();
        new TimePickerDialog(this, (vue, heure, minute) -> {
            int minutes = heure * 60 + minute;
            if (debut) {
                regles.setCreneau(minutes, regles.getFinMinutes());
            } else {
                regles.setCreneau(regles.getDebutMinutes(), minutes);
            }
            rafraichirBoutonsCreneau();
        }, minutesActuelles / 60, minutesActuelles % 60, true).show();
    }

    private void rafraichirBoutonsCreneau() {
        boutonDebut.setText(getString(R.string.debut_format, formatHeure(regles.getDebutMinutes())));
        boutonFin.setText(getString(R.string.fin_format, formatHeure(regles.getFinMinutes())));
    }

    private static String formatHeure(int minutes) {
        return String.format(Locale.FRANCE, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private boolean serviceAccessibiliteActif() {
        String activeServices = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(activeServices)) {
            return false;
        }
        String nomService = getPackageName() + "/" + BlocageAccessibilityService.class.getName();
        for (String service : activeServices.split(":")) {
            if (service.equalsIgnoreCase(nomService)) {
                return true;
            }
        }
        return false;
    }
}
