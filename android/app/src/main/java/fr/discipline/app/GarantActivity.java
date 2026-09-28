package fr.discipline.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.LinearLayout;

import org.json.JSONObject;

import java.util.List;

/**
 * Garants : les inviter, les retirer (avec l'accord de l'un d'eux), une pause
 * qu'ils accordent, les demandes en attente. Ouverte aussi par le lien de leur
 * réponse (discipline://garant?r=…), qui applique l'accord.
 */
public class GarantActivity extends Ecran {
    private static final String[] PAUSES = {"1 heure", "3 heures", "1 jour", "3 jours", "1 semaine"};
    private static final int[] PAUSES_MINUTES = {60, 180, 1440, 4320, 10080};

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        if (etat == null) {
            lireLien(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        lireLien(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        rafraichir();
    }

    private void lireLien(Intent intent) {
        Uri u = intent == null ? null : intent.getData();
        if (u != null && u.getQueryParameter("r") != null) {
            montrerReponse(Garant.recevoir(donnees, u.getQueryParameter("r")));
        }
    }

    private void montrerReponse(String message) {
        new AlertDialog.Builder(this).setTitle("Garant").setMessage(message).setPositiveButton("OK", null).show();
        rafraichir();
    }

    @Override
    protected void rafraichir() {
        LinearLayout c = page("Garants", true);
        List<JSONObject> garants = Garant.garants(donnees);
        List<JSONObject> invites = Garant.liste(donnees, "invites");

        LinearLayout intro = Ui.ajouter(c, Ui.carte(this), 12);
        intro.addView(Ui.petit(this, "Un garant valide chaque assouplissement : désactiver, alléger ou supprimer une "
                + "limite, changer de profil, les vacances… Il reçoit un SMS avec un lien, lit ce que tu demandes et "
                + "accepte ou refuse. Sa réponse revient par SMS : un appui sur son lien et c’est appliqué. Durcir "
                + "reste libre. Tant qu’un garant est là, le délai et les codes de confiance ne servent plus."));
        intro.addView(Ui.petit(this, "Sans réponse, rien ne s’assouplit. Plusieurs garants : un seul oui suffit."));

        JSONObject moi = Garant.moi(donnees);
        LinearLayout carteMoi = Ui.ajouter(c, Ui.carte(this), 8);
        carteMoi.addView(Ui.lien(this, "Toi", moi.optString("nom").isEmpty() ? "à remplir"
                : moi.optString("nom") + " · " + moi.optString("numero"), v -> demanderMoi(null)));
        carteMoi.addView(Ui.petit(this, "Ton prénom et ton numéro figurent dans les liens : la réponse du garant "
                + "revient à ce numéro."));

        Ui.ajouter(c, Ui.section(this, "Tes garants"), 16);
        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 8);
        if (garants.isEmpty() && invites.isEmpty()) {
            carte.addView(Ui.petit(this, "Personne pour l’instant."));
        }
        for (JSONObject g : garants) {
            LinearLayout r = Ui.ajouter(carte, Ui.rangee(this), 4);
            Ui.etirer(r, Ui.corps(this, g.optString("nom") + " · " + g.optString("numero")));
            r.addView(Ui.croix(this, v -> demanderGarant(Donnees.changement("garant-retirer", g.optString("id")),
                    "retirer " + g.optString("nom") + " des garants", null)));
            if (!g.optString("cleSuivante").isEmpty()) {
                carte.addView(Ui.petit(this, "Nouvelle clé en service dans "
                        + Ui.duree(g.optLong("cleA") - Horloge.maintenant()) + "."));
            }
            carte.addView(Ui.lien(this, "Clé perdue ? Renvoyer une invitation", null,
                    v -> Garant.inviter(this, donnees, g)));
        }
        for (JSONObject inv : invites) {
            LinearLayout r = Ui.ajouter(carte, Ui.rangee(this), 4);
            Ui.etirer(r, Ui.corps(this, inv.optString("nom") + " · pas encore accepté"));
            r.addView(Ui.croix(this, v -> {
                // une invitation sans réponse n'a aucun pouvoir : l'annuler est libre
                donnees.appliquer(Donnees.changement("garant-retirer", inv.optString("id")));
                rafraichir();
            }));
            carte.addView(Ui.lien(this, "Envoyer l’invitation à " + inv.optString("nom"), null,
                    v -> Garant.inviter(this, donnees, inv)));
        }
        Ui.ajouter(carte, Ui.lien(this, "＋ Ajouter un garant", null, v -> ajouter()), 8);

        if (!garants.isEmpty()) {
            Ui.ajouter(c, Ui.section(this, "Pause"), 16);
            LinearLayout pause = Ui.ajouter(c, Ui.carte(this), 8);
            long fin = Garant.pause(donnees);
            if (fin > Horloge.maintenant()) {
                pause.addView(Ui.corps(this, "Sans garant encore " + Ui.duree(fin - Horloge.maintenant()) + "."));
                pause.addView(Ui.petit(this, "Pendant la pause, les assouplissements passent comme avant (délai, "
                        + "badge). Retirer ou ajouter un garant demande toujours son accord."));
            } else {
                pause.addView(Ui.lien(this, "Demander une pause sans garant", null, v -> choisirPause()));
                pause.addView(Ui.petit(this, "Pour faire beaucoup de modifications d’un coup : le garant l’accorde, "
                        + "puis tout est libre le temps choisi."));
            }
        }

        List<JSONObject> demandes = Garant.demandes(donnees);
        Ui.ajouter(c, Ui.section(this, "Demandes en attente"), 16);
        LinearLayout att = Ui.ajouter(c, Ui.carte(this), 8);
        if (demandes.isEmpty()) {
            att.addView(Ui.petit(this, "Aucune."));
        }
        for (JSONObject dm : demandes) {
            LinearLayout r = Ui.ajouter(att, Ui.rangee(this), 4);
            Ui.etirer(r, Ui.corps(this, dm.optString("texte")));
            r.addView(Ui.croix(this, v -> {
                Garant.retirer(donnees, "demandes", dm.optString("id"));
                donnees.enregistrer();
                rafraichir();
            }));
            att.addView(Ui.petit(this, "Envoyée il y a " + Ui.duree(Horloge.maintenant() - dm.optLong("t"))
                    + ", valable " + Ui.duree(Garant.VALIDITE) + "."));
            if (!garants.isEmpty()) {
                att.addView(Ui.lien(this, "Relancer", null, v -> relancer(dm, garants)));
            }
        }
        Ui.ajouter(att, Ui.lien(this, "Coller une réponse reçue", null, v -> Choix.texte(this,
                "Colle le SMS du garant", "", "…#r=…", r -> montrerReponse(Garant.recevoir(donnees,
                        Garant.extraire(r))))), 8);
        att.addView(Ui.petit(this, "Si le lien de sa réponse n’ouvre pas Discipline, copie le message et colle-le ici."));
    }

    private void relancer(JSONObject demande, List<JSONObject> garants) {
        String[] noms = new String[garants.size()];
        for (int i = 0; i < noms.length; i++) {
            noms[i] = "Écrire à " + garants.get(i).optString("nom");
        }
        new AlertDialog.Builder(this).setTitle("Relancer")
                .setItems(noms, (d, i) -> Garant.demander(this, donnees, demande, garants.get(i)))
                .setNegativeButton("Annuler", null).show();
    }

    private void choisirPause() {
        new AlertDialog.Builder(this).setTitle("Pause sans garant")
                .setItems(PAUSES, (d, i) -> {
                    JSONObject ch = Donnees.changement("garant-pause", "");
                    try {
                        ch.put("minutes", PAUSES_MINUTES[i]);
                    } catch (Exception ignore) {
                        // clé non nulle
                    }
                    demanderGarant(ch, "une pause de " + PAUSES[i] + " sans garant", null);
                })
                .setNegativeButton("Annuler", null).show();
    }

    /** Ton prénom et ton numéro, puis la suite (ajout d'un garant) s'il y en a une. */
    private void demanderMoi(Runnable suite) {
        JSONObject moi = Garant.moi(donnees);
        Choix.texte(this, "Ton prénom", moi.optString("nom"), "Cyriac", nom -> {
            if (nom.trim().isEmpty()) {
                return;
            }
            Choix.texte(this, "Ton numéro", moi.optString("numero"), "06…", InputType.TYPE_CLASS_PHONE, numero -> {
                if (numero.trim().isEmpty()) {
                    return;
                }
                try {
                    donnees.garant.put("moi", new JSONObject().put("nom", nom.trim())
                            .put("numero", numero.replaceAll("[^0-9+]", "")));
                } catch (Exception ignore) {
                    // clés non nulles
                }
                donnees.enregistrer();
                rafraichir();
                if (suite != null) {
                    suite.run();
                }
            });
        });
    }

    /**
     * Le premier garant s'ajoute librement. Les suivants demandent l'accord d'un
     * garant en place : sinon, s'ajouter soi-même comme garant suffirait à tout ouvrir.
     */
    private void ajouter() {
        if (Garant.moi(donnees).optString("numero").isEmpty()) {
            demanderMoi(this::ajouter);
            return;
        }
        Choix.texte(this, "Prénom du garant", "", "Paul", nom -> {
            if (nom.trim().isEmpty()) {
                return;
            }
            Choix.texte(this, "Son numéro", "", "06…", InputType.TYPE_CLASS_PHONE, numero -> {
                if (numero.trim().isEmpty()) {
                    return;
                }
                JSONObject ch = Donnees.changement("garant-inviter", Garant.nouvelId());
                try {
                    ch.put("nom", nom.trim()).put("numero", numero.replaceAll("[^0-9+]", ""));
                } catch (Exception ignore) {
                    // clés non nulles
                }
                if (Garant.garants(donnees).isEmpty()) {
                    donnees.appliquer(ch);
                    Garant.inviter(this, donnees, Garant.trouver(donnees, "invites", ch.optString("id")));
                    rafraichir();
                } else {
                    demanderGarant(ch, "ajouter " + nom.trim() + " comme garant", null);
                }
            });
        });
    }
}
