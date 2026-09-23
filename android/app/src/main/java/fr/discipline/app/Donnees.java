package fr.discipline.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Tout le réglage de l'appli, en mémoire et enregistré d'un bloc en JSON dans
 * les SharedPreferences. Une seule instance, partagée par les écrans et le
 * service d'accessibilité (même processus).
 */
final class Donnees {
    private static final String PREFS = "discipline_v1";
    private static final String CLE = "donnees";
    private static Donnees instance;

    static class Groupe {
        String id = nouvelId();
        String nom = "";
        Set<String> paquets = new TreeSet<>();

        JSONObject json() throws Exception {
            return new JSONObject().put("id", id).put("nom", nom).put("paquets", new JSONArray(paquets));
        }

        static Groupe de(JSONObject o) {
            Groupe g = new Groupe();
            g.id = o.optString("id", g.id);
            g.nom = o.optString("nom");
            lireChaines(o.optJSONArray("paquets"), g.paquets);
            return g;
        }
    }

    static class Profil {
        String id = nouvelId();
        String nom = "";
        Set<String> limites = new TreeSet<>();
    }

    final Context contexte;
    private final SharedPreferences prefs;

    final List<Limite> limites = new ArrayList<>();
    final Map<String, Groupe> groupes = new LinkedHashMap<>();
    final List<Profil> profils = new ArrayList<>();
    String profilActif;
    final List<String> messages = new ArrayList<>();
    /** Identifiant de puce → nom du badge. */
    final Map<String, String> badges = new TreeMap<>();

    int toleranceSecondes = 5;
    int debutJourneeMinutes;
    final Set<String> suiviesApplis = new TreeSet<>();
    final Set<String> suiviesGroupes = new TreeSet<>();
    long vacancesJusquA;

    int delaiAssouplissement;
    boolean nfcPourModifier;
    boolean alerteAccessibilite;

    /** null tant que l'appli n'a jamais relevé la liste des applis installées. */
    Set<String> applisConnues;
    final List<JSONObject> enAttente = new ArrayList<>();
    /** États des conditions et limites : déblocages NFC, passes, rallonges. */
    JSONObject etats = new JSONObject();
    /** "aaaaMMjj" → {idLimite: [ouvertures autorisées, bloquées, rallonges]}. */
    JSONObject compteurs = new JSONObject();

    static synchronized Donnees get(Context c) {
        if (instance == null) {
            instance = new Donnees(c.getApplicationContext());
        }
        return instance;
    }

