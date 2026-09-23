package fr.discipline.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Petit graphe en barres (un jour par barre), avec une ligne de limite facultative. */
class Barres extends View {
    private final long[] valeurs;
    private final String[] etiquettes;
    private final int couleur;
    private final long limite;
    private final Paint pinceau = new Paint(Paint.ANTI_ALIAS_FLAG);

    Barres(Context c, long[] valeurs, String[] etiquettes, int couleur, long limite) {
        super(c);
        this.valeurs = valeurs;
        this.etiquettes = etiquettes;
        this.couleur = couleur;
        this.limite = limite;
        setMinimumHeight(Ui.dp(c, 130));
    }

    @Override
    protected void onMeasure(int largeur, int hauteur) {
        super.onMeasure(largeur, MeasureSpec.makeMeasureSpec(Ui.dp(getContext(), 130), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = valeurs.length;
        if (n == 0) {
            return;
        }
        float basTexte = Ui.dp(getContext(), 16);
        float h = getHeight() - basTexte;
        float w = getWidth();
        long max = Math.max(1, limite);
        for (long v : valeurs) {
            max = Math.max(max, v);
        }
        float pas = w / n;
        float marge = Math.max(1, pas * 0.18f);
        float rayon = Math.min(Ui.dp(getContext(), 4), (pas - 2 * marge) / 2);
        pinceau.setTextSize(Ui.dp(getContext(), 10));
        pinceau.setTextAlign(Paint.Align.CENTER);
        int tousLes = n > 10 ? 5 : 1;
        for (int i = 0; i < n; i++) {
            float haut = h - (h - Ui.dp(getContext(), 4)) * valeurs[i] / max;
            pinceau.setColor(limite > 0 && valeurs[i] > limite ? Ui.ROUGE : couleur);
            if (valeurs[i] > 0) {
                canvas.drawRoundRect(new RectF(i * pas + marge, haut, (i + 1) * pas - marge, h), rayon, rayon, pinceau);
            }
            if ((n - 1 - i) % tousLes == 0) {
                pinceau.setColor(Ui.TEXTE2);
                canvas.drawText(etiquettes[i], i * pas + pas / 2, getHeight() - Ui.dp(getContext(), 3), pinceau);
            }
        }
        if (limite > 0) {
            float y = h - (h - Ui.dp(getContext(), 4)) * limite / max;
            pinceau.setColor(Ui.ORANGE);
            pinceau.setStrokeWidth(Ui.dp(getContext(), 1));
            canvas.drawLine(0, y, w, y, pinceau);
        }
    }
}
