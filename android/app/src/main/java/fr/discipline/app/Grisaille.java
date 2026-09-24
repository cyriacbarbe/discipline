package fr.discipline.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * Écran en noir et blanc (correction des couleurs « monochromie » d'Android)
 * tant qu'une cible d'une limite « grisaille » est au premier plan.
 * Demande WRITE_SECURE_SETTINGS, accordée une fois par adb. On ne rend la
 * couleur que si c'est nous qui l'avions retirée.
 */
final class Grisaille {
    private Grisaille() {
    }

    private static final String ACTIVE = "accessibility_display_daltonizer_enabled";
    private static final String MODE = "accessibility_display_daltonizer";
    private static final int MONOCHROMIE = 0;
    /** Mode de correction que l'utilisateur avait avant nous. */
    private static final String AVANT = "grisailleAvant";

    static boolean possible(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("grisaille", Context.MODE_PRIVATE);
    }

    static void appliquer(Context c, boolean gris) {
        if (!possible(c)) {
            return;
        }
        SharedPreferences p = prefs(c);
        boolean parNous = p.contains(AVANT);
        try {
            if (gris && !parNous) {
                int active = Settings.Secure.getInt(c.getContentResolver(), ACTIVE, 0);
                int mode = Settings.Secure.getInt(c.getContentResolver(), MODE, -1);
                if (active == 1 && mode == MONOCHROMIE) {
                    return; // déjà en noir et blanc de lui-même
                }
                p.edit().putString(AVANT, active + ":" + mode).apply();
                Settings.Secure.putInt(c.getContentResolver(), MODE, MONOCHROMIE);
                Settings.Secure.putInt(c.getContentResolver(), ACTIVE, 1);
            } else if (!gris && parNous) {
                String[] avant = p.getString(AVANT, "0:-1").split(":");
                if (Integer.parseInt(avant[1]) >= 0) {
                    Settings.Secure.putInt(c.getContentResolver(), MODE, Integer.parseInt(avant[1]));
                }
                Settings.Secure.putInt(c.getContentResolver(), ACTIVE, Integer.parseInt(avant[0]));
                p.edit().remove(AVANT).apply();
            }
        } catch (RuntimeException ignore) {
            // réglage refusé par ce téléphone
        }
    }
}
