package fr.discipline.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Les règles de blocage, en SharedPreferences : la liste des applications
 * bloquées, le créneau horaire global (et ses variantes par application),
 * les quotas quotidiens, les sessions limitées et les badges NFC.
 */
class Regles {
    static final String MODE_GLOBAL = "global";
    static final String MODE_PERSONNALISE = "personnalise";
    static final String MODE_AUCUN = "aucun";

    private static final String PREFS = "discipline_regles";
    private static final String CLE_APPLIS = "applis_bloquees";
    private static final String CLE_CRENEAU_ACTIF = "creneau_actif";
    private static final String CLE_DEBUT = "creneau_debut_minutes";
    private static final String CLE_FIN = "creneau_fin_minutes";

    private static final String CLE_CRENEAU_MODE_PREFIX = "creneau_mode_";
    private static final String CLE_CRENEAU_DEBUT_APPLI_PREFIX = "creneau_debut_appli_";
    private static final String CLE_CRENEAU_FIN_APPLI_PREFIX = "creneau_fin_appli_";

    private static final String CLE_QUOTA_PREFIX = "quota_minutes_";

    private static final String CLE_DEBLOCAGE_FIN_PREFIX = "deblocage_fin_";

    private static final String CLE_SESSION_APPLI_PREFIX = "session_appli_";
    private static final String CLE_SESSION_MAX = "session_max_par_jour";
    private static final String CLE_SESSION_DUREE = "session_duree_minutes";
    private static final String CLE_SESSION_DATE = "session_date";
    private static final String CLE_SESSION_COMPTE = "session_compte";

    private static final String CLE_NFC_TAGS = "nfc_tags";
    private static final String CLE_NFC_NOM_PREFIX = "nfc_nom_";
    private static final String CLE_NFC_APPLIS_PREFIX = "nfc_applis_";
    private static final String CLE_NFC_DUREE = "nfc_duree_minutes";

    private final Context contexte;
    private final SharedPreferences prefs;

