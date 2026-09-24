package fr.discipline.app;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

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
import java.util.TreeMap;

/**
 * Récupère l'usage passé que garde Android (« Accès aux données d'utilisation ») :
 * <ul>
 * <li>le détail des ouvertures (≈ 10 derniers jours) entre dans le {@link Journal},
 * comme si Discipline l'avait suivi ;</li>
 * <li>plus loin, Android ne garde que des totaux par appli sur des périodes
 * (jours, semaines, mois, années) : ils vont dans {@code historique.txt}, une fois
 * pour toutes, pour l'écran Historique.</li>
 * </ul>
 * Rien n'écrase ce que Discipline a déjà : on ne remplit que le temps d'avant son journal.
 */
final class Historique {
    private static final String FICHIER = "historique.txt";
    private static final int[] PERIODES = {
            UsageStatsManager.INTERVAL_DAILY, UsageStatsManager.INTERVAL_WEEKLY,
            UsageStatsManager.INTERVAL_MONTHLY, UsageStatsManager.INTERVAL_YEARLY};
    private static boolean enCours;

    /** Une période résumée par Android : temps par appli entre début et fin. */
    static final class Bloc {
        final long debut;
        final long fin;
        final Map<String, Long> temps = new HashMap<>();

        Bloc(long debut, long fin) {
            this.debut = debut;
            this.fin = fin;
        }
    }

    private Historique() {
    }

    static boolean autorise(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                == AppOpsManager.MODE_ALLOWED;
    }

    /** Importe en arrière-plan ; {@code apres} tourne (hors thread principal) si quelque chose est arrivé. */
    static void importerEnFond(Context c, Runnable apres) {
        Context app = c.getApplicationContext();
        synchronized (Historique.class) {
            if (enCours || !autorise(app)) {
                return;
            }
            enCours = true;
        }
        new Thread(() -> {
            try {
                if (importer(app)) {
                    apres.run();
                }
            } catch (RuntimeException ignore) {
                // Android refuse parfois (utilisateur verrouillé…) : on retentera à la prochaine ouverture
            } finally {
                synchronized (Historique.class) {
                    enCours = false;
                }
            }
        }).start();
    }

    private static boolean importer(Context c) {
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        Set<String> lanceurs = Applications.lanceurs(c.getPackageManager());
        Journal journal = Journal.get(c);
        boolean nouveau = journal.ajouterAnciens(passages(usm, lanceurs, journal.premierDebut())) > 0;

        File f = new File(c.getFilesDir(), FICHIER);
        if (!f.exists()) {
            ecrire(f, blocs(usm, lanceurs, journal.premierDebut()));
            nouveau = true;
        }
        return nouveau;
    }

    /** Les passages au premier plan d'avant {@code limite}, reconstitués depuis les événements d'Android. */
    private static List<Journal.Intervalle> passages(UsageStatsManager usm, Set<String> lanceurs, long limite) {
        List<Journal.Intervalle> resultat = new ArrayList<>();
        UsageEvents evenements = usm.queryEvents(0, limite);
        UsageEvents.Event e = new UsageEvents.Event();
        Map<String, String> paquets = new HashMap<>();
        String courant = null;
        long debut = 0;
        while (evenements.hasNextEvent()) {
            evenements.getNextEvent(e);
            long t = e.getTimeStamp();
            String paquet = paquets.computeIfAbsent(e.getPackageName(), p -> p);
            switch (e.getEventType()) {
                case UsageEvents.Event.MOVE_TO_FOREGROUND:
                    if (paquet.equals(courant)) {
                        break;
                    }
                    ajouter(resultat, courant, debut, t);
                    courant = lanceurs.contains(paquet) ? null : paquet;
                    debut = t;
                    break;
                case UsageEvents.Event.MOVE_TO_BACKGROUND:
                    if (paquet.equals(courant)) {
                        ajouter(resultat, courant, debut, t);
                        courant = null;
                    }
                    break;
                case UsageEvents.Event.SCREEN_NON_INTERACTIVE:
                case UsageEvents.Event.KEYGUARD_SHOWN:
                case UsageEvents.Event.DEVICE_SHUTDOWN:
                    ajouter(resultat, courant, debut, t);
                    courant = null;
                    break;
                default:
                    break;
            }
        }
        ajouter(resultat, courant, debut, limite);
        return resultat;
    }

