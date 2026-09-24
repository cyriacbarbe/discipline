package fr.discipline.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Décide si une limite bloque maintenant. Chaque condition est « respectée »
 * ou non ; ET lie plus fort que OU ; la limite bloque quand la combinaison
 * n'est pas respectée. Tout se calcule à partir du {@link Journal}.
 */
final class Moteur {

    static final class Resultat {
        boolean bloque;
        /** Première condition non respectée (celle qui explique le blocage). */
        Condition cause;
        /** Quand ce sera de nouveau disponible (0 = on ne sait pas). */
        long dispoA;
        /** Temps restant avant blocage pour les conditions de temps. */
        long restant = Long.MAX_VALUE;
        /** La session en cours vient de commencer : c'est une ouverture. */
        boolean ouverture;
        /** Temps passé sur les cibles aujourd'hui et ouvertures du jour, pour l'écran de blocage. */
        long tempsDuJour;
        int ouverturesDuJour;
        boolean rallongeEnCours;
        /** Premier quota de la limite et ce qui en est consommé (ms pour le temps, nombre sinon). */
        Condition jauge;
        long jaugeFait;
        long jaugeMax;
    }

    private final Donnees d;
    private final Journal j;

    Moteur(Context c) {
        d = Donnees.get(c);
        j = Journal.get(c);
    }

    /** Limites actives qui visent ce paquet et s'appliquent maintenant. */
    List<Limite> limitesPour(String paquet, long maintenant) {
        List<Limite> liste = new ArrayList<>();
        boolean vacances = d.enVacances(maintenant);
        for (Limite l : d.limites) {
            boolean enVigueur = !vacances && l.sAppliqueA(maintenant) || immediatEnCours(l, maintenant) != null;
            if (l.active && !l.conditions.isEmpty() && enVigueur && d.cibles(l).contains(paquet)) {
                liste.add(l);
            }
        }
        return liste;
    }

    Resultat evaluer(Limite l, long maintenant) {
        Resultat r = new Resultat();
        Set<String> cibles = d.cibles(l);
        long tolerance = d.toleranceSecondes * 1000L;
        List<long[]> sessions = j.sessions(cibles, maintenant - 8L * 86_400_000L, maintenant, tolerance);
        long[] courante = null;
        if (!sessions.isEmpty() && sessions.get(sessions.size() - 1)[1] >= maintenant - 1500) {
            courante = sessions.get(sessions.size() - 1);
            r.ouverture = courante[0] == j.debutEnCours();
        }
        long debutJour = d.debutJournee(maintenant);
        r.tempsDuJour = j.temps(cibles, debutJour, maintenant);
        r.ouverturesDuJour = compterDepuis(sessions, debutJour);

        if (l.conditions.isEmpty()) {
            return r;
        }
        if (!l.sAppliqueA(maintenant) || d.enVacances(maintenant)) {
            // Hors plage et en vacances, seul un « Bloque-moi ça » lancé à la main tient encore.
            Condition immediat = immediatEnCours(l, maintenant);
            if (immediat != null) {
                r.bloque = true;
                r.cause = immediat;
                r.dispoA = d.etat(immediat.id).optLong("jusqua");
            }
            return r;
        }
        // La rallonge ne lève que les quotas atteints (temps, ouvertures, sessions, pauses) :
        // un blocage immédiat, un badge ou une friction restent exigés.
        long rallonge = d.etat(l.id).optLong("rallonge");
        r.rallongeEnCours = maintenant < rallonge;

        int n = l.conditions.size();
        boolean[] respectee = new boolean[n];
        long[] dispo = new long[n];
        for (int i = 0; i < n; i++) {
            Condition c = l.conditions.get(i);
            long[] detail = new long[2]; // {dispoA, restant}
            if (r.rallongeEnCours && c.estQuota()) {
                respectee[i] = true;
                detail[1] = rallonge - maintenant;
            } else {
                respectee[i] = respectee(l, c, sessions, courante, cibles, maintenant, tolerance, detail);
            }
            dispo[i] = detail[0];
            if (respectee[i] && detail[1] > 0 && detail[1] < r.restant) {
                r.restant = detail[1];
            }
        }

        jauge(r, l, sessions, cibles, maintenant);

        // ET lie plus fort que OU : on parcourt des groupes ET séparés par des OU.
        boolean respecteeGlobale = false;
        long meilleurDispo = Long.MAX_VALUE;
        int debutGroupe = 0;
        for (int i = 0; i < n; i++) {
            boolean finGroupe = i == n - 1 || !l.et.get(i);
            if (!finGroupe) {
                continue;
            }
            boolean ok = true;
            long dispoGroupe = 0;
            for (int k = debutGroupe; k <= i; k++) {
                if (!respectee[k]) {
                    ok = false;
                    dispoGroupe = dispo[k] == 0 || dispoGroupe == -1 ? -1 : Math.max(dispoGroupe, dispo[k]);
                }
            }
            if (ok) {
                respecteeGlobale = true;
            } else if (dispoGroupe > 0) {
                meilleurDispo = Math.min(meilleurDispo, dispoGroupe);
            }
            debutGroupe = i + 1;
        }
        if (!respecteeGlobale) {
            r.bloque = true;
            r.dispoA = meilleurDispo == Long.MAX_VALUE ? 0 : meilleurDispo;
            for (int i = 0; i < n; i++) {
                if (!respectee[i]) {
                    r.cause = l.conditions.get(i);
                    break;
                }
            }
        }
        return r;
    }

