package fr.discipline.app;

import android.widget.ImageView;
import android.widget.LinearLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Historique d'usage : mois par mois tant que le journal a le détail, puis les
 * périodes plus anciennes telles qu'Android les a résumées.
 */
public class HistoriqueActivity extends Ecran {
    private static final long JOUR = 86_400_000L;

    @Override
    protected void onResume() {
        super.onResume();
        rafraichir();
        Historique.importerEnFond(this, () -> runOnUiThread(this::rafraichir));
    }

    @Override
    protected void rafraichir() {
        LinearLayout c = page("Historique d’usage", true);
        Ui.ajouter(c, Ui.lien(this, "Bilan de la semaine dernière", null,
                v -> startActivity(new android.content.Intent(this, BilanActivity.class))), 4);
        if (!Historique.autorise(this)) {
            MainActivity.carteAccesUsage(this, c);
        }
        Journal journal = Journal.get(this);
        long maintenant = Horloge.maintenant();
        long premier = journal.premierDebut();

        Calendar mois = Calendar.getInstance();
        mois.setTimeInMillis(maintenant);
        mois.set(Calendar.DAY_OF_MONTH, 1);
        mois.set(Calendar.HOUR_OF_DAY, 0);
        mois.set(Calendar.MINUTE, 0);
        mois.set(Calendar.SECOND, 0);
        mois.set(Calendar.MILLISECOND, 0);
        SimpleDateFormat nomMois = new SimpleDateFormat("MMMM yyyy", Locale.FRANCE);
        while (true) {
            long debut = mois.getTimeInMillis();
            mois.add(Calendar.MONTH, 1);
            long fin = Math.min(mois.getTimeInMillis(), maintenant);
            mois.add(Calendar.MONTH, -2);
            long depuis = Math.max(debut, premier);
            carte(c, nomMois.format(new Date(debut)), null, journal.tempsParAppli(depuis, fin), fin - depuis);
            if (debut <= premier) {
                break;
            }
        }

        List<Historique.Bloc> blocs = Historique.lire(this);
        if (!blocs.isEmpty()) {
            Ui.ajouter(c, Ui.section(this, "Plus ancien : totaux gardés par Android"), 20);
            Ui.ajouter(c, Ui.petit(this, "Avant ça, Android ne garde que le temps total par appli sur des périodes "
                    + "(jours, semaines, mois, années), sans le détail des ouvertures."), 4);
        }
        SimpleDateFormat date = new SimpleDateFormat("d MMM yyyy", Locale.FRANCE);
        for (Historique.Bloc b : blocs) {
            carte(c, "Du " + date.format(new Date(b.debut)) + " au " + date.format(new Date(b.fin)), "résumé Android",
                    b.temps, b.fin - b.debut);
        }
    }

    /** Une période : temps total, moyenne par jour, les 5 applis les plus utilisées. */
    private void carte(LinearLayout c, String titre, String note, Map<String, Long> temps, long duree) {
        temps.remove(getPackageName());
        long total = 0;
        for (long t : temps.values()) {
            total += t;
        }
        LinearLayout carte = Ui.ajouter(c, Ui.carte(this), 10);
        LinearLayout r = Ui.rangee(this);
        LinearLayout textes = Ui.etirer(r, Ui.colonne(this));
        textes.addView(Ui.texte(this, titre.substring(0, 1).toUpperCase(Locale.FRANCE) + titre.substring(1), 16, Ui.TEXTE, true));
        long jours = Math.max(1, (duree + JOUR / 2) / JOUR);
        textes.addView(Ui.petit(this, (note == null ? "" : note + " · ") + "≈ " + Ui.duree(total / jours) + " par jour"));
        r.addView(Ui.texte(this, Ui.duree(total), 20, Ui.TEXTE, true));
        carte.addView(r);

        List<Map.Entry<String, Long>> tri = new ArrayList<>(temps.entrySet());
        tri.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        for (int i = 0; i < Math.min(5, tri.size()); i++) {
            String paquet = tri.get(i).getKey();
            LinearLayout ligne = Ui.ajouter(carte, Ui.rangee(this), 6);
            ImageView ic = new ImageView(this);
            int t = Ui.dp(this, 24);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(t, t);
            lp.setMargins(0, 0, Ui.dp(this, 10), 0);
            ic.setImageDrawable(Applications.icone(this, paquet));
            ligne.addView(ic, lp);
            Ui.etirer(ligne, Ui.corps(this, Applications.nom(this, paquet)));
            ligne.addView(Ui.texte(this, Ui.duree(tri.get(i).getValue()), 14, Ui.TEXTE2, false));
        }
    }
}
