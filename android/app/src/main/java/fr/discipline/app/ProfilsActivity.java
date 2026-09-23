package fr.discipline.app;

import android.app.AlertDialog;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Profils : un jeu de limites actives qu'on allume d'un coup (travail, week-end…). */
public class ProfilsActivity extends Ecran {

    @Override
    protected void onResume() {
        super.onResume();
        rafraichir();
    }

    @Override
    protected void rafraichir() {
        LinearLayout c = page("Profils", true);
        Ui.ajouter(c, Ui.petit(this, "Activer un profil allume ses limites et éteint les autres."), 8);
        for (Donnees.Profil p : new ArrayList<>(donnees.profils)) {
            boolean actif = p.id.equals(donnees.profilActif);
            LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 10);
            LinearLayout r = Ui.rangee(this);
            carte.addView(r);
            LinearLayout textes = Ui.etirer(r, Ui.colonne(this));
            textes.addView(Ui.texte(this, p.nom, 17, Ui.TEXTE, true));
            textes.addView(Ui.petit(this, p.limites.size() + " limite(s)"));
            if (actif) {
                r.addView(Ui.pastille(this, "actif", Ui.VERT));
            }
            r.addView(Ui.croix(this, v -> {
                donnees.profils.remove(p);
                if (actif) {
                    donnees.profilActif = null;
                }
                donnees.enregistrer();
                rafraichir();
            }));
            carte.setOnClickListener(v -> choisirLimites(p.nom, p.limites, () -> {
                donnees.enregistrer();
                rafraichir();
            }));
            if (!actif) {
                Ui.ajouter(carte, Ui.boutonDiscret(this, "Activer"), 10).setOnClickListener(v -> activer(p));
            }
        }
        boutonBas(c, "＋ Nouveau profil", v -> Choix.texte(this, "Nom du profil", "", "Travail", t -> {
            if (t.isEmpty()) {
                return;
            }
            Donnees.Profil p = new Donnees.Profil();
            p.nom = t;
            for (Limite l : donnees.limites) {
                if (l.active) {
                    p.limites.add(l.id);
                }
            }
            choisirLimites(t, p.limites, () -> {
                donnees.profils.add(p);
                donnees.enregistrer();
                rafraichir();
            });
        }));
    }

    private void choisirLimites(String titre, Set<String> ids, Runnable ok) {
        List<Limite> limites = donnees.limites;
        if (limites.isEmpty()) {
            toast("Crée d’abord des limites.");
            return;
        }
        String[] noms = new String[limites.size()];
        boolean[] coches = new boolean[limites.size()];
        for (int i = 0; i < noms.length; i++) {
            noms[i] = limites.get(i).nomAffiche(donnees);
            coches[i] = ids.contains(limites.get(i).id);
        }
        new AlertDialog.Builder(this)
                .setTitle(titre + " : limites actives")
                .setMultiChoiceItems(noms, coches, (d, i, coche) -> coches[i] = coche)
                .setPositiveButton("Valider", (d, w) -> {
                    ids.clear();
                    for (int i = 0; i < coches.length; i++) {
                        if (coches[i]) {
                            ids.add(limites.get(i).id);
                        }
                    }
                    ok.run();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    /** Éteindre une limite active assouplit : anti-triche dans ce cas. */
    private void activer(Donnees.Profil p) {
        boolean assouplit = false;
        for (Limite l : donnees.limites) {
            if (l.active && !p.limites.contains(l.id)) {
                assouplit = true;
            }
        }
        if (assouplit) {
            garde(Donnees.changement("profil", p.id), "passer au profil « " + p.nom + " »", null);
        } else {
            donnees.appliquer(Donnees.changement("profil", p.id));
            rafraichir();
        }
    }
}
