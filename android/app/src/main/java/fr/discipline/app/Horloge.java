package fr.discipline.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * Heure de confiance : avancer ou reculer l'horloge du téléphone à la main ne
 * change rien. Elle suit le temps écoulé depuis le démarrage (que l'on ne
 * peut pas trafiquer) à partir d'un point d'ancrage ; quand l'heure est
 * réglée automatiquement par le réseau, c'est elle qui fait foi.
 */
final class Horloge {
    private static final String PREFS = "horloge";
    private static Context contexte;
    private static boolean charge;
    /** Point d'ancrage : heure de confiance à tel temps écoulé depuis le démarrage. */
    private static long ancreConfiance;
    private static long ancreEcoule;
    /** Heure du téléphone moins heure de confiance. */
    private static long decalage;
    private static boolean auto;
    private static long autoLuA = -1;

    private Horloge() {
    }

    static synchronized void init(Context c) {
        if (contexte == null) {
            contexte = c.getApplicationContext();
        }
    }

    static synchronized long maintenant() {
        long mur = System.currentTimeMillis();
        long ecoule = SystemClock.elapsedRealtime();
        if (contexte == null) {
            return mur;
        }
        if (!charge) {
            SharedPreferences p = contexte.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            decalage = p.getLong("decalage", 0);
            ancreConfiance = p.getLong("confiance", 0);
            ancreEcoule = p.getLong("ecoule", 0);
            charge = true;
        }
        if (autoLuA < 0 || ecoule - autoLuA > 10_000L) {
            autoLuA = ecoule;
            auto = Settings.Global.getInt(contexte.getContentResolver(), Settings.Global.AUTO_TIME, 1) == 1;
        }
        if (auto || ancreEcoule == 0 || ecoule < ancreEcoule) {
            // Heure réseau (fiable), premier appel ou redémarrage : on se réancre.
            long confiance = auto ? mur : mur - decalage;
            ancrer(confiance, ecoule, mur - confiance);
            return confiance;
        }
        long confiance = ancreConfiance + (ecoule - ancreEcoule);
        long d = mur - confiance;
        if (Math.abs(d - decalage) > 5_000L) {
            // L'horloge du téléphone a été changée à la main : on le note, sans le suivre.
            ancrer(confiance, ecoule, d);
        }
        return confiance;
    }

    private static void ancrer(long confiance, long ecoule, long nouveauDecalage) {
        boolean changement = Math.abs(nouveauDecalage - decalage) > 1000L || ancreEcoule == 0 || ecoule < ancreEcoule
                || ecoule - ancreEcoule > 3_600_000L;
        ancreConfiance = confiance;
        ancreEcoule = ecoule;
        decalage = nouveauDecalage;
        if (changement) {
            contexte.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong("decalage", decalage).putLong("confiance", confiance).putLong("ecoule", ecoule).apply();
        }
    }

    /** Écart entre l'horloge du téléphone et l'heure de confiance (0 si personne n'y a touché). */
    static synchronized long decalage() {
        maintenant();
        return decalage;
    }
}
