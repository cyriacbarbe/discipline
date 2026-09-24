package fr.discipline.app;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Accès aux notifications (accordé à la main) : donne au service la liste des
 * lecteurs audio en cours, pour couper le son d'une appli bloquée, et met en
 * sourdine les notifications d'une appli bloquée par une limite « silence » :
 * elles reviennent quand l'appli redevient disponible.
 */
public class Notifications extends NotificationListenerService {

    static boolean autorise(Context c) {
        String actives = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return actives != null && actives.contains(new ComponentName(c, Notifications.class).flattenToString());
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn.isOngoing() || sbn.getPackageName().equals(getPackageName())) {
            return;
        }
        Donnees donnees = Donnees.get(this);
        Moteur moteur = new Moteur(this);
        long maintenant = Horloge.maintenant();
        for (Limite l : moteur.limitesPour(sbn.getPackageName(), maintenant)) {
            if (!l.silence) {
                continue;
            }
            Moteur.Resultat r = moteur.evaluer(l, maintenant);
            if (r.bloque) {
                long attente = r.dispoA > maintenant ? r.dispoA - maintenant : 30 * 60_000L;
                try {
                    snoozeNotification(sbn.getKey(), Math.min(attente, 24 * 3_600_000L));
                } catch (RuntimeException ignore) {
                    // notification déjà partie
                }
                donnees.compter(l.id, Donnees.SOURDINE);
                return;
            }
        }
    }
}
