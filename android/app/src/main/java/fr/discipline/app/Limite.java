package fr.discipline.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Limite = cibles (applis et groupes) + conditions combinées par ET/OU +
 * exceptions (jours, plages horaires, dates) + action quand elle est atteinte.
 */
class Limite {
    static final int FERMER = 0;
    static final int AUTRE_APPLI = 1;
    static final int ECRAN = 2;

    String id = Donnees.nouvelId();
    String nom = "";
    boolean active = true;
    Set<String> applis = new TreeSet<>();
    Set<String> groupes = new TreeSet<>();
    List<Condition> conditions = new ArrayList<>();
    /** Opérateur entre la condition i et i+1 : true = ET, false = OU. */
    List<Boolean> et = new ArrayList<>();

    /** Jours où la limite s'applique, bit 0 = lundi. */
    int jours = 0x7F;
    /** Plages où elle s'applique (minutes début, fin) ; vide = toute la journée. */
    List<int[]> plages = new ArrayList<>();
    /** Périodes où elle ne s'applique pas (du, au : débuts de journée). */
    List<long[]> datesExclues = new ArrayList<>();

    int action = ECRAN;
    String appliAlternative;
    List<String> messages = new ArrayList<>();
    String image;
    boolean afficherDispo = true;

    int rallongeMinutes;
    int rallongeNombre = 1;
    int rallongeAttente;
    boolean rallongeNfc;

    /** Seuils des bulles de temps restant, en minutes. */
    List<Integer> bulles = new ArrayList<>();

    boolean sAppliqueA(long maintenant) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(maintenant);
        if ((jours & (1 << Periode.indexJour(c.get(Calendar.DAY_OF_WEEK)))) == 0) {
            return false;
        }
        for (long[] d : datesExclues) {
            if (maintenant >= d[0] && maintenant < d[1] + 86_400_000L) {
                return false;
            }
        }
        if (plages.isEmpty()) {
            return true;
        }
        int minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        for (int[] p : plages) {
            boolean dedans = p[0] <= p[1] ? minute >= p[0] && minute < p[1] : minute >= p[0] || minute < p[1];
            if (dedans) {
                return true;
            }
        }
        return false;
    }

    Condition conditionDeType(int type) {
        for (Condition c : conditions) {
            if (c.type == type) {
                return c;
            }
        }
        return null;
    }

    /** Période de référence (rallonges, graphes) : celle de la première condition qui en a une, sinon le jour. */
    Periode periodeReference(Donnees d) {
        for (Condition c : conditions) {
            if (c.aPeriode()) {
                return c.periode;
            }
        }
        Periode p = new Periode();
        p.debutMinutes = d.debutJourneeMinutes;
        return p;
    }

    String resumeConditions() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) {
                s.append(et.get(i - 1) ? " ET " : " OU ");
            }
            s.append(conditions.get(i).resume());
        }
        return s.toString();
    }

    String nomAffiche(Donnees d) {
        if (!nom.trim().isEmpty()) {
            return nom.trim();
        }
        List<String> noms = new ArrayList<>();
        for (String g : groupes) {
            Donnees.Groupe groupe = d.groupes.get(g);
            if (groupe != null) {
                noms.add(groupe.nom);
            }
        }
        for (String p : applis) {
            noms.add(Applications.nom(d.contexte, p));
        }
        if (noms.isEmpty()) {
            return conditions.isEmpty() ? "Limite" : Condition.nomType(conditions.get(0).type);
        }
        String texte = String.join(", ", noms.subList(0, Math.min(3, noms.size())));
        return noms.size() > 3 ? texte + "…" : texte;
    }

    Limite copie() {
        try {
            return de(json());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    JSONObject json() throws Exception {
        JSONObject o = new JSONObject().put("id", id).put("nom", nom).put("active", active)
                .put("applis", new JSONArray(applis)).put("groupes", new JSONArray(groupes))
                .put("jours", jours).put("action", action).put("alt", appliAlternative)
                .put("messages", new JSONArray(messages)).put("image", image).put("dispo", afficherDispo)
                .put("rMin", rallongeMinutes).put("rNb", rallongeNombre).put("rAtt", rallongeAttente)
                .put("rNfc", rallongeNfc).put("bulles", new JSONArray(bulles));
        JSONArray cs = new JSONArray();
        for (Condition c : conditions) {
            cs.put(c.json());
        }
        o.put("conditions", cs).put("et", new JSONArray(et));
        JSONArray ps = new JSONArray();
        for (int[] p : plages) {
            ps.put(new JSONArray().put(p[0]).put(p[1]));
        }
        o.put("plages", ps);
        JSONArray ds = new JSONArray();
        for (long[] d : datesExclues) {
            ds.put(new JSONArray().put(d[0]).put(d[1]));
        }
        return o.put("dates", ds);
    }

    static Limite de(JSONObject o) {
        Limite l = new Limite();
        l.id = o.optString("id", l.id);
        l.nom = o.optString("nom");
        l.active = o.optBoolean("active", true);
        Donnees.lireChaines(o.optJSONArray("applis"), l.applis);
        Donnees.lireChaines(o.optJSONArray("groupes"), l.groupes);
        l.jours = o.optInt("jours", 0x7F);
        l.action = o.optInt("action", ECRAN);
        l.appliAlternative = o.isNull("alt") ? null : o.optString("alt", null);
        Donnees.lireChaines(o.optJSONArray("messages"), l.messages);
        l.image = o.isNull("image") ? null : o.optString("image", null);
        l.afficherDispo = o.optBoolean("dispo", true);
        l.rallongeMinutes = o.optInt("rMin");
        l.rallongeNombre = o.optInt("rNb", 1);
        l.rallongeAttente = o.optInt("rAtt");
        l.rallongeNfc = o.optBoolean("rNfc");
        JSONArray b = o.optJSONArray("bulles");
        for (int i = 0; b != null && i < b.length(); i++) {
            l.bulles.add(b.optInt(i));
        }
        JSONArray cs = o.optJSONArray("conditions");
        for (int i = 0; cs != null && i < cs.length(); i++) {
            l.conditions.add(Condition.de(cs.optJSONObject(i)));
        }
        JSONArray et = o.optJSONArray("et");
        for (int i = 0; i < l.conditions.size() - 1; i++) {
            l.et.add(et == null || et.optBoolean(i, true));
        }
        JSONArray ps = o.optJSONArray("plages");
        for (int i = 0; ps != null && i < ps.length(); i++) {
            JSONArray p = ps.optJSONArray(i);
            l.plages.add(new int[]{p.optInt(0), p.optInt(1)});
        }
        JSONArray ds = o.optJSONArray("dates");
        for (int i = 0; ds != null && i < ds.length(); i++) {
            JSONArray d = ds.optJSONArray(i);
            l.datesExclues.add(new long[]{d.optLong(0), d.optLong(1)});
        }
        return l;
    }
}
