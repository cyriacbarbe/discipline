package fr.discipline.app;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import org.json.JSONObject;

/** Anti-triche : « Libre » par défaut ; durcir est immédiat, assouplir peut attendre ou demander le badge. */
public class AntiTricheActivity extends Ecran {
    private int delai;
    private boolean nfc;
    private boolean alerte;
    private boolean strict;

    @Override
    protected void onResume() {
        super.onResume();
        delai = donnees.delaiAssouplissement;
        nfc = donnees.nfcPourModifier;
        alerte = donnees.alerteAccessibilite;
        strict = donnees.modeStrict;
        rafraichir();
    }

    @Override
    protected void rafraichir() {
        LinearLayout c = page("Anti-triche", true);
        boolean libre = delai == 0 && !nfc && !alerte;
        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 12);
        carte.addView(Ui.texte(this, libre ? "Libre" : "Protégé", 20, libre ? Ui.TEXTE2 : Ui.VERT, true));
        carte.addView(Ui.petit(this, "Durcir une règle est toujours immédiat. Ces réglages ne freinent que "
                + "les assouplissements : relâcher, désactiver ou supprimer une limite active, allonger la tolérance, "
                + "ajouter un badge, importer un réglage, partir en vacances…"));

        LinearLayout reglages = Ui.ajouter(c, Ui.carte(this), 8);
        reglages.addView(Ui.lien(this, "Délai avant d’assouplir", delai == 0 ? "aucun" : Ui.duree(delai * 60_000L),
                v -> Choix.nombre(this, "Délai avant d’assouplir (min, 0 = aucun)", delai, x -> {
                    if (x >= 0) {
                        delai = x;
                    }
                    rafraichir();
                })));
        CheckBox caseNfc = Ui.caseACocher(this, "Exiger le badge NFC pour modifier", nfc);
        caseNfc.setOnCheckedChangeListener((b, coche) -> {
            if (coche && donnees.badges.isEmpty()) {
                b.setChecked(false);
                toast("Enregistre d’abord un badge dans ⚙ > Badges NFC.");
                return;
            }
            nfc = coche;
        });
        reglages.addView(caseNfc);
        CheckBox caseAlerte = Ui.caseACocher(this, "M’alerter si l’accessibilité est coupée", alerte);
        caseAlerte.setOnCheckedChangeListener((b, coche) -> {
            alerte = coche;
            if (coche && Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        });
        reglages.addView(caseAlerte);

        Ui.ajouter(c, Ui.section(this, "Protéger Discipline elle-même"), 16);
        LinearLayout protection = Ui.ajouter(c, Ui.carte(this), 8);
        CheckBox caseStrict = Ui.caseACocher(this, "Mode strict", strict);
        caseStrict.setOnCheckedChangeListener((b, coche) -> strict = coche);
        protection.addView(caseStrict);
        protection.addView(Ui.petit(this, "Les pages des Réglages d’Android qui permettraient d’arrêter Discipline "
                + "(forcer l’arrêt, désinstaller, effacer les données, couper l’accessibilité, retirer l’administrateur) "
                + "se referment d’elles-mêmes. Pour le quitter, il faut repasser ici, avec le délai ou le badge."));
        DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
        boolean adminActif = dpm.isAdminActive(Admin.composant(this));
        protection.addView(Ui.lien(this, "Administrateur de l’appareil", adminActif ? "actif ✓" : "à activer", v -> {
            if (adminActif) {
                toast("Déjà actif. Il se retire dans Réglages > Sécurité > Applis d’administration (fermé en mode strict).");
                return;
            }
            startActivity(new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Admin.composant(this))
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Empêche de désinstaller Discipline sur un coup de tête. Aucun autre pouvoir."));
        }));
        protection.addView(Ui.petit(this, "Tant qu’il est actif, Android refuse de désinstaller Discipline."));
        protection.addView(Ui.lien(this, "Accès aux notifications", Notifications.autorise(this) ? "autorisé ✓" : "à autoriser",
                v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        protection.addView(Ui.petit(this, "Sert à couper le son d’une appli bloquée qui joue en arrière-plan, "
                + "et à mettre ses notifications en sourdine si la limite le demande."));
        protection.addView(Ui.petit(this, "Ensemble, c’est ce qu’on peut faire de mieux sans outil d’entreprise. "
                + "Reste possible : redémarrer en mode sans échec (appui long sur « Éteindre ») ou réinitialiser le "
                + "téléphone — assez pénible pour décourager un moment de faiblesse."));

        confiance(c);

        boutonBas(c, "Enregistrer", v -> enregistrer());
    }

    // ---- Personne de confiance -------------------------------------------

    private void confiance(LinearLayout c) {
        Ui.ajouter(c, Ui.section(this, "Personne de confiance"), 16);
        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 8);
        if (donnees.confianceNom.isEmpty()) {
            carte.addView(Ui.petit(this, "Quelqu’un qui garde dix codes à usage unique. Quand l’anti-triche fait "
                    + "attendre, un de ses codes permet de passer tout de suite : il faut lui demander, et lui "
                    + "dire pourquoi. Il reçoit aussi ton bilan de la semaine si tu veux."));
            carte.addView(Ui.lien(this, "＋ Choisir quelqu’un", null, v -> choisirPersonne()));
            return;
        }
        LinearLayout r = Ui.ajouter(carte, Ui.rangee(this), 0);
        Ui.etirer(r, Ui.corps(this, donnees.confianceNom
                + (donnees.confianceNumero.isEmpty() ? "" : " · " + donnees.confianceNumero)));
        r.addView(Ui.croix(this, v -> {
            // retirer la personne ne fait que durcir : plus de raccourci au délai
            donnees.appliquer(Donnees.changement("confiance", ""));
            rafraichir();
        }));
        int reste = donnees.confianceCodes.size();
        carte.addView(Ui.petit(this, reste == 0 ? "Plus aucun code : à renouveler."
                : reste + " code(s) encore valable(s)."));
        carte.addView(Ui.lien(this, "Renouveler les codes", null,
                v -> nouveauxCodes(donnees.confianceNom, donnees.confianceNumero)));
        if (donnees.delaiAssouplissement == 0) {
            carte.addView(Ui.petit(this, "Sans délai avant d’assouplir, les codes ne servent à rien : règle un délai "
                    + "ci-dessus."));
        }
    }

    private void choisirPersonne() {
        Choix.texte(this, "Son prénom", "", "Paul", nom -> {
            if (nom.trim().isEmpty()) {
                return;
            }
            Choix.texte(this, "Son numéro (vide = pas de SMS)", "", "06…", android.text.InputType.TYPE_CLASS_PHONE,
                    numero -> nouveauxCodes(nom.trim(), numero.trim()));
        });
    }

    /** Nouveaux codes : les anciens ne valent plus. Ça ouvre un raccourci au délai, donc anti-triche. */
    private void nouveauxCodes(String nom, String numero) {
        java.util.List<String> codes = Confiance.nouveauxCodes();
        org.json.JSONArray haches = new org.json.JSONArray();
        StringBuilder liste = new StringBuilder();
        for (String code : codes) {
            haches.put(Confiance.hacher(code));
            liste.append(code).append("\n");
        }
        JSONObject ch;
        try {
            ch = Donnees.changement("confiance", "").put("nom", nom).put("numero", numero).put("codes", haches);
        } catch (Exception e) {
            return;
        }
        garde(ch, "confier des codes à " + nom, () -> montrerCodes(nom, numero, liste.toString().trim()));
    }

    private void montrerCodes(String nom, String numero, String codes) {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this)
                .setTitle("Les codes de " + nom)
                .setMessage("Montre cet écran à " + nom + " (qu’il le prenne en photo) ou envoie-les-lui. Ils ne "
                        + "seront plus jamais affichés, et chacun ne sert qu’une fois.\n\n" + codes)
                .setPositiveButton("C’est fait", null);
        if (!numero.isEmpty()) {
            b.setNeutralButton("Envoyer par SMS", (d, w) -> Confiance.sms(this, numero,
                    "Discipline : garde ces codes pour moi, et ne m’en donne un que si ma raison te paraît bonne.\n"
                            + codes));
        }
        b.setCancelable(false).show();
    }

    private void enregistrer() {
        JSONObject ch;
        try {
            ch = Donnees.changement("antitriche", "").put("delai", delai).put("nfc", nfc).put("alerte", alerte)
                    .put("strict", strict);
        } catch (Exception e) {
            return;
        }
        boolean assouplit = delai < donnees.delaiAssouplissement
                || (!nfc && donnees.nfcPourModifier)
                || (!alerte && donnees.alerteAccessibilite)
                || (!strict && donnees.modeStrict);
        Runnable fin = () -> {
            Surveillance.planifier(this);
            finish();
        };
        if (assouplit) {
            garde(ch, "assouplir l’anti-triche", fin);
        } else {
            donnees.appliquer(ch);
            fin.run();
        }
    }
}
