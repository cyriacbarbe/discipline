package fr.discipline.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Accueil : le carré du temps passé, les limites en vigueur et « Ajouter une limite ». */
public class MainActivity extends Ecran {
    private static final long JOUR = 86_400_000L;

    private boolean popupMajAffichee;
    private String[] miseAJour;

    @Override
    protected void onResume() {
        super.onResume();
        rafraichir();
        verifierMiseAJour();
        Surveillance.planifier(this);
    }

    @Override
    protected void rafraichir() {
        LinearLayout c = page("Discipline", false);
        actionEntete("⚙", v -> startActivity(new Intent(this, ParametresActivity.class)));
        long maintenant = System.currentTimeMillis();

        if (miseAJour != null) {
            Button maj = Ui.ajouter(c, Ui.boutonPlein(this, "⬇ Mise à jour disponible : " + miseAJour[0]
                    + "\nTouche ici pour la télécharger"), 12);
            maj.setMinHeight(Ui.dp(this, 72));
            maj.setOnClickListener(v -> telecharger());
        }
        if (!Surveillance.serviceActif(this)) {
            miseEnRoute(c);
        }
        if (donnees.enVacances(maintenant)) {
            LinearLayout v = Ui.ajouter(c, Ui.carte(this), 12);
            v.setBackground(Ui.fond(this, 0xFF3A2F12, 22));
            v.addView(Ui.titre(this, "🏖 Mode vacances"));
            v.addView(Ui.petit(this, "Aucune limite ne s’applique jusqu’au " + Donnees.dateCourte(donnees.vacancesJusquA) + "."));
        }
        nouvellesApplis(c);
        carre(c, maintenant);

        TextView titreLimites = Ui.ajouter(c, Ui.section(this, "Limites en vigueur"), 8);
        Donnees.Profil profil = donnees.profil(donnees.profilActif);
        if (profil != null) {
            titreLimites.setText(titreLimites.getText() + "  ·  PROFIL " + profil.nom.toUpperCase());
        }
        Moteur moteur = new Moteur(this);
        for (Limite l : donnees.limites) {
            ligneLimite(c, l, moteur, maintenant);
        }
        if (donnees.limites.isEmpty()) {
            Ui.ajouter(c, Ui.petit(this, "Aucune limite pour l’instant."), 6);
        }
        enAttente(c);
        boutonBas(c, "＋ Ajouter une limite", v -> startActivity(new Intent(this, ChoixTypeActivity.class)));
    }

