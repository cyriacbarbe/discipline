package fr.discipline.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Journal d'usage tenu par le service d'accessibilité : chaque passage d'une
 * appli au premier plan (paquet, début, fin), gardé pour toujours dans un
 * fichier texte et en mémoire, complété vers le passé par {@link Historique}. C'est la source de tous les comptes : temps, sessions,
 * ouvertures, statistiques de l'accueil.
 */
final class Journal {
    private static Journal instance;

    static final class Intervalle {
        final String paquet;
        final long debut;
        final long fin;

        Intervalle(String paquet, long debut, long fin) {
            this.paquet = paquet;
            this.debut = debut;
            this.fin = fin;
        }
    }

    private final File fichier;
    private final List<Intervalle> intervalles = new ArrayList<>();
    private String paquetEnCours;
    private long debutEnCours;
    private boolean enCoursBloque;
    /** Applis visibles hors du premier plan (image dans l'image, écran partagé) → début. */
    private final Map<String, Long> secondaires = new HashMap<>();
    private final SharedPreferences prefs;

    static synchronized Journal get(Context c) {
        Horloge.init(c);
        if (instance == null) {
            instance = new Journal(c.getApplicationContext());
        }
        return instance;
    }

    private Journal(Context contexte) {
        fichier = new File(contexte.getFilesDir(), "journal.txt");
        prefs = contexte.getSharedPreferences("journal", Context.MODE_PRIVATE);
        // Un seul exemplaire de chaque nom de paquet : le journal garde tout l'historique.
        Map<String, String> paquets = new HashMap<>();
        if (fichier.exists()) {
            try (BufferedReader lecteur = new BufferedReader(new FileReader(fichier))) {
                String ligne;
                while ((ligne = lecteur.readLine()) != null) {
                    String[] champs = ligne.split("\t");
                    if (champs.length == 3) {
                        String paquet = paquets.computeIfAbsent(champs[0], p -> p);
                        intervalles.add(new Intervalle(paquet, Long.parseLong(champs[1]), Long.parseLong(champs[2])));
                    }
                }
            } catch (IOException | NumberFormatException e) {
                // journal abîmé : on garde ce qui a pu être lu
            }
        }
        // Les fenêtres secondaires s'écrivent à leur fermeture, pas forcément dans l'ordre des débuts.
        Collections.sort(intervalles, (a, b) -> Long.compare(a.debut, b.debut));
        compacter();
    }

    /**
     * Au-delà de 60 jours, le détail ne sert plus qu'aux statistiques : chaque
     * journée est réduite à un passage par appli, de la durée totale, mis bout
     * à bout depuis minuit. Les totaux par jour et par appli restent justes.
     */
    private void compacter() {
        long limite = minuit(System.currentTimeMillis() - 60L * 86_400_000L);
        long deja = prefs.getLong("compacte", 0);
        if (limite - deja < 7L * 86_400_000L) {
            return;
        }
        List<Intervalle> gardes = new ArrayList<>();
        Map<String, Long> jour = new LinkedHashMap<>();
        long minuit = -1;
        int k = 0;
        for (; k < intervalles.size() && intervalles.get(k).debut < limite; k++) {
            Intervalle i = intervalles.get(k);
            if (i.debut < deja) {
                gardes.add(i);
                continue;
            }
            long m = minuit(i.debut);
            if (m != minuit) {
                bout(gardes, jour, minuit);
                minuit = m;
            }
            jour.merge(i.paquet, i.fin - i.debut, Long::sum);
        }
        bout(gardes, jour, minuit);
        gardes.addAll(intervalles.subList(k, intervalles.size()));
        if (gardes.size() < intervalles.size()) {
            intervalles.clear();
            intervalles.addAll(gardes);
            reecrire();
        }
        prefs.edit().putLong("compacte", limite).apply();
    }

