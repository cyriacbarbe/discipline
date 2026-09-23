package fr.discipline.app;

import android.widget.LinearLayout;

import java.util.ArrayList;

/** Bibliothèque de messages réutilisables sur les écrans de blocage. */
public class MessagesActivity extends Ecran {

    @Override
    protected void onResume() {
        super.onResume();
        rafraichir();
    }

    @Override
    protected void rafraichir() {
        LinearLayout c = page("Messages", true);
        Ui.ajouter(c, Ui.petit(this, "Variables : {appli} {temps} {ouvertures} {dispo} {rallonges}"), 8);
        for (String m : new ArrayList<>(donnees.messages)) {
            LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 8);
            LinearLayout r = Ui.rangee(this);
            carte.addView(r);
            Ui.etirer(r, Ui.corps(this, m));
            r.addView(Ui.croix(this, v -> {
                donnees.messages.remove(m);
                donnees.enregistrer();
                rafraichir();
            }));
            carte.setOnClickListener(v -> Choix.texte(this, "Message", m, "", t -> {
                int i = donnees.messages.indexOf(m);
                if (i >= 0 && !t.isEmpty()) {
                    donnees.messages.set(i, t);
                    donnees.enregistrer();
                }
                rafraichir();
            }));
        }
        boutonBas(c, "＋ Nouveau message", v -> Choix.texte(this, "Message", "", "Tu as déjà passé {temps} ici aujourd’hui.", t -> {
            if (!t.isEmpty() && !donnees.messages.contains(t)) {
                donnees.messages.add(t);
                donnees.enregistrer();
            }
            rafraichir();
        }));
    }
}
