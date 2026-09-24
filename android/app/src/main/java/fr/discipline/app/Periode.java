package fr.discipline.app;

import org.json.JSONObject;

import java.util.Calendar;

/**
 * Période de comptage d'une condition : heure (pile ou fenêtre glissante de
 * 60 min), jour (dont on choisit l'heure de début) ou semaine (jour et heure
 * de début au choix).
 */
class Periode {
    static final int HEURE = 0;
    static final int JOUR = 1;
    static final int SEMAINE = 2;

    static final String[] JOURS = {"lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"};
    /** Lundi = 0 … dimanche = 6, pour indexer {@link #JOURS}. */
    static int indexJour(int jourCalendar) {
        return (jourCalendar + 5) % 7;
    }

    static int jourCalendar(int index) {
        return index == 6 ? Calendar.SUNDAY : Calendar.MONDAY + index;
    }

    int unite = JOUR;
    boolean glissante;
    int debutMinutes;
    int jourSemaine = Calendar.MONDAY;

    long debut(long maintenant) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(maintenant);
        if (unite == HEURE) {
            if (glissante) {
                return maintenant - 3_600_000L;
            }
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            return c.getTimeInMillis();
        }
        c.set(Calendar.HOUR_OF_DAY, debutMinutes / 60);
        c.set(Calendar.MINUTE, debutMinutes % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (unite == JOUR) {
            if (c.getTimeInMillis() > maintenant) {
                c.add(Calendar.DAY_OF_YEAR, -1);
            }
            return c.getTimeInMillis();
        }
        c.set(Calendar.DAY_OF_WEEK, jourSemaine);
        while (c.getTimeInMillis() > maintenant) {
            c.add(Calendar.DAY_OF_YEAR, -7);
        }
        Calendar suivant = (Calendar) c.clone();
        suivant.add(Calendar.DAY_OF_YEAR, 7);
        while (suivant.getTimeInMillis() <= maintenant) {
            c.add(Calendar.DAY_OF_YEAR, 7);
            suivant.add(Calendar.DAY_OF_YEAR, 7);
        }
        return c.getTimeInMillis();
    }

    /** Fin de la période en cours (pour une heure glissante : au plus tard dans 60 min). */
    long fin(long maintenant) {
        if (unite == HEURE && glissante) {
            return maintenant + 3_600_000L;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(debut(maintenant));
        c.add(unite == HEURE ? Calendar.HOUR_OF_DAY : unite == JOUR ? Calendar.DAY_OF_YEAR : Calendar.WEEK_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    String libelle() {
        if (unite == HEURE) {
            return glissante ? "par heure glissante" : "par heure";
        }
        if (unite == JOUR) {
            return debutMinutes == 0 ? "par jour" : "par jour (dès " + Ui.heure(debutMinutes) + ")";
        }
        return "par semaine (dès le " + JOURS[indexJour(jourSemaine)] + " " + Ui.heure(debutMinutes) + ")";
    }

    /** « aujourd’hui », « cette semaine »… pour parler de la période en cours. */
    String enCours() {
        if (unite == HEURE) {
            return glissante ? "sur la dernière heure" : "cette heure-ci";
        }
        return unite == JOUR ? "aujourd’hui" : "cette semaine";
    }

    JSONObject json() throws Exception {
        return new JSONObject().put("u", unite).put("g", glissante).put("d", debutMinutes).put("j", jourSemaine);
    }

    static Periode de(JSONObject o) {
        Periode p = new Periode();
        if (o != null) {
            p.unite = o.optInt("u", JOUR);
            p.glissante = o.optBoolean("g");
            p.debutMinutes = o.optInt("d");
            p.jourSemaine = o.optInt("j", Calendar.MONDAY);
        }
        return p;
    }
}
