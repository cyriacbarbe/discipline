package fr.discipline.app;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

import java.util.Calendar;
import java.util.Map;

/** Lit le temps passé aujourd'hui sur une application, via UsageStatsManager. */
class Usage {
    private final Context contexte;

    Usage(Context contexte) {
        this.contexte = contexte.getApplicationContext();
    }

    boolean permissionAccordee() {
        AppOpsManager appOps = (AppOpsManager) contexte.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), contexte.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** Minutes d'usage depuis minuit pour ce paquet (0 si non mesurable, notamment sans permission). */
    int minutesUtiliseesAujourdHui(String paquet) {
        UsageStatsManager gestionnaire =
                (UsageStatsManager) contexte.getSystemService(Context.USAGE_STATS_SERVICE);

        Calendar debutJour = Calendar.getInstance();
        debutJour.set(Calendar.HOUR_OF_DAY, 0);
        debutJour.set(Calendar.MINUTE, 0);
        debutJour.set(Calendar.SECOND, 0);
        debutJour.set(Calendar.MILLISECOND, 0);

        Map<String, UsageStats> stats = gestionnaire.queryAndAggregateUsageStats(
                debutJour.getTimeInMillis(), System.currentTimeMillis());
        UsageStats stat = stats.get(paquet);
        return stat == null ? 0 : (int) (stat.getTotalTimeInForeground() / 60000);
    }
}
