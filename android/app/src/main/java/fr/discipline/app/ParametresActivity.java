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
        long maintenant = Horloge.maintenant();

        LinearLayout temps = Ui.ajouter(c, Ui.carte(this), 12);
        temps.addView(Ui.lien(this, "Tolérance de session", donnees.toleranceSecondes + " s",
                v -> Choix.nombre(this, "Tolérance de session (s, 30 au plus)", donnees.toleranceSecondes, x -> {
                    if (x < 0 || x == donnees.toleranceSecondes) {
                        rafraichir();
                    } else if (x > 30) {
                        toast("30 s au plus : au-delà, on sortirait et reviendrait sans jamais ouvrir de nouvelle session.");
                    } else if (x < donnees.toleranceSecondes) {
                        donnees.toleranceSecondes = x;
                        donnees.enregistrer();
                        rafraichir();
                    } else {
                        try {
                            garde(Donnees.changement("tolerance", "tolerance").put("valeur", x),
                                    "passer la tolérance à " + x + " s", null);
                        } catch (Exception ignore) {
                            // JSON simple, n'échoue pas
                        }
                    }
                })));
        temps.addView(Ui.petit(this, "Revenir sur une appli moins de ce temps après l’avoir quittée (écran éteint compris) "
                + "continue la même session ; au-delà, c’est une nouvelle ouverture."));
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
