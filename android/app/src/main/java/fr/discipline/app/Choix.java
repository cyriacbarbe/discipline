package fr.discipline.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/** Fenêtres de choix : applis et groupes (avec leurs pastilles), heure, date, texte. */
final class Choix {
    private Choix() {
    }

    private static final class Element {
        final String id;
        final String nom;
        final String detail;
        final boolean groupe;

        Element(String id, String nom, String detail, boolean groupe) {
            this.id = id;
            this.nom = nom;
            this.detail = detail;
            this.groupe = groupe;
        }
    }

    /**
     * Choix multiple d'applis et, si {@code groupes} n'est pas null, de groupes.
     * Chaque appli montre ses groupes en pastilles. Les ensembles sont modifiés à la validation.
     */
    static void cibles(Activity a, String titre, Set<String> applis, Set<String> groupes, Runnable ok) {
        Donnees d = Donnees.get(a);
        List<Element> tous = new ArrayList<>();
        if (groupes != null) {
            for (Donnees.Groupe g : d.groupes.values()) {
                tous.add(new Element(g.id, g.nom, "Groupe · " + g.paquets.size() + " appli(s)", true));
            }
        }
        for (AppInfo app : Applications.installees(a.getPackageManager(), a.getPackageName())) {
            List<String> noms = new ArrayList<>();
            for (Donnees.Groupe g : d.groupesDe(app.paquet)) {
                noms.add("● " + g.nom);
            }
            tous.add(new Element(app.paquet, app.nom, String.join("   ", noms), false));
        }
        Set<String> applisCochees = new HashSet<>(applis);
        Set<String> groupesCoches = groupes == null ? new HashSet<>() : new HashSet<>(groupes);
        List<Element> visibles = new ArrayList<>(tous);

        LinearLayout vue = Ui.colonne(a);
        int p = Ui.dp(a, 16);
        vue.setPadding(p, p / 2, p, 0);
        EditText recherche = Ui.ajouter(vue, Ui.champ(a, "", "Rechercher"), 0);
        ListView liste = new ListView(a);
        vue.addView(liste, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 420)));

        BaseAdapter adaptateur = new BaseAdapter() {
            @Override
            public int getCount() {
                return visibles.size();
            }

            @Override
            public Object getItem(int i) {
                return visibles.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View recyclee, ViewGroup parent) {
                LinearLayout ligne;
                if (recyclee == null) {
                    ligne = Ui.rangee(a);
                    ligne.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
                    CheckBox cb = Ui.caseACocher(a, "", false);
                    cb.setClickable(false);
                    cb.setFocusable(false);
                    ligne.addView(cb);
                    LinearLayout textes = Ui.colonne(a);
                    textes.addView(Ui.corps(a, ""));
                    textes.addView(Ui.texte(a, "", 12, Ui.VERT, false));
                    Ui.etirer(ligne, textes);
                } else {
                    ligne = (LinearLayout) recyclee;
                }
                Element e = visibles.get(i);
                LinearLayout textes = (LinearLayout) ligne.getChildAt(1);
                ((TextView) textes.getChildAt(0)).setText(e.nom);
                TextView detail = (TextView) textes.getChildAt(1);
                detail.setText(e.detail);
                detail.setTextColor(e.groupe ? Ui.TEXTE2 : Ui.VERT);
                detail.setVisibility(e.detail.isEmpty() ? View.GONE : View.VISIBLE);
                ((CheckBox) ligne.getChildAt(0)).setChecked(
                        e.groupe ? groupesCoches.contains(e.id) : applisCochees.contains(e.id));
                return ligne;
            }
        };
        liste.setAdapter(adaptateur);
        liste.setOnItemClickListener((parent, v, i, id) -> {
            Element e = visibles.get(i);
            Set<String> ensemble = e.groupe ? groupesCoches : applisCochees;
            if (!ensemble.remove(e.id)) {
                ensemble.add(e.id);
            }
            adaptateur.notifyDataSetChanged();
        });
        Ui.surChangement(recherche, t -> {
            String q = t.trim().toLowerCase(Locale.FRANCE);
            visibles.clear();
            for (Element e : tous) {
                if (q.isEmpty() || e.nom.toLowerCase(Locale.FRANCE).contains(q)) {
                    visibles.add(e);
                }
            }
            adaptateur.notifyDataSetChanged();
        });

        new AlertDialog.Builder(a)
                .setTitle(titre)
                .setView(vue)
                .setPositiveButton("Valider", (dlg, w) -> {
                    applis.clear();
                    applis.addAll(applisCochees);
                    if (groupes != null) {
                        groupes.clear();
                        groupes.addAll(groupesCoches);
                    }
                    ok.run();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    static void appli(Activity a, String titre, Consumer<String> choix) {
        List<AppInfo> applis = Applications.installees(a.getPackageManager(), a.getPackageName());
        String[] noms = new String[applis.size()];
        for (int i = 0; i < noms.length; i++) {
            noms[i] = applis.get(i).nom;
        }
        new AlertDialog.Builder(a)
                .setTitle(titre)
                .setItems(noms, (dlg, i) -> choix.accept(applis.get(i).paquet))
                .setNegativeButton("Annuler", null)
                .show();
    }

    static void heure(Activity a, int minutes, IntConsumer ok) {
        new TimePickerDialog(a, (v, h, m) -> ok.accept(h * 60 + m), minutes / 60, minutes % 60, true).show();
    }

    /** Renvoie le début du jour choisi. */
    static void date(Activity a, long ms, LongConsumer ok) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        new DatePickerDialog(a, (v, an, mois, jour) -> {
            Calendar choisi = Calendar.getInstance();
            choisi.clear();
            choisi.set(an, mois, jour);
            ok.accept(choisi.getTimeInMillis());
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    static void texte(Activity a, String titre, String valeur, String indice, Consumer<String> ok) {
        texte(a, titre, valeur, indice, InputType.TYPE_CLASS_TEXT, ok);
    }

    /** Saisie d'un nombre entier ; vide = -1. */
    static void nombre(Activity a, String titre, int valeur, IntConsumer ok) {
        texte(a, titre, valeur < 0 ? "" : String.valueOf(valeur), "", InputType.TYPE_CLASS_NUMBER, t -> {
            try {
                ok.accept(t.isEmpty() ? -1 : Integer.parseInt(t));
            } catch (NumberFormatException ignore) {
                // saisie illisible : rien ne change
            }
        });
    }

    static void texte(Activity a, String titre, String valeur, String indice, int type, Consumer<String> ok) {
        EditText champ = Ui.champ(a, valeur, indice);
        champ.setInputType(type);
        LinearLayout cadre = Ui.colonne(a);
        int p = Ui.dp(a, 20);
        cadre.setPadding(p, p / 2, p, 0);
        cadre.addView(champ);
        AlertDialog dialogue = new AlertDialog.Builder(a)
                .setTitle(titre)
                .setView(cadre)
                .setPositiveButton("Enregistrer", (dlg, w) -> ok.accept(champ.getText().toString().trim()))
                .setNegativeButton("Annuler", null)
                .create();
        // Entrée enregistre
        champ.setOnEditorActionListener((v, action, ev) -> {
            ok.accept(champ.getText().toString().trim());
            dialogue.dismiss();
            return true;
        });
        dialogue.show();
        champ.requestFocus();
        champ.setSelection(champ.getText().length());
    }

    static void jourSemaine(Activity a, IntConsumer ok) {
        String[] jours = new String[7];
        for (int i = 0; i < 7; i++) {
            jours[i] = Periode.JOURS[i].substring(0, 1).toUpperCase(Locale.FRANCE) + Periode.JOURS[i].substring(1);
        }
        new AlertDialog.Builder(a)
                .setTitle("Jour de début")
                .setItems(jours, (dlg, i) -> ok.accept(Periode.jourCalendar(i)))
                .show();
    }
}
