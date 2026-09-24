package fr.discipline.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
            friction(c, titre, texte, r.cause, moteur, appli, r.ouverturesDuJour + 1);
        } else if (type == Condition.NFC) {
            titre.setText("🔒 " + appli);
            texte.setText(donnees.badges.isEmpty()
                    ? "Aucun badge enregistré : ajoute-le dans ⚙ > Badges NFC."
                    : "Bipe ton badge contre le téléphone pour l’ouvrir.");
        } else {
            titre.setText("✋ " + appli);
            texte.setText(limite.messages.isEmpty() ? constat(r, appli, maintenant) : message(r, appli, maintenant));
            if (!limite.messages.isEmpty()) {
                Ui.ajouter(c, Ui.texte(this, constat(r, appli, maintenant), 15, Ui.TEXTE2, false), 10)
                        .setGravity(Gravity.CENTER);
            }
            historique(c, r, moteur, maintenant);
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

    /** Pourquoi c'est fermé, dit avec les chiffres de la règle choisie. */
    private String constat(Moteur.Resultat r, String appli, long maintenant) {
        Condition c = r.cause;
        if (c == null) {
            return "Limite atteinte.";
        }
        int n = c.valeurDuJour(maintenant);
        String periode = c.periode.enCours();
        boolean jaugeDeLaCause = r.jauge == c && r.jaugeMax > 0;
        switch (c.type) {
            case Condition.SESSIONS:
                if (n == 0) {
                    return "Aucune session n’est autorisée " + periode + ".";
                }
                if (jaugeDeLaCause && r.jaugeFait < r.jaugeMax) {
                    return "Ta session a atteint ses " + Condition.minutes(c.valeur2) + ".";
                }
                return "Tu as déjà utilisé tes " + n + " session" + (n > 1 ? "s" : "") + " " + periode + ".";
            case Condition.OUVERTURES:
                return n == 0 ? "Aucune ouverture n’est autorisée " + periode + "."
                        : "Tu as déjà ouvert " + appli + " " + n + " fois " + periode + ", c’est ton maximum.";
            case Condition.TEMPS:
                if (n == 0) {
                    return "Tu as choisi de ne pas l’ouvrir " + limite.quand() + ".";
                }
                return "Tu as passé " + (jaugeDeLaCause ? Ui.duree(r.jaugeFait) : Condition.minutes(n)) + " sur "
                        + Condition.minutes(n) + " autorisées " + periode + ".";
            case Condition.DUREE_SESSION:
                return "Tu as atteint tes " + Condition.minutes(n) + " d’affilée.";
            case Condition.PAUSE:
                return "Tu t’es donné " + Condition.minutes(c.valeur) + " de pause entre deux usages.";
            case Condition.PAUSE_PROPORTIONNELLE:
                return "Une pause à la mesure du temps que tu viens d’y passer.";
            case Condition.IMMEDIAT:
                return "Tu as lancé un blocage de " + Condition.minutes(c.valeur) + ".";
            default:
                return "Limite atteinte.";
        }
    }

    /** Ta règle en clair, puis tes sessions de la période : de quelle heure à quelle heure. */
    private void historique(LinearLayout c, Moteur.Resultat r, Moteur moteur, long maintenant) {
        LinearLayout regle = Ui.ajouter(c, Ui.carte(this), 24);
        regle.addView(Ui.section(this, "Ta règle"));
        regle.addView(Ui.corps(this, limite.nomAffiche(donnees) + " : " + limite.phrase()));

        Condition cause = r.cause;
        boolean semaine = cause != null && cause.aPeriode() && cause.periode.unite == Periode.SEMAINE;
        long depuis = cause != null && cause.aPeriode() ? moteur.debutCompte(cause, maintenant)
                : Math.max(new Periode().debut(maintenant), donnees.remise(Periode.JOUR));
        List<long[]> sessions = Journal.get(this).sessions(donnees.cibles(limite), depuis, maintenant,
                donnees.toleranceSecondes * 1000L);
        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 8);
        String periode = cause != null && cause.aPeriode() ? cause.periode.enCours() : "aujourd’hui";
        carte.addView(Ui.section(this, "Tes sessions " + periode));
        if (sessions.isEmpty()) {
            carte.addView(Ui.petit(this, "Aucune."));
            return;
        }
        SimpleDateFormat f = new SimpleDateFormat(semaine ? "EEE HH:mm" : "HH:mm", Locale.FRANCE);
        SimpleDateFormat h = new SimpleDateFormat("HH:mm", Locale.FRANCE);
        long total = 0;
        for (long[] s : sessions) {
            total += s[1] - s[0];
        }
        int debut = Math.max(0, sessions.size() - 8);
        if (debut > 0) {
            carte.addView(Ui.petit(this, "… et " + debut + " plus tôt"));
        }
        for (int i = debut; i < sessions.size(); i++) {
            long[] s = sessions.get(i);
            LinearLayout ligne = Ui.ajouter(carte, Ui.rangee(this), 4);
            Ui.etirer(ligne, Ui.corps(this, (i + 1) + ".  " + f.format(new Date(s[0])) + " → " + h.format(new Date(s[1]))));
            ligne.addView(Ui.petit(this, Ui.duree(s[1] - s[0])));
        }
        Ui.ajouter(carte, Ui.petit(this, sessions.size() + " session" + (sessions.size() > 1 ? "s" : "")
                + ", " + Ui.duree(total) + " en tout"), 8);
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

    private static final String[] PHRASES = {
            "Je choisis ce que je fais de mon temps.",
            "Ce que je vais y trouver peut attendre.",
            "Je peux faire autre chose de plus utile maintenant.",
            "Mon attention est précieuse, je la garde.",
    };

    private static void activer(Button b, boolean oui) {
        b.setEnabled(oui);
        b.setAlpha(oui ? 1f : 0.4f);
    }

    /** « Ce que je fais » et « ce que je  fais » se valent : casse, accents, espaces et ponctuation ignorés. */
    private static String simplifier(String s) {
        String sans = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sans.toLowerCase(Locale.FRANCE).replaceAll("[^a-z0-9]", "");
    }

    /** Champ dont l'Entrée ouvre, si c'est permis. */
    private EditText champFriction(LinearLayout c, String indice, Button ouvrir) {
        EditText champ = Ui.ajouter(c, Ui.champ(this, "", indice), 20);
        champ.setOnEditorActionListener((v, action, ev) -> {
            if (ouvrir.isEnabled()) {
                ouvrir.performClick();
            }
            return true;
        });
        return champ;
    }

    private void friction(LinearLayout c, TextView titre, TextView texte, Condition cause, Moteur moteur,
                          String appli, int ouverture) {
        titre.setText("Respire.");
        texte.setText("Tu veux vraiment ouvrir " + appli + " ?");
        Button ouvrir = Ui.boutonPlein(this, "Ouvrir " + appli);
        activer(ouvrir, false);
        EditText[] motif = new EditText[1];
        ouvrir.setOnClickListener(v -> {
            if (motif[0] != null) {
                donnees.noterMotif(paquet, "ouverture", motif[0].getText().toString().trim());
            }
            moteur.accorderPasse(cause, Horloge.maintenant());
            ouvrirAppli();
        });
        if (cause.modeFriction == Condition.FRICTION_PHRASE) {
            String phrase = PHRASES[new Random().nextInt(PHRASES.length)];
            Ui.ajouter(c, Ui.texte(this, "Recopie : « " + phrase + " »", 17, Ui.TEXTE, true), 24);
            EditText champ = champFriction(c, "La phrase, mot pour mot", ouvrir);
            Ui.surChangement(champ, s -> activer(ouvrir, simplifier(s).equals(simplifier(phrase))));
            Ui.ajouter(c, ouvrir, 24);
            return;
        }
        if (cause.modeFriction == Condition.FRICTION_POURQUOI) {
            Ui.ajouter(c, Ui.texte(this, "Pour quoi faire ?", 17, Ui.TEXTE, true), 24);
            motif[0] = champFriction(c, "Ce que je viens y chercher", ouvrir);
            Ui.surChangement(motif[0], s -> activer(ouvrir, s.trim().length() >= 10));
            Ui.ajouter(c, Ui.petit(this, "Dix caractères au moins. Tes réponses sont gardées dans les statistiques de la limite."), 8);
            Ui.ajouter(c, ouvrir, 24);
            return;
        }
        if (cause.modeFriction == Condition.FRICTION_CALCUL) {
            Random hasard = new Random();
            int a = 12 + hasard.nextInt(38);
            int b = 3 + hasard.nextInt(7);
            Ui.ajouter(c, Ui.texte(this, a + " × " + b + " = ?", 32, Ui.TEXTE, true), 24);
            EditText champ = champFriction(c, "Résultat", ouvrir);
            champ.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            Ui.surChangement(champ, s -> activer(ouvrir, s.trim().equals(String.valueOf(a * b))));
            Ui.ajouter(c, ouvrir, 24);
            return;
        }
        TextView compte = Ui.ajouter(c, Ui.texte(this, "", 56, Ui.TEXTE, true), 24);
        compte.setGravity(Gravity.CENTER);
        Ui.ajouter(c, ouvrir, 24);
        int secondes = cause.secondesFriction(ouverture);
        if (secondes > cause.valeur) {
            Ui.ajouter(c, Ui.petit(this, ouverture + "e ouverture aujourd’hui : l’attente double à chaque fois."), 8);
        }
        long fin = Horloge.maintenant() + secondes * 1000L;
        minuterie = new Runnable() {
            @Override
            public void run() {
                long reste = fin - Horloge.maintenant();
                if (reste <= 0) {
                    compte.setText("✓");
                    activer(ouvrir, true);
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
        int attente = limite.rallongeAttente;
        if (limite.rallongeProgressive) {
            // Chaque rallonge du jour double l'attente de la suivante (30 min au plus).
            int prises = donnees.compteur(limite.id, donnees.cleJour(maintenant), Donnees.RALLONGES);
            attente = Math.max(attente, 15);
            for (int i = 0; i < prises && attente < 1800; i++) {
                attente *= 2;
            }
            attente = Math.min(attente, 1800);
        }
        String prix = attente > 0 ? " — " + Ui.duree(attente * 1000L) + " d’attente" : "";
        Button b = Ui.ajouter(c, Ui.bouton(this, "＋" + limite.rallongeMinutes + " min (encore " + restantes + ")" + prix,
                Ui.ORANGE), 24);
        int attenteFinale = attente;
        b.setOnClickListener(v -> {
            b.setEnabled(false);
            attendre(b, attenteFinale, () -> {
                b.setEnabled(true);
                Runnable accorder = () -> {
                    moteur.accorderRallonge(limite, Horloge.maintenant());
                    ouvrirAppli();
                };
                Runnable badge = limite.rallongeNfc
                        ? () -> demanderBadge("La rallonge demande un bip du badge.", accorder) : accorder;
                if (limite.rallongeMotif) {
                    demanderMotif(badge);
                } else {
                    badge.run();
                }
            });
        });
    }

    /** La rallonge se justifie : le motif est gardé avec les statistiques de la limite. */
    private void demanderMotif(Runnable suite) {
        EditText champ = Ui.champ(this, "", "Pourquoi j’en ai besoin");
        int p = Ui.dp(this, 20);
        LinearLayout cadre = Ui.colonne(this);
        cadre.setPadding(p, p / 2, p, 0);
        cadre.addView(champ);
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Rallonge : pour quoi faire ?")
                .setView(cadre)
                .setPositiveButton("Valider", null)
                .setNegativeButton("Annuler", null)
                .create();
        Runnable valider = () -> {
            String motif = champ.getText().toString().trim();
            if (motif.length() < 10) {
                toast("Dix caractères au moins.");
                return;
            }
            donnees.noterMotif(paquet, "rallonge", motif);
            d.dismiss();
            suite.run();
        };
        champ.setOnEditorActionListener((v, action, ev) -> {
            valider.run();
            return true;
        });
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> valider.run()));
        d.show();
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
