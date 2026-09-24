package fr.discipline.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Map;
import java.util.Set;

/**
 * Widget d'accueil : temps passé aujourd'hui, limites qui bloquent ou
 * comptent (et ce qu'il reste), bouton pour lancer une concentration
 * (limite à blocage immédiat).
 */
public class Widget extends AppWidgetProvider {
    private static final String LANCER = "fr.discipline.app.LANCER";
    private static final String EXTRA_CONDITION = "condition";
    private static final int LIGNES = 5;
    private static long derniere;

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        mettreAJour(c);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        if (LANCER.equals(intent.getAction())) {
            Donnees d = Donnees.get(c);
            String id = intent.getStringExtra(EXTRA_CONDITION);
            for (Limite l : d.limites) {
                Condition im = l.conditionDeType(Condition.IMMEDIAT);
                if (im != null && im.id.equals(id) && l.active) {
                    new Moteur(c).lancerBlocageImmediat(im, Horloge.maintenant());
                }
            }
            mettreAJour(c);
            return;
        }
        super.onReceive(c, intent);
    }

    /** Depuis le tic du service : une fois par minute suffit, le widget compte en minutes. */
    static void siBesoin(Context c, long maintenant) {
        if (Math.abs(maintenant - derniere) >= 60_000L) {
            mettreAJour(c);
        }
    }

    static void mettreAJour(Context c) {
        derniere = Horloge.maintenant();
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, Widget.class));
        if (ids.length == 0) {
            return;
        }
        Donnees d = Donnees.get(c);
        Moteur moteur = new Moteur(c);
        long maintenant = Horloge.maintenant();
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);

        long total = 0;
        Set<String> libres = d.libres();
        for (Map.Entry<String, Long> e : Journal.get(c).tempsParAppli(d.debutJournee(maintenant), maintenant).entrySet()) {
            if (!libres.contains(Cibles.base(e.getKey()))) {
                total += e.getValue();
            }
        }
        v.setTextViewText(R.id.widget_temps, Ui.duree(total));
        v.setViewVisibility(R.id.widget_haut, d.widgetTemps ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.widget_etats, d.widgetLimites ? View.VISIBLE : View.GONE);

        StringBuilder etats = new StringBuilder();
        int lignes = 0;
        int lancables = 0;
        Condition aLancer = null;
        boolean vacances = d.enVacances(maintenant);
        for (Limite l : d.limites) {
            if (!l.active || l.conditions.isEmpty() || d.widgetCachees.contains(l.id)) {
                continue;
            }
            Condition im = l.conditionDeType(Condition.IMMEDIAT);
            boolean immediat = im != null && d.etat(im.id).optLong("jusqua") > maintenant;
            if (im != null && !immediat) {
                lancables++;
                aLancer = aLancer == null ? im : aLancer;
            }
            if (!immediat && (vacances || !l.sAppliqueA(maintenant))) {
                continue;
            }
            Moteur.Resultat r = moteur.evaluer(l, maintenant);
            String etat;
            if (r.bloque) {
                etat = r.dispoA > maintenant ? "bloquée " + Ui.duree(r.dispoA - maintenant) : "bloquée";
            } else if (r.rallongeEnCours) {
                etat = "rallonge, " + Ui.duree(r.restant);
            } else if (r.restant != Long.MAX_VALUE && r.restant > 0) {
                etat = "reste " + Ui.duree(r.restant);
            } else {
                continue;
            }
            if (lignes++ < LIGNES) {
                etats.append(etats.length() == 0 ? "" : "\n").append(r.bloque ? "✗ " : "✓ ")
                        .append(l.nomAffiche(d)).append(" — ").append(etat);
            }
        }
        if (lignes > LIGNES) {
            etats.append("\n… et ").append(lignes - LIGNES).append(" autre(s)");
        }
        v.setTextViewText(R.id.widget_etats, etats.length() == 0
                ? (vacances ? "En vacances : aucune limite." : "Rien ne bloque ni ne compte.") : etats);

        int drapeaux = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        v.setOnClickPendingIntent(R.id.widget_haut, PendingIntent.getActivity(c, 1,
                new Intent(c, HistoriqueActivity.class), drapeaux));
        v.setOnClickPendingIntent(R.id.widget_etats, PendingIntent.getActivity(c, 2,
                new Intent(c, MainActivity.class), drapeaux));
        if (lancables == 0 || !d.widgetConcentration) {
            v.setViewVisibility(R.id.widget_lancer, View.GONE);
        } else {
            v.setViewVisibility(R.id.widget_lancer, View.VISIBLE);
            v.setTextViewText(R.id.widget_lancer, lancables == 1
                    ? "▶ Concentration " + Ui.duree(aLancer.valeur * 60_000L) : "▶ Concentration…");
            // plusieurs concentrations possibles : l'accueil les propose toutes
            v.setOnClickPendingIntent(R.id.widget_lancer, lancables == 1
                    ? PendingIntent.getBroadcast(c, 3, new Intent(c, Widget.class).setAction(LANCER)
                            .putExtra(EXTRA_CONDITION, aLancer.id), drapeaux)
                    : PendingIntent.getActivity(c, 3, new Intent(c, MainActivity.class), drapeaux));
        }
        m.updateAppWidget(ids, v);
    }
}
