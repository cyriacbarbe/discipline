package fr.discipline.app;

import android.content.Intent;
import android.widget.LinearLayout;

/**
 * « Ajouter une limite » : on choisit ce qu'on veut obtenir, en mots de tous
 * les jours ; les règles plus techniques restent accessibles en bas.
 */
public class ChoixTypeActivity extends Ecran {
    /** {emoji + titre, exemple, type de règle, modèle}. */
    private static final Object[][] INTENTIONS = {
            {"⏱  Limiter mon temps", "Par exemple 30 min par jour sur Instagram.", Condition.TEMPS, null},
            {"🎟  Quelques sessions par jour", "3 sessions de 20 min, puis c’est fermé.", Condition.SESSIONS, null},
            {"🔢  Moins ouvrir une appli", "10 ouvertures par jour au plus.", Condition.OUVERTURES, null},
            {"⛔  Bloquer complètement", "Plus d’accès du tout tant que la limite est active.", Condition.TEMPS,
                    LimiteEditActivity.MODELE_BLOQUER},
            {"🌙  Bloquer à certaines heures", "La nuit, au travail, pendant les repas…", Condition.TEMPS,
                    LimiteEditActivity.MODELE_HORAIRES},
            {"🤔  Réfléchir avant d’ouvrir", "Un compte à rebours, une phrase à recopier, un calcul…", Condition.FRICTION, null},
            {"⏸  Une pause entre deux usages", "Après l’avoir fermée, attendre 30 min.", Condition.PAUSE, null},
            {"🎯  Me concentrer à la demande", "Un bouton « Bloque-moi ça pendant 1 h ».", Condition.IMMEDIAT, null},
            {"🔑  Ouvrir seulement avec mon badge", "L’appli reste fermée tant que la puce NFC n’est pas bipée.",
                    Condition.NFC, null},
    };

    @Override
    protected void onResume() {
        super.onResume();
        rafraichir();
    }

    @Override
    protected void rafraichir() {
        LinearLayout c = page("Que veux-tu faire ?", true);
        for (int i = 0; i < INTENTIONS.length; i++) {
            Object[] x = INTENTIONS[i];
            carte(c, (String) x[0], (String) x[1], (Integer) x[2], (String) x[3], i == 0 ? 12 : 8);
        }
        Ui.ajouter(c, Ui.section(this, "Autres règles"), 16);
        for (int type : new int[]{Condition.DUREE_SESSION, Condition.PAUSE_PROPORTIONNELLE}) {
            carte(c, Condition.nomType(type), Condition.descriptionType(type), type, null, 8);
        }
        Ui.ajouter(c, Ui.petit(this, "Tout se règle ensuite, et plusieurs règles peuvent se combiner dans une même limite."), 12);
    }

    private void carte(LinearLayout c, String titre, String exemple, int type, String modele, int marge) {
        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), marge);
        carte.addView(Ui.texte(this, titre, 17, Ui.TEXTE, true));
        Ui.ajouter(carte, Ui.petit(this, exemple), 4);
        carte.setOnClickListener(v -> {
            startActivity(new Intent(this, LimiteEditActivity.class).putExtra(LimiteEditActivity.EXTRA_TYPE, type)
                    .putExtra(LimiteEditActivity.EXTRA_MODELE, modele));
            finish();
        });
    }
}
