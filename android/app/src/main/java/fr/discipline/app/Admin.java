package fr.discipline.app;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Administrateur de l'appareil : tant qu'il est actif, Android refuse de
 * désinstaller Discipline. Il ne demande aucun pouvoir sur le téléphone.
 */
public class Admin extends DeviceAdminReceiver {

    static ComponentName composant(Context c) {
        return new ComponentName(c, Admin.class);
    }

    @Override
    public CharSequence onDisableRequested(Context c, Intent intent) {
        return "Discipline pourra ensuite être désinstallée et ne protégera plus tes limites.";
    }
}
