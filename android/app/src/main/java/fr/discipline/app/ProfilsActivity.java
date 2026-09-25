package fr.discipline.app;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.location.Location;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Profils : un jeu de limites actives qu'on allume d'un coup (travail, week-end…). */
public class ProfilsActivity extends Ecran {

    @Override
    protected void onResume() {
        super.onResume();
        rafraichir();
    }

    @Override
    protected void rafraichir() {
        LinearLayout c = page("Profils", true);
        Ui.ajouter(c, Ui.petit(this, "Activer un profil allume ses limites et éteint les autres. Un déclencheur "
                + "automatique, lui, allume les limites du profil en plus des autres tant qu’il est vrai (dans ce lieu, "
                + "sur ce Wi-Fi…), puis les rend comme avant."), 8);
        for (Donnees.Profil p : new ArrayList<>(donnees.profils)) {
            boolean actif = p.id.equals(donnees.profilActif);
            LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 10);
            LinearLayout r = Ui.rangee(this);
            carte.addView(r);
            LinearLayout textes = Ui.etirer(r, Ui.colonne(this));
            textes.addView(Ui.texte(this, p.nom, 17, Ui.TEXTE, true));
            textes.addView(Ui.petit(this, p.limites.size() + " limite(s)"));
            if (actif) {
                r.addView(Ui.pastille(this, "actif", Ui.VERT));
            } else if (Declencheurs.enCours(this, p)) {
                r.addView(Ui.pastille(this, "déclenché", Ui.ORANGE));
            }
            r.addView(Ui.croix(this, v -> {
                donnees.profils.remove(p);
                if (actif) {
                    donnees.profilActif = null;
                }
                donnees.enregistrer();
                rafraichir();
            }));
            carte.setOnClickListener(v -> choisirLimites(p.nom, p.limites, () -> {
                donnees.enregistrer();
                rafraichir();
            }));
            for (Declencheurs.Declencheur x : new ArrayList<>(p.declencheurs)) {
                LinearLayout ligne = Ui.ajouter(carte, Ui.rangee(this), 6);
                Ui.etirer(ligne, Ui.corps(this, "⚡ " + x.resume()));
                ligne.addView(Ui.croix(this, v -> retirer(p, x)));
            }
            carte.addView(Ui.lien(this, "＋ Déclencheur automatique", null, v -> choisirType(p)));
            if (!actif) {
                Ui.ajouter(carte, Ui.boutonDiscret(this, "Activer"), 10).setOnClickListener(v -> activerProfil(p, null));
            }
        }
        boutonBas(c, "＋ Nouveau profil", v -> Choix.texte(this, "Nom du profil", "", "Travail", t -> {
            if (t.isEmpty()) {
                return;
            }
            Donnees.Profil p = new Donnees.Profil();
            p.nom = t;
            for (Limite l : donnees.limites) {
                if (l.active) {
                    p.limites.add(l.id);
                }
            }
            choisirLimites(t, p.limites, () -> {
                donnees.profils.add(p);
                donnees.enregistrer();
                rafraichir();
            });
        }));
    }

    private void choisirLimites(String titre, Set<String> ids, Runnable ok) {
        List<Limite> limites = donnees.limites;
        if (limites.isEmpty()) {
            toast("Crée d’abord des limites.");
            return;
        }
        String[] noms = new String[limites.size()];
        boolean[] coches = new boolean[limites.size()];
        for (int i = 0; i < noms.length; i++) {
            noms[i] = limites.get(i).nomAffiche(donnees);
            coches[i] = ids.contains(limites.get(i).id);
        }
        new AlertDialog.Builder(this)
                .setTitle(titre + " : limites actives")
                .setMultiChoiceItems(noms, coches, (d, i, coche) -> coches[i] = coche)
                .setPositiveButton("Valider", (d, w) -> {
                    ids.clear();
                    for (int i = 0; i < coches.length; i++) {
                        if (coches[i]) {
                            ids.add(limites.get(i).id);
                        }
                    }
                    ok.run();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    // ---- Déclencheurs ------------------------------------------------------

    private Donnees.Profil enCours;
    private int typeEnCours;

    private static JSONObject changement(Donnees.Profil p, List<Declencheurs.Declencheur> liste) {
        try {
            JSONArray ds = new JSONArray();
            for (Declencheurs.Declencheur x : liste) {
                ds.put(x.json());
            }
            return Donnees.changement("declencheurs", p.id).put("liste", ds);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void ajouter(Donnees.Profil p, Declencheurs.Declencheur x) {
        List<Declencheurs.Declencheur> liste = new ArrayList<>(p.declencheurs);
        liste.add(x);
        // un déclencheur de plus ne fait qu'allumer des limites : pas d'anti-triche
        donnees.appliquer(changement(p, liste));
        rafraichir();
    }

    /** Un déclencheur de moins, c'est moins de limites allumées : anti-triche. */
    private void retirer(Donnees.Profil p, Declencheurs.Declencheur x) {
        List<Declencheurs.Declencheur> liste = new ArrayList<>(p.declencheurs);
        liste.remove(x);
        garde(changement(p, liste), "retirer un déclencheur de « " + p.nom + " »", null);
    }

    private void choisirType(Donnees.Profil p) {
        new AlertDialog.Builder(this)
                .setTitle("Déclencher « " + p.nom + " »")
                .setItems(Declencheurs.NOMS, (d, type) -> {
                    enCours = p;
                    typeEnCours = type;
                    if (Declencheurs.autorise(this, type)) {
                        configurer();
                    } else {
                        requestPermissions(Declencheurs.permissions(type), 50);
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requete, String[] permissions, int[] resultats) {
        super.onRequestPermissionsResult(requete, permissions, resultats);
        if (requete == 51) {
            configurer();
            return;
        }
        if (requete != 50 || enCours == null) {
            return;
        }
        if (!Declencheurs.autorise(this, typeEnCours)) {
            toast("Sans cette autorisation, le déclencheur ne peut rien voir.");
            return;
        }
        boolean arrierePlan = android.os.Build.VERSION.SDK_INT >= 29
                && (typeEnCours == Declencheurs.LIEU || typeEnCours == Declencheurs.WIFI)
                && checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED;
        if (arrierePlan) {
            toast("Choisis « Toujours autoriser » : la vérification se fait en arrière-plan.");
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 51);
            return;
        }
        configurer();
    }

    private void configurer() {
        Donnees.Profil p = enCours;
        Declencheurs.Declencheur x = new Declencheurs.Declencheur();
        x.type = typeEnCours;
        switch (x.type) {
            case Declencheurs.LIEU: {
                Location ici = Declencheurs.position(this);
                if (ici == null) {
                    toast("Position inconnue : active la localisation, puis réessaie dans un instant.");
                    return;
                }
                x.lat = ici.getLatitude();
                x.lon = ici.getLongitude();
                Choix.nombre(this, "Rayon autour d’ici (mètres)", 200, r -> {
                    x.rayon = Math.max(50, r);
                    ajouter(p, x);
                });
                break;
            }
            case Declencheurs.WIFI: {
                String actuel = Declencheurs.wifi(this);
                Choix.texte(this, "Nom du réseau Wi-Fi", actuel == null ? "" : actuel, "Maison", t -> {
                    if (!t.trim().isEmpty()) {
                        x.valeur = t.trim();
                        ajouter(p, x);
                    }
                });
                break;
            }
            case Declencheurs.BLUETOOTH:
                choisirBluetooth(p, x);
                break;
            case Declencheurs.AGENDA:
                Choix.texte(this, "Mot du titre (vide = tout rendez-vous)", "", "Travail", t -> {
                    x.valeur = t.trim();
                    ajouter(p, x);
                });
                break;
            default:
                ajouter(p, x);
                break;
        }
    }

    @SuppressWarnings("deprecation")
    private void choisirBluetooth(Donnees.Profil p, Declencheurs.Declencheur x) {
        List<String> noms = new ArrayList<>();
        try {
            BluetoothAdapter adaptateur = BluetoothAdapter.getDefaultAdapter();
            if (adaptateur != null) {
                for (BluetoothDevice appareil : adaptateur.getBondedDevices()) {
                    if (appareil.getName() != null) {
                        noms.add(appareil.getName());
                    }
                }
            }
        } catch (SecurityException ignore) {
            // liste inaccessible : saisie à la main
        }
        if (noms.isEmpty()) {
            Choix.texte(this, "Nom de l’appareil Bluetooth", "", "Voiture", t -> {
                if (!t.trim().isEmpty()) {
                    x.valeur = t.trim();
                    ajouter(p, x);
                }
            });
            return;
        }
        String[] tous = noms.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Appareil Bluetooth")
                .setItems(tous, (d, i) -> {
                    x.valeur = tous[i];
                    ajouter(p, x);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }
}