    private static long minuit(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static void bout(List<Intervalle> dans, Map<String, Long> jour, long minuit) {
        long t = minuit;
        for (Map.Entry<String, Long> e : jour.entrySet()) {
            dans.add(new Intervalle(e.getKey(), t, t + e.getValue()));
            t += e.getValue();
        }
        jour.clear();
    }

    /** Range un passage à sa place (les débuts restent croissants) et l'ajoute au fichier. */
    private void ajouter(Intervalle i) {
        int k = intervalles.size();
        while (k > 0 && intervalles.get(k - 1).debut > i.debut) {
            k--;
        }
        intervalles.add(k, i);
        try (FileWriter ecrivain = new FileWriter(fichier, true)) {
            ecrivain.write(i.paquet + "\t" + i.debut + "\t" + i.fin + "\n");
        } catch (IOException ignore) {
            // gardé en mémoire
        }
    }

    /**
     * Applis visibles sans être au premier plan (image dans l'image, autre
     * moitié d'un écran partagé) : leur temps compte aussi.
     */
    synchronized void secondaires(Set<String> visibles, long maintenant) {
        for (Iterator<Map.Entry<String, Long>> it = secondaires.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> e = it.next();
            if (!visibles.contains(e.getKey()) || e.getKey().equals(paquetEnCours)) {
                if (maintenant - e.getValue() >= 500) {
                    ajouter(new Intervalle(e.getKey(), e.getValue(), maintenant));
                }
                it.remove();
            }
        }
        for (String p : visibles) {
            if (!p.equals(paquetEnCours)) {
                secondaires.putIfAbsent(p, maintenant);
            }
        }
    }

    /** Début de la plus ancienne entrée (maintenant si le journal est vide). */
    synchronized long premierDebut() {
        if (!intervalles.isEmpty()) {
            return intervalles.get(0).debut;
        }
        return paquetEnCours != null ? debutEnCours : Horloge.maintenant();
    }

    /**
     * Ajoute des passages plus anciens que tout le journal (historique d'Android),
     * triés par début. Ceux qui chevauchent le journal sont ignorés. Rend le nombre ajouté.
     */
    synchronized int ajouterAnciens(List<Intervalle> anciens) {
        long limite = premierDebut();
        List<Intervalle> gardes = new ArrayList<>();
        for (Intervalle i : anciens) {
            if (i.fin <= limite) {
                gardes.add(i);
            }
        }
        if (!gardes.isEmpty()) {
            intervalles.addAll(0, gardes);
            reecrire();
        }
        return gardes.size();
    }

    private void reecrire() {
        try (FileWriter ecrivain = new FileWriter(fichier, false)) {
            for (Intervalle i : intervalles) {
                ecrivain.write(i.paquet + "\t" + i.debut + "\t" + i.fin + "\n");
            }
        } catch (IOException ignore) {
            // le journal en mémoire reste juste
        }
    }

    /** Une appli passe au premier plan (ferme l'intervalle précédent). */
    synchronized void ouvrir(String paquet, long maintenant) {
        fermer(maintenant);
        paquetEnCours = paquet;
        debutEnCours = maintenant;
        enCoursBloque = false;
        Long secondaire = secondaires.remove(paquet);
        if (secondaire != null && maintenant - secondaire >= 500) {
            ajouter(new Intervalle(paquet, secondaire, maintenant));
        }
    }

    synchronized void fermer(long maintenant) {
        if (paquetEnCours != null && !enCoursBloque && maintenant - debutEnCours >= 500) {
            ajouter(new Intervalle(paquetEnCours, debutEnCours, maintenant));
        }
        paquetEnCours = null;
    }

    /** L'ouverture en cours a été refusée : elle ne compte ni comme temps ni comme ouverture. */
    synchronized void marquerBloque() {
        enCoursBloque = true;
    }

    synchronized long debutEnCours() {
        return paquetEnCours == null || enCoursBloque ? -1 : debutEnCours;
    }

    /** Intervalles qui touchent [debut, fin], y compris celui en cours ; paquets null = tous. */
    synchronized List<Intervalle> entre(Set<String> paquets, long debut, long fin) {
        List<Intervalle> resultat = new ArrayList<>();
        int bas = 0;
        int haut = intervalles.size();
        // premier intervalle dont la fin peut dépasser debut (les débuts sont croissants)
        while (bas < haut) {
            int milieu = (bas + haut) >>> 1;
            if (intervalles.get(milieu).debut < debut - 86_400_000L) {
                bas = milieu + 1;
            } else {
                haut = milieu;
            }
        }
        for (int k = bas; k < intervalles.size(); k++) {
            Intervalle i = intervalles.get(k);
            if (i.debut > fin) {
                break;
            }
            if (i.fin >= debut && (paquets == null || paquets.contains(i.paquet))) {
                resultat.add(i);
            }
        }
        long maintenant = Horloge.maintenant();
        if (paquetEnCours != null && !enCoursBloque && debutEnCours <= fin && maintenant >= debut
                && (paquets == null || paquets.contains(paquetEnCours))) {
            resultat.add(new Intervalle(paquetEnCours, debutEnCours, maintenant));
        }
        for (Map.Entry<String, Long> e : secondaires.entrySet()) {
            if (e.getValue() <= fin && maintenant >= debut && (paquets == null || paquets.contains(e.getKey()))) {
                resultat.add(new Intervalle(e.getKey(), e.getValue(), maintenant));
            }
        }
        Collections.sort(resultat, (a, b) -> Long.compare(a.debut, b.debut));
        return resultat;
    }

    /** Temps passé sur ces paquets : deux passages simultanés (image dans l'image) ne comptent qu'une fois. */
    long temps(Set<String> paquets, long debut, long fin) {
        long total = 0;
        long couvert = debut;
        for (Intervalle i : entre(paquets, debut, fin)) {
            long a = Math.max(couvert, i.debut);
            long b = Math.min(fin, i.fin);
            if (b > a) {
                total += b - a;
                couvert = b;
            }
        }
        return total;
    }

    Map<String, Long> tempsParAppli(long debut, long fin) {
        Map<String, Long> totaux = new HashMap<>();
        for (Intervalle i : entre(null, debut, fin)) {
            long t = Math.max(0, Math.min(fin, i.fin) - Math.max(debut, i.debut));
            totaux.merge(i.paquet, t, Long::sum);
        }
        return totaux;
    }

    /**
     * Sessions sur ces paquets depuis {@code depuis} : les passages successifs
     * séparés de moins que la tolérance sont fusionnés. {début, fin} par session.
     */
    List<long[]> sessions(Set<String> paquets, long depuis, long maintenant, long tolerance) {
        List<long[]> sessions = new ArrayList<>();
        for (Intervalle i : entre(paquets, depuis, maintenant)) {
            long[] derniere = sessions.isEmpty() ? null : sessions.get(sessions.size() - 1);
            if (derniere != null && i.debut - derniere[1] <= tolerance) {
                derniere[1] = Math.max(derniere[1], i.fin);
            } else {
                sessions.add(new long[]{i.debut, i.fin});
            }
        }
        return sessions;
    }
}
