package fr.discipline.app;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * Ce que vise une limite, tel que le journal et le moteur le consultent (ils
 * ne font que demander « ce paquet en fait-il partie ? »). Comprend :
 * <ul>
 * <li>les parties d'appli : « paquet#shorts » fait partie de « paquet » ;</li>
 * <li>les sites : ceux de la liste de la limite, ses mots-clés, et le site
 * de chaque appli visée (« site:youtube.com » fait partie de YouTube) ;</li>
 * <li>la liste blanche : tout sauf les exceptions et les applis toujours libres
 * (téléphone, Discipline…), nouvelles applis comprises.</li>
 * </ul>
 * Parcourir l'ensemble ne rend que les paquets nommés, pas « tout le reste ».
 */
final class Cibles extends AbstractSet<String> {
    private final Set<String> paquets;
    private final boolean sauf;
    private final Collection<String> sites;
    private final Set<String> libres;

    Cibles(Set<String> paquets, boolean sauf, Collection<String> sites, Set<String> libres) {
        this.paquets = paquets;
        this.sauf = sauf;
        this.sites = sites;
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
        String base = base(p);
        if (p.startsWith("site:")) {
            String hote = base.substring(5);
            String partie = p.length() > base.length() ? p.substring(base.length() + 1) : null;
            String appli = Sites.paquet(hote);
            boolean appliVisee = appli != null && (paquets.contains(appli)
                    || partie != null && paquets.contains(appli + "#" + partie));
            // En « tout sauf », la liste dit ce qui est épargné : la réponse s'inverse.
            boolean liste = appliVisee || partie != null && sites.contains(partie);
            for (String s : sites) {
                liste |= Sites.couvre(s, hote);
            }
            return sauf != liste;
        }
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
