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
        if (d.enVacances(maintenant)) {
            return liste;
        }
        for (Limite l : d.limites) {
            if (l.active && !l.conditions.isEmpty() && l.sAppliqueA(maintenant) && d.cibles(l).contains(paquet)) {
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

        if (!l.sAppliqueA(maintenant) || d.enVacances(maintenant) || l.conditions.isEmpty()) {
            return r;
        }
        long rallonge = d.etat(l.id).optLong("rallonge");
        if (maintenant < rallonge) {
            r.rallongeEnCours = true;
            r.restant = rallonge - maintenant;
            return r;
        }

        int n = l.conditions.size();
        boolean[] respectee = new boolean[n];
        long[] dispo = new long[n];
        for (int i = 0; i < n; i++) {
            Condition c = l.conditions.get(i);
            long[] detail = new long[2]; // {dispoA, restant}
            respectee[i] = respectee(c, sessions, courante, cibles, maintenant, tolerance, detail);
            dispo[i] = detail[0];
            if (respectee[i] && detail[1] > 0 && detail[1] < r.restant) {
                r.restant = detail[1];
            }
        }

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

    private static int compterDepuis(List<long[]> sessions, long debut) {
        int n = 0;
        for (long[] s : sessions) {
            if (s[0] >= debut) {
                n++;
            }
        }
        return n;
    }

    private boolean respectee(Condition c, List<long[]> sessions, long[] courante, Set<String> cibles,
                              long maintenant, long tolerance, long[] detail) {
        long duree = courante == null ? 0 : maintenant - courante[0];
        switch (c.type) {
            case Condition.TEMPS: {
                long utilise = j.temps(cibles, c.periode.debut(maintenant), maintenant);
                long restant = c.valeurDuJour(maintenant) * 60_000L - utilise;
                detail[0] = c.periode.fin(maintenant);
                detail[1] = restant;
                return restant > 0;
            }
            case Condition.OUVERTURES: {
                int ouvertures = compterDepuis(sessions, c.periode.debut(maintenant)) + (courante == null ? 1 : 0);
                detail[0] = c.periode.fin(maintenant);
                return ouvertures <= c.valeurDuJour(maintenant);
            }
            case Condition.DUREE_SESSION: {
                long restant = c.valeurDuJour(maintenant) * 60_000L - duree;
                detail[0] = maintenant + tolerance + 1000;
                detail[1] = courante == null ? 0 : restant;
                return courante == null || restant > 0;
            }
            case Condition.SESSIONS: {
                int nombre = compterDepuis(sessions, c.periode.debut(maintenant)) + (courante == null ? 1 : 0);
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
        long debut = l.periodeReference(d).debut(maintenant);
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
