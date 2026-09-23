package fr.discipline.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class NfcActivity extends Activity {

    private NfcAdapter nfcAdapter;
    private Regles regles;
    private TextView texteEtat;
    private ListView listeTags;
    private List<String> tagIdsAffiches = new ArrayList<>();
    private boolean enAttenteDEnregistrement;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfc);

        regles = new Regles(this);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        texteEtat = findViewById(R.id.texte_etat_nfc);
        listeTags = findViewById(R.id.liste_tags_nfc);

        Button boutonEnregistrer = findViewById(R.id.bouton_enregistrer_badge);
        boutonEnregistrer.setOnClickListener(v -> {
            enAttenteDEnregistrement = true;
            texteEtat.setText(R.string.nfc_approcher_badge);
        });

        traiterIntentEventuel(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        rafraichirEtatEtListe();
        if (nfcAdapter != null) {
            Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        traiterIntentEventuel(intent);
    }

    private void traiterIntentEventuel(Intent intent) {
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) {
            return;
        }
        String id = idHex(tag);

        if (enAttenteDEnregistrement) {
            enAttenteDEnregistrement = false;
            Intent choix = new Intent(this, ChoixApplisTagActivity.class);
            choix.putExtra(ChoixApplisTagActivity.EXTRA_TAG_ID, id);
            startActivity(choix);
            return;
        }

        int nombre = regles.debloquerViaTag(id);
        if (nombre > 0) {
            Toast.makeText(this, getString(R.string.nfc_deblocage_reussi, regles.getNomTag(id), nombre),
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.nfc_badge_inconnu, Toast.LENGTH_LONG).show();
        }
    }

    private void rafraichirEtatEtListe() {
        texteEtat.setText(nfcAdapter == null ? getString(R.string.nfc_indisponible) : "");

        List<String> ids = new ArrayList<>(regles.getTagsNfc());
        Collections.sort(ids, Comparator.comparing(id -> regles.getNomTag(id).toLowerCase(Locale.FRANCE)));
        tagIdsAffiches = ids;

        List<String> lignes = new ArrayList<>();
        for (String id : ids) {
            lignes.add(getString(R.string.nfc_ligne_tag_format, regles.getNomTag(id), regles.getApplisTag(id).size()));
        }
        listeTags.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, lignes));

        listeTags.setOnItemClickListener((parent, view, position, id) -> {
            Intent choix = new Intent(this, ChoixApplisTagActivity.class);
            choix.putExtra(ChoixApplisTagActivity.EXTRA_TAG_ID, tagIdsAffiches.get(position));
            startActivity(choix);
        });
        listeTags.setOnItemLongClickListener((parent, view, position, id) -> {
            String tagId = tagIdsAffiches.get(position);
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.nfc_confirmer_suppression, regles.getNomTag(tagId)))
                    .setPositiveButton(R.string.oui, (dialog, which) -> {
                        regles.supprimerTag(tagId);
                        rafraichirEtatEtListe();
                    })
                    .setNegativeButton(R.string.non, null)
                    .show();
            return true;
        });
    }

    private static String idHex(Tag tag) {
        byte[] octets = tag.getId();
        StringBuilder texte = new StringBuilder();
        for (byte octet : octets) {
            texte.append(String.format(Locale.ROOT, "%02x", octet));
        }
        return texte.toString();
    }
}
