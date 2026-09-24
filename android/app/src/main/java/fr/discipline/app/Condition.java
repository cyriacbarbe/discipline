package fr.discipline.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Une condition d'une limite : l'un des neuf types du cahier des charges, avec ses réglages. */
class Condition {
    static final int TEMPS = 1;
    static final int OUVERTURES = 2;
    static final int DUREE_SESSION = 3;
    static final int SESSIONS = 4;
    static final int PAUSE = 5;
    static final int PAUSE_PROPORTIONNELLE = 6;
    static final int FRICTION = 7;
    static final int IMMEDIAT = 8;
    static final int NFC = 9;
    static final int NOMBRE_TYPES = 9;

    static final int NFC_SESSION = 0;
    static final int NFC_MINUTES = 1;
    static final int NFC_FIN_PERIODE = 2;

    String id = Donnees.nouvelId();
    int type;
    /** Minutes (temps, durée, pause, blocage), ouvertures, sessions ou secondes (friction) selon le type. */
    int valeur;
    /** SESSIONS : durée max d'une session (min). NFC : minutes de déblocage. */
    int valeur2;
    double coef = 1;
    /** Lundi … dimanche, -1 = valeur par défaut ; null = la même tous les jours. */
    int[] parJour;
    Periode periode = new Periode();
    int modeNfc = NFC_SESSION;
    /** NFC : badges qui débloquent cette condition (vide = n'importe quel badge enregistré). */
    Set<String> badges = new TreeSet<>();
    /** FRICTION : ce qu'il faut franchir. */
    int modeFriction = FRICTION_ATTENTE;
    /** FRICTION : l'attente double à chaque ouverture de la journée. */
    boolean doubler;

    static final int FRICTION_ATTENTE = 0;
    static final int FRICTION_PHRASE = 1;
    static final int FRICTION_POURQUOI = 2;
    static final int FRICTION_CALCUL = 3;
    static final String[] MODES_FRICTION = {"Compte à rebours", "Recopier une phrase", "Dire pourquoi", "Calcul mental"};

    /**
     * Quota qui se consomme (temps, ouvertures, sessions, pauses) : c'est ce
     * qu'une rallonge lève, et rien d'autre.
     */
    boolean estQuota() {
        return type == TEMPS || type == OUVERTURES || type == DUREE_SESSION || type == SESSIONS
                || type == PAUSE || type == PAUSE_PROPORTIONNELLE;
    }

    /** Valeur d'un jour de la semaine (lundi = 0), la valeur commune à défaut. */
    private int valeurJour(int index) {
        return parJour == null || !aValeurParJour() || parJour[index] < 0 ? valeur : parJour[index];
    }

