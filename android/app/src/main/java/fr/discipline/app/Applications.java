package fr.discipline.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Liste les applications installées disposant d'un lanceur, triées par nom. */
final class Applications {
    private static final Map<String, String> NOMS = new HashMap<>();
    private static final Map<String, Drawable.ConstantState> ICONES = new HashMap<>();

    private Applications() {
    }

    static List<AppInfo> installees(PackageManager pm, String paquetAExclure) {
        Intent intentLanceurs = new Intent(Intent.ACTION_MAIN);
        intentLanceurs.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolus = pm.queryIntentActivities(intentLanceurs, 0);
        List<AppInfo> applis = new ArrayList<>();
        Set<String> vus = new HashSet<>();
        for (ResolveInfo resolu : resolus) {
            String paquet = resolu.activityInfo.packageName;
            if (paquet.equals(paquetAExclure) || !vus.add(paquet)) {
                continue;
            }
            String nom = resolu.loadLabel(pm).toString();
            synchronized (NOMS) {
                NOMS.put(paquet, nom);
            }
            applis.add(new AppInfo(paquet, nom));
        }
        Collections.sort(applis, Comparator.comparing(a -> a.nom.toLowerCase(Locale.FRANCE)));
        return applis;
    }

    /** Écrans d'accueil installés : leur temps ne compte pas comme usage. */
    static Set<String> lanceurs(PackageManager pm) {
        Intent accueil = new Intent(Intent.ACTION_MAIN);
        accueil.addCategory(Intent.CATEGORY_HOME);
        Set<String> paquets = new HashSet<>();
        for (ResolveInfo resolu : pm.queryIntentActivities(accueil, 0)) {
            paquets.add(resolu.activityInfo.packageName);
        }
        return paquets;
    }

    static String nom(Context c, String paquet) {
        if (paquet == null) {
            return "";
        }
        if (paquet.startsWith("site:") || paquet.contains("#")) {
            return Sites.nom(paquet, paquet.startsWith("site:") ? "" : nom(c, Cibles.base(paquet)));
        }
        synchronized (NOMS) {
            String connu = NOMS.get(paquet);
            if (connu != null) {
                return connu;
            }
        }
        String nom;
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(paquet, 0);
            nom = pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            nom = paquet;
        }
        synchronized (NOMS) {
            NOMS.put(paquet, nom);
        }
        return nom;
    }

    /** Icône de l'appli (null si désinstallée), chargée une fois puis gardée en mémoire. */
    static Drawable icone(Context c, String paquet) {
        paquet = Cibles.base(paquet);
        Drawable.ConstantState etat;
        synchronized (ICONES) {
            etat = ICONES.get(paquet);
        }
        if (etat == null) {
            Drawable d;
            try {
                d = c.getPackageManager().getApplicationIcon(paquet);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
            etat = d.getConstantState();
            if (etat == null) {
                return d;
            }
            synchronized (ICONES) {
                ICONES.put(paquet, etat);
            }
        }
        // Une copie par vue : une même icône peut s'afficher deux fois (groupe et appli).
        return etat.newDrawable(c.getResources());
    }
}
