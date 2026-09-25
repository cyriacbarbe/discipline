package fr.discipline.app;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Switch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * ⚙ > Widget d'accueil : les widgets posés et le réglage des prochains ; avec
 * un identifiant de widget (pose, appui long > Réglages), le réglage de celui-là.
 */
public class WidgetActivity extends Ecran {
    private static final String DEFAUT = "defaut";
    private int id = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean defaut;

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        defaut = getIntent().getBooleanExtra(DEFAUT, false);
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            // le widget reste posé même si on revient en arrière sans rien régler
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        rafraichir();
    }

    @Override
    protected void rafraichir() {
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            regler(page("Ce widget", true), donnees.widgetAPart(id));
        } else if (defaut) {
            LinearLayout c = page("Prochains widgets", true);
            Ui.ajouter(c, Ui.petit(this, "Le réglage de départ des widgets que tu poseras ; chacun se règle "
                    + "ensuite à part."), 8);
            regler(c, donnees.widgetDefaut);
        } else {
            apercu();
        }
    }

    private void apercu() {
        LinearLayout c = page("Widget d’accueil", true);
        AppWidgetManager m = AppWidgetManager.getInstance(this);
        ComponentName widget = new ComponentName(this, Widget.class);
        int[] poses = m.getAppWidgetIds(widget);

        LinearLayout poser = Ui.ajouter(c, Ui.carte(this), 12);
        poser.addView(Ui.corps(this, poses.length == 0 ? "Le widget n’est pas encore sur ton écran d’accueil."
                : poses.length == 1 ? "Le widget est sur ton écran d’accueil." : "Le widget est posé " + poses.length + " fois."));
        if (m.isRequestPinAppWidgetSupported()) {
            // une fois posé, le lanceur ouvre le réglage du nouveau widget
            PendingIntent regler = PendingIntent.getActivity(this, 0, new Intent(this, WidgetActivity.class),
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Ui.ajouter(poser, poses.length == 0 ? Ui.boutonPlein(this, "Ajouter le widget à l’écran d’accueil")
                    : Ui.boutonDiscret(this, "En ajouter un autre"), 12)
                    .setOnClickListener(v -> m.requestPinAppWidget(widget, null, regler));
        } else {
            Ui.ajouter(poser, Ui.petit(this, "Ton lanceur ne sait pas l’ajouter d’ici : appuie longuement sur un "
                    + "espace vide de l’écran d’accueil, puis Widgets > Discipline."), 8);
        }

        if (poses.length > 0) {
            Ui.ajouter(c, Ui.section(this, "Tes widgets"), 16);
            LinearLayout liste = Ui.ajouter(c, Ui.carte(this), 8);
            for (int i = 0; i < poses.length; i++) {
                int x = poses[i];
                liste.addView(Ui.lien(this, "Widget " + (i + 1), resume(donnees.widget(x)),
                        v -> startActivity(new Intent(this, WidgetActivity.class)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, x))));
            }
            Ui.ajouter(liste, Ui.petit(this, "Sur Android 12 et plus, un appui long sur le widget > Réglages "
                    + "ouvre aussi son réglage."), 6);
        }

        Ui.ajouter(c, Ui.section(this, "Les prochains"), 16);
        LinearLayout prochains = Ui.ajouter(c, Ui.carte(this), 8);
        prochains.addView(Ui.lien(this, "Réglage de départ", resume(donnees.widgetDefaut),
                v -> startActivity(new Intent(this, WidgetActivity.class).putExtra(DEFAUT, true))));
    }

    private static String resume(Donnees.ReglageWidget w) {
        List<String> parts = new ArrayList<>();
        if (w.compact) {
            parts.add("compact");
        }
        if (w.temps) {
            parts.add("temps");
        }
        if (w.profil) {
            parts.add("profil");
        }
        if (w.limites) {
            parts.add("limites");
        }
        if (w.applis > 0 && !w.compact) {
            parts.add("applis");
        }
        if (w.concentration) {
            parts.add("concentration");
        }
        return parts.isEmpty() ? "vide" : String.join(", ", parts);
    }

    private void regler(LinearLayout c, Donnees.ReglageWidget w) {
        Ui.ajouter(c, Ui.section(this, "Ce qu’il montre"), 16);
        LinearLayout montre = Ui.ajouter(c, Ui.carte(this), 8);
        interrupteur(montre, "Temps passé aujourd’hui", w.temps, x -> w.temps = x);
        if (w.temps) {
            interrupteur(montre, "… et hier à la même heure", w.hier, x -> w.hier = x);
        }
        if (!w.compact) {
            interrupteur(montre, "Profil actif (le toucher en change)", w.profil, x -> w.profil = x);
        }
        interrupteur(montre, "Limites qui bloquent ou comptent", w.limites, x -> w.limites = x);
        if (w.limites) {
            interrupteur(montre, "Sessions et ouvertures restantes", w.quotas, x -> w.quotas = x);
            if (!w.compact) {
                montre.addView(reglette("Lignes de limites", w.lignes, 1, 10, String.valueOf(w.lignes),
                        x -> w.lignes = x));
            }
        }
        if (!w.compact) {
            montre.addView(reglette("Applis les plus utilisées", w.applis, 0, 5,
                    w.applis == 0 ? "aucune" : String.valueOf(w.applis), x -> w.applis = x));
        }
        interrupteur(montre, "Bouton « Concentration »", w.concentration, x -> w.concentration = x);
        if (w.concentration) {
            choixConcentration(montre, w);
        }

        if (w.limites && !donnees.limites.isEmpty()) {
            Ui.ajouter(c, Ui.section(this, "Limites affichées"), 16);
            LinearLayout liste = Ui.ajouter(c, Ui.carte(this), 8);
            for (Limite l : donnees.limites) {
                CheckBox cb = Ui.caseACocher(this, l.nomAffiche(donnees), !w.cachees.contains(l.id));
                cb.setOnCheckedChangeListener((b, coche) -> {
                    if (coche) {
                        w.cachees.remove(l.id);
                    } else {
                        w.cachees.add(l.id);
                    }
                    enregistrer();
                });
                liste.addView(cb);
            }
            Ui.ajouter(liste, Ui.petit(this, "Seules celles qui bloquent ou qui comptent s’y affichent."), 6);
        }

        Ui.ajouter(c, Ui.section(this, "Apparence"), 16);
        LinearLayout apparence = Ui.ajouter(c, Ui.carte(this), 8);
        interrupteur(apparence, "Compact, sur une ligne", w.compact, x -> w.compact = x);
        Ui.ajouter(apparence, Ui.petit(this, "Fond"), 10);
        Ui.ajouter(apparence, Ui.puces(this, Donnees.ReglageWidget.FONDS, w.fond, x -> {
            w.fond = x;
            enregistrerEtRafraichir();
        }), 6);
        apparence.addView(reglette("Opacité du fond", w.opacite / 10, 0, 10, w.opacite + " %",
                x -> w.opacite = x * 10));
        Ui.ajouter(apparence, Ui.petit(this, "Taille du texte"), 6);
        Ui.ajouter(apparence, Ui.puces(this, Donnees.ReglageWidget.TAILLES, w.taille, x -> {
            w.taille = x;
            enregistrerEtRafraichir();
        }), 6);

        Ui.ajouter(c, Ui.section(this, "Quand on le touche"), 16);
        LinearLayout appuis = Ui.ajouter(c, Ui.carte(this), 8);
        appuis.addView(Ui.lien(this, "Le temps du jour ouvre", Donnees.ReglageWidget.CIBLES_APPUI[w.appuiTemps],
                v -> choisirCible("Le temps du jour ouvre", x -> w.appuiTemps = x)));
        appuis.addView(Ui.lien(this, "Les limites ouvrent", Donnees.ReglageWidget.CIBLES_APPUI[w.appuiLimites],
                v -> choisirCible("Les limites ouvrent", x -> w.appuiLimites = x)));

        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Ui.ajouter(c, Ui.boutonDiscret(this, "En faire le réglage des prochains widgets"), 16)
                    .setOnClickListener(v -> {
                        donnees.widgetDefaut = w.copie();
                        enregistrer();
                        toast("Les prochains widgets partiront de ce réglage.");
                    });
            boutonBas(c, "Terminé", v -> finish());
        }
    }

    /** Laquelle lancer quand il y en a plusieurs (ou demander à chaque fois). */
    private void choixConcentration(LinearLayout carte, Donnees.ReglageWidget w) {
        List<Limite> avec = new ArrayList<>();
        for (Limite l : donnees.limites) {
            if (l.conditionDeType(Condition.IMMEDIAT) != null) {
                avec.add(l);
            }
        }
        if (avec.size() < 2) {
            return;
        }
        String[] noms = new String[avec.size() + 1];
        noms[0] = "Demander à chaque fois";
        String actuel = noms[0];
        for (int i = 0; i < avec.size(); i++) {
            Limite l = avec.get(i);
            noms[i + 1] = l.nomAffiche(donnees);
            if (l.conditionDeType(Condition.IMMEDIAT).id.equals(w.concentrationId)) {
                actuel = noms[i + 1];
            }
        }
        carte.addView(Ui.lien(this, "Le bouton lance", actuel, v -> new AlertDialog.Builder(this)
                .setTitle("Le bouton lance")
                .setItems(noms, (d, i) -> {
                    w.concentrationId = i == 0 ? "" : avec.get(i - 1).conditionDeType(Condition.IMMEDIAT).id;
                    enregistrerEtRafraichir();
                })
                .setNegativeButton("Annuler", null)
                .show()));
    }

    private void choisirCible(String titre, IntConsumer suite) {
        new AlertDialog.Builder(this)
                .setTitle(titre)
                .setItems(Donnees.ReglageWidget.CIBLES_APPUI, (d, i) -> {
                    suite.accept(i);
                    enregistrerEtRafraichir();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private LinearLayout reglette(String libelle, int valeur, int min, int max, String affiche, IntConsumer suite) {
        return Ui.reglette(this, libelle, affiche,
                v -> {
                    suite.accept(Math.max(min, valeur - 1));
                    enregistrerEtRafraichir();
                },
                v -> {
                    suite.accept(Math.min(max, valeur + 1));
                    enregistrerEtRafraichir();
                },
                null);
    }

    private void interrupteur(LinearLayout carte, String libelle, boolean valeur, Consumer<Boolean> suite) {
        LinearLayout r = Ui.ajouter(carte, Ui.rangee(this), 4);
        Ui.etirer(r, Ui.corps(this, libelle));
        Switch s = Ui.interrupteur(this, valeur);
        s.setOnCheckedChangeListener((b, coche) -> {
            suite.accept(coche);
            enregistrerEtRafraichir();
        });
        r.addView(s);
    }

    private void enregistrerEtRafraichir() {
        enregistrer();
        rafraichir();
    }

    private void enregistrer() {
        donnees.enregistrer();
        Widget.mettreAJour(this);
    }
}
