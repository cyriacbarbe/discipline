package fr.discipline.app;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Ce que vise une limite, tel que le journal et le moteur le consultent (ils
 * ne font que demander « ce paquet en fait-il partie ? »). Comprend :
 * <ul>
 * <li>les parties d'appli : « paquet#shorts » fait partie de « paquet » ;</li>
 * <li>les sites propres à la limite (« site:idLimite ») ;</li>
 * <li>la liste blanche : tout sauf les exceptions et les applis toujours libres
 * (téléphone, Discipline…), nouvelles applis comprises.</li>
 * </ul>
 * Parcourir l'ensemble ne rend que les paquets nommés, pas « tout le reste ».
 */
final class Cibles extends AbstractSet<String> {
    private final Set<String> paquets;
    private final boolean sauf;
    private final String site;
    private final Set<String> libres;

    Cibles(Set<String> paquets, boolean sauf, String site, Set<String> libres) {
        this.paquets = paquets;
        this.sauf = sauf;
        this.site = site;
        this.libres = libres;
    }

    /** « youtube#shorts » → « youtube ». */
    static String base(String paquet) {
        int diese = paquet.indexOf('#');
        return diese < 0 ? paquet : paquet.substring(0, diese);
    }

    @Override
    public boolean contains(Object o) {
        if (!(o instanceof String)) {
            return false;
        }
        String p = (String) o;
        if (p.startsWith("site:")) {
            return sauf || p.equals(site);
        }
        String base = base(p);
        if (sauf) {
            return !libres.contains(base) && !paquets.contains(p) && !paquets.contains(base);
        }
        return paquets.contains(p) || paquets.contains(base);
    }

    @Override
    public Iterator<String> iterator() {
        return paquets.iterator();
    }

    @Override
    public int size() {
        return paquets.size();
    }
}
