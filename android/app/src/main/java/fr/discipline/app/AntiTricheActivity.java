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

        boutonBas(c, "Enregistrer", v -> enregistrer());
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
