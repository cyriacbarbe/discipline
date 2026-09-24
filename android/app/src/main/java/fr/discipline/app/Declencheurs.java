package fr.discipline.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Looper;
import android.provider.CalendarContract;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Déclencheurs automatiques d'un profil : lieu, Wi-Fi, Bluetooth, en charge,
 * rendez-vous de l'agenda, Ne pas déranger. Tant que l'un d'eux est vrai, les
 * limites du profil s'allument en plus de celles déjà actives ; quand il ne
 * l'est plus, elles reviennent à leur état d'avant. Un déclencheur ne peut
 * donc que durcir : il n'éteint jamais une limite allumée à la main.
 */
final class Declencheurs {
    private Declencheurs() {
    }

    static final int LIEU = 0;
    static final int WIFI = 1;
    static final int BLUETOOTH = 2;
    static final int CHARGE = 3;
    static final int AGENDA = 4;
    static final int NE_PAS_DERANGER = 5;
    static final String[] NOMS = {"Dans un lieu", "Connecté à un Wi-Fi", "Connecté à un appareil Bluetooth",
            "En charge", "Pendant un rendez-vous de l’agenda", "Quand Ne pas déranger est activé"};

    static final class Declencheur {
        int type;
        /** Wi-Fi : nom du réseau ; Bluetooth : nom de l'appareil ; agenda : mot du titre (vide = tous). */
        String valeur = "";
        double lat;
        double lon;
        /** Lieu : rayon en mètres. */
        int rayon = 200;

        JSONObject json() throws Exception {
            return new JSONObject().put("t", type).put("v", valeur).put("lat", lat).put("lon", lon).put("r", rayon);
        }

        static Declencheur de(JSONObject o) {
            Declencheur d = new Declencheur();
            d.type = o.optInt("t");
            d.valeur = o.optString("v");
            d.lat = o.optDouble("lat");
            d.lon = o.optDouble("lon");
            d.rayon = o.optInt("r", 200);
            return d;
        }

        String resume() {
            switch (type) {
                case LIEU:
                    return "Dans un rayon de " + rayon + " m";
                case WIFI:
                    return "Wi-Fi « " + valeur + " »";
                case BLUETOOTH:
                    return "Bluetooth « " + valeur + " »";
                case AGENDA:
                    return valeur.isEmpty() ? "Pendant un rendez-vous" : "Rendez-vous contenant « " + valeur + " »";
                default:
                    return NOMS[type];
            }
        }
    }

    /** Autorisations Android que demande ce type de déclencheur. */
    static String[] permissions(int type) {
        switch (type) {
            case LIEU:
            case WIFI:
                return new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
            case BLUETOOTH:
                return android.os.Build.VERSION.SDK_INT >= 31
                        ? new String[]{Manifest.permission.BLUETOOTH_CONNECT} : new String[0];
            case AGENDA:
                return new String[]{Manifest.permission.READ_CALENDAR};
            default:
                return new String[0];
        }
    }

    static boolean autorise(Context c, int type) {
        for (String p : permissions(type)) {
            if (c.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /** Appareils Bluetooth connectés, tenus à jour par le service d'accessibilité. */
    static final Set<String> bluetooth = new HashSet<>();
    private static Location position;
    private static long dernierePosition;
    private static long derniereVerification;

    /** Appelé par le tic du service : toutes les 30 s au plus. */
    static void verifier(Context c, Donnees d, long maintenant) {
        if (maintenant - derniereVerification < 30_000L) {
            return;
        }
        derniereVerification = maintenant;
        Set<String> voulues = new HashSet<>();
        for (Donnees.Profil p : d.profils) {
            for (Declencheur x : p.declencheurs) {
                if (vrai(c, x)) {
                    voulues.addAll(p.limites);
                    break;
                }
            }
        }
        boolean change = false;
        for (Limite l : d.limites) {
            if (voulues.contains(l.id)) {
                if (!l.active) {
                    l.active = true;
                    d.auto.add(l.id);
                    change = true;
                }
            } else if (d.auto.remove(l.id)) {
                l.active = false;
                change = true;
            }
        }
        if (d.auto.retainAll(voulues) || change) {
            d.enregistrer();
        }
    }

    /** Profils dont un déclencheur est vrai en ce moment (pour l'affichage). */
    static boolean enCours(Context c, Donnees.Profil p) {
        for (Declencheur x : p.declencheurs) {
            if (vrai(c, x)) {
                return true;
            }
        }
        return false;
    }

    private static boolean vrai(Context c, Declencheur x) {
        if (!autorise(c, x.type)) {
            return false;
        }
        try {
            switch (x.type) {
                case LIEU:
                    Location ici = position(c);
                    if (ici == null) {
                        return false;
                    }
                    float[] distance = new float[1];
                    Location.distanceBetween(ici.getLatitude(), ici.getLongitude(), x.lat, x.lon, distance);
                    return distance[0] <= x.rayon + Math.min(ici.getAccuracy(), 100);
                case WIFI:
                    String ssid = wifi(c);
                    return ssid != null && ssid.equalsIgnoreCase(x.valeur);
                case BLUETOOTH:
                    synchronized (bluetooth) {
                        return bluetooth.contains(x.valeur);
                    }
                case CHARGE:
                    Intent batterie = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    return batterie != null && batterie.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
                case AGENDA:
                    return rendezVous(c, x.valeur);
                case NE_PAS_DERANGER:
                    NotificationManager nm = c.getSystemService(NotificationManager.class);
                    return nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL;
                default:
                    return false;
            }
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Nom du Wi-Fi connecté, ou null (Android le cache sans l'accès à la position). */
    @SuppressWarnings("deprecation")
    static String wifi(Context c) {
        WifiManager wm = c.getApplicationContext().getSystemService(WifiManager.class);
        WifiInfo info = wm == null ? null : wm.getConnectionInfo();
        if (info == null || info.getSSID() == null) {
            return null;
        }
        String ssid = info.getSSID().replace("\"", "");
        return ssid.isEmpty() || ssid.equals("<unknown ssid>") ? null : ssid;
    }

    /** Dernière position connue ; en redemande une (réseau) toutes les 5 minutes. */
    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    static Location position(Context c) {
        LocationManager lm = c.getSystemService(LocationManager.class);
        long maintenant = System.currentTimeMillis();
        if (maintenant - dernierePosition > 5 * 60_000L) {
            dernierePosition = maintenant;
            for (String fournisseur : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(fournisseur);
                if (l != null && (position == null || l.getTime() > position.getTime())) {
                    position = l;
                }
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, l -> position = l, Looper.getMainLooper());
            }
        }
        return position;
    }

    private static boolean rendezVous(Context c, String mot) {
        long maintenant = System.currentTimeMillis();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, maintenant);
        ContentUris.appendId(b, maintenant + 1);
        String[] colonnes = {CalendarContract.Instances.TITLE, CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.AVAILABILITY};
        try (Cursor cur = c.getContentResolver().query(b.build(), colonnes, null, null, null)) {
            while (cur != null && cur.moveToNext()) {
                String titre = cur.getString(0) == null ? "" : cur.getString(0);
                boolean journee = cur.getInt(1) == 1;
                boolean occupe = cur.getInt(2) == CalendarContract.Instances.AVAILABILITY_BUSY;
                if (!journee && occupe && (mot.isEmpty()
                        || titre.toLowerCase(Locale.FRANCE).contains(mot.toLowerCase(Locale.FRANCE)))) {
                    return true;
                }
            }
        }
        return false;
    }
}
