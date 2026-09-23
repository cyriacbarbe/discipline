package fr.discipline.app;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private Regles regles;
    private TextView etatService;
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
        listeApplications.setAdapter(new ListeApplicationsAdapter(this, applicationsInstallees(), regles));
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean actif = serviceAccessibiliteActif();
        etatService.setText(actif ? R.string.service_actif : R.string.service_inactif);
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

    private List<AppInfo> applicationsInstallees() {
        PackageManager pm = getPackageManager();
        Intent intentLanceurs = new Intent(Intent.ACTION_MAIN);
        intentLanceurs.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolus = pm.queryIntentActivities(intentLanceurs, 0);
        List<AppInfo> applis = new ArrayList<>();
        for (ResolveInfo resolu : resolus) {
            String paquet = resolu.activityInfo.packageName;
            if (paquet.equals(getPackageName())) {
                continue;
            }
            applis.add(new AppInfo(paquet, resolu.loadLabel(pm).toString()));
        }
        Collections.sort(applis, Comparator.comparing(a -> a.nom.toLowerCase(Locale.FRANCE)));
        return applis;
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
