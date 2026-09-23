package fr.discipline.app;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

public class AppDetailActivity extends Activity {

    static final String EXTRA_PAQUET = "paquet";
    static final String EXTRA_NOM = "nom";

    private static final String[] MODES = {Regles.MODE_GLOBAL, Regles.MODE_PERSONNALISE, Regles.MODE_AUCUN};

    private Regles regles;
    private String paquet;
    private Button boutonDebut;
    private Button boutonFin;
    private View blocCreneauPersonnalise;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_detail);

        regles = new Regles(this);
        paquet = getIntent().getStringExtra(EXTRA_PAQUET);

        ((TextView) findViewById(R.id.titre_appli)).setText(getIntent().getStringExtra(EXTRA_NOM));

        Switch interrupteurBloquee = findViewById(R.id.interrupteur_bloquee);
        interrupteurBloquee.setChecked(regles.getApplisBloquees().contains(paquet));
        interrupteurBloquee.setOnCheckedChangeListener((b, coche) -> regles.setBloquee(paquet, coche));

        blocCreneauPersonnalise = findViewById(R.id.bloc_creneau_personnalise);
        boutonDebut = findViewById(R.id.bouton_debut_appli);
        boutonFin = findViewById(R.id.bouton_fin_appli);
        boutonDebut.setOnClickListener(v -> choisirHeure(true));
        boutonFin.setOnClickListener(v -> choisirHeure(false));
        rafraichirBoutonsCreneau();

        Spinner spinnerCreneau = findViewById(R.id.spinner_creneau);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.modes_creneau, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCreneau.setAdapter(adapter);

        String modeActuel = regles.getModeCreneau(paquet);
        spinnerCreneau.setSelection(indexDeMode(modeActuel));
        blocCreneauPersonnalise.setVisibility(
                Regles.MODE_PERSONNALISE.equals(modeActuel) ? View.VISIBLE : View.GONE);

        spinnerCreneau.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String mode = MODES[position];
                regles.setModeCreneau(paquet, mode);
                blocCreneauPersonnalise.setVisibility(
                        Regles.MODE_PERSONNALISE.equals(mode) ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        EditText champQuota = findViewById(R.id.champ_quota);
        int quota = regles.getQuotaMinutes(paquet);
        champQuota.setText(quota > 0 ? String.valueOf(quota) : "");
        champQuota.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String texte = s.toString().trim();
                int minutes = 0;
                if (!texte.isEmpty()) {
                    try {
                        minutes = Integer.parseInt(texte);
                    } catch (NumberFormatException e) {
                        return;
                    }
                }
                regles.setQuotaMinutes(paquet, minutes);
            }
        });

        Switch interrupteurSession = findViewById(R.id.interrupteur_session);
        interrupteurSession.setChecked(regles.isAppliSession(paquet));
        interrupteurSession.setOnCheckedChangeListener((b, coche) -> regles.setAppliSession(paquet, coche));
    }

    private void choisirHeure(boolean debut) {
        int minutesActuelles = debut ? regles.getDebutMinutes(paquet) : regles.getFinMinutes(paquet);
        new TimePickerDialog(this, (vue, heure, minute) -> {
            int minutes = heure * 60 + minute;
            if (debut) {
                regles.setCreneau(paquet, minutes, regles.getFinMinutes(paquet));
            } else {
                regles.setCreneau(paquet, regles.getDebutMinutes(paquet), minutes);
            }
            rafraichirBoutonsCreneau();
        }, minutesActuelles / 60, minutesActuelles % 60, true).show();
    }

    private void rafraichirBoutonsCreneau() {
        boutonDebut.setText(getString(R.string.debut_format, formatHeure(regles.getDebutMinutes(paquet))));
        boutonFin.setText(getString(R.string.fin_format, formatHeure(regles.getFinMinutes(paquet))));
    }

    private static String formatHeure(int minutes) {
        return String.format(Locale.FRANCE, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private static int indexDeMode(String mode) {
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i].equals(mode)) {
                return i;
            }
        }
        return 0;
    }
}
