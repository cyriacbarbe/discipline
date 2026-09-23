package fr.discipline.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Les règles de blocage, en SharedPreferences : la liste des applications
 * bloquées et, en option, le créneau horaire où le blocage s'applique
 * (en dehors du créneau, les applications listées restent accessibles).
 */
class Regles {
    private static final String PREFS = "discipline_regles";
    private static final String CLE_APPLIS = "applis_bloquees";
    private static final String CLE_CRENEAU_ACTIF = "creneau_actif";
    private static final String CLE_DEBUT = "creneau_debut_minutes";
    private static final String CLE_FIN = "creneau_fin_minutes";

    private final SharedPreferences prefs;

    Regles(Context contexte) {
        prefs = contexte.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    Set<String> getApplisBloquees() {
        return new HashSet<>(prefs.getStringSet(CLE_APPLIS, Collections.<String>emptySet()));
    }

    void setBloquee(String paquet, boolean bloquee) {
        Set<String> applis = getApplisBloquees();
        if (bloquee) {
            applis.add(paquet);
        } else {
            applis.remove(paquet);
        }
        prefs.edit().putStringSet(CLE_APPLIS, applis).apply();
    }

    boolean isCreneauActif() {
        return prefs.getBoolean(CLE_CRENEAU_ACTIF, false);
    }

    void setCreneauActif(boolean actif) {
        prefs.edit().putBoolean(CLE_CRENEAU_ACTIF, actif).apply();
    }

    int getDebutMinutes() {
        return prefs.getInt(CLE_DEBUT, 22 * 60);
    }

    int getFinMinutes() {
        return prefs.getInt(CLE_FIN, 7 * 60);
    }

    void setCreneau(int debutMinutes, int finMinutes) {
        prefs.edit().putInt(CLE_DEBUT, debutMinutes).putInt(CLE_FIN, finMinutes).apply();
    }

    /** true si l'application donnée doit être bloquée maintenant. */
    boolean estBloqueeMaintenant(String paquet) {
        if (!getApplisBloquees().contains(paquet)) {
            return false;
        }
        if (!isCreneauActif()) {
            return true;
        }
        return dansCreneau(minutesDepuisMinuit(), getDebutMinutes(), getFinMinutes());
    }

    private static int minutesDepuisMinuit() {
        Calendar maintenant = Calendar.getInstance();
        return maintenant.get(Calendar.HOUR_OF_DAY) * 60 + maintenant.get(Calendar.MINUTE);
    }

    // Le créneau peut traverser minuit (ex. 22:00 -> 07:00).
    static boolean dansCreneau(int minutes, int debut, int fin) {
        if (debut == fin) {
            return true;
        }
        if (debut < fin) {
            return minutes >= debut && minutes < fin;
        }
        return minutes >= debut || minutes < fin;
    }
}
