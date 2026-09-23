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
    private static final int IMAGE = 1;

    private Limite l;
    private boolean nouvelle;

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        Limite existante = donnees.limite(getIntent().getStringExtra(EXTRA_ID));
        if (existante != null) {
            l = existante.copie();
        } else {
            nouvelle = true;
            l = new Limite();
            l.conditions.add(Condition.nouvelle(getIntent().getIntExtra(EXTRA_TYPE, Condition.TEMPS)));
        }
        rafraichir();
    }

    @Override
    protected void rafraichir() {
        LinearLayout c = page(nouvelle ? "Nouvelle limite" : "Modifier la limite", true);
        if (!nouvelle) {
            actionEntete("Supprimer", v -> supprimer());
        }

        LinearLayout general = Ui.ajouter(c, Ui.carte(this), 12);
        general.addView(Ui.lien(this, "Nom", l.nom.isEmpty() ? "(automatique)" : l.nom,
                v -> Choix.texte(this, "Nom de la limite", l.nom, l.nomAffiche(donnees), t -> {
                    l.nom = t;
                    rafraichir();
                })));
        general.addView(Ui.lien(this, "Applis et groupes", resumeCibles(),
                v -> Choix.cibles(this, "Ce que vise la limite", l.applis, l.groupes, this::rafraichir)));
        if (!l.applis.isEmpty() || !l.groupes.isEmpty()) {
            general.addView(Ui.petit(this, l.nomAffiche(donnees).equals(l.nom) ? nomsCibles() : l.nomAffiche(donnees)));
        }

        Ui.ajouter(c, Ui.section(this, "Règles"), 20);
        for (int i = 0; i < l.conditions.size(); i++) {
            if (i > 0) {
                operateur(c, i - 1);
            }
            condition(c, i);
        }
        Ui.ajouter(c, Ui.boutonDiscret(this, "＋ Combiner avec une autre règle"), 10).setOnClickListener(v -> combiner());

        exceptions(c);
        action(c);
        rallonge(c);
        bulles(c);

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
                reglage(carte, "Temps autorisé", cond.valeur, "min", x -> cond.valeur = Math.max(1, x));
                break;
            case Condition.OUVERTURES:
                reglage(carte, "Ouvertures autorisées", cond.valeur, "", x -> cond.valeur = Math.max(0, x));
                break;
            case Condition.DUREE_SESSION:
                reglage(carte, "Durée maximale d’une session", cond.valeur, "min", x -> cond.valeur = Math.max(1, x));
                break;
            case Condition.SESSIONS:
                reglage(carte, "Nombre de sessions", cond.valeur, "", x -> cond.valeur = Math.max(1, x));
                reglage(carte, "Durée maximale de chacune", cond.valeur2, "min", x -> cond.valeur2 = Math.max(1, x));
                break;
            case Condition.PAUSE:
                reglage(carte, "Pause après usage", cond.valeur, "min", x -> cond.valeur = Math.max(1, x));
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
                reglage(carte, "Compte à rebours", cond.valeur, "s", x -> cond.valeur = Math.max(1, x));
                break;
            case Condition.IMMEDIAT:
                reglage(carte, "Durée du blocage", cond.valeur, "min", x -> cond.valeur = Math.max(1, x));
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
                    reglage(carte, "Minutes de déblocage", cond.valeur2, "min", x -> cond.valeur2 = Math.max(1, x));
                }
                if (donnees.badges.isEmpty()) {
                    Ui.ajouter(carte, Ui.texte(this, "Aucun badge enregistré : ajoute-le dans ⚙ > Badges NFC.",
                            13, Ui.ORANGE, false), 6);
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

    private void reglage(LinearLayout parent, String libelle, int valeur, String unite, IntConsumer suite) {
        parent.addView(Ui.lien(this, libelle, valeur + (unite.isEmpty() ? "" : " " + unite),
                v -> Choix.nombre(this, libelle + (unite.isEmpty() ? "" : " (" + unite + ")"), valeur, x -> {
                    if (x >= 0) {
                        suite.accept(x);
                    }
                    rafraichir();
                })));
    }

    private void periode(LinearLayout carte, Periode p) {
        Ui.ajouter(carte, Ui.corps(this, "Période"), 10);
        carte.addView(Ui.choixUnique(this, new String[]{"heure", "jour", "semaine"}, p.unite, x -> {
            p.unite = x;
            rafraichir();
        }));
        if (p.unite == Periode.HEURE) {
            CheckBox g = Ui.caseACocher(this, "Glissante (les 60 dernières minutes)", p.glissante);
            g.setOnCheckedChangeListener((b, coche) -> p.glissante = coche);
            carte.addView(g);
            return;
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

    private void exceptions(LinearLayout c) {
        Ui.ajouter(c, Ui.section(this, "Quand elle s’applique"), 20);
        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 8);
        carte.addView(Ui.corps(this, "Jours"));
        LinearLayout jours = Ui.ajouter(carte, Ui.rangee(this), 8);
        String[] lettres = {"L", "M", "M", "J", "V", "S", "D"};
        for (int j = 0; j < 7; j++) {
            final int bit = 1 << j;
            TextView p = Ui.pastille(this, " " + lettres[j] + " ", (l.jours & bit) != 0 ? Ui.VERT : Ui.CARTE2);
            p.setTextSize(15);
            p.setOnClickListener(v -> {
                l.jours ^= bit;
                rafraichir();
            });
            jours.addView(p);
            if (j < 6) {
                jours.addView(Ui.petit(this, "  "));
            }
        }

        Ui.ajouter(carte, Ui.corps(this, "Plages horaires"), 14);
        if (l.plages.isEmpty()) {
            carte.addView(Ui.petit(this, "Toute la journée."));
        }
        for (int[] p : new ArrayList<>(l.plages)) {
            LinearLayout r = Ui.rangee(this);
            Ui.etirer(r, Ui.corps(this, "de " + Ui.heure(p[0]) + " à " + Ui.heure(p[1])));
            r.addView(Ui.croix(this, v -> {
                l.plages.remove(p);
                rafraichir();
            }));
            carte.addView(r);
        }
        carte.addView(Ui.lien(this, "＋ Ajouter une plage", null, v -> Choix.heure(this, 9 * 60, debut -> {
            toast("Et l’heure de fin ?");
            Choix.heure(this, Math.min(debut + 120, 23 * 60 + 59), fin -> {
                l.plages.add(new int[]{debut, fin});
                rafraichir();
            });
        })));

        Ui.ajouter(carte, Ui.corps(this, "Dates exclues"), 14);
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
        reglage(carte, "Nombre par période", l.rallongeNombre, "fois", x -> l.rallongeNombre = Math.max(1, x));
        reglage(carte, "Attente avant de l’obtenir", l.rallongeAttente, "s", x -> l.rallongeAttente = Math.max(0, x));
        CheckBox nfc = Ui.caseACocher(this, "Exiger un bip du badge", l.rallongeNfc);
        nfc.setOnCheckedChangeListener((b, coche) -> l.rallongeNfc = coche);
        carte.addView(nfc);
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
        if (l.applis.isEmpty() && l.groupes.isEmpty()) {
            toast("Choisis au moins une appli ou un groupe.");
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
        garde(Donnees.changementLimite(l, false), "modifier « " + l.nomAffiche(donnees) + " »", this::finish);
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
