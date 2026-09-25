package fr.discipline.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Base des écrans : page sombre défilante avec titre, lecture des badges NFC
 * et « garde » anti-triche (badge exigé, assouplissements différés).
 */
public abstract class Ecran extends Activity {
    protected Donnees donnees;
    private ScrollView defilement;
    private LinearLayout racine;
    private LinearLayout entete;
    private NfcAdapter nfc;
    private Runnable apresBadge;
    private AlertDialog dialogueBadge;

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        donnees = Donnees.get(this);
        donnees.appliquerEnAttente();
        nfc = NfcAdapter.getDefaultAdapter(this);
        getWindow().setStatusBarColor(Ui.FOND);
        getWindow().setNavigationBarColor(Ui.FOND);
    }

    /** (Re)construit la page et renvoie la colonne où mettre le contenu ; la position de défilement est gardée. */
    protected LinearLayout page(String titre, boolean retour) {
        int position = 0;
        if (defilement == null) {
            defilement = new ScrollView(this);
            defilement.setBackgroundColor(Ui.FOND);
            defilement.setFillViewport(true);
            racine = Ui.colonne(this);
            int p = Ui.dp(this, 16);
            racine.setPadding(p, Ui.dp(this, 12), p, Ui.dp(this, 32));
            defilement.addView(racine);
            setContentView(defilement);
        } else {
            position = defilement.getScrollY();
            racine.removeAllViews();
        }
        entete = Ui.rangee(this);
        if (retour) {
            TextView fleche = Ui.texte(this, "←", 24, Ui.TEXTE, false);
            fleche.setPadding(0, Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4));
            fleche.setOnClickListener(v -> finish());
            entete.addView(fleche);
        }
        Ui.etirer(entete, Ui.texte(this, titre, 24, Ui.TEXTE, true));
        Ui.ajouter(racine, entete, 0);
        final int y = position;
        defilement.post(() -> defilement.scrollTo(0, y));
        return racine;
    }

    /** Ajoute un bouton à droite du titre. */
    protected TextView actionEntete(String texte, View.OnClickListener clic) {
        TextView v = Ui.texte(this, texte, 16, Ui.VERT, true);
        v.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), 0, Ui.dp(this, 8));
        v.setOnClickListener(clic);
        entete.addView(v);
        return v;
    }

    /** Bouton principal pleine largeur. */
    protected Button boutonBas(LinearLayout parent, String texte, View.OnClickListener clic) {
        Button b = Ui.ajouter(parent, Ui.boutonPlein(this, texte), 16);
        b.setOnClickListener(clic);
        return b;
    }

    protected void toast(String texte) {
        Toast.makeText(this, texte, Toast.LENGTH_LONG).show();
    }

    /** Reconstruire l'écran après un changement. */
    protected void rafraichir() {
    }

    /** Vrai pour les écrans qui lisent les badges en permanence. */
    protected boolean ecouteNfc() {
        return false;
    }

    /** Un badge a été bipé (hors demande de confirmation). */
    protected void badgeLu(String id) {
    }

    @Override
    protected void onResume() {
        super.onResume();
        activerNfc();
    }

    private void activerNfc() {
        if (nfc == null || (!ecouteNfc() && apresBadge == null)) {
            return;
        }
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        try {
            nfc.enableForegroundDispatch(this, pi, null, null);
        } catch (IllegalStateException ignore) {
            // activité pas encore au premier plan
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfc != null) {
            try {
                nfc.disableForegroundDispatch(this);
            } catch (IllegalStateException ignore) {
                // déjà désactivé
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String id = idBadge(intent);
        if (id == null) {
            return;
        }
        if (apresBadge != null) {
            if (donnees.badges.containsKey(id)) {
                Runnable suite = apresBadge;
                apresBadge = null;
                if (dialogueBadge != null) {
                    dialogueBadge.dismiss();
                }
                suite.run();
            } else {
                toast("Badge inconnu.");
            }
            return;
        }
        badgeLu(id);
    }

    static String idBadge(Intent intent) {
        Tag tag = intent == null ? null : intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) {
            return null;
        }
        StringBuilder texte = new StringBuilder();
        for (byte octet : tag.getId()) {
            texte.append(String.format(Locale.ROOT, "%02x", octet));
        }
        return texte.toString();
    }

    /** Demande de biper un badge enregistré avant de continuer. */
    protected void demanderBadge(String message, Runnable suite) {
        if (nfc == null || donnees.badges.isEmpty()) {
            suite.run();
            return;
        }
        apresBadge = suite;
        dialogueBadge = new AlertDialog.Builder(this)
                .setTitle("Bipe ton badge")
                .setMessage(message)
                .setNegativeButton("Annuler", (d, w) -> apresBadge = null)
                .setOnCancelListener(d -> apresBadge = null)
                .show();
        activerNfc();
    }

    /**
     * Applique un changement qui peut assouplir les règles : badge exigé si
     * l'anti-triche le demande, puis tout de suite ou après le délai réglé.
     */
    protected void garde(JSONObject changement, String texte, Runnable apres) {
        Runnable suite = () -> {
            if (donnees.delaiAssouplissement > 0 && !donnees.confianceCodes.isEmpty()) {
                proposerConfiance(changement, texte, apres);
            } else {
                finirGarde(changement, texte, apres, donnees.delaiAssouplissement > 0);
            }
        };
        if (donnees.nfcPourModifier) {
            demanderBadge("L’anti-triche demande le badge pour : " + texte + ".", suite);
        } else {
            suite.run();
        }
    }

    private void finirGarde(JSONObject changement, String texte, Runnable apres, boolean attendre) {
        if (attendre) {
            donnees.differer(changement, texte);
            toast("Anti-triche : « " + texte + " » sera appliqué dans "
                    + Ui.duree(donnees.delaiAssouplissement * 60_000L) + ".");
        } else {
            donnees.appliquer(changement);
        }
        if (apres != null) {
            apres.run();
        }
        rafraichir();
    }

    /** Délai à attendre, ou un code de la personne de confiance pour passer tout de suite. */
    private void proposerConfiance(JSONObject changement, String texte, Runnable apres) {
        String nom = donnees.confianceNom;
        new AlertDialog.Builder(this)
                .setTitle("Anti-triche")
                .setMessage("« " + texte + " » attendra " + Ui.duree(donnees.delaiAssouplissement * 60_000L)
                        + ". Avec un code de " + nom + ", c’est tout de suite.")
                .setPositiveButton("Code de " + nom, (d, w) -> Choix.texte(this, "Code de " + nom, "", "6 chiffres",
                        android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                        code -> {
                            if (Confiance.utiliser(donnees, code)) {
                                toast("Code accepté ; il ne resservira pas.");
                                finirGarde(changement, texte, apres, false);
                            } else {
                                toast("Ce code n’est pas (ou plus) valable.");
                            }
                        }))
                .setNeutralButton(donnees.confianceNumero.isEmpty() ? "Attendre" : "Écrire à " + nom, (d, w) -> {
                    if (donnees.confianceNumero.isEmpty()) {
                        finirGarde(changement, texte, apres, true);
                        return;
                    }
                    Confiance.sms(this, donnees.confianceNumero, "Discipline : je voudrais " + texte
                            + ". Si tu es d’accord, envoie-moi un de tes codes.");
                    // au retour, la question attend le code
                    proposerConfiance(changement, texte, apres);
                })
                .setNegativeButton(donnees.confianceNumero.isEmpty() ? "Annuler" : "Attendre", (d, w) -> {
                    if (!donnees.confianceNumero.isEmpty()) {
                        finirGarde(changement, texte, apres, true);
                    }
                })
                .show();
    }

    /** Passer à un profil. Éteindre une limite active assouplit : anti-triche dans ce cas. */
    protected void activerProfil(Donnees.Profil p, Runnable apres) {
        boolean assouplit = false;
        for (Limite l : donnees.limites) {
            if (l.active && !p.limites.contains(l.id)) {
                assouplit = true;
            }
        }
        if (assouplit) {
            garde(Donnees.changement("profil", p.id), "passer au profil « " + p.nom + " »", apres);
        } else {
            donnees.appliquer(Donnees.changement("profil", p.id));
            if (apres != null) {
                apres.run();
            }
            rafraichir();
        }
    }

    static Donnees.Groupe copieGroupe(Donnees.Groupe g) {
        try {
            return Donnees.Groupe.de(g.json());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static JSONObject changementGroupe(Donnees.Groupe g, boolean suppression) {
        try {
            JSONObject ch = Donnees.changement("groupe", g.id);
            return suppression ? ch : ch.put("groupe", g.json());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Retirer des applis d'un groupe utilisé par une limite active assouplit : anti-triche. */
    void modifierGroupe(Donnees.Groupe g, boolean suppression, Runnable apres) {
        JSONObject ch = changementGroupe(g, suppression);
        Donnees.Groupe ancien = donnees.groupes.get(g.id);
        boolean assouplit = ancien != null && donnees.groupeUtilise(g.id)
                && (suppression || !g.paquets.containsAll(ancien.paquets));
        if (assouplit) {
            garde(ch, (suppression ? "supprimer" : "retirer des applis de") + " « " + ancien.nom + " »", apres);
        } else {
            donnees.appliquer(ch);
            if (apres != null) {
                apres.run();
            }
            rafraichir();
        }
    }
}
