package fr.discipline.app;

import android.widget.LinearLayout;

import java.util.ArrayList;

/** Groupes d'applis : une appli peut être dans plusieurs groupes. */
public class GroupesActivity extends Ecran {

    @Override
    protected void onResume() {
        super.onResume();
        rafraichir();
    }

    @Override
    protected void rafraichir() {
        LinearLayout c = page("Groupes", true);
        Ui.ajouter(c, Ui.petit(this, "Une limite sur un groupe compte le temps cumulé de toutes ses applis."), 8);
        for (Donnees.Groupe g : new ArrayList<>(donnees.groupes.values())) {
            LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 10);
            LinearLayout r = Ui.rangee(this);
            carte.addView(r);
            LinearLayout textes = Ui.etirer(r, Ui.colonne(this));
            textes.addView(Ui.texte(this, g.nom, 17, Ui.TEXTE, true));
            textes.addView(Ui.petit(this, noms(g)));
            r.addView(Ui.croix(this, v -> modifierGroupe(g, true, null)));
            carte.setOnClickListener(v -> {
                Donnees.Groupe copie = copieGroupe(g);
                Choix.cibles(this, g.nom, copie.paquets, null, () -> modifierGroupe(copie, false, null));
            });
            textes.getChildAt(0).setOnClickListener(v -> Choix.texte(this, "Nom du groupe", g.nom, "", t -> {
                if (!t.isEmpty()) {
                    Donnees.Groupe copie = copieGroupe(g);
                    copie.nom = t;
                    donnees.appliquer(changementGroupe(copie, false));
                    rafraichir();
                }
            }));
        }
        boutonBas(c, "＋ Nouveau groupe", v -> Choix.texte(this, "Nom du groupe", "", "Réseaux sociaux", t -> {
            if (t.isEmpty()) {
                return;
            }
            Donnees.Groupe g = new Donnees.Groupe();
            g.nom = t;
            Choix.cibles(this, t, g.paquets, null, () -> {
                donnees.appliquer(changementGroupe(g, false));
                rafraichir();
            });
        }));
    }

    private String noms(Donnees.Groupe g) {
        if (g.paquets.isEmpty()) {
            return "vide — touche pour choisir les applis";
        }
        ArrayList<String> noms = new ArrayList<>();
        for (String p : g.paquets) {
            noms.add(Applications.nom(this, p));
        }
        return String.join(", ", noms);
    }
}