    /** Condition « Bloque-moi ça » lancée et pas encore écoulée, ou null. */
    Condition immediatEnCours(Limite l, long maintenant) {
        for (Condition c : l.conditions) {
            if (c.type == Condition.IMMEDIAT && maintenant < d.etat(c.id).optLong("jusqua")) {
                return c;
            }
        }
        return null;
    }

    /** Début du compte d'une condition : sa période, ou la dernière remise à zéro si elle est plus récente. */
    long debutCompte(Condition c, long maintenant) {
        return Math.max(c.periode.debut(maintenant), d.remise(c.periode.unite));
    }

    /** Ce qui est consommé du premier quota (temps, ouvertures, sessions), pour la jauge de l'accueil. */
    private void jauge(Resultat r, Limite l, List<long[]> sessions, Set<String> cibles, long maintenant) {
        for (Condition c : l.conditions) {
            if (c.type == Condition.TEMPS) {
                r.jauge = c;
                r.jaugeMax = c.valeurDuJour(maintenant) * 60_000L;
                r.jaugeFait = tempsCompte(l, cibles, debutCompte(c, maintenant), maintenant);
                return;
            }
            if (c.type == Condition.OUVERTURES || c.type == Condition.SESSIONS) {
                r.jauge = c;
                r.jaugeMax = c.valeurDuJour(maintenant);
                r.jaugeFait = compterDepuis(l, sessions, debutCompte(c, maintenant));
                return;
            }
        }
    }

    private static int compterDepuis(List<long[]> sessions, long debut) {
        int n = 0;
        for (long[] s : sessions) {
            if (s[0] >= debut) {
                n++;
            }
        }
        return n;
    }

    /** Temps sur les cibles depuis {@code debut}, seulement dans les plages si la limite le demande. */
    private long tempsCompte(Limite l, Set<String> cibles, long debut, long maintenant) {
        if (!l.dansPlage) {
            return j.temps(cibles, debut, maintenant);
        }
        long total = 0;
        for (long[] f : l.fenetres(debut, maintenant)) {
            total += j.temps(cibles, f[0], f[1]);
        }
        return total;
    }

    private static int compterDepuis(Limite l, List<long[]> sessions, long debut) {
        if (!l.dansPlage) {
            return compterDepuis(sessions, debut);
        }
        int n = 0;
        for (long[] s : sessions) {
            if (s[0] >= debut && l.sAppliqueA(s[0])) {
                n++;
            }
        }
        return n;
    }

    private boolean respectee(Limite l, Condition c, List<long[]> sessions, long[] courante, Set<String> cibles,
                              long maintenant, long tolerance, long[] detail) {
        long duree = courante == null ? 0 : maintenant - Math.max(courante[0], d.remise(Periode.HEURE));
        switch (c.type) {
            case Condition.TEMPS: {
                long utilise = tempsCompte(l, cibles, debutCompte(c, maintenant), maintenant);
                long restant = c.valeurDuJour(maintenant) * 60_000L - utilise;
                detail[0] = glissante(c) ? dispoGlissante(l, cibles, maintenant, utilise - c.valeurDuJour(maintenant) * 60_000L)
                        : c.periode.fin(maintenant);
                detail[1] = restant;
                return restant > 0;
            }
            case Condition.OUVERTURES: {
                int ouvertures = compterDepuis(l, sessions, debutCompte(c, maintenant)) + (courante == null ? 1 : 0);
                detail[0] = c.periode.fin(maintenant);
                if (glissante(c)) {
                    // Il faut que sortent de la fenêtre assez d'ouvertures anciennes.
                    int enTrop = ouvertures - c.valeurDuJour(maintenant);
                    long debut = debutCompte(c, maintenant);
                    for (long[] s : sessions) {
                        if (s[0] >= debut && --enTrop <= 0) {
                            detail[0] = s[0] + 3_600_000L;
                            break;
                        }
                    }
                }
                return ouvertures <= c.valeurDuJour(maintenant);
            }
            case Condition.DUREE_SESSION: {
                long restant = c.valeurDuJour(maintenant) * 60_000L - duree;
                detail[0] = maintenant + tolerance + 1000;
                detail[1] = courante == null ? 0 : restant;
                return courante == null || restant > 0;
            }
            case Condition.SESSIONS: {
                int nombre = compterDepuis(l, sessions, debutCompte(c, maintenant)) + (courante == null ? 1 : 0);
                if (nombre > c.valeurDuJour(maintenant)) {
                    detail[0] = c.periode.fin(maintenant);
                    return false;
                }
                long restant = c.valeur2 * 60_000L - duree;
                detail[0] = maintenant + tolerance + 1000;
                detail[1] = courante == null ? 0 : restant;
                return courante == null || restant > 0;
            }
            case Condition.PAUSE:
            case Condition.PAUSE_PROPORTIONNELLE: {
                int indexPrecedente = sessions.size() - (courante == null ? 1 : 2);
                if (indexPrecedente < 0) {
                    return true;
                }
                long[] precedente = sessions.get(indexPrecedente);
                if (precedente[1] < d.remise(Periode.HEURE)) {
                    return true; // remise à zéro depuis : plus de pause due
                }
                long attente = c.type == Condition.PAUSE ? c.valeur * 60_000L
                        : (long) ((precedente[1] - precedente[0]) * c.coef);
                long reference = courante == null ? maintenant : courante[0];
                detail[0] = precedente[1] + attente;
                return reference - precedente[1] >= attente;
            }
            case Condition.FRICTION:
                return courante == null || passeValide(d.etat(c.id), courante, maintenant);
            case Condition.IMMEDIAT: {
                long jusqua = d.etat(c.id).optLong("jusqua");
                detail[0] = jusqua;
                return maintenant >= jusqua;
            }
            case Condition.NFC: {
                JSONObject e = d.etat(c.id);
                if (c.modeNfc == Condition.NFC_SESSION) {
                    return courante == null ? maintenant < e.optLong("fenetre") : passeValide(e, courante, maintenant);
                }
                return maintenant < e.optLong("jusqua");
            }
            default:
                return true;
        }
    }

