package fr.discipline.app;

import android.content.Intent;
import android.net.Uri;
import android.widget.LinearLayout;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Paramètres : tolérance, début de journée, « Suivies », vacances et liens vers les sous-pages. */
public class ParametresActivity extends Ecran {
    private static final int EXPORTER = 41;
    private static final int IMPORTER = 42;
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

        LinearLayout sauvegarde = Ui.ajouter(c, Ui.carte(this), 8);
        sauvegarde.addView(Ui.lien(this, "Exporter le réglage", "", v -> startActivityForResult(
                new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                        .putExtra(Intent.EXTRA_TITLE, "discipline-reglage-" + donnees.cleJour(Horloge.maintenant()) + ".json"),
                EXPORTER)));
        sauvegarde.addView(Ui.lien(this, "Importer un réglage", "", v -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), IMPORTER)));
        sauvegarde.addView(Ui.petit(this, "Limites, groupes, profils, messages, badges et réglages, dans un fichier. "
                + "Importer remplace tout le réglage (l’anti-triche s’applique) ; l’historique d’usage n’est pas touché."));

        Ui.ajouter(c, Ui.petit(this, "Discipline " + BuildConfig.VERSION_NAME), 16);
    }

    @Override
    protected void onActivityResult(int requete, int resultat, Intent data) {
        super.onActivityResult(requete, resultat, data);
        Uri fichier = data == null ? null : data.getData();
        if (resultat != RESULT_OK || fichier == null) {
            return;
        }
        if (requete == EXPORTER) {
            try (OutputStream sortie = getContentResolver().openOutputStream(fichier)) {
                sortie.write(donnees.exporter().toString(2).getBytes(StandardCharsets.UTF_8));
                toast("Réglage exporté.");
            } catch (Exception e) {
                toast("Export impossible : " + e.getMessage());
            }
        } else if (requete == IMPORTER) {
            try (InputStream entree = getContentResolver().openInputStream(fichier)) {
                ByteArrayOutputStream tampon = new ByteArrayOutputStream();
                byte[] morceau = new byte[8192];
                for (int n; (n = entree.read(morceau)) > 0; ) {
                    tampon.write(morceau, 0, n);
                }
                JSONObject reglages = new JSONObject(tampon.toString("UTF-8"));
                if (!reglages.has("limites")) {
                    toast("Ce fichier n’est pas un réglage de Discipline.");
                    return;
                }
                garde(Donnees.changement("import", "").put("reglages", reglages),
                        "importer un réglage (" + reglages.getJSONArray("limites").length() + " limites)", null);
            } catch (Exception e) {
                toast("Import impossible : " + e.getMessage());
            }
        }
    }

    private static org.json.JSONObject vacances(long jusqua) {
        try {
            return Donnees.changement("vacances", "").put("jusqua", jusqua);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