    /**
     * Cette version de la condition ne laisse jamais passer plus que {@code a} :
     * même condition, réglages égaux ou plus durs. Sert à appliquer tout de
     * suite un durcissement, sans le délai de l'anti-triche.
     */
    boolean aussiStricteQue(Condition a) {
        if (type != a.type || !id.equals(a.id)) {
            return false;
        }
        try {
            if (aPeriode() != a.aPeriode() || aPeriode() && !periode.json().toString().equals(a.periode.json().toString())) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        switch (type) {
            case TEMPS:
            case OUVERTURES:
            case DUREE_SESSION:
            case SESSIONS:
                for (int i = 0; i < 7; i++) {
                    if (valeurJour(i) > a.valeurJour(i)) {
                        return false;
                    }
                }
                return type != SESSIONS || valeur2 <= a.valeur2;
            case PAUSE:
            case IMMEDIAT:
                return valeur >= a.valeur;
            case PAUSE_PROPORTIONNELLE:
                return coef >= a.coef;
            case FRICTION:
                return modeFriction == a.modeFriction && valeur >= a.valeur && (doubler || !a.doubler);
            case NFC:
                return modeNfc == a.modeNfc && (modeNfc != NFC_MINUTES || valeur2 <= a.valeur2)
                        && (a.badges.isEmpty() || !badges.isEmpty() && a.badges.containsAll(badges));
            default:
                return false;
        }
    }

    /** Attente de la friction pour la n-ième ouverture du jour (n ≥ 1), plafonnée à 10 min. */
    int secondesFriction(int ouverture) {
        long s = valeur;
        for (int i = 1; doubler && i < ouverture && s < 600; i++) {
            s *= 2;
        }
        return (int) Math.min(s, Math.max(valeur, 600));
    }

    static Condition nouvelle(int type) {
        Condition c = new Condition();
        c.type = type;
        switch (type) {
            case TEMPS: c.valeur = 30; break;
            case OUVERTURES: c.valeur = 10; break;
            case DUREE_SESSION: c.valeur = 15; break;
            case SESSIONS: c.valeur = 3; c.valeur2 = 20; break;
            case PAUSE: c.valeur = 30; break;
            case FRICTION: c.valeur = 15; break;
            case IMMEDIAT: c.valeur = 60; break;
            case NFC: c.valeur2 = 15; break;
            default: break;
        }
        return c;
    }

    static String nomType(int type) {
        switch (type) {
            case TEMPS: return "Temps par période";
            case OUVERTURES: return "Ouvertures par période";
            case DUREE_SESSION: return "Durée de session";
            case SESSIONS: return "Sessions autorisées";
            case PAUSE: return "Pause après usage";
            case PAUSE_PROPORTIONNELLE: return "Pause proportionnelle";
            case FRICTION: return "Friction à l’ouverture";
            case IMMEDIAT: return "Blocage immédiat";
            case NFC: return "Badge NFC";
            default: return "?";
        }
    }

    static String descriptionType(int type) {
        switch (type) {
            case TEMPS: return "Pas plus de X min par heure, jour ou semaine.";
            case OUVERTURES: return "Pas plus de N ouvertures par heure, jour ou semaine.";
            case DUREE_SESSION: return "Jamais plus de X min d’affilée.";
            case SESSIONS: return "N sessions par période, chacune d’une durée maximale.";
            case PAUSE: return "Après avoir fermé l’appli, attendre X min.";
            case PAUSE_PROPORTIONNELLE: return "Attente = temps passé × coefficient.";
            case FRICTION: return "Compte à rebours de X s avant d’accéder à l’appli.";
            case IMMEDIAT: return "« Bloque-moi ça pendant X », lancé à la main.";
            case NFC: return "Fermée tant que la puce n’est pas bipée.";
            default: return "";
        }
    }

    boolean aPeriode() {
        return type == TEMPS || type == OUVERTURES || type == SESSIONS
                || (type == NFC && modeNfc == NFC_FIN_PERIODE);
    }

    boolean aValeurParJour() {
        // Sur une semaine, une valeur « du jour » n'a pas de sens : le compte court sur sept jours.
        boolean semaine = aPeriode() && periode.unite == Periode.SEMAINE;
        return !semaine && (type == TEMPS || type == OUVERTURES || type == DUREE_SESSION || type == SESSIONS);
    }

    int valeurDuJour(long maintenant) {
        if (parJour == null || !aValeurParJour()) {
            return valeur;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(maintenant);
        int v = parJour[Periode.indexJour(c.get(Calendar.DAY_OF_WEEK))];
        return v < 0 ? valeur : v;
    }

    String resume() {
        String variantes = parJour != null && aValeurParJour() ? " (varie selon le jour)" : "";
        switch (type) {
            case TEMPS: return valeur + " min " + periode.libelle() + variantes;
            case OUVERTURES: return valeur + " ouverture" + (valeur > 1 ? "s " : " ") + periode.libelle() + variantes;
            case DUREE_SESSION: return valeur + " min d’affilée au plus" + variantes;
            case SESSIONS: return valeur + " session" + (valeur > 1 ? "s" : "") + " de " + valeur2 + " min " + periode.libelle() + variantes;
            case PAUSE: return "pause de " + valeur + " min après usage";
            case PAUSE_PROPORTIONNELLE: return "pause = temps passé × " + String.format(Locale.FRANCE, "%.2g", coef);
            case FRICTION:
                if (modeFriction == FRICTION_PHRASE) {
                    return "recopier une phrase avant d’ouvrir";
                }
                if (modeFriction == FRICTION_POURQUOI) {
                    return "dire pourquoi avant d’ouvrir";
                }
                if (modeFriction == FRICTION_CALCUL) {
                    return "calcul mental avant d’ouvrir";
                }
                return "compte à rebours de " + valeur + " s" + (doubler ? ", qui double à chaque ouverture" : "");
            case IMMEDIAT: return "blocage de " + valeur + " min à la demande";
            case NFC:
                return (badges.isEmpty() ? "badge NFC" : badges.size() == 1 ? "1 badge précis" : badges.size() + " badges précis")
                        + ", déblocage : " + (modeNfc == NFC_SESSION ? "une session"
                        : modeNfc == NFC_MINUTES ? valeur2 + " min" : "jusqu’à la fin de la période (" + periode.libelle() + ")");
            default: return "";
        }
    }

    JSONObject json() throws Exception {
        JSONObject o = new JSONObject().put("id", id).put("t", type).put("v", valeur).put("v2", valeur2)
                .put("c", coef).put("p", periode.json()).put("n", modeNfc).put("b", new JSONArray(badges))
                .put("f", modeFriction).put("fd", doubler);
        if (parJour != null) {
            JSONArray a = new JSONArray();
            for (int v : parJour) {
                a.put(v);
            }
            o.put("j", a);
        }
        return o;
    }

    static Condition de(JSONObject o) {
        Condition c = new Condition();
        c.id = o.optString("id", c.id);
        c.type = o.optInt("t", TEMPS);
        c.valeur = o.optInt("v");
        c.valeur2 = o.optInt("v2");
        c.coef = o.optDouble("c", 1);
        c.periode = Periode.de(o.optJSONObject("p"));
        c.modeNfc = o.optInt("n");
        JSONArray b = o.optJSONArray("b");
        for (int i = 0; b != null && i < b.length(); i++) {
            c.badges.add(b.optString(i));
        }
        c.modeFriction = o.optInt("f");
        c.doubler = o.optBoolean("fd");
        JSONArray a = o.optJSONArray("j");
        if (a != null && a.length() == 7) {
            c.parJour = new int[7];
            for (int i = 0; i < 7; i++) {
                c.parJour[i] = a.optInt(i, -1);
            }
        }
        return c;
    }
}
