package fr.discipline.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Widget d'accueil, réglé widget par widget ({@link Donnees.ReglageWidget}) :
 * temps du jour (et d'hier à la même heure), profil actif, limites qui
 * bloquent ou comptent, applis les plus utilisées, bouton « Concentration ».
 */
public class Widget extends AppWidgetProvider {
    private static final String LANCER = "fr.discipline.app.LANCER";
    private static final String EXTRA_CONDITION = "condition";
    private static final Class<?>[] CIBLES = {HistoriqueActivity.class, MainActivity.class, BilanActivity.class, null};
    private static long derniere;

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        mettreAJour(c);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        if (LANCER.equals(intent.getAction())) {
            lancer(c, intent.getStringExtra(EXTRA_CONDITION));
            mettreAJour(c);
            return;
        }
        super.onReceive(c, intent);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        Donnees d = Donnees.get(c);
        for (int id : ids) {
            d.widgets.remove(id);
        }
        d.enregistrer();
    }

    /** Après une restauration de sauvegarde, Android renumérote les widgets. */
    @Override
    public void onRestored(Context c, int[] anciens, int[] nouveaux) {
        Donnees d = Donnees.get(c);
        List<Donnees.ReglageWidget> reglages = new ArrayList<>();
        for (int id : anciens) {
            reglages.add(d.widgets.remove(id));
        }
        for (int i = 0; i < nouveaux.length; i++) {
            if (reglages.get(i) != null) {
                d.widgets.put(nouveaux[i], reglages.get(i));
            }
        }
        d.enregistrer();
    }

    /** Lance la concentration (blocage immédiat) de cette condition, si sa limite est active. */
    static void lancer(Context c, String conditionId) {
        Donnees d = Donnees.get(c);
        for (Limite l : d.limites) {
            Condition im = l.conditionDeType(Condition.IMMEDIAT);
            if (im != null && im.id.equals(conditionId) && l.active) {
                new Moteur(c).lancerBlocageImmediat(im, Horloge.maintenant());
            }
        }
    }

    /** Concentrations qu'on peut lancer maintenant (limite active, pas déjà en cours). */
    static List<Condition> lancables(Donnees d, long maintenant) {
        List<Condition> liste = new ArrayList<>();
        for (Limite l : d.limites) {
            Condition im = l.conditionDeType(Condition.IMMEDIAT);
            if (l.active && im != null && d.etat(im.id).optLong("jusqua") <= maintenant) {
                liste.add(im);
            }
        }
        return liste;
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
        for (int id : ids) {
            m.updateAppWidget(id, vue(c, id));
        }
    }

    private static long totalHorsLibres(Map<String, Long> temps, Set<String> libres) {
        long total = 0;
        for (Map.Entry<String, Long> e : temps.entrySet()) {
            if (!libres.contains(Cibles.base(e.getKey()))) {
                total += e.getValue();
            }
        }
        return total;
    }

    private static RemoteViews vue(Context c, int id) {
        Donnees d = Donnees.get(c);
        Donnees.ReglageWidget w = d.widget(id);
        Moteur moteur = new Moteur(c);
        Journal journal = Journal.get(c);
        long maintenant = Horloge.maintenant();
        long debut = d.debutJournee(maintenant);
        Set<String> libres = d.libres();
        RemoteViews v = new RemoteViews(c.getPackageName(), w.compact ? R.layout.widget_compact : R.layout.widget);

        // apparence
        v.setInt(R.id.widget_fond, "setColorFilter", Donnees.ReglageWidget.COULEURS_FOND[w.fond]);
        v.setInt(R.id.widget_fond, "setImageAlpha", Math.round(w.opacite * 2.55f));
        float e = Donnees.ReglageWidget.ECHELLES[w.taille];
        taille(v, R.id.widget_temps, (w.compact ? 18 : 24) * e);
        taille(v, R.id.widget_legende, 13 * e);
        taille(v, R.id.widget_profil, 13 * e);
        taille(v, R.id.widget_etats, 13 * e);
        taille(v, R.id.widget_applis, 12 * e);
        taille(v, R.id.widget_lancer, (w.compact ? 15 : 14) * e);

        // temps du jour
        Map<String, Long> aujourdhui = journal.tempsParAppli(debut, maintenant);
        aujourdhui.remove(c.getPackageName());
        v.setViewVisibility(R.id.widget_haut, w.temps ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.widget_temps, Ui.duree(totalHorsLibres(aujourdhui, libres)));
        String legende = "aujourd’hui";
        if (w.hier) {
            long jour = 24 * 3600_000L;
            legende += " · hier " + Ui.duree(totalHorsLibres(journal.tempsParAppli(debut - jour, maintenant - jour), libres));
        }
        v.setTextViewText(R.id.widget_legende, legende);

        // profil actif
        if (w.profil && !d.profils.isEmpty()) {
            Donnees.Profil p = d.profilActif == null ? null : d.profil(d.profilActif);
            v.setViewVisibility(R.id.widget_profil, View.VISIBLE);
            v.setTextViewText(R.id.widget_profil, "◉ " + (p == null ? "Aucun profil" : p.nom) + "  ⇄");
        } else {
            v.setViewVisibility(R.id.widget_profil, View.GONE);
        }

        // limites
        v.setViewVisibility(R.id.widget_etats, w.limites ? View.VISIBLE : View.GONE);
        int max = w.compact ? 1 : w.lignes;
        StringBuilder etats = new StringBuilder();
        int lignes = 0;
        boolean vacances = d.enVacances(maintenant);
        for (Limite l : d.limites) {
            if (!l.active || l.conditions.isEmpty() || w.cachees.contains(l.id)) {
                continue;
            }
            Condition im = l.conditionDeType(Condition.IMMEDIAT);
            boolean immediat = im != null && d.etat(im.id).optLong("jusqua") > maintenant;
            if (!immediat && (vacances || !l.sAppliqueA(maintenant))) {
                continue;
            }
            String etat = etat(moteur.evaluer(l, maintenant), maintenant, w.quotas);
            if (etat == null) {
                continue;
            }
            if (lignes++ < max) {
                etats.append(etats.length() == 0 ? "" : "\n").append(etat.startsWith("bloquée") ? "✗ " : "✓ ")
                        .append(l.nomAffiche(d)).append(" — ").append(etat);
            }
        }
        if (lignes > max && !w.compact) {
            etats.append("\n… et ").append(lignes - max).append(" autre(s)");
        }
        v.setTextViewText(R.id.widget_etats, etats.length() == 0
                ? (vacances ? "En vacances : aucune limite." : "Rien ne bloque ni ne compte.") : etats);

        // applis les plus utilisées
        List<Map.Entry<String, Long>> tri = new ArrayList<>();
        for (Map.Entry<String, Long> x : aujourdhui.entrySet()) {
            if (!libres.contains(Cibles.base(x.getKey()))) {
                tri.add(x);
            }
        }
        tri.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        StringBuilder applis = new StringBuilder();
        for (int i = 0; i < Math.min(w.applis, tri.size()); i++) {
            applis.append(i == 0 ? "" : " · ").append(Applications.nom(c, tri.get(i).getKey()))
                    .append(" ").append(Ui.duree(tri.get(i).getValue()));
        }
        v.setViewVisibility(R.id.widget_applis, applis.length() == 0 ? View.GONE : View.VISIBLE);
        v.setTextViewText(R.id.widget_applis, applis);

        // appuis
        int drapeaux = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        v.setOnClickPendingIntent(R.id.widget_haut, ouvrir(c, id * 8 + 1, CIBLES[w.appuiTemps]));
        v.setOnClickPendingIntent(R.id.widget_etats, ouvrir(c, id * 8 + 2, CIBLES[w.appuiLimites]));
        v.setOnClickPendingIntent(R.id.widget_applis, ouvrir(c, id * 8 + 3, CIBLES[w.appuiTemps]));
        v.setOnClickPendingIntent(R.id.widget_profil, PendingIntent.getActivity(c, id * 8 + 4,
                new Intent(c, WidgetChoixActivity.class).putExtra(WidgetChoixActivity.QUOI, WidgetChoixActivity.PROFIL),
                drapeaux));

        // concentration
        List<Condition> lancables = lancables(d, maintenant);
        Condition choisie = null;
        for (Condition x : lancables) {
            if (x.id.equals(w.concentrationId) || lancables.size() == 1) {
                choisie = x;
            }
        }
        if (lancables.isEmpty() || !w.concentration) {
            v.setViewVisibility(R.id.widget_lancer, View.GONE);
        } else {
            v.setViewVisibility(R.id.widget_lancer, View.VISIBLE);
            v.setTextViewText(R.id.widget_lancer, w.compact ? "▶" : choisie != null
                    ? "▶ Concentration " + Ui.duree(choisie.valeur * 60_000L) : "▶ Concentration…");
            // plusieurs concentrations et aucune choisie : on demande laquelle
            v.setOnClickPendingIntent(R.id.widget_lancer, choisie != null
                    ? PendingIntent.getBroadcast(c, id * 8 + 5, new Intent(c, Widget.class).setAction(LANCER)
                            .putExtra(EXTRA_CONDITION, choisie.id), drapeaux)
                    : PendingIntent.getActivity(c, id * 8 + 5, new Intent(c, WidgetChoixActivity.class)
                            .putExtra(WidgetChoixActivity.QUOI, WidgetChoixActivity.CONCENTRATION), drapeaux));
        }
        return v;
    }

    /** « bloquée 12 min », « reste 20 min · 2 sessions »… ; null si la limite n'a rien à dire. */
    private static String etat(Moteur.Resultat r, long maintenant, boolean quotas) {
        if (r.bloque) {
            return r.dispoA > maintenant ? "bloquée " + Ui.duree(r.dispoA - maintenant) : "bloquée";
        }
        if (r.rallongeEnCours) {
            return "rallonge, " + Ui.duree(r.restant);
        }
        String temps = r.restant != Long.MAX_VALUE && r.restant > 0 ? "reste " + Ui.duree(r.restant) : null;
        String quota = null;
        if (quotas && r.jauge != null && (r.jauge.type == Condition.SESSIONS || r.jauge.type == Condition.OUVERTURES)) {
            long reste = Math.max(0, r.jaugeMax - r.jaugeFait);
            quota = reste + (r.jauge.type == Condition.SESSIONS ? " session" : " ouverture") + (reste > 1 ? "s" : "");
        }
        if (temps == null) {
            return quota == null ? null : "reste " + quota;
        }
        return quota == null ? temps : temps + " · " + quota;
    }

    private static PendingIntent ouvrir(Context c, int code, Class<?> cible) {
        return cible == null ? null : PendingIntent.getActivity(c, code, new Intent(c, cible),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void taille(RemoteViews v, int vue, float sp) {
        v.setTextViewTextSize(vue, TypedValue.COMPLEX_UNIT_SP, sp);
    }
}
