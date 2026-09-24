package fr.discipline.app;

import android.content.Intent;
import android.widget.LinearLayout;

/** Paramètres : tolérance, début de journée, « Suivies », vacances et liens vers les sous-pages. */
public class ParametresActivity extends Ecran {
    private static final long JOUR = 86_400_000L;

    @Override
    protected void onResume() {
        super.onResume();
        rafraichir();
    }

    @Override
    protected void rafraichir() {
        LinearLayout c = page("Paramètres", true);
        long maintenant = System.currentTimeMillis();

        LinearLayout temps = Ui.ajouter(c, Ui.carte(this), 12);
        temps.addView(Ui.lien(this, "Tolérance de session", donnees.toleranceSecondes + " s",
                v -> Choix.nombre(this, "Tolérance de session (s)", donnees.toleranceSecondes, x -> {
                    if (x >= 0) {
                        donnees.toleranceSecondes = x;
                        donnees.enregistrer();
                    }
                    rafraichir();
                })));
        temps.addView(Ui.petit(this, "Deux passages sur une appli séparés de moins que ça comptent pour une seule session."));
        temps.addView(Ui.lien(this, "Début de la journée", Ui.heure(donnees.debutJourneeMinutes),
                v -> Choix.heure(this, donnees.debutJourneeMinutes, m -> {
                    donnees.debutJourneeMinutes = m;
                    donnees.enregistrer();
                    rafraichir();
                })));
        int suivies = donnees.suiviesApplis.size() + donnees.suiviesGroupes.size();
        temps.addView(Ui.lien(this, "Applis « Suivies » (accueil)", suivies == 0 ? "aucune" : suivies + " choix",
                v -> Choix.cibles(this, "Suivies", donnees.suiviesApplis, donnees.suiviesGroupes, () -> {
                    donnees.enregistrer();
                    rafraichir();
                })));

        LinearLayout vacances = Ui.ajouter(c, Ui.carte(this), 8);
        if (donnees.enVacances(maintenant)) {
            vacances.addView(Ui.lien(this, "🏖 Mode vacances", "jusqu’au " + Donnees.dateCourte(donnees.vacancesJusquA), null));
            Ui.ajouter(vacances, Ui.boutonDiscret(this, "Arrêter les vacances"), 6).setOnClickListener(v -> {
                donnees.appliquer(vacances(0));
                rafraichir();
            });
        } else {
            vacances.addView(Ui.lien(this, "🏖 Mode vacances", "désactivé", v -> Choix.date(this, maintenant + JOUR, jour -> {
                long fin = jour + JOUR;
                if (fin <= maintenant) {
                    return;
                }
                garde(vacances(fin), "vacances jusqu’au " + Donnees.dateCourte(fin), null);
            })));
            vacances.addView(Ui.petit(this, "Suspend toutes les limites jusqu’au jour choisi (inclus)."));
        }

        LinearLayout liens = Ui.ajouter(c, Ui.carte(this), 8);
        liens.addView(Ui.lien(this, "Historique d’usage", Historique.autorise(this) ? "" : "accès à autoriser",
                v -> startActivity(new Intent(this, HistoriqueActivity.class))));
        liens.addView(Ui.lien(this, "Groupes", String.valueOf(donnees.groupes.size()),
                v -> startActivity(new Intent(this, GroupesActivity.class))));
        liens.addView(Ui.lien(this, "Bibliothèque de messages", String.valueOf(donnees.messages.size()),
                v -> startActivity(new Intent(this, MessagesActivity.class))));
        liens.addView(Ui.lien(this, "Profils", String.valueOf(donnees.profils.size()),
                v -> startActivity(new Intent(this, ProfilsActivity.class))));
        liens.addView(Ui.lien(this, "Badges NFC", String.valueOf(donnees.badges.size()),
                v -> startActivity(new Intent(this, NfcActivity.class))));
        liens.addView(Ui.lien(this, "Anti-triche", donnees.delaiAssouplissement == 0 && !donnees.nfcPourModifier
                ? "Libre" : "Activé", v -> startActivity(new Intent(this, AntiTricheActivity.class))));

        Ui.ajouter(c, Ui.petit(this, "Discipline " + BuildConfig.VERSION_NAME), 16);
    }

    private static org.json.JSONObject vacances(long jusqua) {
        try {
            return Donnees.changement("vacances", "").put("jusqua", jusqua);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
