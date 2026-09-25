package fr.discipline.app;

import android.widget.LinearLayout;

import java.util.List;

/** Ouvert depuis le widget : changer de profil, ou choisir quelle concentration lancer. */
public class WidgetChoixActivity extends Ecran {
    static final String QUOI = "quoi";
    static final int PROFIL = 0;
    static final int CONCENTRATION = 1;

    @Override
    protected void onResume() {
        super.onResume();
        rafraichir();
    }

    @Override
    protected void rafraichir() {
        if (getIntent().getIntExtra(QUOI, PROFIL) == CONCENTRATION) {
            concentrations();
        } else {
            profils();
        }
    }

    private void profils() {
        LinearLayout c = page("Changer de profil", true);
        if (donnees.profils.isEmpty()) {
            Ui.ajouter(c, Ui.corps(this, "Aucun profil pour l’instant : crée-les dans ⚙ > Profils."), 12);
            return;
        }
        Ui.ajouter(c, Ui.petit(this, "Passer à un profil qui éteint des limites passe par l’anti-triche."), 8);
        for (Donnees.Profil p : donnees.profils) {
            boolean actif = p.id.equals(donnees.profilActif);
            LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 10);
            LinearLayout r = Ui.rangee(this);
            carte.addView(r);
            Ui.etirer(r, Ui.texte(this, p.nom, 17, Ui.TEXTE, true));
            if (actif) {
                r.addView(Ui.pastille(this, "actif", Ui.VERT));
            } else {
                carte.setOnClickListener(v -> activerProfil(p, this::fini));
            }
        }
    }

    private void concentrations() {
        LinearLayout c = page("Concentration", true);
        List<Condition> lancables = Widget.lancables(donnees, Horloge.maintenant());
        if (lancables.isEmpty()) {
            Ui.ajouter(c, Ui.corps(this, "Aucune concentration à lancer : elles sont déjà en cours, ou "
                    + "aucune limite active n’a de blocage immédiat."), 12);
            return;
        }
        for (Limite l : donnees.limites) {
            Condition im = l.conditionDeType(Condition.IMMEDIAT);
            if (im == null || !lancables.contains(im)) {
                continue;
            }
            LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 10);
            carte.addView(Ui.texte(this, l.nomAffiche(donnees), 17, Ui.TEXTE, true));
            Ui.ajouter(carte, Ui.bouton(this, "▶ Bloque-moi ça pendant " + Ui.duree(im.valeur * 60_000L), Ui.ROUGE), 10)
                    .setOnClickListener(v -> {
                        Widget.lancer(this, im.id);
                        fini();
                    });
        }
    }

    private void fini() {
        Widget.mettreAJour(this);
        finish();
    }
}
