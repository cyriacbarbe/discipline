package fr.discipline.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Briques d'interface du style sombre et sobre : grosses cartes arrondies, vert = ok, rouge = bloqué. */
final class Ui {
    static final int FOND = 0xFF111317;
    static final int CARTE = 0xFF1C1F24;
    static final int CARTE2 = 0xFF2A2E35;
    static final int TEXTE = 0xFFECEFF4;
    static final int TEXTE2 = 0xFF9AA3AF;
    static final int VERT = 0xFF2EA043;
    static final int ROUGE = 0xFFE5534B;
    static final int ORANGE = 0xFFD29922;

    private Ui() {
    }

    static int dp(Context c, float valeur) {
        return Math.round(valeur * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable fond(Context c, int couleur, float rayonDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(couleur);
        g.setCornerRadius(dp(c, rayonDp));
        return g;
    }

    static LinearLayout colonne(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout rangee(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout carte(Context c) {
        LinearLayout l = colonne(c);
        l.setBackground(fond(c, CARTE, 22));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    /** Ajoute une vue en pleine largeur avec une marge au-dessus. */
    static <T extends View> T ajouter(ViewGroup parent, T vue, int margeHautDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(parent.getContext(), margeHautDp);
        parent.addView(vue, lp);
        return vue;
    }

    /** Ajoute une vue qui prend la place restante d'une rangée. */
    static <T extends View> T etirer(LinearLayout rangee, T vue) {
        rangee.addView(vue, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return vue;
    }

    static TextView texte(Context c, CharSequence t, float sp, int couleur, boolean gras) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(couleur);
        if (gras) {
            v.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return v;
    }

    static TextView titre(Context c, CharSequence t) {
        return texte(c, t, 18, TEXTE, true);
    }

    static TextView section(Context c, CharSequence t) {
        TextView v = texte(c, t.toString().toUpperCase(Locale.FRANCE), 12, TEXTE2, true);
        v.setLetterSpacing(0.08f);
        v.setPadding(dp(c, 6), dp(c, 14), 0, dp(c, 2));
        return v;
    }

    static TextView corps(Context c, CharSequence t) {
        return texte(c, t, 15, TEXTE, false);
    }

    static TextView petit(Context c, CharSequence t) {
        return texte(c, t, 13, TEXTE2, false);
    }

    static Button bouton(Context c, CharSequence t, int couleur) {
        Button b = new Button(c);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(TEXTE);
        b.setBackground(fond(c, couleur, 16));
        b.setStateListAnimator(null);
        b.setMinHeight(dp(c, 48));
        int p = dp(c, 14);
        b.setPadding(p, 0, p, 0);
        return b;
    }

    static Button boutonPlein(Context c, CharSequence t) {
        return bouton(c, t, VERT);
    }

    static Button boutonDiscret(Context c, CharSequence t) {
        return bouton(c, t, CARTE2);
    }

    /** Petite croix de suppression. */
    static TextView croix(Context c, View.OnClickListener clic) {
        TextView x = texte(c, "✕", 18, TEXTE2, false);
        int p = dp(c, 10);
        x.setPadding(p, p / 2, p, p / 2);
        x.setOnClickListener(clic);
        return x;
    }

    static TextView pastille(Context c, CharSequence t, int couleur) {
        TextView v = texte(c, t, 12, TEXTE, true);
        v.setBackground(fond(c, couleur, 10));
        v.setPadding(dp(c, 8), dp(c, 2), dp(c, 8), dp(c, 2));
        return v;
    }

    static Switch interrupteur(Context c, boolean coche) {
        Switch s = new Switch(c);
        s.setChecked(coche);
        s.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{VERT, TEXTE2}));
        s.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{0x882EA043, 0x559AA3AF}));
        return s;
    }

    static CheckBox caseACocher(Context c, CharSequence t, boolean coche) {
        CheckBox cb = new CheckBox(c);
        cb.setText(t);
        cb.setTextColor(TEXTE);
        cb.setTextSize(15);
        cb.setChecked(coche);
        cb.setButtonTintList(ColorStateList.valueOf(VERT));
        return cb;
    }

    static RadioGroup choixUnique(Context c, String[] libelles, int choisi, IntConsumer choix) {
        RadioGroup g = new RadioGroup(c);
        for (int i = 0; i < libelles.length; i++) {
            RadioButton r = new RadioButton(c);
            r.setId(View.generateViewId());
            r.setText(libelles[i]);
            r.setTextColor(TEXTE);
            r.setTextSize(15);
            r.setButtonTintList(ColorStateList.valueOf(VERT));
            g.addView(r);
            if (i == choisi) {
                r.setChecked(true);
            }
            final int index = i;
            r.setOnCheckedChangeListener((b, coche) -> {
                if (coche) {
                    choix.accept(index);
                }
            });
        }
        return g;
    }

    static EditText champ(Context c, String valeur, String indice) {
        EditText e = new EditText(c);
        e.setText(valeur);
        e.setHint(indice);
        e.setTextColor(TEXTE);
        e.setHintTextColor(0xFF5F6671);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setBackgroundTintList(ColorStateList.valueOf(TEXTE2));
        return e;
    }

    static void surChangement(EditText e, Consumer<String> suite) {
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                suite.accept(s.toString());
            }
        });
    }
    /** Rangée cliquable « libellé … valeur › ». */
    static LinearLayout lien(Context c, String libelle, String valeur, View.OnClickListener clic) {
        LinearLayout r = rangee(c);
        r.setPadding(0, dp(c, 10), 0, dp(c, 10));
        etirer(r, corps(c, libelle));
        if (valeur != null) {
            r.addView(petit(c, valeur));
        }
        TextView fleche = texte(c, "  ›", 18, TEXTE2, false);
        r.addView(fleche);
        r.setOnClickListener(clic);
        return r;
    }

    /** Rangée « libellé   − valeur + » : les boutons ajustent d'un pas, toucher la valeur la fait saisir. */
    static LinearLayout reglette(Context c, String libelle, String valeur, View.OnClickListener moins,
                                 View.OnClickListener plus, View.OnClickListener saisir) {
        LinearLayout r = rangee(c);
        r.setPadding(0, dp(c, 8), 0, dp(c, 8));
        etirer(r, corps(c, libelle));
        r.addView(rond(c, "−", moins));
        TextView v = texte(c, valeur, 17, TEXTE, true);
        v.setMinWidth(dp(c, 84));
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(c, 6), dp(c, 8), dp(c, 6), dp(c, 8));
        v.setOnClickListener(saisir);
        r.addView(v);
        r.addView(rond(c, "+", plus));
        return r;
    }

    private static TextView rond(Context c, String t, View.OnClickListener clic) {
        TextView b = texte(c, t, 22, TEXTE, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(fond(c, CARTE2, 21));
        int s = dp(c, 42);
        b.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        b.setOnClickListener(clic);
        return b;
    }

    /** Choix unique en pastilles côte à côte (courts libellés). */
    static LinearLayout puces(Context c, String[] libelles, int choisi, IntConsumer choix) {
        LinearLayout r = rangee(c);
        for (int i = 0; i < libelles.length; i++) {
            TextView p = texte(c, libelles[i], 15, TEXTE, i == choisi);
            p.setGravity(Gravity.CENTER);
            p.setBackground(fond(c, i == choisi ? VERT : CARTE2, 14));
            p.setPadding(dp(c, 12), dp(c, 9), dp(c, 12), dp(c, 9));
            final int index = i;
            p.setOnClickListener(v -> choix.accept(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.leftMargin = i == 0 ? 0 : dp(c, 6);
            r.addView(p, lp);
        }
        return r;
    }

    /** Barre de progression : la part consommée, dans la couleur donnée. */
    static LinearLayout jauge(Context c, float part, int couleur) {
        LinearLayout j = rangee(c);
        j.setBackground(fond(c, CARTE2, 4));
        float p = Math.max(0f, Math.min(1f, part));
        View plein = new View(c);
        plein.setBackground(fond(c, couleur, 4));
        j.addView(plein, new LinearLayout.LayoutParams(0, dp(c, 8), p));
        j.addView(new View(c), new LinearLayout.LayoutParams(0, dp(c, 8), 1f - p));
        return j;
    }

    static String duree(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        long minutes = ms / 60_000L;
        if (minutes >= 60) {
            long h = minutes / 60;
            long m = minutes % 60;
            return m == 0 ? h + " h" : h + " h " + String.format(Locale.FRANCE, "%02d", m);
        }
        if (minutes >= 1) {
            return minutes + " min";
        }
        return (ms / 1000) + " s";
    }

    static String heure(int minutes) {
        return String.format(Locale.FRANCE, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
