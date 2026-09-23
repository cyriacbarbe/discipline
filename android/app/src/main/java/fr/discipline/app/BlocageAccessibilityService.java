package fr.discipline.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Regarde quelle application passe au premier plan. Si elle est dans la
 * liste des applications bloquées (et, le cas échéant, dans le créneau
 * horaire configuré), renvoie à l'accueil et affiche l'écran de blocage.
 */
public class BlocageAccessibilityService extends AccessibilityService {

    private String dernierPaquetBloque;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence paquet = event.getPackageName();
        if (paquet == null) {
            return;
        }
        String nomPaquet = paquet.toString();

        if (nomPaquet.equals(getPackageName())) {
            dernierPaquetBloque = null;
            return;
        }

        Regles regles = new Regles(this);
        if (!regles.estBloqueeMaintenant(nomPaquet)) {
            dernierPaquetBloque = null;
            return;
        }

        // Évite de relancer l'écran de blocage en boucle sur la même app.
        if (nomPaquet.equals(dernierPaquetBloque)) {
            return;
        }
        dernierPaquetBloque = nomPaquet;

        performGlobalAction(GLOBAL_ACTION_HOME);

        Intent blocage = new Intent(this, BlocageActivity.class);
        blocage.putExtra(BlocageActivity.EXTRA_PAQUET, nomPaquet);
        blocage.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(blocage);
    }

    @Override
    public void onInterrupt() {
    }
}