    private Donnees(Context contexte) {
        this.contexte = contexte;
        prefs = contexte.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            lire(new JSONObject(prefs.getString(CLE, "{}")));
        } catch (Exception e) {
            // réglage illisible : on repart d'un réglage vide plutôt que de planter
        }
    }

    static String nouvelId() {
        return Long.toString(System.currentTimeMillis(), 36) + Integer.toString(new Random().nextInt(1296), 36);
    }

    static void lireChaines(JSONArray a, Collection<String> dans) {
        for (int i = 0; a != null && i < a.length(); i++) {
            dans.add(a.optString(i));
        }
    }

    private void lire(JSONObject o) throws Exception {
        JSONArray ls = o.optJSONArray("limites");
        for (int i = 0; ls != null && i < ls.length(); i++) {
            limites.add(Limite.de(ls.getJSONObject(i)));
        }
        JSONArray gs = o.optJSONArray("groupes");
        for (int i = 0; gs != null && i < gs.length(); i++) {
            Groupe g = Groupe.de(gs.getJSONObject(i));
            groupes.put(g.id, g);
        }
        JSONArray ps = o.optJSONArray("profils");
        for (int i = 0; ps != null && i < ps.length(); i++) {
            JSONObject p = ps.getJSONObject(i);
            Profil profil = new Profil();
            profil.id = p.optString("id", profil.id);
            profil.nom = p.optString("nom");
            lireChaines(p.optJSONArray("limites"), profil.limites);
            profils.add(profil);
        }
        profilActif = o.optString("profilActif", null);
        lireChaines(o.optJSONArray("messages"), messages);
        JSONObject bs = o.optJSONObject("badges");
        if (bs != null) {
            for (Iterator<String> it = bs.keys(); it.hasNext(); ) {
                String id = it.next();
                badges.put(id, bs.optString(id));
            }
        }
        toleranceSecondes = o.optInt("tolerance", 5);
        debutJourneeMinutes = o.optInt("debutJournee");
        lireChaines(o.optJSONArray("suiviesApplis"), suiviesApplis);
        lireChaines(o.optJSONArray("suiviesGroupes"), suiviesGroupes);
        vacancesJusquA = o.optLong("vacances");
        delaiAssouplissement = o.optInt("delai");
        nfcPourModifier = o.optBoolean("nfcModif");
        alerteAccessibilite = o.optBoolean("alerte");
        JSONArray connues = o.optJSONArray("connues");
        if (connues != null) {
            applisConnues = new HashSet<>();
            lireChaines(connues, applisConnues);
        }
        JSONArray att = o.optJSONArray("enAttente");
        for (int i = 0; att != null && i < att.length(); i++) {
            enAttente.add(att.getJSONObject(i));
        }
        etats = o.optJSONObject("etats") != null ? o.getJSONObject("etats") : new JSONObject();
        compteurs = o.optJSONObject("compteurs") != null ? o.getJSONObject("compteurs") : new JSONObject();
        String plusVieux = cleJour(System.currentTimeMillis() - 40L * 86_400_000L);
        List<String> aRetirer = new ArrayList<>();
        for (Iterator<String> it = compteurs.keys(); it.hasNext(); ) {
            String jour = it.next();
            if (jour.compareTo(plusVieux) < 0) {
                aRetirer.add(jour);
            }
        }
        for (String jour : aRetirer) {
            compteurs.remove(jour);
        }
    }

    synchronized void enregistrer() {
        try {
            JSONObject o = new JSONObject();
            JSONArray ls = new JSONArray();
            for (Limite l : limites) {
                ls.put(l.json());
            }
            o.put("limites", ls);
            JSONArray gs = new JSONArray();
            for (Groupe g : groupes.values()) {
                gs.put(g.json());
            }
            o.put("groupes", gs);
            JSONArray ps = new JSONArray();
            for (Profil p : profils) {
                ps.put(new JSONObject().put("id", p.id).put("nom", p.nom).put("limites", new JSONArray(p.limites)));
            }
            o.put("profils", ps).put("profilActif", profilActif).put("messages", new JSONArray(messages))
                    .put("badges", new JSONObject(badges)).put("tolerance", toleranceSecondes)
                    .put("debutJournee", debutJourneeMinutes).put("suiviesApplis", new JSONArray(suiviesApplis))
                    .put("suiviesGroupes", new JSONArray(suiviesGroupes)).put("vacances", vacancesJusquA)
                    .put("delai", delaiAssouplissement).put("nfcModif", nfcPourModifier)
                    .put("alerte", alerteAccessibilite).put("enAttente", new JSONArray(enAttente))
                    .put("etats", etats).put("compteurs", compteurs);
            if (applisConnues != null) {
                o.put("connues", new JSONArray(applisConnues));
            }
            prefs.edit().putString(CLE, o.toString()).apply();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- Accès -------------------------------------------------------------

    Limite limite(String id) {
        for (Limite l : limites) {
            if (l.id.equals(id)) {
                return l;
            }
        }
        return null;
    }

    Profil profil(String id) {
        for (Profil p : profils) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    Set<String> paquetsDe(Set<String> applis, Set<String> idsGroupes) {
        Set<String> paquets = new HashSet<>(applis);
        for (String g : idsGroupes) {
            Groupe groupe = groupes.get(g);
            if (groupe != null) {
                paquets.addAll(groupe.paquets);
            }
        }
        return paquets;
    }

    Set<String> cibles(Limite l) {
        return paquetsDe(l.applis, l.groupes);
    }

    List<Groupe> groupesDe(String paquet) {
        List<Groupe> liste = new ArrayList<>();
        for (Groupe g : groupes.values()) {
            if (g.paquets.contains(paquet)) {
                liste.add(g);
            }
        }
        return liste;
    }

    /** Vrai si le groupe sert à une limite active : le modifier peut alors assouplir. */
    boolean groupeUtilise(String id) {
        for (Limite l : limites) {
            if (l.active && l.groupes.contains(id)) {
                return true;
            }
        }
        return false;
    }

    boolean enVacances(long maintenant) {
        return maintenant < vacancesJusquA;
    }

    JSONObject etat(String id) {
        JSONObject e = etats.optJSONObject(id);
        if (e == null) {
            e = new JSONObject();
            try {
                etats.put(id, e);
            } catch (Exception ignore) {
                // clé non nulle : ne peut pas échouer
            }
        }
        return e;
    }

    // ---- Journées et compteurs ---------------------------------------------

    long debutJournee(long maintenant) {
        Periode p = new Periode();
        p.debutMinutes = debutJourneeMinutes;
        return p.debut(maintenant);
    }

    String cleJour(long ms) {
        return new SimpleDateFormat("yyyyMMdd", Locale.FRANCE).format(new Date(debutJournee(ms)));
    }

    static final int AUTORISEES = 0;
    static final int BLOQUEES = 1;
    static final int RALLONGES = 2;

    synchronized void compter(String idLimite, int quoi) {
        try {
            String jour = cleJour(System.currentTimeMillis());
            JSONObject duJour = compteurs.optJSONObject(jour);
            if (duJour == null) {
                duJour = new JSONObject();
                compteurs.put(jour, duJour);
            }
            JSONArray c = duJour.optJSONArray(idLimite);
            if (c == null) {
                c = new JSONArray().put(0).put(0).put(0);
                duJour.put(idLimite, c);
            }
            c.put(quoi, c.optInt(quoi) + 1);
        } catch (Exception ignore) {
            // compteur perdu, sans conséquence sur le blocage
        }
    }

    int compteur(String idLimite, String jour, int quoi) {
        JSONObject duJour = compteurs.optJSONObject(jour);
        JSONArray c = duJour == null ? null : duJour.optJSONArray(idLimite);
        return c == null ? 0 : c.optInt(quoi);
    }

    // ---- Changements (appliqués tout de suite ou différés par l'anti-triche) --

    static JSONObject changement(String type, String id) {
        try {
            return new JSONObject().put("type", type).put("id", id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static JSONObject changementLimite(Limite l, boolean suppression) {
        try {
            JSONObject ch = changement("limite", l.id);
            return suppression ? ch : ch.put("limite", l.json());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    synchronized void appliquer(JSONObject ch) {
        String id = ch.optString("id");
        switch (ch.optString("type")) {
            case "limite": {
                Limite ancienne = limite(id);
                JSONObject lj = ch.optJSONObject("limite");
                if (lj == null) {
                    limites.remove(ancienne);
                    for (Profil p : profils) {
                        p.limites.remove(id);
                    }
                } else if (ancienne != null) {
                    limites.set(limites.indexOf(ancienne), Limite.de(lj));
                } else {
                    limites.add(Limite.de(lj));
                }
                break;
            }
            case "active": {
                Limite l = limite(id);
                if (l != null) {
                    l.active = ch.optBoolean("valeur");
                }
                break;
            }
            case "vacances":
                vacancesJusquA = ch.optLong("jusqua");
                break;
            case "profil": {
                Profil p = profil(id);
                if (p != null) {
                    profilActif = p.id;
                    for (Limite l : limites) {
                        l.active = p.limites.contains(l.id);
                    }
                }
                break;
            }
            case "groupe": {
                JSONObject gj = ch.optJSONObject("groupe");
                if (gj == null) {
                    groupes.remove(id);
                    for (Limite l : limites) {
                        l.groupes.remove(id);
                    }
                    suiviesGroupes.remove(id);
                } else {
                    groupes.put(id, Groupe.de(gj));
                }
                break;
            }
            case "antitriche":
                delaiAssouplissement = ch.optInt("delai");
                nfcPourModifier = ch.optBoolean("nfc");
                alerteAccessibilite = ch.optBoolean("alerte");
                break;
            default:
                break;
        }
        enregistrer();
    }

    synchronized void differer(JSONObject ch, String texte) {
        try {
            ch.put("a", System.currentTimeMillis() + delaiAssouplissement * 60_000L).put("texte", texte);
        } catch (Exception ignore) {
            // clés non nulles
        }
        enAttente.add(ch);
        enregistrer();
    }

    /** Applique les changements différés dont l'heure est venue. */
    synchronized void appliquerEnAttente() {
        long maintenant = System.currentTimeMillis();
        boolean fait = false;
        for (Iterator<JSONObject> it = enAttente.iterator(); it.hasNext(); ) {
            JSONObject ch = it.next();
            if (ch.optLong("a") <= maintenant) {
                it.remove();
                appliquer(ch);
                fait = true;
            }
        }
        if (fait) {
            enregistrer();
        }
    }

    synchronized void annulerEnAttente(JSONObject ch) {
        enAttente.remove(ch);
        enregistrer();
    }

    // ---- NFC ---------------------------------------------------------------

    /** Débloque toutes les conditions « badge NFC » ; renvoie le nombre de limites concernées. */
    synchronized int debloquerParBadge(long maintenant) {
        int nombre = 0;
        try {
            for (Limite l : limites) {
                boolean touchee = false;
                for (Condition c : l.conditions) {
                    if (c.type != Condition.NFC) {
                        continue;
                    }
                    JSONObject e = etat(c.id);
                    if (c.modeNfc == Condition.NFC_SESSION) {
                        e.put("fenetre", maintenant + 60_000L);
                    } else if (c.modeNfc == Condition.NFC_MINUTES) {
                        e.put("jusqua", maintenant + c.valeur2 * 60_000L);
                    } else {
                        e.put("jusqua", c.periode.fin(maintenant));
                    }
                    touchee = true;
                }
                if (touchee) {
                    nombre++;
                }
            }
        } catch (Exception ignore) {
            // clés non nulles
        }
        enregistrer();
        return nombre;
    }

    static String dateCourte(long ms) {
        return new SimpleDateFormat("EEE d MMM HH:mm", Locale.FRANCE).format(new Date(ms));
    }
}
