package fr.discipline.app;

import android.content.Context;
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
        CheckBox caseApplication = (CheckBox) (convertView != null
                ? convertView
                : LayoutInflater.from(getContext()).inflate(R.layout.item_application, parent, false));

        AppInfo app = getItem(position);
        caseApplication.setOnCheckedChangeListener(null);
        caseApplication.setText(app.nom);
        caseApplication.setChecked(regles.getApplisBloquees().contains(app.paquet));
        caseApplication.setOnCheckedChangeListener((bouton, coche) -> regles.setBloquee(app.paquet, coche));

        return caseApplication;
    }
}
