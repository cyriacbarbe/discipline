package fr.discipline.app;

import android.Manifest;
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

    @Override
    protected void onResume() {
        super.onResume();
        delai = donnees.delaiAssouplissement;
        nfc = donnees.nfcPourModifier;
        alerte = donnees.alerteAccessibilite;
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
        CheckBox admin = Ui.caseACocher(this, "Administrateur de l’appareil (bientôt)", false);
        admin.setEnabled(false);
        admin.setAlpha(0.5f);
        reglages.addView(admin);

        boutonBas(c, "Enregistrer", v -> enregistrer());
    }

    private void enregistrer() {
        JSONObject ch;
        try {
            ch = Donnees.changement("antitriche", "").put("delai", delai).put("nfc", nfc).put("alerte", alerte);
        } catch (Exception e) {
            return;
        }
        boolean assouplit = delai < donnees.delaiAssouplissement
                || (!nfc && donnees.nfcPourModifier)
                || (!alerte && donnees.alerteAccessibilite);
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
