package fr.discipline.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

/**
 * Écran montré quand une limite est atteinte : image, message, « de nouveau
 * disponible dans », compte à rebours de friction, badge NFC, rallonge.
 */
public class BlocageActivity extends Ecran {
    static final String EXTRA_PAQUET = "paquet";
    static final String EXTRA_LIMITE = "limite";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable minuterie;
    private String message;
    private Limite limite;
    private String paquet;

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent.hasExtra(EXTRA_LIMITE)) {
            setIntent(intent);
            message = null;
            rafraichir();
            return;
        }
        super.onNewIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        rafraichir();
    }

    @Override
    protected void onPause() {
        super.onPause();
        arreterMinuterie();
    }

    private void arreterMinuterie() {
        if (minuterie != null) {
            handler.removeCallbacks(minuterie);
            minuterie = null;
        }
    }

    @Override
    protected boolean ecouteNfc() {
        return true;
    }

    @Override
    protected void rafraichir() {
        arreterMinuterie();
        paquet = getIntent().getStringExtra(EXTRA_PAQUET);
        limite = donnees.limite(getIntent().getStringExtra(EXTRA_LIMITE));
        if (limite == null || paquet == null) {
            finish();
            return;
        }
        long maintenant = Horloge.maintenant();
        Moteur moteur = new Moteur(this);
        Moteur.Resultat r = moteur.evaluer(limite, maintenant);
        String appli = Applications.nom(this, paquet);

        LinearLayout c = page("", false);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setMinimumHeight(getResources().getDisplayMetrics().heightPixels - Ui.dp(this, 80));

        if (limite.image != null) {
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setMaxHeight(Ui.dp(this, 260));
            try {
                image.setImageURI(Uri.parse(limite.image));
                Ui.ajouter(c, image, 24);
            } catch (RuntimeException ignore) {
                // image devenue illisible
            }
        }

        int type = r.cause == null ? 0 : r.cause.type;
        TextView titre = Ui.ajouter(c, Ui.texte(this, "", 28, Ui.TEXTE, true), 32);
        titre.setGravity(Gravity.CENTER);
        TextView texte = Ui.ajouter(c, Ui.texte(this, "", 17, Ui.TEXTE2, false), 12);
        texte.setGravity(Gravity.CENTER);

        if (!r.bloque) {
            titre.setText(appli + " est de nouveau disponible");
            Ui.ajouter(c, Ui.boutonPlein(this, "Ouvrir " + appli), 32).setOnClickListener(v -> ouvrirAppli());
        } else if (type == Condition.FRICTION) {
            friction(c, titre, texte, r.cause, moteur, appli);
        } else if (type == Condition.NFC) {
            titre.setText("🔒 " + appli);
            texte.setText(donnees.badges.isEmpty()
                    ? "Aucun badge enregistré : ajoute-le dans ⚙ > Badges NFC."
                    : "Bipe ton badge contre le téléphone pour l’ouvrir.");
        } else {
            titre.setText("✋ " + appli);
            texte.setText(message(r, appli, maintenant));
            if (limite.afficherDispo && r.dispoA > maintenant) {
                TextView dispo = Ui.ajouter(c, Ui.texte(this, "", 20, Ui.VERT, true), 24);
                dispo.setGravity(Gravity.CENTER);
                long a = r.dispoA;
                minuterie = new Runnable() {
                    @Override
                    public void run() {
                        long reste = a - Horloge.maintenant();
                        if (reste <= 0) {
                            rafraichir();
                            return;
                        }
                        dispo.setText("De nouveau disponible dans " + Ui.duree(reste));
                        handler.postDelayed(this, 1000);
                    }
                };
                minuterie.run();
            }
            rallonge(c, moteur, maintenant);
        }

        Button fermer = Ui.ajouter(c, Ui.boutonDiscret(this, "Fermer"), 32);
        fermer.setOnClickListener(v -> fermer());
    }

    private String message(Moteur.Resultat r, String appli, long maintenant) {
        if (message == null) {
            message = limite.messages.isEmpty()
                    ? "Limite atteinte : {temps} passés aujourd’hui."
                    : limite.messages.get(new Random().nextInt(limite.messages.size()));
        }
        return message
                .replace("{appli}", appli)
                .replace("{temps}", Ui.duree(r.tempsDuJour))
                .replace("{ouvertures}", String.valueOf(r.ouverturesDuJour))
                .replace("{dispo}", r.dispoA > maintenant ? Ui.duree(r.dispoA - maintenant) : "—")
                .replace("{rallonges}", String.valueOf(new Moteur(this).rallongesRestantes(limite, maintenant)));
    }

    private void friction(LinearLayout c, TextView titre, TextView texte, Condition cause, Moteur moteur, String appli) {
        titre.setText("Respire.");
        texte.setText("Tu veux vraiment ouvrir " + appli + " ?");
        TextView compte = Ui.ajouter(c, Ui.texte(this, "", 56, Ui.TEXTE, true), 24);
        compte.setGravity(Gravity.CENTER);
        Button ouvrir = Ui.ajouter(c, Ui.boutonPlein(this, "Ouvrir " + appli), 24);
        ouvrir.setEnabled(false);
        ouvrir.setAlpha(0.4f);
        ouvrir.setOnClickListener(v -> {
            moteur.accorderPasse(cause, Horloge.maintenant());
            ouvrirAppli();
        });
        long fin = Horloge.maintenant() + cause.valeur * 1000L;
        minuterie = new Runnable() {
            @Override
            public void run() {
                long reste = fin - Horloge.maintenant();
                if (reste <= 0) {
                    compte.setText("✓");
                    ouvrir.setEnabled(true);
                    ouvrir.setAlpha(1f);
                    return;
                }
                compte.setText(String.valueOf((reste + 999) / 1000));
                handler.postDelayed(this, 250);
            }
        };
        minuterie.run();
    }

    private void rallonge(LinearLayout c, Moteur moteur, long maintenant) {
        if (limite.rallongeMinutes <= 0) {
            return;
        }
        int restantes = moteur.rallongesRestantes(limite, maintenant);
        if (restantes <= 0) {
            Ui.ajouter(c, Ui.petit(this, "Plus de rallonge possible pour cette période."), 24);
            return;
        }
        Button b = Ui.ajouter(c, Ui.bouton(this, "＋" + limite.rallongeMinutes + " min (encore " + restantes + ")", Ui.ORANGE), 24);
        b.setOnClickListener(v -> {
            b.setEnabled(false);
            attendre(b, limite.rallongeAttente, () -> {
                Runnable accorder = () -> {
                    moteur.accorderRallonge(limite, Horloge.maintenant());
                    ouvrirAppli();
                };
                if (limite.rallongeNfc) {
                    demanderBadge("La rallonge demande un bip du badge.", accorder);
                } else {
                    accorder.run();
                }
                b.setEnabled(true);
            });
        });
    }

    private void attendre(Button b, int secondes, Runnable suite) {
        if (secondes <= 0) {
            suite.run();
            return;
        }
        arreterMinuterie();
        long fin = Horloge.maintenant() + secondes * 1000L;
        CharSequence texte = b.getText();
        minuterie = new Runnable() {
            @Override
            public void run() {
                long reste = fin - Horloge.maintenant();
                if (reste <= 0) {
                    b.setText(texte);
                    suite.run();
                    return;
                }
                b.setText("Patiente " + ((reste + 999) / 1000) + " s…");
                handler.postDelayed(this, 250);
            }
        };
        minuterie.run();
    }

    @Override
    protected void badgeLu(String id) {
        if (!donnees.badges.containsKey(id)) {
            toast("Badge inconnu.");
            return;
        }
        boolean ouvreCelleCi = false;
        for (Condition c : limite != null ? limite.conditions : new java.util.ArrayList<Condition>()) {
            ouvreCelleCi |= Donnees.ouvre(c, id);
        }
        if (!ouvreCelleCi) {
            toast("Ce badge n’ouvre pas cette limite.");
            return;
        }
        donnees.debloquerParBadge(id, Horloge.maintenant());
        ouvrirAppli();
    }

    private void ouvrirAppli() {
        Intent i = getPackageManager().getLaunchIntentForPackage(paquet);
        finish();
        if (i != null) {
            startActivity(i);
        }
    }

    private void fermer() {
        finish();
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @Override
    public void onBackPressed() {
        fermer();
    }
}
