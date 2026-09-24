package fr.discipline.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
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
        /** Allument ses limites tant que l'un d'eux est vrai (voir {@link Declencheurs}). */
        List<Declencheurs.Declencheur> declencheurs = new ArrayList<>();
    }

    /** Limites allumées par un déclencheur, qu'on éteindra quand il cessera. */
    final Set<String> auto = new TreeSet<>();

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
    /** Widget d'accueil : ce qu'il montre, et les limites qu'il tait. */
    boolean widgetTemps = true;
    boolean widgetLimites = true;
    boolean widgetConcentration = true;
    final Set<String> widgetCachees = new TreeSet<>();

    int delaiAssouplissement;
    boolean nfcPourModifier;
    boolean alerteAccessibilite;
    /** Mode strict : les pages des Réglages qui arrêteraient Discipline se referment. */
    boolean modeStrict;
    /** Personne de confiance : ses codes à usage unique (hachés) remplacent le délai. */
    String confianceNom = "";
    String confianceNumero = "";
    final List<String> confianceCodes = new ArrayList<>();

    /** null tant que l'appli n'a jamais relevé la liste des applis installées. */
    Set<String> applisConnues;
    final List<JSONObject> enAttente = new ArrayList<>();
    /** États des conditions et limites : déblocages NFC, passes, rallonges. */
    JSONObject etats = new JSONObject();
    /** Raisons données pour ouvrir ou rallonger : {t, paquet, quoi, texte}, les 200 dernières. */
    final List<JSONObject> motifs = new ArrayList<>();
    /** "aaaaMMjj" → {idLimite: [ouvertures autorisées, bloquées, rallonges]}. */
    JSONObject compteurs = new JSONObject();

    static synchronized Donnees get(Context c) {
        Horloge.init(c);
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

    /**
     * Import d'un réglage exporté : remplace limites, groupes, profils,
     * messages, badges et réglages, mais garde l'état du téléphone (déblocages
     * en cours, compteurs, changements en attente, applis connues).
     */
    private void remplacer(JSONObject o) {
        if (o == null) {
            return;
        }
        JSONObject gardeEtats = etats;
        JSONObject gardeCompteurs = compteurs;
        Set<String> gardeConnues = applisConnues;
        limites.clear();
        groupes.clear();
        profils.clear();
        messages.clear();
        badges.clear();
        auto.clear();
        suiviesApplis.clear();
        suiviesGroupes.clear();
        widgetCachees.clear();
        try {
            // appelé pendant le parcours des changements en attente : ne pas y toucher
            JSONObject r = new JSONObject(o.toString());
            r.remove("enAttente");
            lire(r);
        } catch (Exception e) {
            // import partiel : on garde ce qui a pu être lu
        }
        etats = gardeEtats;
        compteurs = gardeCompteurs;
        applisConnues = gardeConnues;
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
            JSONArray ds = p.optJSONArray("decl");
            for (int j = 0; ds != null && j < ds.length(); j++) {
                profil.declencheurs.add(Declencheurs.Declencheur.de(ds.getJSONObject(j)));
            }
            profils.add(profil);
        }
        lireChaines(o.optJSONArray("auto"), auto);
        profilActif = o.optString("profilActif", null);
        lireChaines(o.optJSONArray("messages"), messages);
        JSONObject bs = o.optJSONObject("badges");
        if (bs != null) {
            for (Iterator<String> it = bs.keys(); it.hasNext(); ) {
                String id = it.next();
                badges.put(id, bs.optString(id));
            }
        }
        toleranceSecondes = Math.min(30, Math.max(0, o.optInt("tolerance", 5)));
        debutJourneeMinutes = o.optInt("debutJournee");
        lireChaines(o.optJSONArray("suiviesApplis"), suiviesApplis);
        lireChaines(o.optJSONArray("suiviesGroupes"), suiviesGroupes);
        vacancesJusquA = o.optLong("vacances");
        JSONObject w = o.optJSONObject("widget");
        if (w != null) {
            widgetTemps = w.optBoolean("temps", true);
            widgetLimites = w.optBoolean("limites", true);
            widgetConcentration = w.optBoolean("conc", true);
            lireChaines(w.optJSONArray("cachees"), widgetCachees);
        }
        delaiAssouplissement = o.optInt("delai");
        nfcPourModifier = o.optBoolean("nfcModif");
        alerteAccessibilite = o.optBoolean("alerte");
        modeStrict = o.optBoolean("strict");
        JSONObject cf = o.optJSONObject("confiance");
        if (cf != null) {
            lireConfiance(cf);
        }
        JSONArray connues = o.optJSONArray("connues");
        if (connues != null) {
            applisConnues = new HashSet<>();
            lireChaines(connues, applisConnues);
        }
        JSONArray mts = o.optJSONArray("motifs");
        for (int i = 0; mts != null && i < mts.length(); i++) {
            motifs.add(mts.getJSONObject(i));
        }
        JSONArray att = o.optJSONArray("enAttente");
        for (int i = 0; att != null && i < att.length(); i++) {
            enAttente.add(att.getJSONObject(i));
        }
        etats = o.optJSONObject("etats") != null ? o.getJSONObject("etats") : new JSONObject();
        compteurs = o.optJSONObject("compteurs") != null ? o.getJSONObject("compteurs") : new JSONObject();
        String plusVieux = cleJour(Horloge.maintenant() - 40L * 86_400_000L);
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
        prefs.edit().putString(CLE, json().toString()).apply();
    }

    /** Le réglage seul, sans l'état du téléphone : ce qu'on exporte. */
    synchronized JSONObject exporter() {
        JSONObject o = json();
        for (String cle : new String[]{"etats", "compteurs", "enAttente", "connues", "vacances", "motifs", "auto", "confiance"}) {
            o.remove(cle);
        }
        return o;
    }

    private JSONObject json() {
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
                JSONArray ds = new JSONArray();
                for (Declencheurs.Declencheur x : p.declencheurs) {
                    ds.put(x.json());
                }
                ps.put(new JSONObject().put("id", p.id).put("nom", p.nom).put("limites", new JSONArray(p.limites))
                        .put("decl", ds));
            }
            o.put("profils", ps).put("auto", new JSONArray(auto)).put("profilActif", profilActif).put("messages", new JSONArray(messages))
                    .put("badges", new JSONObject(badges)).put("tolerance", toleranceSecondes)
                    .put("debutJournee", debutJourneeMinutes).put("suiviesApplis", new JSONArray(suiviesApplis))
                    .put("suiviesGroupes", new JSONArray(suiviesGroupes)).put("vacances", vacancesJusquA)
                    .put("widget", new JSONObject().put("temps", widgetTemps).put("limites", widgetLimites)
                            .put("conc", widgetConcentration).put("cachees", new JSONArray(widgetCachees)))
                    .put("delai", delaiAssouplissement).put("nfcModif", nfcPourModifier)
                    .put("alerte", alerteAccessibilite).put("strict", modeStrict).put("motifs", new JSONArray(motifs)).put("enAttente", new JSONArray(enAttente))
                    .put("etats", etats).put("compteurs", compteurs)
                    .put("confiance", new JSONObject().put("nom", confianceNom).put("numero", confianceNumero)
                            .put("codes", new JSONArray(confianceCodes)));
            if (applisConnues != null) {
                o.put("connues", new JSONArray(applisConnues));
            }
            return o;
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
        return new Cibles(paquetsDe(l.applis, l.groupes), l.toutSauf, l.sites, libres());
    }

    private Set<String> libres;

    /** Applis jamais visées par une liste blanche : téléphone, urgences, réglages, lanceur, Discipline. */
    Set<String> libres() {
        if (libres == null) {
            Set<String> l = new HashSet<>(Applications.lanceurs(contexte.getPackageManager()));
            l.add(contexte.getPackageName());
            Collections.addAll(l, "com.android.phone", "com.android.server.telecom", "com.android.emergency",
                    "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer",
                    "com.android.settings", "com.android.systemui", "com.android.packageinstaller",
                    "com.google.android.packageinstaller");
            try {
                String numeroteur = contexte.getSystemService(android.telecom.TelecomManager.class).getDefaultDialerPackage();
                if (numeroteur != null) {
                    l.add(numeroteur);
                }
            } catch (RuntimeException ignore) {
                // pas de téléphonie
            }
            libres = l;
        }
        return libres;
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

    /** Dernière remise à zéro des compteurs de cette unité (heure, jour, semaine), 0 = jamais. */
    long remise(int unite) {
        return etat("remise").optLong(String.valueOf(unite));
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
    static final int SOURDINE = 3;

    synchronized void noterMotif(String paquet, String quoi, String texte) {
        try {
            motifs.add(new JSONObject().put("t", Horloge.maintenant()).put("paquet", paquet).put("quoi", quoi).put("texte", texte));
            while (motifs.size() > 200) {
                motifs.remove(0);
            }
            enregistrer();
        } catch (Exception ignore) {
            // motif perdu
        }
    }

    synchronized void compter(String idLimite, int quoi) {
        try {
            String jour = cleJour(Horloge.maintenant());
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
            case "declencheurs": {
                Profil p = profil(id);
                JSONArray ds = ch.optJSONArray("liste");
                if (p != null && ds != null) {
                    p.declencheurs.clear();
                    for (int j = 0; j < ds.length(); j++) {
                        p.declencheurs.add(Declencheurs.Declencheur.de(ds.optJSONObject(j)));
                    }
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
                    auto.clear(); // les limites appartiennent désormais au profil choisi
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
            case "remise": {
                // repartir de zéro pour la semaine, c'est aussi repartir de zéro pour le jour et l'heure
                JSONObject r = etat("remise");
                try {
                    for (int u = Periode.HEURE; u <= ch.optInt("unite"); u++) {
                        r.put(String.valueOf(u), Horloge.maintenant());
                    }
                } catch (Exception ignore) {
                    // clés non nulles
                }
                break;
            }
            case "badge":
                badges.put(id, ch.optString("nom", "Badge"));
                break;
            case "tolerance":
                toleranceSecondes = Math.min(30, Math.max(0, ch.optInt("valeur", 5)));
                break;
            case "import":
                remplacer(ch.optJSONObject("reglages"));
                break;
            case "confiance":
                lireConfiance(ch);
                break;
            case "antitriche":
                delaiAssouplissement = ch.optInt("delai");
                nfcPourModifier = ch.optBoolean("nfc");
                alerteAccessibilite = ch.optBoolean("alerte");
                modeStrict = ch.optBoolean("strict", modeStrict);
                break;
            default:
                break;
        }
        enregistrer();
    }

    private void lireConfiance(JSONObject cf) {
        confianceNom = cf.optString("nom");
        confianceNumero = cf.optString("numero");
        confianceCodes.clear();
        lireChaines(cf.optJSONArray("codes"), confianceCodes);
    }

    synchronized void differer(JSONObject ch, String texte) {
        try {
            ch.put("a", Horloge.maintenant() + delaiAssouplissement * 60_000L).put("texte", texte);
        } catch (Exception ignore) {
            // clés non nulles
        }
        enAttente.add(ch);
        enregistrer();
    }

    /** Applique les changements différés dont l'heure est venue. */
    synchronized void appliquerEnAttente() {
        long maintenant = Horloge.maintenant();
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

    /** Mots-clés (entrées sans point) des limites actives, cherchés dans les adresses. */
    Set<String> motsCles() {
        Set<String> mots = new HashSet<>();
        for (Limite l : limites) {
            for (String s : l.active ? l.sites : Collections.<String>emptyList()) {
                if (!s.contains(".")) {
                    mots.add(s);
                }
            }
        }
        return mots;
    }

    // ---- NFC ---------------------------------------------------------------

    /** Ce badge ouvre-t-il cette condition (condition sans badge attitré = n'importe lequel) ? */
    static boolean ouvre(Condition c, String badge) {
        return c.type == Condition.NFC && (c.badges.isEmpty() || c.badges.contains(badge));
    }

    /** Une condition NFC active accepte n'importe quel badge : en ajouter un assouplirait. */
    boolean badgeQuelconqueAccepte() {
        if (nfcPourModifier) {
            return true;
        }
        for (Limite l : limites) {
            for (Condition c : l.conditions) {
                if (l.active && c.type == Condition.NFC && c.badges.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Débloque les conditions NFC que ce badge ouvre (et elles seules). Rend le nombre de limites touchées. */
    synchronized int debloquerParBadge(String badge, long maintenant) {
        int nombre = 0;
        try {
            for (Limite l : limites) {
                boolean touchee = false;
                for (Condition c : l.conditions) {
                    if (!ouvre(c, badge)) {
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