    /** Comme le journal : moins d'une demi-seconde ne compte pas ; deux écrans d'une même appli se suivent en un passage. */
    private static void ajouter(List<Journal.Intervalle> liste, String paquet, long debut, long fin) {
        if (paquet == null || fin - debut < 500) {
            return;
        }
        Journal.Intervalle dernier = liste.isEmpty() ? null : liste.get(liste.size() - 1);
        if (dernier != null && dernier.paquet.equals(paquet) && debut - dernier.fin <= 1000) {
            liste.set(liste.size() - 1, new Journal.Intervalle(paquet, dernier.debut, fin));
        } else {
            liste.add(new Journal.Intervalle(paquet, debut, fin));
        }
    }

    /**
     * Totaux d'Android d'avant {@code couverture}, du plus fin au plus grossier :
     * chaque niveau ne prend que les périodes entièrement antérieures à ce qu'on a déjà.
     */
    private static List<Bloc> blocs(UsageStatsManager usm, Set<String> lanceurs, long couverture) {
        List<Bloc> resultat = new ArrayList<>();
        for (int periode : PERIODES) {
            TreeMap<Long, Bloc> niveau = new TreeMap<>();
            List<UsageStats> stats = usm.queryUsageStats(periode, 0, couverture);
            if (stats == null) {
                continue;
            }
            for (UsageStats u : stats) {
                long temps = u.getTotalTimeInForeground();
                if (u.getLastTimeStamp() > couverture || temps <= 0 || lanceurs.contains(u.getPackageName())) {
                    continue;
                }
                Bloc b = niveau.computeIfAbsent(u.getFirstTimeStamp(), d -> new Bloc(d, u.getLastTimeStamp()));
                b.temps.merge(u.getPackageName(), temps, Long::sum);
            }
            if (!niveau.isEmpty()) {
                resultat.addAll(niveau.values());
                couverture = niveau.firstKey();
            }
        }
        return resultat;
    }

    private static void ecrire(File f, List<Bloc> blocs) {
        try (FileWriter ecrivain = new FileWriter(f, false)) {
            for (Bloc b : blocs) {
                for (Map.Entry<String, Long> t : b.temps.entrySet()) {
                    ecrivain.write(b.debut + "\t" + b.fin + "\t" + t.getKey() + "\t" + t.getValue() + "\n");
                }
            }
        } catch (IOException ignore) {
            // on retentera à la prochaine ouverture (le fichier n'existe pas)
            f.delete();
        }
    }

    /** Les périodes résumées par Android, de la plus récente à la plus ancienne. */
    static List<Bloc> lire(Context c) {
        TreeMap<Long, Bloc> blocs = new TreeMap<>();
        File f = new File(c.getFilesDir(), FICHIER);
        if (f.exists()) {
            try (BufferedReader lecteur = new BufferedReader(new FileReader(f))) {
                String ligne;
                while ((ligne = lecteur.readLine()) != null) {
                    String[] champs = ligne.split("\t");
                    if (champs.length == 4) {
                        long debut = Long.parseLong(champs[0]);
                        long fin = Long.parseLong(champs[1]);
                        blocs.computeIfAbsent(debut, d -> new Bloc(d, fin)).temps.put(champs[2], Long.parseLong(champs[3]));
                    }
                }
            } catch (IOException | NumberFormatException ignore) {
                // on montre ce qui a pu être lu
            }
        }
        return new ArrayList<>(blocs.descendingMap().values());
    }
}
