package fr.discipline.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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

    static synchronized Journal get(Context c) {
        if (instance == null) {
            instance = new Journal(c.getApplicationContext());
        }
        return instance;
    }

    private Journal(Context contexte) {
        fichier = new File(contexte.getFilesDir(), "journal.txt");
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
    }

    /** Début de la plus ancienne entrée (maintenant si le journal est vide). */
    synchronized long premierDebut() {
        if (!intervalles.isEmpty()) {
            return intervalles.get(0).debut;
        }
        return paquetEnCours != null ? debutEnCours : System.currentTimeMillis();
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
    }

    synchronized void fermer(long maintenant) {
        if (paquetEnCours != null && !enCoursBloque && maintenant - debutEnCours >= 500) {
            Intervalle i = new Intervalle(paquetEnCours, debutEnCours, maintenant);
            intervalles.add(i);
            try (FileWriter ecrivain = new FileWriter(fichier, true)) {
                ecrivain.write(i.paquet + "\t" + i.debut + "\t" + i.fin + "\n");
            } catch (IOException ignore) {
                // gardé en mémoire
            }
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
        long maintenant = System.currentTimeMillis();
        if (paquetEnCours != null && !enCoursBloque && debutEnCours <= fin && maintenant >= debut
                && (paquets == null || paquets.contains(paquetEnCours))) {
            resultat.add(new Intervalle(paquetEnCours, debutEnCours, maintenant));
        }
        return resultat;
    }

    long temps(Set<String> paquets, long debut, long fin) {
        long total = 0;
        for (Intervalle i : entre(paquets, debut, fin)) {
            total += Math.max(0, Math.min(fin, i.fin) - Math.max(debut, i.debut));
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
