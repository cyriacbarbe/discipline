package fr.discipline.app;

import android.accessibilityservice.AccessibilityService;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regarde quelle appli passe au premier plan, tient le {@link Journal} et,
 * toutes les deux secondes, demande au {@link Moteur} si une limite bloque
 * l'appli en cours. Montre aussi les bulles de temps restant.
 *
 * Ce qui est au premier plan se précise avec {@link Sites} (site ouvert dans
 * le navigateur, Shorts, Reels) ; les applis visibles ailleurs (image dans
 * l'image, écran partagé) et le son en arrière-plan sont surveillés aussi.
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
    /** Ce que le journal note pour le premier plan : le paquet, ou un site, des Shorts… */
    private String cle;
    /** Appli sortie de l'image dans l'image pour être bloquée : écran de blocage obligatoire. */
    private String sortieDImage;
    private long dernierSecondaire;
    private long derniereInspectionReglages;
    private String avantExtinction;
    private boolean ecranAllume = true;
    private long dernierBlocage;
    private String dernierBloque;
    private long dernierEnregistrement;

    private final Runnable tic = new Runnable() {
        @Override
        public void run() {
            if (ecranAllume) {
                long maintenant = Horloge.maintenant();
                if (maintenant - dernierEnregistrement > 60_000L) {
                    dernierEnregistrement = maintenant;
                    donnees.appliquerEnAttente();
                    donnees.enregistrer();
                }
                preciser();
                verifier(false);
                secondaires(maintenant);
                couperLeSon(maintenant);
                grisaille(maintenant);
            }
            Declencheurs.verifier(BlocageAccessibilityService.this, donnees, Horloge.maintenant());
            Bilan.verifier(BlocageAccessibilityService.this, Horloge.maintenant());
            handler.postDelayed(this, TIC);
        }
    };

    private final BroadcastReceiver ecran = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            long maintenant = Horloge.maintenant();
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                ecranAllume = false;
                avantExtinction = premierPlan;
                premierPlan = null;
                cle = null;
                journal.fermer(maintenant);
                Grisaille.appliquer(c, false);
                donnees.enregistrer();
            } else if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                ecranAllume = true;
                if (avantExtinction != null && premierPlan == null) {
                    changerPremierPlan(avantExtinction);
                }
            }
        }
    };

    /** Appareils Bluetooth connectés, pour les déclencheurs de profil. */
    private final BroadcastReceiver bluetooth = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            BluetoothDevice appareil = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (appareil == null || !Declencheurs.autorise(c, Declencheurs.BLUETOOTH)) {
                return;
            }
            String nom;
            try {
                nom = appareil.getName();
            } catch (SecurityException e) {
                return;
            }
            if (nom == null) {
                return;
            }
            synchronized (Declencheurs.bluetooth) {
                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
                    Declencheurs.bluetooth.add(nom);
                } else {
                    Declencheurs.bluetooth.remove(nom);
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
        IntentFilter bt = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        bt.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(bluetooth, bt);
        handler.postDelayed(tic, TIC);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        handler.removeCallbacks(tic);
        Grisaille.appliquer(this, false);
        try {
            unregisterReceiver(ecran);
            unregisterReceiver(bluetooth);
        } catch (IllegalArgumentException ignore) {
            // déjà retiré
        }
        if (journal != null) {
            journal.fermer(Horloge.maintenant());
        }
        if (donnees != null && donnees.alerteAccessibilite) {
            Surveillance.alerter(this);
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (donnees == null) {
            return;
        }
        CharSequence paquet = event.getPackageName();
        if (donnees.modeStrict && paquet != null && estReglages(paquet.toString())) {
            protegerReglages();
        }
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
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
        long maintenant = Horloge.maintenant();
        premierPlan = paquet;
        cle = null;
        if (lanceurs.contains(paquet)) {
            journal.fermer(maintenant);
            dernierBloque = null;
            return;
        }
        cle = raffiner(paquet);
        journal.ouvrir(cle, maintenant);
        verifier(true);
    }

    /** Le site ou la partie d'appli a changé sans changer d'appli : c'est une nouvelle entrée. */
    private void preciser() {
        if (premierPlan == null || cle == null || !Sites.inspecte(premierPlan)) {
            return;
        }
        String nouvelle = raffiner(premierPlan);
        if (!nouvelle.equals(cle)) {
            cle = nouvelle;
            journal.ouvrir(cle, Horloge.maintenant());
            verifier(true);
        }
    }

    private String raffiner(String paquet) {
        if (!Sites.inspecte(paquet)) {
            return paquet;
        }
        AccessibilityNodeInfo racine = getRootInActiveWindow();
        if (racine == null || racine.getPackageName() == null || !paquet.equals(racine.getPackageName().toString())) {
            return cle != null ? cle : paquet;
        }
        String trouve = Sites.cle(racine, paquet, donnees.motsCles());
        // Barre d'adresse masquée le temps d'un défilement : on garde le site d'avant.
        return trouve != null ? trouve : cle != null ? cle : paquet;
    }

    private void verifier(boolean changement) {
        String paquet = cle;
        if (paquet == null || lanceurs.contains(paquet) || paquet.equals(getPackageName())) {
            return;
        }
        long maintenant = Horloge.maintenant();
        if (paquet.equals(dernierBloque) && maintenant - dernierBlocage < 1500) {
            return;
        }
        List<Limite> limites = moteur.limitesPour(paquet, maintenant);
        Limite bloquante = null;
        Moteur.Resultat resultatBloquant = null;
        for (Limite l : limites) {
            Moteur.Resultat r = moteur.evaluer(l, maintenant);
            // Une friction ou un badge à passer n'est pas un refus : l'ouverture se comptera si elle aboutit.
            boolean aPasser = r.cause != null && (r.cause.type == Condition.FRICTION || r.cause.type == Condition.NFC);
            if (changement && r.ouverture && !aPasser) {
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
        boolean ecranObligatoire = type == Condition.FRICTION || type == Condition.NFC || paquet.equals(sortieDImage);
        sortieDImage = null;
        // Un site ou des Shorts : on revient en arrière dans la même appli, sans la quitter.
        boolean partie = !paquet.equals(premierPlan);
        if (l.action == Limite.AUTRE_APPLI && !ecranObligatoire && l.appliAlternative != null) {
            Intent autre = getPackageManager().getLaunchIntentForPackage(l.appliAlternative);
            if (autre != null) {
                autre.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(autre);
                return;
            }
        }
        if (partie) {
            performGlobalAction(GLOBAL_ACTION_BACK);
        } else if (l.action != Limite.ECRAN && !ecranObligatoire) {
            // L'écran de blocage se pose par-dessus l'appli : passer par l'accueil
            // mettrait une vidéo en image dans l'image.
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
        if (l.action == Limite.ECRAN || ecranObligatoire) {
            Intent blocage = new Intent(this, BlocageActivity.class);
            blocage.putExtra(BlocageActivity.EXTRA_PAQUET, paquet);
            blocage.putExtra(BlocageActivity.EXTRA_LIMITE, l.id);
            blocage.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(blocage);
        } else {
            String dispo = r.dispoA > 0 ? ", de nouveau dans " + Ui.duree(r.dispoA - Horloge.maintenant()) : "";
            String raison = r.cause == null ? "limite atteinte" : r.cause.resume();
            Toast.makeText(this, Applications.nom(this, paquet) + " : " + raison + dispo, Toast.LENGTH_LONG).show();
        }
    }

    /** Premier blocage qui vise ce paquet maintenant, sans rien compter (null = libre). */
    private Moteur.Resultat bloque(String paquet, long maintenant) {
        for (Limite l : moteur.limitesPour(paquet, maintenant)) {
            Moteur.Resultat r = moteur.evaluer(l, maintenant);
            if (r.bloque) {
                return r;
            }
        }
        return null;
    }

    // ---- Image dans l'image, écran partagé ---------------------------------

    /**
     * Les applis visibles hors du premier plan comptent dans le journal ; si
     * l'une est bloquée, on la sort de l'image dans l'image (elle revient en
     * plein écran, où le blocage ordinaire la prend) ou on quitte l'écran partagé.
     */
    private void secondaires(long maintenant) {
        Set<String> visibles = new HashSet<>();
        String enImage = null;
        String enPartage = null;
        List<AccessibilityWindowInfo> fenetres;
        try {
            fenetres = getWindows();
        } catch (RuntimeException e) {
            return;
        }
        for (AccessibilityWindowInfo w : fenetres) {
            if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION || w.isActive() || w.isFocused()) {
                continue;
            }
            AccessibilityNodeInfo racine = w.getRoot();
            if (racine == null || racine.getPackageName() == null) {
                continue;
            }
            String p = racine.getPackageName().toString();
            if (p.equals(premierPlan) || lanceurs.contains(p) || p.equals(getPackageName())) {
                continue;
            }
            visibles.add(p);
            if (bloque(p, maintenant) != null) {
                if (w.isInPictureInPictureMode()) {
                    enImage = p;
                } else {
                    enPartage = p;
                }
            }
        }
        journal.secondaires(visibles, maintenant);
        if ((enImage == null && enPartage == null) || maintenant - dernierSecondaire < 3000) {
            return;
        }
        dernierSecondaire = maintenant;
        if (enImage != null) {
            Intent i = getPackageManager().getLaunchIntentForPackage(enImage);
            if (i != null) {
                sortieDImage = enImage;
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } else {
            performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN);
            Toast.makeText(this, Applications.nom(this, enPartage) + " est bloquée, même en écran partagé.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ---- Son en arrière-plan -----------------------------------------------

    /**
     * Une appli bloquée (quota épuisé, blocage immédiat) ne continue pas à
     * jouer derrière : sa lecture est mise en pause. Demande l'accès aux
     * notifications, qui donne la liste des lecteurs en cours.
     */
    private void couperLeSon(long maintenant) {
        if (!Notifications.autorise(this)) {
            return;
        }
        List<MediaController> lecteurs;
        try {
            MediaSessionManager msm = getSystemService(MediaSessionManager.class);
            lecteurs = msm.getActiveSessions(new ComponentName(this, Notifications.class));
        } catch (RuntimeException e) {
            return;
        }
        for (MediaController lecteur : lecteurs) {
            PlaybackState etat = lecteur.getPlaybackState();
            if (etat == null || etat.getState() != PlaybackState.STATE_PLAYING) {
                continue;
            }
            Moteur.Resultat r = bloque(lecteur.getPackageName(), maintenant);
            if (r != null && r.cause != null && (r.cause.estQuota() || r.cause.type == Condition.IMMEDIAT)) {
                lecteur.getTransportControls().pause();
            }
        }
    }

    // ---- Noir et blanc -----------------------------------------------------

    private void grisaille(long maintenant) {
        boolean gris = false;
        if (cle != null && !lanceurs.contains(premierPlan)) {
            for (Limite l : moteur.limitesPour(cle, maintenant)) {
                gris |= l.grisaille;
            }
        }
        Grisaille.appliquer(this, gris);
    }

    // ---- Mode strict -------------------------------------------------------

    private static boolean estReglages(String paquet) {
        return paquet.contains("settings") || paquet.contains("packageinstaller")
                || paquet.equals("com.samsung.accessibility") || paquet.contains("permissioncontroller");
    }

    /** Ce qui, sur une page qui parle de Discipline, l'arrêterait ou la désinstallerait. */
    private static final String[] DANGERS = {
            "forcer l", "force stop", "désinstaller", "uninstall", "vider le stockage", "effacer les données",
            "clear storage", "clear data", "utiliser discipline", "use discipline", "désactiver", "deactivate",
    };

    /** Mode strict : une page des Réglages qui permettrait d'arrêter Discipline se referme. */
    private void protegerReglages() {
        long maintenant = System.currentTimeMillis();
        if (maintenant - derniereInspectionReglages < 400) {
            return;
        }
        derniereInspectionReglages = maintenant;
        AccessibilityNodeInfo racine = getRootInActiveWindow();
        if (racine == null) {
            return;
        }
        String textes = textes(racine).toLowerCase(Locale.FRANCE).replace('’', '\'');
        if (!textes.contains("discipline")) {
            return;
        }
        for (String danger : DANGERS) {
            if (textes.contains(danger)) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                performGlobalAction(GLOBAL_ACTION_HOME);
                Toast.makeText(this, "Mode strict : cette page est fermée. On le désactive dans Discipline "
                        + "(avec le délai de l’anti-triche).", Toast.LENGTH_LONG).show();
                return;
            }
        }
    }

    /** Textes visibles de la page (400 éléments au plus). */
    private static String textes(AccessibilityNodeInfo racine) {
        StringBuilder s = new StringBuilder();
        ArrayDeque<AccessibilityNodeInfo> file = new ArrayDeque<>();
        file.add(racine);
        for (int vus = 0; !file.isEmpty() && vus < 400; vus++) {
            AccessibilityNodeInfo n = file.poll();
            if (n.getText() != null) {
                s.append(n.getText()).append('\n');
            }
            if (n.getContentDescription() != null) {
                s.append(n.getContentDescription()).append('\n');
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo enfant = n.getChild(i);
                if (enfant != null) {
                    file.add(enfant);
                }
            }
        }
        return s.toString();
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
