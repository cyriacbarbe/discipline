package fr.discipline.app;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Switch;

import java.util.function.Consumer;

/** Paramètres > Widget : le poser sur l'écran d'accueil, choisir ce qu'il montre. */
public class WidgetActivity extends Ecran {

    @Override
    protected void onResume() {
        super.onResume();
        rafraichir();
    }

    @Override
    protected void rafraichir() {
        LinearLayout c = page("Widget d’accueil", true);
        AppWidgetManager m = AppWidgetManager.getInstance(this);
        ComponentName widget = new ComponentName(this, Widget.class);
        int poses = m.getAppWidgetIds(widget).length;

        LinearLayout poser = Ui.ajouter(c, Ui.carte(this), 12);
        poser.addView(Ui.corps(this, poses == 0 ? "Le widget n’est pas encore sur ton écran d’accueil."
                : poses == 1 ? "Le widget est sur ton écran d’accueil." : "Le widget est posé " + poses + " fois."));
        if (m.isRequestPinAppWidgetSupported()) {
            Ui.ajouter(poser, poses == 0 ? Ui.boutonPlein(this, "Ajouter le widget à l’écran d’accueil")
                    : Ui.boutonDiscret(this, "En ajouter un autre"), 12)
                    .setOnClickListener(v -> m.requestPinAppWidget(widget, null, null));
        } else {
            Ui.ajouter(poser, Ui.petit(this, "Ton lanceur ne sait pas l’ajouter d’ici : appuie longuement sur un "
                    + "espace vide de l’écran d’accueil, puis Widgets > Discipline."), 8);
        }

        Ui.ajouter(c, Ui.section(this, "Ce qu’il montre"), 16);
        LinearLayout montre = Ui.ajouter(c, Ui.carte(this), 8);
        interrupteur(montre, "Temps passé aujourd’hui", donnees.widgetTemps, x -> donnees.widgetTemps = x);
        interrupteur(montre, "Limites qui bloquent ou comptent", donnees.widgetLimites, x -> donnees.widgetLimites = x);
        interrupteur(montre, "Bouton « Concentration »", donnees.widgetConcentration,
                x -> donnees.widgetConcentration = x);

        if (donnees.widgetLimites && !donnees.limites.isEmpty()) {
            Ui.ajouter(c, Ui.section(this, "Limites affichées"), 16);
            LinearLayout liste = Ui.ajouter(c, Ui.carte(this), 8);
            for (Limite l : donnees.limites) {
                CheckBox cb = Ui.caseACocher(this, l.nomAffiche(donnees), !donnees.widgetCachees.contains(l.id));
                cb.setOnCheckedChangeListener((b, coche) -> {
                    if (coche) {
                        donnees.widgetCachees.remove(l.id);
                    } else {
                        donnees.widgetCachees.add(l.id);
                    }
                    enregistrer();
                });
                liste.addView(cb);
            }
            Ui.ajouter(liste, Ui.petit(this, "Seules celles qui bloquent ou qui comptent un temps s’y affichent."), 6);
        }
    }

    private void interrupteur(LinearLayout carte, String libelle, boolean valeur, Consumer<Boolean> suite) {
        LinearLayout r = Ui.ajouter(carte, Ui.rangee(this), 4);
        Ui.etirer(r, Ui.corps(this, libelle));
        Switch s = Ui.interrupteur(this, valeur);
        s.setOnCheckedChangeListener((b, coche) -> {
            suite.accept(coche);
            enregistrer();
            rafraichir();
        });
        r.addView(s);
    }

    private void enregistrer() {
        donnees.enregistrer();
        Widget.mettreAJour(this);
    }
}
