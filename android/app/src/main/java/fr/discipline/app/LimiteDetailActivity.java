package fr.discipline.app;

import android.content.Intent;
import android.widget.LinearLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

/** Détail d'une limite : état, compteurs du jour, graphes sur 7 et 30 jours. */
public class LimiteDetailActivity extends Ecran {
    static final String EXTRA_ID = "id";
    private static final long JOUR = 86_400_000L;

    @Override
    protected void onResume() {
        super.onResume();
        rafraichir();
    }

    @Override
    protected void rafraichir() {
        Limite l = donnees.limite(getIntent().getStringExtra(EXTRA_ID));
        if (l == null) {
            finish();
            return;
        }
        LinearLayout c = page(l.nomAffiche(donnees), true);
        actionEntete("Modifier", v -> startActivity(new Intent(this, LimiteEditActivity.class)
                .putExtra(LimiteEditActivity.EXTRA_ID, l.id)));
        long maintenant = Horloge.maintenant();
        Moteur moteur = new Moteur(this);
        Moteur.Resultat r = moteur.evaluer(l, maintenant);

        LinearLayout etat = Ui.ajouter(c, Ui.carte(this), 12);
        etat.addView(Ui.corps(this, l.resumeConditions()));
        String texte;
        int couleur;
        if (!l.active) {
            texte = "Désactivée";
            couleur = Ui.TEXTE2;
        } else if (donnees.enVacances(maintenant) || !l.sAppliqueA(maintenant)) {
            texte = "En pause (hors de ses jours ou horaires)";
            couleur = Ui.TEXTE2;
        } else {
            texte = MainActivity.etatLisible(r, maintenant);
            couleur = r.bloque ? Ui.ROUGE : Ui.VERT;
        }
        Ui.ajouter(etat, Ui.texte(this, texte, 17, couleur, true), 10);

        Condition immediat = l.conditionDeType(Condition.IMMEDIAT);
        if (immediat != null && l.active) {
            long jusqua = donnees.etat(immediat.id).optLong("jusqua");
            if (jusqua > maintenant) {
                Ui.ajouter(etat, Ui.petit(this, "Blocage immédiat en cours, fin dans " + Ui.duree(jusqua - maintenant)), 6);
            } else {
                Ui.ajouter(etat, Ui.bouton(this, "▶ Bloque-moi ça pendant " + Ui.duree(immediat.valeur * 60_000L), Ui.ROUGE), 10)
                        .setOnClickListener(v -> {
                            moteur.lancerBlocageImmediat(immediat, Horloge.maintenant());
                            rafraichir();
                        });
            }
        }

        String jour = donnees.cleJour(maintenant);
        LinearLayout compteurs = Ui.ajouter(c, Ui.carte(this), 8);
        compteurs.addView(Ui.section(this, "Aujourd’hui"));
        chiffre(compteurs, "Temps passé", Ui.duree(r.tempsDuJour));
        chiffre(compteurs, "Ouvertures", String.valueOf(r.ouverturesDuJour));
        chiffre(compteurs, "Ouvertures bloquées", String.valueOf(donnees.compteur(l.id, jour, Donnees.BLOQUEES)));
        chiffre(compteurs, "Rallonges prises", String.valueOf(donnees.compteur(l.id, jour, Donnees.RALLONGES)));
        if (l.rallongeMinutes > 0) {
            chiffre(compteurs, "Rallonges encore possibles", String.valueOf(moteur.rallongesRestantes(l, maintenant)));
        }

        Set<String> cibles = donnees.cibles(l);
        long limiteJour = 0;
        Condition temps = l.conditionDeType(Condition.TEMPS);
        if (temps != null && temps.periode.unite == Periode.JOUR) {
            limiteJour = temps.valeur * 60_000L;
        }
        graphe(c, "Temps sur 7 jours", cibles, 7, limiteJour, maintenant);
        graphe(c, "Temps sur 30 jours", cibles, 30, limiteJour, maintenant);

        long[] bloquees = new long[30];
        String[] etiquettes = new String[30];
        int totalRallonges = 0;
        long debut = donnees.debutJournee(maintenant);
        for (int i = 0; i < 30; i++) {
            long d = debut - (29 - i) * JOUR + JOUR / 2;
            String cle = donnees.cleJour(d);
            bloquees[i] = donnees.compteur(l.id, cle, Donnees.BLOQUEES);
            totalRallonges += donnees.compteur(l.id, cle, Donnees.RALLONGES);
            etiquettes[i] = etiquette(d, 30);
        }
        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 8);
        carte.addView(Ui.section(this, "Ouvertures bloquées sur 30 jours"));
        Ui.ajouter(carte, new Barres(this, bloquees, etiquettes, Ui.ORANGE, 0), 8);
        Ui.ajouter(carte, Ui.petit(this, totalRallonges + " rallonge(s) prise(s) sur 30 jours."), 8);
    }

    private void chiffre(LinearLayout parent, String libelle, String valeur) {
        LinearLayout r = Ui.ajouter(parent, Ui.rangee(this), 6);
        Ui.etirer(r, Ui.corps(this, libelle));
        r.addView(Ui.texte(this, valeur, 16, Ui.TEXTE, true));
    }

    private void graphe(LinearLayout c, String titre, Set<String> cibles, int jours, long limite, long maintenant) {
        Journal journal = Journal.get(this);
        long debut = donnees.debutJournee(maintenant);
        long[] valeurs = new long[jours];
        String[] etiquettes = new String[jours];
        long total = 0;
        for (int i = 0; i < jours; i++) {
            long d = debut - (jours - 1 - i) * JOUR;
            valeurs[i] = journal.temps(cibles, d, Math.min(d + JOUR, maintenant));
            total += valeurs[i];
            etiquettes[i] = etiquette(d + JOUR / 2, jours);
        }
        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 8);
        LinearLayout r = Ui.rangee(this);
        Ui.etirer(r, Ui.section(this, titre));
        r.addView(Ui.petit(this, "moyenne " + Ui.duree(total / jours) + "/j"));
        carte.addView(r);
        Ui.ajouter(carte, new Barres(this, valeurs, etiquettes, Ui.VERT, limite), 8);
    }

    private static String etiquette(long ms, int jours) {
        return new SimpleDateFormat(jours <= 7 ? "EEEEE" : "d", Locale.FRANCE).format(new Date(ms)).toUpperCase(Locale.FRANCE);
    }
}
