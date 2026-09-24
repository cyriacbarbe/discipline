package fr.discipline.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Personne de confiance : elle garde dix codes à usage unique, montrés une
 * seule fois à la création. Un de ses codes remplace le délai de l'anti-triche
 * pour un assouplissement, et ne resservira pas : l'apprendre ne donne rien.
 */
final class Confiance {
    private Confiance() {
    }

    static final int NOMBRE_CODES = 10;

    static List<String> nouveauxCodes() {
        SecureRandom hasard = new SecureRandom();
        List<String> codes = new ArrayList<>();
        while (codes.size() < NOMBRE_CODES) {
            String code = String.format(java.util.Locale.ROOT, "%06d", hasard.nextInt(1_000_000));
            if (!codes.contains(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    static String hacher(String code) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256")
                    .digest(("discipline:" + code.trim()).getBytes(StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder();
            for (byte b : h) {
                s.append(String.format("%02x", b));
            }
            return s.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Le code est-il l'un des codes restants ? Si oui, il est rayé. */
    static boolean utiliser(Donnees d, String code) {
        boolean bon = d.confianceCodes.remove(hacher(code));
        if (bon) {
            d.enregistrer();
        }
        return bon;
    }

    /** Ouvre l'appli SMS avec le message prêt ; rien ne part sans l'envoyer soi-même. */
    static void sms(Context c, String numero, String texte) {
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(numero)))
                .putExtra("sms_body", texte)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (RuntimeException e) {
            c.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, texte), "Envoyer").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }
}
