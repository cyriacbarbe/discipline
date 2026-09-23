package fr.discipline.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChoixApplisTagActivity extends Activity {

    static final String EXTRA_TAG_ID = "tag_id";

    private Regles regles;
    private String tagId;
    private final Set<String> selection = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choix_applis_tag);

        regles = new Regles(this);
        tagId = getIntent().getStringExtra(EXTRA_TAG_ID);

        EditText champNom = findViewById(R.id.champ_nom_tag);
        champNom.setText(regles.getNomTag(tagId));

        selection.addAll(regles.getApplisTag(tagId));

        List<AppInfo> applis = Applications.installees(getPackageManager(), getPackageName());
        ListView liste = findViewById(R.id.liste_applis_tag);
        liste.setAdapter(new ArrayAdapter<AppInfo>(this, R.layout.item_application_simple, applis) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                CheckBox caseApplication = (CheckBox) (convertView != null
                        ? convertView
                        : LayoutInflater.from(getContext()).inflate(R.layout.item_application_simple, parent, false));
                AppInfo app = getItem(position);
                caseApplication.setOnCheckedChangeListener(null);
                caseApplication.setText(app.nom);
                caseApplication.setChecked(selection.contains(app.paquet));
                caseApplication.setOnCheckedChangeListener((bouton, coche) -> {
                    if (coche) {
                        selection.add(app.paquet);
                    } else {
                        selection.remove(app.paquet);
                    }
                });
                return caseApplication;
            }
        });

        findViewById(R.id.bouton_enregistrer_tag).setOnClickListener(v -> {
            String nom = champNom.getText().toString().trim();
            regles.enregistrerTag(tagId, nom.isEmpty() ? tagId : nom, selection);
            finish();
        });
    }
}