    private void miseEnRoute(LinearLayout c) {
        LinearLayout m = Ui.ajouter(c, Ui.carte(this), 12);
        m.setBackground(Ui.fond(this, 0xFF3A1D1B, 22));
        m.addView(Ui.titre(this, "✗ Le blocage n’est pas actif"));
        Ui.ajouter(m, Ui.petit(this, "1. Android bloque ce réglage pour une appli installée hors Play Store : "
                + "ouvre la fiche de l’appli, touche ⋮ en haut à droite, puis « Autoriser les paramètres restreints »."), 8);
        Ui.ajouter(m, Ui.boutonDiscret(this, "Ouvrir la fiche de l’appli"), 8).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))));
        Ui.ajouter(m, Ui.petit(this, "2. Dans Accessibilité, ouvre « Discipline » et active-la : c’est ce qui "
                + "mesure le temps passé et bloque les applis."), 12);
        Ui.ajouter(m, Ui.boutonPlein(this, "Ouvrir l’accessibilité"), 8).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    }

    /** Propose de ranger dans un groupe les applis installées depuis la dernière visite. */
    private void nouvellesApplis(LinearLayout c) {
        List<AppInfo> installees = Applications.installees(getPackageManager(), getPackageName());
        if (donnees.applisConnues == null) {
            donnees.applisConnues = new HashSet<>();
            for (AppInfo a : installees) {
                donnees.applisConnues.add(a.paquet);
            }
            donnees.enregistrer();
            return;
        }
        List<AppInfo> nouvelles = new ArrayList<>();
        for (AppInfo a : installees) {
            if (!donnees.applisConnues.contains(a.paquet)) {
                nouvelles.add(a);
            }
        }
        if (nouvelles.isEmpty()) {
            return;
        }
        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 12);
        carte.addView(Ui.titre(this, "Nouvelle(s) appli(s) installée(s)"));
        for (AppInfo a : nouvelles) {
            LinearLayout r = Ui.ajouter(carte, Ui.rangee(this), 8);
            Ui.etirer(r, Ui.corps(this, a.nom));
            if (!donnees.groupes.isEmpty()) {
                Button ajouter = Ui.boutonDiscret(this, "Ajouter à un groupe");
                ajouter.setOnClickListener(v -> ajouterAUnGroupe(a));
                r.addView(ajouter);
            }
            r.addView(Ui.croix(this, v -> {
                donnees.applisConnues.add(a.paquet);
                donnees.enregistrer();
                rafraichir();
            }));
        }
        if (donnees.groupes.isEmpty()) {
            Ui.ajouter(carte, Ui.petit(this, "Crée des groupes dans ⚙ pour y ranger tes applis."), 6);
        }
    }

    private void ajouterAUnGroupe(AppInfo a) {
        List<Donnees.Groupe> groupes = new ArrayList<>(donnees.groupes.values());
        String[] noms = new String[groupes.size()];
        boolean[] coches = new boolean[groupes.size()];
        for (int i = 0; i < noms.length; i++) {
            noms[i] = groupes.get(i).nom;
        }
        new AlertDialog.Builder(this)
                .setTitle("Ajouter " + a.nom + " à…")
                .setMultiChoiceItems(noms, coches, (d, i, coche) -> coches[i] = coche)
                .setPositiveButton("Valider", (d, w) -> {
                    for (int i = 0; i < coches.length; i++) {
                        if (coches[i]) {
                            groupes.get(i).paquets.add(a.paquet);
                        }
                    }
                    donnees.applisConnues.add(a.paquet);
                    donnees.enregistrer();
                    rafraichir();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    /** Le carré : temps sur le téléphone, temps sur les « Suivies », applis les plus utilisées. */
    private void carre(LinearLayout c, long maintenant) {
        Journal journal = Journal.get(this);
        long debut = donnees.debutJournee(maintenant);
        long debutSemainePrecedente = debut - 7 * JOUR;

        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 12);
        carte.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        long telephone = journal.temps(null, debut, maintenant);
        long moyenne = journal.temps(null, debutSemainePrecedente, debut) / 7;
        carte.addView(Ui.texte(this, Ui.duree(telephone), 40, Ui.TEXTE, true));
        carte.addView(Ui.petit(this, "sur le téléphone aujourd’hui"));
        carte.addView(ecart(telephone, moyenne, 13));

        Set<String> suivies = donnees.paquetsDe(donnees.suiviesApplis, donnees.suiviesGroupes);
        LinearLayout blocSuivies = Ui.ajouter(carte, Ui.colonne(this), 16);
        if (suivies.isEmpty()) {
            TextView lien = Ui.texte(this, "Choisir les applis « Suivies » ›", 14, Ui.VERT, true);
            lien.setOnClickListener(v -> startActivity(new Intent(this, ParametresActivity.class)));
            blocSuivies.addView(lien);
        } else {
            long tempsSuivies = journal.temps(suivies, debut, maintenant);
            long moyenneSuivies = journal.temps(suivies, debutSemainePrecedente, debut) / 7;
            LinearLayout r = Ui.rangee(this);
            Ui.etirer(r, Ui.corps(this, "Suivies"));
            r.addView(Ui.texte(this, Ui.duree(tempsSuivies), 20, Ui.TEXTE, true));
            blocSuivies.addView(r);
            blocSuivies.addView(ecart(tempsSuivies, moyenneSuivies, 12));
        }

        Map<String, Long> aujourdhui = journal.tempsParAppli(debut, maintenant);
        aujourdhui.remove(getPackageName());
        Map<String, Long> avant = journal.tempsParAppli(debutSemainePrecedente, debut);
        List<Map.Entry<String, Long>> tri = new ArrayList<>(aujourdhui.entrySet());
        tri.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        if (!tri.isEmpty()) {
            Ui.ajouter(carte, Ui.section(this, "Les plus utilisées"), 8);
        }
        for (int i = 0; i < Math.min(5, tri.size()); i++) {
            String paquet = tri.get(i).getKey();
            long t = tri.get(i).getValue();
            LinearLayout r = Ui.ajouter(carte, Ui.rangee(this), 6);
            Ui.etirer(r, Ui.corps(this, Applications.nom(this, paquet)));
            TextView e = ecart(t, avant.getOrDefault(paquet, 0L) / 7, 12);
            e.setPadding(0, 0, Ui.dp(this, 10), 0);
            r.addView(e);
            r.addView(Ui.texte(this, Ui.duree(t), 15, Ui.TEXTE, true));
        }
        Ui.ajouter(carte, Ui.petit(this, "Écarts comparés à la moyenne par jour de la semaine précédente."), 10)
                .setTextSize(11);
    }

    private TextView ecart(long valeur, long reference, float taille) {
        long diff = valeur - reference;
        String signe = diff > 0 ? "▲ " : diff < 0 ? "▼ " : "= ";
        TextView t = Ui.texte(this, signe + Ui.duree(Math.abs(diff)) + (taille >= 13 ? " vs semaine précédente" : ""),
                taille, diff > 0 ? Ui.ROUGE : Ui.VERT, false);
        return t;
    }

    private void ligneLimite(LinearLayout c, Limite l, Moteur moteur, long maintenant) {
        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 8);
        LinearLayout r = Ui.rangee(this);
        carte.addView(r);
        LinearLayout textes = Ui.etirer(r, Ui.colonne(this));
        textes.addView(Ui.texte(this, l.nomAffiche(donnees), 16, Ui.TEXTE, true));
        textes.addView(Ui.petit(this, l.resumeConditions()));
        TextView etat = Ui.texte(this, "", 13, Ui.TEXTE2, true);
        etat.setPadding(0, Ui.dp(this, 4), 0, 0);
        textes.addView(etat);
        if (!l.active) {
            etat.setText("Désactivée");
        } else if (donnees.enVacances(maintenant) || !l.sAppliqueA(maintenant)) {
            etat.setText("En pause (hors de ses jours ou horaires)");
        } else {
            Moteur.Resultat res = moteur.evaluer(l, maintenant);
            etat.setText(etatLisible(res, maintenant));
            etat.setTextColor(res.bloque ? Ui.ROUGE : Ui.VERT);
        }
        Switch s = Ui.interrupteur(this, l.active);
        s.setOnCheckedChangeListener((b, coche) -> {
            if (coche) {
                donnees.appliquer(changementActive(l, true));
                rafraichir();
            } else {
                rafraichir();
                garde(changementActive(l, false), "désactiver « " + l.nomAffiche(donnees) + " »", null);
            }
        });
        r.addView(s);
        carte.setOnClickListener(v -> startActivity(new Intent(this, LimiteDetailActivity.class)
                .putExtra(LimiteDetailActivity.EXTRA_ID, l.id)));

        Condition immediat = l.conditionDeType(Condition.IMMEDIAT);
        if (immediat != null && l.active) {
            long jusqua = donnees.etat(immediat.id).optLong("jusqua");
            if (jusqua <= maintenant) {
                Button lancer = Ui.ajouter(carte, Ui.bouton(this, "▶ Bloque-moi ça pendant " + Ui.duree(immediat.valeur * 60_000L), Ui.ROUGE), 10);
                lancer.setOnClickListener(v -> {
                    moteur.lancerBlocageImmediat(immediat, System.currentTimeMillis());
                    rafraichir();
                });
            }
        }
    }

    static String etatLisible(Moteur.Resultat r, long maintenant) {
        if (r.bloque) {
            if (r.cause != null && r.cause.type == Condition.NFC) {
                return "✗ Fermée — bipe le badge";
            }
            return r.dispoA > maintenant ? "✗ Bloquée — de nouveau disponible dans " + Ui.duree(r.dispoA - maintenant) : "✗ Bloquée";
        }
        if (r.rallongeEnCours) {
            return "✓ Rallonge en cours — " + Ui.duree(r.restant);
        }
        if (r.restant != Long.MAX_VALUE && r.restant > 0) {
            return "✓ Il reste " + Ui.duree(r.restant);
        }
        return "✓ Disponible";
    }

    static JSONObject changementActive(Limite l, boolean active) {
        try {
            return Donnees.changement("active", l.id).put("valeur", active);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void enAttente(LinearLayout c) {
        if (donnees.enAttente.isEmpty()) {
            return;
        }
        Ui.ajouter(c, Ui.section(this, "Assouplissements en attente (anti-triche)"), 8);
        for (JSONObject ch : new ArrayList<>(donnees.enAttente)) {
            LinearLayout r = Ui.ajouter(c, Ui.carte(this), 6);
            LinearLayout ligne = Ui.rangee(this);
            r.addView(ligne);
            LinearLayout textes = Ui.etirer(ligne, Ui.colonne(this));
            textes.addView(Ui.corps(this, ch.optString("texte")));
            textes.addView(Ui.petit(this, "appliqué le " + Donnees.dateCourte(ch.optLong("a"))));
            ligne.addView(Ui.croix(this, v -> {
                donnees.annulerEnAttente(ch);
                rafraichir();
            }));
        }
    }

    private void verifierMiseAJour() {
        new Thread(() -> {
            try {
                String[] maj = MiseAJour.miseAJourSiDisponible();
                if (maj == null) {
                    return;
                }
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    boolean nouveau = miseAJour == null;
                    miseAJour = maj;
                    if (nouveau) {
                        rafraichir();
                    }
                    if (!popupMajAffichee) {
                        popupMajAffichee = true;
                        new AlertDialog.Builder(this)
                                .setTitle("Mise à jour disponible")
                                .setMessage(maj[0] + " est prête. Télécharge-la puis ouvre le fichier pour "
                                        + "l’installer par-dessus : tes réglages sont conservés.")
                                .setPositiveButton("Télécharger", (d, w) -> telecharger())
                                .setNegativeButton("Plus tard", null)
                                .show();
                    }
                });
            } catch (Exception e) {
                // hors ligne ou aucune Release publiée : pas de bouton
            }
        }).start();
    }

    private void telecharger() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(miseAJour[1])));
    }
}
