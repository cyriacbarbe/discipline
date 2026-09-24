package fr.discipline.app;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Map;

/**
 * Badges NFC : les enregistrer et les nommer. Ouverte par un bip hors de
 * l'appli, elle débloque les limites « badge NFC » puis se referme.
 */
public class NfcActivity extends Ecran {
    private boolean enregistrement;

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        String id = idBadge(getIntent());
        if (id != null) {
            if (donnees.badges.containsKey(id)) {
                int n = donnees.debloquerParBadge(id, Horloge.maintenant());
                Toast.makeText(this, n > 0 ? "🔓 « " + donnees.badges.get(id) + " » : " + n + " limite(s) débloquée(s)"
                        : "Aucune limite à débloquer par badge.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Badge inconnu.", Toast.LENGTH_LONG).show();
            }
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        rafraichir();
    }

    @Override
    protected boolean ecouteNfc() {
        return true;
    }

    @Override
    protected void rafraichir() {
        if (isFinishing()) {
            return;
        }
        LinearLayout c = page("Badges NFC", true);
        if (NfcAdapter.getDefaultAdapter(this) == null) {
            Ui.ajouter(c, Ui.texte(this, "Ce téléphone n’a pas de NFC.", 15, Ui.ORANGE, false), 12);
        }
        Ui.ajouter(c, Ui.petit(this, "Chaque badge ne débloque que les limites « Badge NFC » qui l’acceptent "
                + "(une limite sans badge attitré accepte n’importe lequel), et sert de clé quand l’anti-triche l’exige. "
                + "Ajouter un badge alors qu’une limite accepte n’importe lequel passe par l’anti-triche."), 8);
        for (Map.Entry<String, String> b : new ArrayList<>(donnees.badges.entrySet())) {
            LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 10);
            LinearLayout r = Ui.rangee(this);
            carte.addView(r);
            LinearLayout textes = Ui.etirer(r, Ui.colonne(this));
            textes.addView(Ui.texte(this, b.getValue(), 17, Ui.TEXTE, true));
            textes.addView(Ui.petit(this, b.getKey()));
            r.addView(Ui.croix(this, v -> supprimer(b.getKey())));
            carte.setOnClickListener(v -> Choix.texte(this, "Nom du badge", b.getValue(), "", t -> {
                if (!t.isEmpty()) {
                    donnees.badges.put(b.getKey(), t);
                    donnees.enregistrer();
                }
                rafraichir();
            }));
        }
        if (enregistrement) {
            LinearLayout attente = Ui.ajouter(c, Ui.carte(this), 16);
            attente.addView(Ui.texte(this, "📶 Approche le badge du téléphone…", 17, Ui.VERT, true));
            Ui.ajouter(attente, Ui.boutonDiscret(this, "Annuler"), 10).setOnClickListener(v -> {
                enregistrement = false;
                rafraichir();
            });
        } else {
            boutonBas(c, "＋ Enregistrer un badge", v -> {
                enregistrement = true;
                rafraichir();
            });
        }
    }

    private void supprimer(String id) {
        Runnable suite = () -> {
            donnees.badges.remove(id);
            if (donnees.badges.isEmpty()) {
                donnees.nfcPourModifier = false;
            }
            donnees.enregistrer();
            rafraichir();
        };
        if (donnees.nfcPourModifier) {
            demanderBadge("L’anti-triche demande le badge pour en retirer un.", suite);
        } else {
            suite.run();
        }
    }

    @Override
    protected void badgeLu(String id) {
        if (!enregistrement) {
            if (donnees.badges.containsKey(id)) {
                int n = donnees.debloquerParBadge(id, Horloge.maintenant());
                toast("🔓 " + n + " limite(s) débloquée(s).");
            } else {
                toast("Badge inconnu : touche « Enregistrer un badge » d’abord.");
            }
            return;
        }
        enregistrement = false;
        if (donnees.badges.containsKey(id)) {
            toast("Ce badge est déjà enregistré.");
            rafraichir();
            return;
        }
        Choix.texte(this, "Nom du badge", "Badge " + (donnees.badges.size() + 1), "", t -> {
            String nom = t.isEmpty() ? "Badge" : t;
            if (donnees.badgeQuelconqueAccepte()) {
                // Un nouveau badge ouvrirait des limites « n'importe quel badge » : c'est un assouplissement.
                try {
                    garde(Donnees.changement("badge", id).put("nom", nom), "ajouter le badge « " + nom + " »", null);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return;
            }
            donnees.badges.put(id, nom);
            donnees.enregistrer();
            rafraichir();
        });
        rafraichir();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        super.onNewIntent(intent);
    }
}
