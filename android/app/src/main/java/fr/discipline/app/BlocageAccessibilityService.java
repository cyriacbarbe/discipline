package fr.discipline.app;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regarde quelle appli passe au premier plan, tient le {@link Journal} et,
 * toutes les deux secondes, demande au {@link Moteur} si une limite bloque
 * l'appli en cours. Montre aussi les bulles de temps restant.
 */
public class BlocageAccessibilityService extends AccessibilityService {
    private static final long TIC = 2000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Boolean> cacheActivites = new HashMap<>();
    private final Map<String, Long> restantsPrecedents = new HashMap<>();
    private Donnees donnees;
    private Journal journal;
    private Moteur moteur;
    private Set<String> lanceurs;
    private String premierPlan;
    private String avantExtinction;
    private boolean ecranAllume = true;
    private long dernierBlocage;
    private String dernierBloque;
    private long dernierEnregistrement;

    private final Runnable tic = new Runnable() {
        @Override
        public void run() {
            if (ecranAllume) {
                long maintenant = System.currentTimeMillis();
                if (maintenant - dernierEnregistrement > 60_000L) {
                    dernierEnregistrement = maintenant;
                    donnees.appliquerEnAttente();
                    donnees.enregistrer();
                }
                verifier(false);
            }
            handler.postDelayed(this, TIC);
        }
    };

    private final BroadcastReceiver ecran = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            long maintenant = System.currentTimeMillis();
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                ecranAllume = false;
                avantExtinction = premierPlan;
                premierPlan = null;
                journal.fermer(maintenant);
                donnees.enregistrer();
            } else if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                ecranAllume = true;
                if (avantExtinction != null && premierPlan == null) {
                    changerPremierPlan(avantExtinction);
                }
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        donnees = Donnees.get(this);
        journal = Journal.get(this);
        moteur = new Moteur(this);
        lanceurs = Applications.lanceurs(getPackageManager());
        IntentFilter filtre = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filtre.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(ecran, filtre);
        handler.postDelayed(tic, TIC);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        handler.removeCallbacks(tic);
        try {
            unregisterReceiver(ecran);
        } catch (IllegalArgumentException ignore) {
            // déjà retiré
        }
        if (journal != null) {
            journal.fermer(System.currentTimeMillis());
        }
        if (donnees != null && donnees.alerteAccessibilite) {
            Surveillance.alerter(this);
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || donnees == null) {
            return;
        }
        CharSequence paquet = event.getPackageName();
        CharSequence classe = event.getClassName();
        if (paquet == null || classe == null || !estActivite(paquet.toString(), classe.toString())) {
            return;
        }
        ecranAllume = true;
        changerPremierPlan(paquet.toString());
    }

    /** Seules les vraies activités comptent (pas le clavier, le volet de notifications…). */
    private boolean estActivite(String paquet, String classe) {
        String cle = paquet + "/" + classe;
        Boolean connu = cacheActivites.get(cle);
        if (connu == null) {
            try {
                getPackageManager().getActivityInfo(new ComponentName(paquet, classe), 0);
                connu = true;
            } catch (PackageManager.NameNotFoundException e) {
                connu = false;
            }
            cacheActivites.put(cle, connu);
        }
        return connu;
    }

    private void changerPremierPlan(String paquet) {
        if (paquet.equals(premierPlan)) {
            return;
        }
        long maintenant = System.currentTimeMillis();
        premierPlan = paquet;
        if (lanceurs.contains(paquet)) {
            journal.fermer(maintenant);
            dernierBloque = null;
            return;
        }
        journal.ouvrir(paquet, maintenant);
        verifier(true);
    }

    private void verifier(boolean changement) {
        String paquet = premierPlan;
        if (paquet == null || lanceurs.contains(paquet) || paquet.equals(getPackageName())) {
            return;
        }
        long maintenant = System.currentTimeMillis();
        if (paquet.equals(dernierBloque) && maintenant - dernierBlocage < 1500) {
            return;
        }
        List<Limite> limites = moteur.limitesPour(paquet, maintenant);
        Limite bloquante = null;
        Moteur.Resultat resultatBloquant = null;
        for (Limite l : limites) {
            Moteur.Resultat r = moteur.evaluer(l, maintenant);
            if (changement && r.ouverture) {
                donnees.compter(l.id, r.bloque ? Donnees.BLOQUEES : Donnees.AUTORISEES);
            }
            if (r.bloque && bloquante == null) {
                bloquante = l;
                resultatBloquant = r;
            } else if (!r.bloque) {
                bulles(l, paquet, r.restant);
            }
        }
        if (bloquante == null) {
            return;
        }
        if (resultatBloquant.ouverture) {
            journal.marquerBloque();
        } else {
            journal.fermer(maintenant);
        }
        dernierBloque = paquet;
        dernierBlocage = maintenant;
        agir(bloquante, resultatBloquant, paquet);
    }

    private void agir(Limite l, Moteur.Resultat r, String paquet) {
        int type = r.cause == null ? 0 : r.cause.type;
        boolean ecranObligatoire = type == Condition.FRICTION || type == Condition.NFC;
        if (l.action == Limite.AUTRE_APPLI && !ecranObligatoire && l.appliAlternative != null) {
            Intent autre = getPackageManager().getLaunchIntentForPackage(l.appliAlternative);
            if (autre != null) {
                autre.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(autre);
                return;
            }
        }
        performGlobalAction(GLOBAL_ACTION_HOME);
        if (l.action == Limite.ECRAN || ecranObligatoire) {
            Intent blocage = new Intent(this, BlocageActivity.class);
            blocage.putExtra(BlocageActivity.EXTRA_PAQUET, paquet);
            blocage.putExtra(BlocageActivity.EXTRA_LIMITE, l.id);
            blocage.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(blocage);
        } else {
            Toast.makeText(this, Applications.nom(this, paquet) + " : limite atteinte", Toast.LENGTH_SHORT).show();
        }
    }

    /** Petite bulle superposée quand le temps restant passe sous un seuil de la limite. */
    private void bulles(Limite l, String paquet, long restant) {
        Long precedent = restantsPrecedents.put(l.id, restant);
        if (l.bulles.isEmpty() || restant == Long.MAX_VALUE || restant <= 0) {
            return;
        }
        long avant = precedent == null ? Long.MAX_VALUE : precedent;
        for (int seuil : l.bulles) {
            long ms = seuil * 60_000L;
            if (avant > ms && restant <= ms) {
                long minutes = Math.max(1, (restant + 59_999L) / 60_000L);
                afficherBulle("⏳ " + Applications.nom(this, paquet) + " : plus que " + minutes + " min");
                return;
            }
        }
    }

    private void afficherBulle(String texte) {
        WindowManager wm = getSystemService(WindowManager.class);
        TextView bulle = Ui.texte(this, texte, 15, Ui.TEXTE, true);
        bulle.setBackground(Ui.fond(this, 0xF01C1F24, 20));
        int p = Ui.dp(this, 14);
        bulle.setPadding(p + p / 2, p, p + p / 2, p);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = Ui.dp(this, 56);
        try {
            wm.addView(bulle, lp);
            handler.postDelayed(() -> {
                try {
                    wm.removeView(bulle);
                } catch (IllegalArgumentException ignore) {
                    // déjà retirée
                }
            }, 3000);
        } catch (RuntimeException ignore) {
            // superposition refusée : pas de bulle
        }
    }

    @Override
    public void onInterrupt() {
    }
}
