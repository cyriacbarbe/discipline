package fr.discipline.app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Liste les applications installées disposant d'un lanceur, triées par nom. */
final class Applications {

    private Applications() {
    }

    static List<AppInfo> installees(PackageManager pm, String paquetAExclure) {
        Intent intentLanceurs = new Intent(Intent.ACTION_MAIN);
        intentLanceurs.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolus = pm.queryIntentActivities(intentLanceurs, 0);
        List<AppInfo> applis = new ArrayList<>();
        for (ResolveInfo resolu : resolus) {
            String paquet = resolu.activityInfo.packageName;
            if (paquet.equals(paquetAExclure)) {
                continue;
            }
            applis.add(new AppInfo(paquet, resolu.loadLabel(pm).toString()));
        }
        Collections.sort(applis, Comparator.comparing(a -> a.nom.toLowerCase(Locale.FRANCE)));
        return applis;
    }
}
