package fr.discipline.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        final boolean groupe;
        String detail = "";
        /** Groupe : ses applis les plus utilisées, dont les icônes défilent. */
        List<String> carrousel;

        Element(String id, String nom, boolean groupe) {
            this.id = id;
            this.nom = nom;
            this.groupe = groupe;
        }
    }

    private static final int TAILLE_CARROUSEL = 5;
    private static final long PAS_CARROUSEL_MS = 1500;

    /** Les applis du groupe, de la plus utilisée ces 7 derniers jours à la moins utilisée. */
    private static List<String> plusUtilisees(Set<String> paquets, Map<String, Long> temps) {
        List<String> tries = new ArrayList<>(paquets);
        tries.sort((x, y) -> Long.compare(temps.getOrDefault(y, 0L), temps.getOrDefault(x, 0L)));
        return tries.subList(0, Math.min(TAILLE_CARROUSEL, tries.size()));
    }

    private static ImageView icone(Activity a) {
        ImageView v = new ImageView(a);
        int t = Ui.dp(a, 36);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(t, t);
        lp.setMargins(Ui.dp(a, 4), 0, Ui.dp(a, 12), 0);
        v.setLayoutParams(lp);
        return v;
    }

    /** Recalcule les détails (taille des groupes, pastilles des applis) : au départ et après un changement de groupe. */
    private static void completer(Donnees d, List<Element> tous, Map<String, Long> temps) {
        for (Element e : tous) {
            if (!e.groupe) {
                List<String> noms = new ArrayList<>();
                for (Donnees.Groupe g : d.groupesDe(e.id)) {
                    noms.add("● " + g.nom);
                }
                e.detail = String.join("   ", noms);
            } else {
                Donnees.Groupe g = d.groupes.get(e.id);
                Set<String> paquets = g == null ? new HashSet<>() : g.paquets;
                e.detail = "Groupe · " + paquets.size() + " appli(s)";
                e.carrousel = plusUtilisees(paquets, temps);
            }
        }
    }

    /** Icône de l'élément ; pour un groupe, celle du carrousel au tour {@code tour}. */
    private static void montrerIcone(Activity a, ImageView v, Element e, int tour) {
        String paquet = !e.groupe ? e.id
                : e.carrousel.isEmpty() ? null : e.carrousel.get(tour % e.carrousel.size());
        v.setImageDrawable(paquet == null ? null : Applications.icone(a, paquet));
    }

    /**
     * Choix multiple d'applis et, si {@code groupes} n'est pas null, de groupes.
     * Chaque appli montre ses groupes en pastilles. Les ensembles sont modifiés à la validation.
     */
    static void cibles(Ecran a, String titre, Set<String> applis, Set<String> groupes, Runnable ok) {
        Donnees d = Donnees.get(a);
        long maintenant = System.currentTimeMillis();
        Map<String, Long> temps = Journal.get(a).tempsParAppli(maintenant - 7 * 24 * 3600_000L, maintenant);
        List<Element> tous = new ArrayList<>();
        if (groupes != null) {
            for (Donnees.Groupe g : d.groupes.values()) {
                tous.add(new Element(g.id, g.nom, true));
            }
        }
        for (AppInfo app : Applications.installees(a.getPackageManager(), a.getPackageName())) {
            tous.add(new Element(app.paquet, app.nom, false));
        }
        completer(d, tous, temps);
        Set<String> applisCochees = new HashSet<>(applis);
        Set<String> groupesCoches = groupes == null ? new HashSet<>() : new HashSet<>(groupes);
        List<Element> visibles = new ArrayList<>(tous);

        LinearLayout vue = Ui.colonne(a);
        int p = Ui.dp(a, 16);
        vue.setPadding(p, p / 2, p, 0);
        EditText recherche = Ui.ajouter(vue, Ui.champ(a, "", "Rechercher"), 0);
        ListView liste = new ListView(a);
        vue.addView(liste, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 420)));

        int[] tour = {0};
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
                    ligne.addView(icone(a));
                    LinearLayout textes = Ui.colonne(a);
                    textes.addView(Ui.corps(a, ""));
                    textes.addView(Ui.texte(a, "", 12, Ui.VERT, false));
                    Ui.etirer(ligne, textes);
                    TextView modifier = Ui.texte(a, "✎", 20, Ui.TEXTE2, false);
                    modifier.setPadding(Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6));
                    ligne.addView(modifier);
                } else {
                    ligne = (LinearLayout) recyclee;
                }
                Element e = visibles.get(i);
                ligne.setTag(e);
                // ✎ d'un groupe : ajouter ou retirer ses applis sans quitter ce choix.
                TextView modifier = (TextView) ligne.getChildAt(3);
                modifier.setVisibility(e.groupe ? View.VISIBLE : View.GONE);
                modifier.setOnClickListener(e.groupe ? v -> {
                    Donnees.Groupe g = d.groupes.get(e.id);
                    if (g == null) {
                        return;
                    }
                    Donnees.Groupe copie = Ecran.copieGroupe(g);
                    cibles(a, g.nom, copie.paquets, null, () -> a.modifierGroupe(copie, false, () -> {
                        completer(d, tous, temps);
                        notifyDataSetChanged();
                    }));
                } : null);
                ImageView ic = (ImageView) ligne.getChildAt(1);
                ic.animate().cancel();
                ic.setAlpha(1f);
                montrerIcone(a, ic, e, tour[0]);
                LinearLayout textes = (LinearLayout) ligne.getChildAt(2);
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

        // Carrousel : les groupes visibles passent à l'icône suivante, en fondu.
        Handler minuterie = new Handler(Looper.getMainLooper());
        Runnable defiler = new Runnable() {
            @Override
            public void run() {
                tour[0]++;
                for (int i = 0; i < liste.getChildCount(); i++) {
                    View ligne = liste.getChildAt(i);
                    Element e = (Element) ligne.getTag();
                    if (e == null || !e.groupe || e.carrousel.size() < 2) {
                        continue;
                    }
                    ImageView ic = (ImageView) ((LinearLayout) ligne).getChildAt(1);
                    int t = tour[0];
                    ic.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                        montrerIcone(a, ic, e, t);
                        ic.animate().alpha(1f).setDuration(200);
                    });
                }
                minuterie.postDelayed(this, PAS_CARROUSEL_MS);
            }
        };
        minuterie.postDelayed(defiler, PAS_CARROUSEL_MS);

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
                .setOnDismissListener(dlg -> minuterie.removeCallbacksAndMessages(null))
                .show();
    }

    static void appli(Activity a, String titre, Consumer<String> choix) {
        List<AppInfo> applis = Applications.installees(a.getPackageManager(), a.getPackageName());
        BaseAdapter adaptateur = new BaseAdapter() {
            @Override
            public int getCount() {
                return applis.size();
            }

            @Override
            public Object getItem(int i) {
                return applis.get(i);
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
                    int p = Ui.dp(a, 20);
                    ligne.setPadding(p, Ui.dp(a, 8), p, Ui.dp(a, 8));
                    ligne.addView(icone(a));
                    Ui.etirer(ligne, Ui.corps(a, ""));
                } else {
                    ligne = (LinearLayout) recyclee;
                }
                AppInfo app = applis.get(i);
                ((ImageView) ligne.getChildAt(0)).setImageDrawable(Applications.icone(a, app.paquet));
                ((TextView) ligne.getChildAt(1)).setText(app.nom);
                return ligne;
            }
        };
        new AlertDialog.Builder(a)
                .setTitle(titre)
                .setAdapter(adaptateur, (dlg, i) -> choix.accept(applis.get(i).paquet))
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
