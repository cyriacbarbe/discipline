package fr.discipline.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

/**
 * Anti-triche « alerte si l'accessibilité est coupée » : une vérification
 * toutes les 15 min (et au moment où le service s'arrête) qui prévient par
 * une notification.
 */
public class Surveillance extends JobService {
    private static final int TACHE = 1;
    private static final String CANAL = "alerte";

    static boolean serviceActif(Context c) {
        String actifs = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(actifs)) {
            return false;
        }
        String nom = c.getPackageName() + "/" + BlocageAccessibilityService.class.getName();
        for (String service : actifs.split(":")) {
            if (service.equalsIgnoreCase(nom)) {
                return true;
            }
        }
        return false;
    }

    /** Programme ou retire la vérification selon le réglage anti-triche. */
    static void planifier(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (!Donnees.get(c).alerteAccessibilite) {
            js.cancel(TACHE);
            return;
        }
        if (js.getPendingJob(TACHE) != null) {
            return;
        }
        js.schedule(new JobInfo.Builder(TACHE, new ComponentName(c, Surveillance.class))
                .setPeriodic(15 * 60_000L)
                .setPersisted(true)
                .build());
    }

    static void alerter(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CANAL, "Alerte anti-triche",
                NotificationManager.IMPORTANCE_HIGH));
        PendingIntent reglages = PendingIntent.getActivity(c, 0,
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CANAL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Discipline ne bloque plus rien")
                .setContentText("L’accessibilité de Discipline est coupée. Touche pour la réactiver.")
                .setContentIntent(reglages)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(TACHE, n);
        } catch (SecurityException ignore) {
            // notifications refusées
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (Donnees.get(this).alerteAccessibilite && !serviceActif(this)) {
            alerter(this);
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