    Regles(Context contexte) {
        this.contexte = contexte.getApplicationContext();
        prefs = this.contexte.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
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

    // --- Créneau personnalisé par application ---

    String getModeCreneau(String paquet) {
        return prefs.getString(CLE_CRENEAU_MODE_PREFIX + paquet, MODE_GLOBAL);
    }

    void setModeCreneau(String paquet, String mode) {
        prefs.edit().putString(CLE_CRENEAU_MODE_PREFIX + paquet, mode).apply();
    }

    int getDebutMinutes(String paquet) {
        return prefs.getInt(CLE_CRENEAU_DEBUT_APPLI_PREFIX + paquet, getDebutMinutes());
    }

    int getFinMinutes(String paquet) {
        return prefs.getInt(CLE_CRENEAU_FIN_APPLI_PREFIX + paquet, getFinMinutes());
    }

    void setCreneau(String paquet, int debutMinutes, int finMinutes) {
        prefs.edit()
                .putInt(CLE_CRENEAU_DEBUT_APPLI_PREFIX + paquet, debutMinutes)
                .putInt(CLE_CRENEAU_FIN_APPLI_PREFIX + paquet, finMinutes)
                .apply();
    }

    // --- Quota quotidien par application ---

    int getQuotaMinutes(String paquet) {
        return prefs.getInt(CLE_QUOTA_PREFIX + paquet, 0);
    }

    void setQuotaMinutes(String paquet, int minutes) {
        prefs.edit().putInt(CLE_QUOTA_PREFIX + paquet, Math.max(0, minutes)).apply();
    }

    private boolean quotaDepasse(String paquet) {
        int quota = getQuotaMinutes(paquet);
        if (quota <= 0) {
            return false;
        }
        return new Usage(contexte).minutesUtiliseesAujourdHui(paquet) >= quota;
    }

    // --- Déblocage temporaire (sessions et badges NFC) ---

    void debloquerTemporairement(String paquet, int minutes) {
        long fin = System.currentTimeMillis() + minutes * 60000L;
        prefs.edit().putLong(CLE_DEBLOCAGE_FIN_PREFIX + paquet, fin).apply();
    }

    private boolean estDeblocageTemporaireActif(String paquet) {
        return System.currentTimeMillis() < prefs.getLong(CLE_DEBLOCAGE_FIN_PREFIX + paquet, 0L);
    }

    // --- Sessions limitées ---

    boolean isAppliSession(String paquet) {
        return prefs.getBoolean(CLE_SESSION_APPLI_PREFIX + paquet, false);
    }

    void setAppliSession(String paquet, boolean active) {
        prefs.edit().putBoolean(CLE_SESSION_APPLI_PREFIX + paquet, active).apply();
    }

    Set<String> getApplisSession() {
        Set<String> resultat = new HashSet<>();
        for (Map.Entry<String, ?> entree : prefs.getAll().entrySet()) {
            String cle = entree.getKey();
            if (cle.startsWith(CLE_SESSION_APPLI_PREFIX) && Boolean.TRUE.equals(entree.getValue())) {
                resultat.add(cle.substring(CLE_SESSION_APPLI_PREFIX.length()));
            }
        }
        return resultat;
    }

    int getSessionMaxParJour() {
        return prefs.getInt(CLE_SESSION_MAX, 4);
    }

    void setSessionMaxParJour(int max) {
        prefs.edit().putInt(CLE_SESSION_MAX, Math.max(0, max)).apply();
    }

    int getSessionDureeMinutes() {
        return prefs.getInt(CLE_SESSION_DUREE, 15);
    }

    void setSessionDureeMinutes(int minutes) {
        prefs.edit().putInt(CLE_SESSION_DUREE, Math.max(1, minutes)).apply();
    }

    private void reinitialiserSessionsSiNouveauJour() {
        String aujourdHui = dateAujourdHui();
        if (!aujourdHui.equals(prefs.getString(CLE_SESSION_DATE, ""))) {
            prefs.edit().putString(CLE_SESSION_DATE, aujourdHui).putInt(CLE_SESSION_COMPTE, 0).apply();
        }
    }

    int getSessionsRestantesAujourdHui() {
        reinitialiserSessionsSiNouveauJour();
        return Math.max(0, getSessionMaxParJour() - prefs.getInt(CLE_SESSION_COMPTE, 0));
    }

    /** Démarre une session pour cette application si le quota quotidien de sessions le permet. */
    void demarrerSession(String paquet) {
        reinitialiserSessionsSiNouveauJour();
        int utilisees = prefs.getInt(CLE_SESSION_COMPTE, 0);
        if (utilisees >= getSessionMaxParJour()) {
            return;
        }
        prefs.edit().putInt(CLE_SESSION_COMPTE, utilisees + 1).apply();
        debloquerTemporairement(paquet, getSessionDureeMinutes());
    }

    private static String dateAujourdHui() {
        Calendar maintenant = Calendar.getInstance();
        return String.format(Locale.ROOT, "%04d-%02d-%02d",
                maintenant.get(Calendar.YEAR),
                maintenant.get(Calendar.MONTH) + 1,
                maintenant.get(Calendar.DAY_OF_MONTH));
    }

    // --- Badges NFC ---

    Set<String> getTagsNfc() {
        return new HashSet<>(prefs.getStringSet(CLE_NFC_TAGS, Collections.<String>emptySet()));
    }

    String getNomTag(String id) {
        return prefs.getString(CLE_NFC_NOM_PREFIX + id, id);
    }

    Set<String> getApplisTag(String id) {
        return new HashSet<>(prefs.getStringSet(CLE_NFC_APPLIS_PREFIX + id, Collections.<String>emptySet()));
    }

    void enregistrerTag(String id, String nom, Set<String> applis) {
        Set<String> tags = getTagsNfc();
        tags.add(id);
        prefs.edit()
                .putStringSet(CLE_NFC_TAGS, tags)
                .putString(CLE_NFC_NOM_PREFIX + id, nom)
                .putStringSet(CLE_NFC_APPLIS_PREFIX + id, applis)
                .apply();
    }

    void supprimerTag(String id) {
        Set<String> tags = getTagsNfc();
        tags.remove(id);
        prefs.edit()
                .putStringSet(CLE_NFC_TAGS, tags)
                .remove(CLE_NFC_NOM_PREFIX + id)
                .remove(CLE_NFC_APPLIS_PREFIX + id)
                .apply();
    }

    int getNfcDureeMinutes() {
        return prefs.getInt(CLE_NFC_DUREE, 30);
    }

    void setNfcDureeMinutes(int minutes) {
        prefs.edit().putInt(CLE_NFC_DUREE, Math.max(1, minutes)).apply();
    }

    /** Débloque temporairement les applications associées à ce badge. Renvoie leur nombre (0 si badge inconnu). */
    int debloquerViaTag(String id) {
        Set<String> applis = getApplisTag(id);
        for (String paquet : applis) {
            debloquerTemporairement(paquet, getNfcDureeMinutes());
        }
        return applis.size();
    }

    /** true si l'application donnée doit être bloquée maintenant. */
    boolean estBloqueeMaintenant(String paquet) {
        if (estDeblocageTemporaireActif(paquet)) {
            return false;
        }
        if (quotaDepasse(paquet)) {
            return true;
        }
        if (!getApplisBloquees().contains(paquet)) {
            return false;
        }
        String mode = getModeCreneau(paquet);
        if (MODE_AUCUN.equals(mode)) {
            return true;
        }
        if (MODE_PERSONNALISE.equals(mode)) {
            return dansCreneau(minutesDepuisMinuit(), getDebutMinutes(paquet), getFinMinutes(paquet));
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
