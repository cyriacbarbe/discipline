package fr.discipline.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bilan de la semaine écoulée (lundi → dimanche) : temps passé, comparaison
 * avec la semaine d'avant, applis les plus prenantes, blocages et rallonges.
 * Une notification le propose chaque lundi matin.
 */
final class Bilan {
    private static final String CANAL = "bilan";
    private static final int NOTIFICATION = 7;

    final long debut;
    final long fin;
    long total;
    long totalAvant;
    final List<String[]> tete = new ArrayList<>();
    int bloquees;
    int rallonges;
    int motifs;

    private Bilan(long debut, long fin) {
        this.debut = debut;
        this.fin = fin;
    }

    /** Lundi 0 h de la semaine qui contient ce moment. */
    static long lundi(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            c.add(Calendar.DAY_OF_MONTH, -1);
        }
        return c.getTimeInMillis();
    }

    /** La semaine complète qui précède celle de ce moment. */
    static Bilan semaineEcoulee(Context c, long maintenant) {
        long fin = lundi(maintenant);
        long debut = lundi(fin - 1);
        Bilan b = new Bilan(debut, fin);
        Donnees d = Donnees.get(c);
        Journal j = Journal.get(c);
        Set<String> libres = d.libres();
        Map<String, Long> temps = j.tempsParAppli(debut, fin);
        List<Map.Entry<String, Long>> tries = new ArrayList<>();
        for (Map.Entry<String, Long> e : temps.entrySet()) {
            if (!libres.contains(Cibles.base(e.getKey()))) {
                b.total += e.getValue();
                tries.add(e);
            }
        }
        tries.sort((x, y) -> Long.compare(y.getValue(), x.getValue()));
        for (int i = 0; i < Math.min(5, tries.size()); i++) {
            String cle = tries.get(i).getKey();
            b.tete.add(new String[]{Applications.nom(c, cle), Ui.duree(tries.get(i).getValue())});
        }
        for (Map.Entry<String, Long> e : j.tempsParAppli(lundi(debut - 1), debut).entrySet()) {
            if (!libres.contains(Cibles.base(e.getKey()))) {
                b.totalAvant += e.getValue();
            }
        }
        for (long jour = debut + 12 * 3600_000L; jour < fin; jour += 24 * 3600_000L) {
            String cle = d.cleJour(jour);
            for (Limite l : d.limites) {
                b.bloquees += d.compteur(l.id, cle, Donnees.BLOQUEES);
                b.rallonges += d.compteur(l.id, cle, Donnees.RALLONGES);
            }
        }
        for (org.json.JSONObject m : d.motifs) {
            long t = m.optLong("t");
            if (t >= debut && t < fin) {
                b.motifs++;
            }
        }
        return b;
    }

    String evolution() {
        if (totalAvant <= 0) {
            return "";
        }
        long pourcent = Math.round((total - totalAvant) * 100.0 / totalAvant);
        return pourcent == 0 ? "autant que la semaine d’avant"
                : (pourcent > 0 ? "+" : "−") + Math.abs(pourcent) + " % par rapport à la semaine d’avant";
    }

    String texte() {
        StringBuilder s = new StringBuilder("Discipline, ma semaine : ")
                .append(Ui.duree(total)).append(" sur le téléphone");
        if (!evolution().isEmpty()) {
            s.append(" (").append(evolution()).append(")");
        }
        s.append(", soit ").append(Ui.duree(total / 7)).append(" par jour.");
        if (!tete.isEmpty()) {
            s.append("\nEn tête :");
            for (String[] t : tete) {
                s.append("\n• ").append(t[0]).append(" : ").append(t[1]);
            }
        }
        s.append("\n").append(bloquees).append(" ouverture(s) bloquée(s), ")
                .append(rallonges).append(" rallonge(s) prise(s).");
        return s.toString();
    }

    /** Appelé par le tic du service : notifie le lundi à partir de 9 h, une fois par semaine. */
    static void verifier(Context c, long maintenant) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(maintenant);
        if (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY || cal.get(Calendar.HOUR_OF_DAY) < 9) {
            return;
        }
        SharedPreferences p = c.getSharedPreferences("bilan", Context.MODE_PRIVATE);
        long semaine = lundi(maintenant);
        if (p.getLong("semaine", 0) == semaine) {
            return;
        }
        p.edit().putLong("semaine", semaine).apply();
        Bilan b = semaineEcoulee(c, maintenant);
        if (b.total <= 0) {
            return;
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CANAL, "Bilan de la semaine",
                NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent ouvrir = PendingIntent.getActivity(c, NOTIFICATION, new Intent(c, BilanActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE);
        String resume = Ui.duree(b.total) + (b.evolution().isEmpty() ? "" : " · " + b.evolution());
        Notification n = new Notification.Builder(c, CANAL)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle("Ta semaine sur le téléphone")
                .setContentText(resume)
                .setStyle(new Notification.BigTextStyle().bigText(b.texte()))
                .setContentIntent(ouvrir)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(NOTIFICATION, n);
        } catch (SecurityException ignore) {
            // notifications refusées
        }
    }
}
