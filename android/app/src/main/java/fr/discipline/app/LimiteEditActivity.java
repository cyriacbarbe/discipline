package fr.discipline.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Création et modification d'une limite (on travaille sur une copie, enregistrée à la fin). */
public class LimiteEditActivity extends Ecran {
    static final String EXTRA_ID = "id";
    static final String EXTRA_TYPE = "type";
    /** Point de départ choisi dans « Que veux-tu faire ? » (null = la règle telle quelle). */
    static final String EXTRA_MODELE = "modele";
    static final String MODELE_BLOQUER = "bloquer";
    static final String MODELE_HORAIRES = "horaires";
    private static final int IMAGE = 1;

    private Limite l;
    private boolean nouvelle;
    /** « Plus de réglages » déplié. */
    private boolean plus;

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        Limite existante = donnees.limite(getIntent().getStringExtra(EXTRA_ID));
        if (existante != null) {
            l = existante.copie();
        } else {
            nouvelle = true;
            l = new Limite();
            Condition cond = Condition.nouvelle(getIntent().getIntExtra(EXTRA_TYPE, Condition.TEMPS));
            String modele = getIntent().getStringExtra(EXTRA_MODELE);
            if (MODELE_BLOQUER.equals(modele) || MODELE_HORAIRES.equals(modele)) {
                cond.valeur = 0;
            }
            if (MODELE_HORAIRES.equals(modele)) {
                l.plages.add(new int[]{22 * 60, 7 * 60});
            }
            l.conditions.add(cond);
        }
        rafraichir();
        if (nouvelle && etat == null) {
            // la première question est toujours « sur quoi ? »
            Choix.cibles(this, "Ce que vise la limite", l.applis, l.groupes, this::rafraichir);
        }
    }

    @Override
    protected void rafraichir() {
        LinearLayout c = page(nouvelle ? "Nouvelle limite" : "Modifier la limite", true);
        if (!nouvelle) {
            actionEntete("Supprimer", v -> supprimer());
        }

        // En une phrase : ce que la limite fera, mis à jour à chaque réglage.
        LinearLayout resume = Ui.ajouter(c, Ui.carte(this), 12);
        resume.setBackground(Ui.fond(this, Ui.CARTE2, 22));
        resume.addView(Ui.texte(this, l.applis.isEmpty() && l.groupes.isEmpty() && !l.toutSauf && l.sites.isEmpty()
                ? "Aucune appli choisie" : l.nomAffiche(donnees), 18, Ui.TEXTE, true));
        Ui.ajouter(resume, Ui.corps(this, l.phrase()), 4);

        Ui.ajouter(c, Ui.section(this, "Sur quoi"), 16);
        LinearLayout general = Ui.ajouter(c, Ui.carte(this), 8);
        general.addView(Ui.lien(this, l.toutSauf ? "Applis épargnées" : "Applis et groupes",
                l.toutSauf && l.applis.isEmpty() && l.groupes.isEmpty() ? "aucune" : resumeCibles(),
                v -> Choix.cibles(this, l.toutSauf ? "Ce que la limite épargne" : "Ce que vise la limite",
                        l.applis, l.groupes, this::rafraichir)));
        if (!l.applis.isEmpty() || !l.groupes.isEmpty()) {
            general.addView(Ui.petit(this, nomsCibles()));
        }
        CheckBox sauf = Ui.caseACocher(this, "Tout le téléphone, sauf ces applis", l.toutSauf);
        sauf.setOnCheckedChangeListener((b, coche) -> {
            l.toutSauf = coche;
            rafraichir();
        });
        general.addView(sauf);
        if (l.toutSauf) {
            general.addView(Ui.petit(this, "Les nouvelles applis sont visées d’office ; le téléphone (appels), "
                    + "les Réglages, l’accueil et Discipline restent libres."));
        }

        Ui.ajouter(c, Ui.section(this, l.conditions.size() > 1 ? "Règles" : "Règle"), 16);
        for (int i = 0; i < l.conditions.size(); i++) {
            if (i > 0) {
                operateur(c, i - 1);
            }
            condition(c, i);
        }

        quand(c);

        Ui.ajouter(c, Ui.boutonDiscret(this, plus ? "Moins de réglages  ▴" : "Plus de réglages  ▾"), 20)
                .setOnClickListener(v -> {
                    plus = !plus;
                    rafraichir();
                });
        if (plus) {
            LinearLayout nom = Ui.ajouter(c, Ui.carte(this), 12);
            nom.addView(Ui.lien(this, "Nom", l.nom.isEmpty() ? "(automatique)" : l.nom,
                    v -> Choix.texte(this, "Nom de la limite", l.nom, l.nomAffiche(donnees), t -> {
                        l.nom = t;
                        rafraichir();
                    })));
            sites(nom);
            Ui.ajouter(c, Ui.boutonDiscret(this, "＋ Combiner avec une autre règle"), 10).setOnClickListener(v -> combiner());
            datesExclues(c);
            action(c);
            rallonge(c);
            bulles(c);
            options(c);
            if (!nouvelle) {
                Ui.ajouter(c, Ui.boutonDiscret(this, "Dupliquer cette limite"), 20).setOnClickListener(v -> dupliquer());
            }
        }

        boutonBas(c, "Enregistrer", v -> enregistrer());
    }

    private String resumeCibles() {
        int n = l.applis.size();
        int g = l.groupes.size();
        if (n == 0 && g == 0) {
            return "à choisir";
        }
        List<String> parts = new ArrayList<>();
        if (g > 0) {
            parts.add(g + " groupe" + (g > 1 ? "s" : ""));
        }
        if (n > 0) {
            parts.add(n + " appli" + (n > 1 ? "s" : ""));
        }
        return String.join(", ", parts);
    }

    /** Sites (« youtube.com ») et mots-clés (« match ») cherchés dans la barre d'adresse. */
    private void sites(LinearLayout carte) {
        Ui.ajouter(carte, Ui.corps(this, l.toutSauf ? "Sites épargnés" : "Sites et mots-clés"), 14);
        for (String s : new ArrayList<>(l.sites)) {
            LinearLayout r = Ui.rangee(this);
            Ui.etirer(r, Ui.corps(this, s.contains(".") ? s : "« " + s + " » dans l’adresse"));
            r.addView(Ui.croix(this, v -> {
                l.sites.remove(s);
                rafraichir();
            }));
            carte.addView(r);
        }
        carte.addView(Ui.lien(this, "＋ Ajouter", null, v -> Choix.texte(this, "Site (lemonde.fr) ou mot-clé (foot)", "",
                "youtube.com", t -> {
                    String s = t.trim().toLowerCase(Locale.ROOT);
                    s = s.contains(".") ? Sites.normaliser(s) : s;
                    if (!s.isEmpty() && !l.sites.contains(s)) {
                        l.sites.add(s);
                    }
                    rafraichir();
                })));
        carte.addView(Ui.petit(this, "Une appli visée vise aussi son site. Les mots-clés marchent dans Chrome, "
                + "Firefox, Samsung Internet, Edge, Brave, Opera et DuckDuckGo."));
    }

    private String nomsCibles() {
        String nom = l.nom;
        l.nom = "";
        String noms = l.nomAffiche(donnees);
        l.nom = nom;
        return noms;
    }

    // ---- Conditions --------------------------------------------------------

    /** Petit curseur ET / OU entre deux règles. */
    private void operateur(LinearLayout c, int i) {
        LinearLayout r = Ui.ajouter(c, Ui.rangee(this), 8);
        r.setGravity(android.view.Gravity.CENTER);
        boolean et = l.et.get(i);
        TextView bEt = Ui.pastille(this, "  ET  ", et ? Ui.VERT : Ui.CARTE2);
        TextView bOu = Ui.pastille(this, "  OU  ", et ? Ui.CARTE2 : Ui.ORANGE);
        bEt.setTextSize(14);
        bOu.setTextSize(14);
        bEt.setOnClickListener(v -> {
            l.et.set(i, true);
            rafraichir();
        });
        bOu.setOnClickListener(v -> {
            l.et.set(i, false);
            rafraichir();
        });
        r.addView(bEt);
        TextView espace = Ui.petit(this, "   ");
        r.addView(espace);
        r.addView(bOu);
    }

    private void condition(LinearLayout parent, int i) {
        Condition cond = l.conditions.get(i);
        LinearLayout carte = Ui.ajouter(parent, Ui.carte(this), 8);
        LinearLayout tete = Ui.rangee(this);
        carte.addView(tete);
        Ui.etirer(tete, Ui.texte(this, Condition.nomType(cond.type), 17, Ui.TEXTE, true));
        if (l.conditions.size() > 1) {
            tete.addView(Ui.croix(this, v -> {
                l.conditions.remove(i);
                l.et.remove(Math.max(0, i - 1));
                rafraichir();
            }));
        }
        carte.addView(Ui.petit(this, Condition.descriptionType(cond.type)));

        switch (cond.type) {
            case Condition.TEMPS:
                reglette(carte, "Temps autorisé", cond.valeur, MIN, 0, x -> cond.valeur = x);
                if (cond.valeur == 0) {
                    Ui.ajouter(carte, Ui.petit(this, "0 min : l’appli reste fermée tant que la limite s’applique."), 2);
                }
                break;
            case Condition.OUVERTURES:
                reglette(carte, "Ouvertures", cond.valeur, "fois", 0, x -> cond.valeur = x);
                break;
            case Condition.DUREE_SESSION:
                reglette(carte, "D’affilée au plus", cond.valeur, MIN, 0, x -> cond.valeur = x);
                break;
            case Condition.SESSIONS:
                reglette(carte, "Sessions", cond.valeur, "", 0, x -> cond.valeur = x);
                reglette(carte, "De", cond.valeur2, MIN, 0, x -> cond.valeur2 = x);
                break;
            case Condition.PAUSE:
                reglette(carte, "Pause", cond.valeur, MIN, 0, x -> cond.valeur = x);
                break;
            case Condition.PAUSE_PROPORTIONNELLE:
                carte.addView(Ui.lien(this, "Coefficient", String.format(Locale.FRANCE, "× %.2f", cond.coef),
                        v -> Choix.texte(this, "Coefficient (ex. 0,5 : 20 min d’usage → 10 min de pause)",
                                String.format(Locale.FRANCE, "%.2f", cond.coef), "",
                                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, t -> {
                                    try {
                                        cond.coef = Math.max(0.05, Double.parseDouble(t.replace(',', '.')));
                                    } catch (NumberFormatException ignore) {
                                        // saisie illisible
                                    }
                                    rafraichir();
                                })));
                break;
            case Condition.FRICTION:
                Ui.ajouter(carte, Ui.corps(this, "Avant d’ouvrir, il faut :"), 10);
                carte.addView(Ui.choixUnique(this, Condition.MODES_FRICTION, cond.modeFriction, x -> {
                    cond.modeFriction = x;
                    rafraichir();
                }));
                if (cond.modeFriction == Condition.FRICTION_ATTENTE) {
                    reglette(carte, "Compte à rebours", cond.valeur, "s", 0, x -> cond.valeur = x);
                    CheckBox doubler = Ui.caseACocher(this, "Doubler l’attente à chaque ouverture du jour (10 min au plus)",
                            cond.doubler);
                    doubler.setOnCheckedChangeListener((b, coche) -> cond.doubler = coche);
                    carte.addView(doubler);
                } else if (cond.modeFriction == Condition.FRICTION_POURQUOI) {
                    Ui.ajouter(carte, Ui.petit(this, "Tes réponses sont gardées (les 200 dernières)."), 4);
                }
                break;
            case Condition.IMMEDIAT:
                reglette(carte, "Durée du blocage", cond.valeur, MIN, 1, x -> cond.valeur = x);
                Ui.ajouter(carte, Ui.petit(this, "Se lance depuis l’accueil avec le bouton « Bloque-moi ça »."), 4);
                break;
            case Condition.NFC:
                Ui.ajouter(carte, Ui.corps(this, "Un bip du badge débloque :"), 10);
                carte.addView(Ui.choixUnique(this, new String[]{"une session", "pendant X minutes",
                        "jusqu’à la fin de la période"}, cond.modeNfc, x -> {
                    cond.modeNfc = x;
                    rafraichir();
                }));
                if (cond.modeNfc == Condition.NFC_MINUTES) {
                    reglette(carte, "Débloquée pendant", cond.valeur2, MIN, 1, x -> cond.valeur2 = x);
                }
                if (donnees.badges.isEmpty()) {
                    Ui.ajouter(carte, Ui.texte(this, "Aucun badge enregistré : ajoute-le dans ⚙ > Badges NFC.",
                            13, Ui.ORANGE, false), 6);
                } else {
                    carte.addView(Ui.lien(this, "Badges qui l’ouvrent", resumeBadges(cond), v -> choisirBadges(cond)));
                }
                break;
            default:
                break;
        }
        if (cond.aPeriode()) {
            periode(carte, cond.periode);
        }
        if (cond.aValeurParJour()) {
            parJour(carte, cond);
        }
    }

    private String resumeBadges(Condition cond) {
        if (cond.badges.isEmpty()) {
            return "tous";
        }
        List<String> noms = new ArrayList<>();
        for (String id : cond.badges) {
            noms.add(donnees.badges.getOrDefault(id, "badge oublié"));
        }
        return String.join(", ", noms);
    }

    /** Aucun coché = n'importe quel badge enregistré. */
    private void choisirBadges(Condition cond) {
        String[] ids = donnees.badges.keySet().toArray(new String[0]);
        String[] noms = new String[ids.length];
        boolean[] coches = new boolean[ids.length];
        for (int i = 0; i < ids.length; i++) {
            noms[i] = donnees.badges.get(ids[i]);
            coches[i] = cond.badges.contains(ids[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle("Badges qui ouvrent cette règle")
                .setMessage("Aucun coché : n’importe lequel.")
                .setMultiChoiceItems(noms, coches, (d, i, coche) -> coches[i] = coche)
                .setPositiveButton("Valider", (d, w) -> {
                    cond.badges.clear();
                    for (int i = 0; i < ids.length; i++) {
                        if (coches[i]) {
                            cond.badges.add(ids[i]);
                        }
                    }
                    rafraichir();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    /** Unité « minutes » d'une réglette : affichée « 1 h 30 », pas de 1 sous 10 min, de 5 ensuite, de 15 dès 2 h. */
    private static final String MIN = "min";

    /** Réglette − valeur + ; toucher la valeur permet de la taper. */
    private void reglette(LinearLayout parent, String libelle, int valeur, String unite, int min, IntConsumer suite) {
        boolean minutes = MIN.equals(unite);
        String affiche = minutes ? Condition.minutes(valeur) : valeur + (unite.isEmpty() ? "" : " " + unite);
        parent.addView(Ui.reglette(this, libelle, affiche,
                v -> {
                    suite.accept(Math.max(min, pas(valeur, false, minutes, "s".equals(unite))));
                    rafraichir();
                },
                v -> {
                    suite.accept(pas(valeur, true, minutes, "s".equals(unite)));
                    rafraichir();
                },
                v -> Choix.nombre(this, libelle + (unite.isEmpty() ? "" : " (" + unite + ")"), valeur, x -> {
                    if (x >= 0) {
                        suite.accept(Math.max(min, x));
                    }
                    rafraichir();
                })));
    }

    /** Valeur suivante (ou précédente), arrondie au pas pour tomber sur des chiffres ronds. */
    private static int pas(int v, boolean monter, boolean minutes, boolean secondes) {
        int base = monter ? v : v - 1;
        int p = secondes ? 5 : !minutes ? 1 : base < 10 ? 1 : base < 120 ? 5 : 15;
        return Math.max(0, monter ? (v / p + 1) * p : ((v - 1) / p) * p);
    }

    private void periode(LinearLayout carte, Periode p) {
        Ui.ajouter(carte, Ui.puces(this, new String[]{"par heure", "par jour", "par semaine"}, p.unite, x -> {
            p.unite = x;
            rafraichir();
        }), 8);
        if (p.unite == Periode.HEURE) {
            CheckBox g = Ui.caseACocher(this, "Glissante (les 60 dernières minutes)", p.glissante);
            g.setOnCheckedChangeListener((b, coche) -> {
                p.glissante = coche;
                rafraichir();
            });
            carte.addView(g);
            return;
        }
        if (!plus && p.debutMinutes == 0 && (p.unite == Periode.JOUR || p.jourSemaine == java.util.Calendar.MONDAY)) {
            return; // minuit, lundi : réglage courant, rangé dans « Plus de réglages »
        }
        if (p.unite == Periode.SEMAINE) {
            carte.addView(Ui.lien(this, "Commence le", Periode.JOURS[Periode.indexJour(p.jourSemaine)],
                    v -> Choix.jourSemaine(this, j -> {
                        p.jourSemaine = j;
                        rafraichir();
                    })));
        }
        carte.addView(Ui.lien(this, "À partir de", Ui.heure(p.debutMinutes), v -> Choix.heure(this, p.debutMinutes, m -> {
            p.debutMinutes = m;
            rafraichir();
        })));
    }

    private void parJour(LinearLayout carte, Condition cond) {
        if (!plus && cond.parJour == null) {
            return;
        }
        CheckBox varie = Ui.ajouter(carte, Ui.caseACocher(this, "Valeur différente selon le jour", cond.parJour != null), 6);
        varie.setOnCheckedChangeListener((b, coche) -> {
            if (coche) {
                cond.parJour = new int[]{-1, -1, -1, -1, -1, -1, -1};
            } else {
                cond.parJour = null;
            }
            rafraichir();
        });
        if (cond.parJour == null) {
            return;
        }
        for (int j = 0; j < 7; j++) {
            final int jour = j;
            int v = cond.parJour[j];
            carte.addView(Ui.lien(this, "    " + Periode.JOURS[j], v < 0 ? "par défaut (" + cond.valeur + ")" : String.valueOf(v),
                    x -> Choix.nombre(this, "Valeur du " + Periode.JOURS[jour] + " (vide = par défaut)", v, n -> {
                        cond.parJour[jour] = n;
                        rafraichir();
                    })));
        }
    }

    private void combiner() {
        new AlertDialog.Builder(this)
                .setTitle("Combiner avec une autre règle")
                .setMessage("ET : bloqué dès que l’une des deux n’est plus respectée.\n"
                        + "OU : bloqué seulement quand aucune des deux n’est respectée.")
                .setPositiveButton("ET", (d, w) -> choisirType(true))
                .setNeutralButton("OU", (d, w) -> choisirType(false))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void choisirType(boolean et) {
        String[] noms = new String[Condition.NOMBRE_TYPES];
        for (int i = 0; i < noms.length; i++) {
            noms[i] = Condition.nomType(i + 1);
        }
        new AlertDialog.Builder(this)
                .setTitle("Quelle règle ?")
                .setItems(noms, (d, i) -> {
                    l.conditions.add(Condition.nouvelle(i + 1));
                    l.et.add(et);
                    rafraichir();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    // ---- Exceptions --------------------------------------------------------

    private void quand(LinearLayout c) {
        Ui.ajouter(c, Ui.section(this, "Quand"), 16);
        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 8);
        int[] raccourcis = {0x7F, 0x1F, 0x60};
        int choisi = -1;
        for (int i = 0; i < raccourcis.length; i++) {
            if (l.jours == raccourcis[i]) {
                choisi = i;
            }
        }
        carte.addView(Ui.puces(this, new String[]{"Tous les jours", "En semaine", "Week-end"}, choisi, x -> {
            l.jours = raccourcis[x];
            rafraichir();
        }));
        LinearLayout jours = Ui.ajouter(carte, Ui.rangee(this), 10);
        String[] lettres = {"L", "M", "M", "J", "V", "S", "D"};
        for (int j = 0; j < 7; j++) {
            final int bit = 1 << j;
            TextView p = Ui.pastille(this, lettres[j], (l.jours & bit) != 0 ? Ui.VERT : Ui.CARTE2);
            p.setTextSize(15);
            p.setGravity(android.view.Gravity.CENTER);
            p.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            p.setOnClickListener(v -> {
                l.jours ^= bit;
                rafraichir();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            lp.leftMargin = j == 0 ? 0 : Ui.dp(this, 4);
            jours.addView(p, lp);
        }

        Ui.ajouter(carte, Ui.corps(this, l.plages.isEmpty() ? "Toute la journée" : "Aux heures"), 14);
        for (int[] p : new ArrayList<>(l.plages)) {
            LinearLayout r = Ui.rangee(this);
            Ui.etirer(r, Ui.corps(this, "de " + Ui.heure(p[0]) + " à " + Ui.heure(p[1])));
            r.addView(Ui.croix(this, v -> {
                l.plages.remove(p);
                rafraichir();
            }));
            carte.addView(r);
        }
        carte.addView(Ui.lien(this, l.plages.isEmpty() ? "＋ Seulement à certaines heures" : "＋ Ajouter une plage", null,
                v -> Choix.heure(this, 9 * 60, debut -> {
                    toast("Et l’heure de fin ?");
                    Choix.heure(this, Math.min(debut + 120, 23 * 60 + 59), fin -> {
                        l.plages.add(new int[]{debut, fin});
                        rafraichir();
                    });
                })));
    }

    private void datesExclues(LinearLayout c) {
        Ui.ajouter(c, Ui.section(this, "Dates où elle ne s’applique pas"), 20);
        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 8);
        if (l.datesExclues.isEmpty()) {
            carte.addView(Ui.petit(this, "Aucune (les vacances se règlent dans ⚙)."));
        }
        for (long[] d : new ArrayList<>(l.datesExclues)) {
            LinearLayout r = Ui.rangee(this);
            String texte = d[0] == d[1] ? "le " + jour(d[0]) : "du " + jour(d[0]) + " au " + jour(d[1]);
            Ui.etirer(r, Ui.corps(this, texte));
            r.addView(Ui.croix(this, v -> {
                l.datesExclues.remove(d);
                rafraichir();
            }));
            carte.addView(r);
        }
        carte.addView(Ui.lien(this, "＋ Exclure des dates", null, v -> Choix.date(this, System.currentTimeMillis(), du -> {
            toast("Et jusqu’à quel jour (inclus) ?");
            Choix.date(this, du, au -> {
                l.datesExclues.add(new long[]{du, Math.max(du, au)});
                rafraichir();
            });
        })));
    }

    private static String jour(long ms) {
        return new java.text.SimpleDateFormat("EEE d MMM yyyy", Locale.FRANCE).format(new java.util.Date(ms));
    }

    // ---- Action et écran de blocage ----------------------------------------

    private boolean ecranForce() {
        return l.conditionDeType(Condition.FRICTION) != null || l.conditionDeType(Condition.NFC) != null;
    }

    private void action(LinearLayout c) {
        Ui.ajouter(c, Ui.section(this, "Quand elle est atteinte"), 20);
        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 8);
        carte.addView(Ui.choixUnique(this, new String[]{"Fermer l’appli", "Ouvrir une autre appli", "Écran de blocage"},
                l.action, x -> {
                    l.action = x;
                    rafraichir();
                }));
        if (l.action == Limite.AUTRE_APPLI) {
            carte.addView(Ui.lien(this, "Appli à ouvrir",
                    l.appliAlternative == null ? "à choisir" : Applications.nom(this, l.appliAlternative),
                    v -> Choix.appli(this, "Appli à ouvrir à la place", p -> {
                        l.appliAlternative = p;
                        rafraichir();
                    })));
        }
        if (ecranForce() && l.action != Limite.ECRAN) {
            Ui.ajouter(carte, Ui.petit(this, "La friction et le badge passent toujours par l’écran de blocage."), 6);
        }
        if (l.action != Limite.ECRAN && !ecranForce()) {
            return;
        }

        Ui.ajouter(carte, Ui.corps(this, "Messages (un est tiré au hasard)"), 14);
        for (String m : new ArrayList<>(l.messages)) {
            LinearLayout r = Ui.ajouter(carte, Ui.rangee(this), 4);
            Ui.etirer(r, Ui.texte(this, "« " + m + " »", 14, Ui.TEXTE, false));
            r.addView(Ui.croix(this, v -> {
                l.messages.remove(m);
                rafraichir();
            }));
        }
        carte.addView(Ui.lien(this, "＋ Nouveau message", null, v -> Choix.texte(this, "Message", "",
                "Tu as déjà passé {temps} ici aujourd’hui.", t -> {
                    if (!t.isEmpty()) {
                        l.messages.add(t);
                        if (!donnees.messages.contains(t)) {
                            donnees.messages.add(t);
                            donnees.enregistrer();
                        }
                    }
                    rafraichir();
                })));
        carte.addView(Ui.lien(this, "＋ Depuis la bibliothèque", null, v -> depuisBibliotheque()));
        carte.addView(Ui.petit(this, "Variables : {appli} {temps} {ouvertures} {dispo} {rallonges}"));

        LinearLayout image = Ui.rangee(this);
        Ui.etirer(image, Ui.lien(this, "Image", l.image == null ? "aucune" : "choisie", v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
            startActivityForResult(i, IMAGE);
        }));
        if (l.image != null) {
            image.addView(Ui.croix(this, v -> {
                l.image = null;
                rafraichir();
            }));
        }
        carte.addView(image);
        CheckBox dispo = Ui.caseACocher(this, "Afficher « de nouveau disponible dans… »", l.afficherDispo);
        dispo.setOnCheckedChangeListener((b, coche) -> l.afficherDispo = coche);
        carte.addView(dispo);
    }

    private void depuisBibliotheque() {
        if (donnees.messages.isEmpty()) {
            toast("La bibliothèque est vide : écris un nouveau message, il y sera rangé.");
            return;
        }
        String[] tous = donnees.messages.toArray(new String[0]);
        boolean[] coches = new boolean[tous.length];
        for (int i = 0; i < tous.length; i++) {
            coches[i] = l.messages.contains(tous[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle("Bibliothèque de messages")
                .setMultiChoiceItems(tous, coches, (d, i, coche) -> coches[i] = coche)
                .setPositiveButton("Valider", (d, w) -> {
                    for (int i = 0; i < tous.length; i++) {
                        l.messages.remove(tous[i]);
                        if (coches[i]) {
                            l.messages.add(tous[i]);
                        }
                    }
                    rafraichir();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requete, int resultat, Intent data) {
        super.onActivityResult(requete, resultat, data);
        if (requete == IMAGE && resultat == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignore) {
                // l'image restera lisible au moins jusqu'au redémarrage
            }
            l.image = uri.toString();
            rafraichir();
        }
    }

    // ---- Rallonge et bulles ------------------------------------------------

    private void rallonge(LinearLayout c) {
        Ui.ajouter(c, Ui.section(this, "Rallonge"), 20);
        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 8);
        carte.addView(Ui.lien(this, "Rallonge", l.rallongeMinutes <= 0 ? "aucune" : "+" + l.rallongeMinutes + " min",
                v -> Choix.nombre(this, "Minutes de rallonge (0 = aucune)", l.rallongeMinutes, x -> {
                    l.rallongeMinutes = Math.max(0, x);
                    rafraichir();
                })));
        if (l.rallongeMinutes <= 0) {
            return;
        }
        reglette(carte, "Par période", l.rallongeNombre, "fois", 0, x -> l.rallongeNombre = x);
        reglette(carte, "Attente avant", l.rallongeAttente, "s", 0, x -> l.rallongeAttente = x);
        CheckBox nfc = Ui.caseACocher(this, "Exiger un bip du badge", l.rallongeNfc);
        nfc.setOnCheckedChangeListener((b, coche) -> l.rallongeNfc = coche);
        carte.addView(nfc);
        CheckBox prog = Ui.caseACocher(this, "L’attente double à chaque rallonge du jour", l.rallongeProgressive);
        prog.setOnCheckedChangeListener((b, coche) -> l.rallongeProgressive = coche);
        carte.addView(prog);
        CheckBox motif = Ui.caseACocher(this, "Dire pourquoi avant de l’obtenir", l.rallongeMotif);
        motif.setOnCheckedChangeListener((b, coche) -> l.rallongeMotif = coche);
        carte.addView(motif);
    }

    private void options(LinearLayout c) {
        Ui.ajouter(c, Ui.section(this, "Options"), 20);
        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 8);
        CheckBox plage = Ui.caseACocher(this, "Ne compter le temps que pendant les jours et plages choisis", l.dansPlage);
        plage.setOnCheckedChangeListener((b, coche) -> l.dansPlage = coche);
        carte.addView(plage);
        CheckBox silence = Ui.caseACocher(this, "Notifications en sourdine tant qu’elle bloque", l.silence);
        silence.setOnCheckedChangeListener((b, coche) -> {
            l.silence = coche;
            if (coche && !Notifications.autorise(this)) {
                toast("Il faut l’accès aux notifications : ⚙ > Anti-triche.");
            }
        });
        carte.addView(silence);
        CheckBox gris = Ui.caseACocher(this, "Écran en noir et blanc pendant l’usage", l.grisaille);
        gris.setOnCheckedChangeListener((b, coche) -> {
            l.grisaille = coche;
            if (coche && !Grisaille.possible(this)) {
                grisailleAide();
            }
        });
        carte.addView(gris);
    }

    /** Le noir et blanc touche un réglage protégé d'Android : une seule commande depuis un PC suffit. */
    private void grisailleAide() {
        new AlertDialog.Builder(this)
                .setTitle("Une autorisation à donner une fois")
                .setMessage("Android réserve ce réglage : il faut le brancher une fois à un PC (débogage USB activé) "
                        + "et taper :\n\nadb shell pm grant " + getPackageName()
                        + " android.permission.WRITE_SECURE_SETTINGS\n\nEn attendant, la case est gardée mais sans effet.")
                .setPositiveButton("Compris", null)
                .show();
    }

    private void bulles(LinearLayout c) {
        Ui.ajouter(c, Ui.section(this, "Bulles de temps restant"), 20);
        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 8);
        if (l.bulles.isEmpty()) {
            carte.addView(Ui.petit(this, "Aucune bulle."));
        }
        for (Integer s : new ArrayList<>(l.bulles)) {
            LinearLayout r = Ui.rangee(this);
            Ui.etirer(r, Ui.corps(this, "quand il reste " + s + " min"));
            r.addView(Ui.croix(this, v -> {
                l.bulles.remove(s);
                rafraichir();
            }));
            carte.addView(r);
        }
        carte.addView(Ui.lien(this, "＋ Ajouter un seuil", null, v -> Choix.nombre(this, "Bulle quand il reste (min)", 5, x -> {
            if (x > 0 && !l.bulles.contains(x)) {
                l.bulles.add(x);
                Collections.sort(l.bulles, Collections.reverseOrder());
            }
            rafraichir();
        })));
    }

    // ---- Enregistrer, supprimer --------------------------------------------

    private void enregistrer() {
        if (l.applis.isEmpty() && l.groupes.isEmpty() && !l.toutSauf && l.sites.isEmpty()) {
            toast("Choisis au moins une appli, un groupe ou un site.");
            return;
        }
        if (l.action == Limite.AUTRE_APPLI && l.appliAlternative == null) {
            toast("Choisis l’appli à ouvrir à la place.");
            return;
        }
        Limite ancienne = donnees.limite(l.id);
        if (ancienne == null || !ancienne.active) {
            donnees.appliquer(Donnees.changementLimite(l, false));
            Donnees.Profil profil = donnees.profil(donnees.profilActif);
            if (ancienne == null && profil != null) {
                profil.limites.add(l.id);
                donnees.enregistrer();
            }
            finish();
            return;
        }
        if (l.aussiStricteQue(ancienne)) {
            // durcir ne se discute pas : appliqué tout de suite
            donnees.appliquer(Donnees.changementLimite(l, false));
            finish();
            return;
        }
        garde(Donnees.changementLimite(l, false), "modifier « " + l.nomAffiche(donnees) + " »", this::finish);
    }

    /** Une copie de ce qui est à l'écran (modifications comprises), à régler puis enregistrer. */
    private void dupliquer() {
        Limite copie = l.copie();
        copie.id = Donnees.nouvelId();
        for (Condition cond : copie.conditions) {
            cond.id = Donnees.nouvelId(); // compteurs et passes à part
        }
        copie.nom = l.nom.isEmpty() ? "" : l.nom + " (copie)";
        l = copie;
        nouvelle = true;
        rafraichir();
        toast("Copie prête : règle-la puis enregistre.");
    }

    private void supprimer() {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer « " + l.nomAffiche(donnees) + " » ?")
                .setMessage("Ses réglages et ses compteurs seront perdus.")
                .setPositiveButton("Supprimer", (d, w) -> {
                    Limite ancienne = donnees.limite(l.id);
                    if (ancienne != null && ancienne.active) {
                        garde(Donnees.changementLimite(l, true), "supprimer « " + l.nomAffiche(donnees) + " »", this::finish);
                    } else {
                        donnees.appliquer(Donnees.changementLimite(l, true));
                        finish();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }
}
