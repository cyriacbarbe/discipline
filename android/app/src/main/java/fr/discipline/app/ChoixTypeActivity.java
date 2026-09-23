package fr.discipline.app;

import android.content.Intent;
import android.widget.LinearLayout;

/** « Ajouter une limite » : choisir parmi les neuf types. */
public class ChoixTypeActivity extends Ecran {

    @Override
    protected void onResume() {
        super.onResume();
        rafraichir();
    }

    @Override
    protected void rafraichir() {
        LinearLayout c = page("Quel type de limite ?", true);
        for (int type = 1; type <= Condition.NOMBRE_TYPES; type++) {
            final int t = type;
            LinearLayout carte = Ui.ajouter(c, Ui.carte(this), type == 1 ? 12 : 8);
            carte.addView(Ui.texte(this, Condition.nomType(type), 17, Ui.TEXTE, true));
            Ui.ajouter(carte, Ui.petit(this, Condition.descriptionType(type)), 4);
            carte.setOnClickListener(v -> {
                startActivity(new Intent(this, LimiteEditActivity.class).putExtra(LimiteEditActivity.EXTRA_TYPE, t));
                finish();
            });
        }
    }
}
