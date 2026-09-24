package fr.discipline.app;

import android.content.Intent;
import android.widget.LinearLayout;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Bilan de la semaine écoulée, à partager avec la personne de confiance. */
public class BilanActivity extends Ecran {

    @Override
    protected void onResume() {
        super.onResume();
        rafraichir();
    }

    @Override
    protected void rafraichir() {
        LinearLayout c = page("Bilan de la semaine", true);
        Bilan b = Bilan.semaineEcoulee(this, Horloge.maintenant());
        SimpleDateFormat f = new SimpleDateFormat("EEE d MMM", Locale.FRANCE);
        Ui.ajouter(c, Ui.petit(this, "Du " + f.format(new Date(b.debut)) + " au " + f.format(new Date(b.fin - 1))), 4);

        LinearLayout total = Ui.ajouter(c, Ui.carte(this), 12);
        total.addView(Ui.texte(this, Ui.duree(b.total), 32, Ui.TEXTE, true));
        total.addView(Ui.petit(this, "sur le téléphone, soit " + Ui.duree(b.total / 7) + " par jour"));
        if (!b.evolution().isEmpty()) {
            total.addView(Ui.texte(this, b.evolution(), 15, b.total <= b.totalAvant ? Ui.VERT : Ui.ORANGE, true));
        }

        if (!b.tete.isEmpty()) {
            Ui.ajouter(c, Ui.section(this, "En tête"), 20);
            LinearLayout tete = Ui.ajouter(c, Ui.carte(this), 8);
            for (String[] t : b.tete) {
                LinearLayout r = Ui.rangee(this);
                Ui.etirer(r, Ui.corps(this, t[0]));
                r.addView(Ui.texte(this, t[1], 15, Ui.TEXTE2, false));
                tete.addView(r);
            }
        }

        Ui.ajouter(c, Ui.section(this, "Tenue"), 20);
        LinearLayout tenue = Ui.ajouter(c, Ui.carte(this), 8);
        tenue.addView(Ui.corps(this, b.bloquees + " ouverture(s) bloquée(s)"));
        tenue.addView(Ui.corps(this, b.rallonges + " rallonge(s) prise(s)"));
        if (b.motifs > 0) {
            tenue.addView(Ui.corps(this, b.motifs + " motif(s) donné(s) :"));
            for (JSONObject m : donnees.motifs) {
                long t = m.optLong("t");
                if (t >= b.debut && t < b.fin) {
                    tenue.addView(Ui.petit(this, "• " + Applications.nom(this, m.optString("paquet")) + " — « "
                            + m.optString("texte") + " »"));
                }
            }
        }

        if (!donnees.confianceNumero.isEmpty()) {
            Ui.ajouter(c, Ui.boutonDiscret(this, "Envoyer à " + donnees.confianceNom), 20)
                    .setOnClickListener(v -> Confiance.sms(this, donnees.confianceNumero, b.texte()));
        }
        boutonBas(c, "Partager", v -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                .setType("text/plain").putExtra(Intent.EXTRA_TEXT, b.texte()), "Partager le bilan")));
    }
}
