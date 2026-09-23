package fr.discipline.app;

import android.widget.LinearLayout;

import org.json.JSONObject;

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
            r.addView(Ui.croix(this, v -> changer(g, true)));
            carte.setOnClickListener(v -> {
                Donnees.Groupe copie = copie(g);
                Choix.cibles(this, g.nom, copie.paquets, null, () -> changer(copie, false));
            });
            textes.getChildAt(0).setOnClickListener(v -> Choix.texte(this, "Nom du groupe", g.nom, "", t -> {
                if (!t.isEmpty()) {
                    Donnees.Groupe copie = copie(g);
                    copie.nom = t;
                    donnees.appliquer(changement(copie, false));
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
                donnees.appliquer(changement(g, false));
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

    private static Donnees.Groupe copie(Donnees.Groupe g) {
        try {
            return Donnees.Groupe.de(g.json());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static JSONObject changement(Donnees.Groupe g, boolean suppression) {
        try {
            JSONObject ch = Donnees.changement("groupe", g.id);
            return suppression ? ch : ch.put("groupe", g.json());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Retirer des applis d'un groupe utilisé par une limite active assouplit : anti-triche. */
    private void changer(Donnees.Groupe g, boolean suppression) {
        JSONObject ch = changement(g, suppression);
        Donnees.Groupe ancien = donnees.groupes.get(g.id);
        boolean assouplit = ancien != null && donnees.groupeUtilise(g.id)
                && (suppression || !g.paquets.containsAll(ancien.paquets));
        if (assouplit) {
            garde(ch, (suppression ? "supprimer" : "retirer des applis de") + " « " + ancien.nom + " »", null);
        } else {
            donnees.appliquer(ch);
            rafraichir();
        }
    }
}