    private static boolean glissante(Condition c) {
        return c.periode.unite == Periode.HEURE && c.periode.glissante;
    }

    /** Heure glissante : quand assez de temps ancien sera sorti de la fenêtre d'une heure. */
    private long dispoGlissante(Limite l, Set<String> cibles, long maintenant, long enTrop) {
        long aSortir = Math.max(1, enTrop + 1000);
        long debut = Math.max(maintenant - 3_600_000L, d.remise(Periode.HEURE));
        for (Journal.Intervalle i : j.entre(cibles, debut, maintenant)) {
            long a = Math.max(i.debut, debut);
            long b = Math.min(i.fin, maintenant);
            if (b - a >= aSortir) {
                return a + aSortir + 3_600_000L;
            }
            aSortir -= Math.max(0, b - a);
        }
        return maintenant + 3_600_000L;
    }

    /**
     * Une passe (friction franchie, badge bipé) ouvre une fenêtre d'une minute
     * pour démarrer une session ; elle vaut ensuite pour toute cette session.
     */
    private static boolean passeValide(JSONObject etat, long[] courante, long maintenant) {
        if (etat.optLong("session", -1) == courante[0]) {
            return true;
        }
        if (maintenant < etat.optLong("fenetre")) {
            try {
                etat.put("session", courante[0]);
            } catch (Exception ignore) {
                // clé non nulle
            }
            return true;
        }
        return false;
    }

    // ---- Rallonges ---------------------------------------------------------

    int rallongesRestantes(Limite l, long maintenant) {
        if (l.rallongeMinutes <= 0) {
            return 0;
        }
        Periode ref = l.periodeReference(d);
        long debut = Math.max(ref.debut(maintenant), d.remise(ref.unite));
        JSONArray prises = d.etat(l.id).optJSONArray("rallonges");
        int utilisees = 0;
        for (int i = 0; prises != null && i < prises.length(); i++) {
            if (prises.optLong(i) >= debut) {
                utilisees++;
            }
        }
        return Math.max(0, l.rallongeNombre - utilisees);
    }

    void accorderRallonge(Limite l, long maintenant) {
        try {
            JSONObject e = d.etat(l.id);
            e.put("rallonge", maintenant + l.rallongeMinutes * 60_000L);
            JSONArray prises = e.optJSONArray("rallonges");
            JSONArray gardees = new JSONArray();
            long limite = maintenant - 8L * 86_400_000L;
            for (int i = 0; prises != null && i < prises.length(); i++) {
                if (prises.optLong(i) >= limite) {
                    gardees.put(prises.optLong(i));
                }
            }
            e.put("rallonges", gardees.put(maintenant));
        } catch (Exception ignore) {
            // clés non nulles
        }
        d.compter(l.id, Donnees.RALLONGES);
        d.enregistrer();
    }

    /** La friction a été franchie : une minute pour ouvrir l'appli. */
    void accorderPasse(Condition c, long maintenant) {
        try {
            d.etat(c.id).put("fenetre", maintenant + 60_000L);
        } catch (Exception ignore) {
            // clé non nulle
        }
        d.enregistrer();
    }

    void lancerBlocageImmediat(Condition c, long maintenant) {
        try {
            d.etat(c.id).put("jusqua", maintenant + c.valeur * 60_000L);
        } catch (Exception ignore) {
            // clé non nulle
        }
        d.enregistrer();
    }
}
