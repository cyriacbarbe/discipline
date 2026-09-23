package fr.discipline.app;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;

import java.util.List;

class ListeApplicationsAdapter extends ArrayAdapter<AppInfo> {

    private final Regles regles;

    ListeApplicationsAdapter(Context context, List<AppInfo> applis, Regles regles) {
        super(context, R.layout.item_application, applis);
        this.regles = regles;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View ligne = convertView != null
                ? convertView
                : LayoutInflater.from(getContext()).inflate(R.layout.item_application, parent, false);

        AppInfo app = getItem(position);

        CheckBox caseApplication = ligne.findViewById(R.id.case_application);
        caseApplication.setOnCheckedChangeListener(null);
        caseApplication.setText(app.nom);
        caseApplication.setChecked(regles.getApplisBloquees().contains(app.paquet));
        caseApplication.setOnCheckedChangeListener((bouton, coche) -> regles.setBloquee(app.paquet, coche));

        ligne.findViewById(R.id.bouton_reglages_appli).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), AppDetailActivity.class);
            intent.putExtra(AppDetailActivity.EXTRA_PAQUET, app.paquet);
            intent.putExtra(AppDetailActivity.EXTRA_NOM, app.nom);
            getContext().startActivity(intent);
        });

        return ligne;
    }
}
