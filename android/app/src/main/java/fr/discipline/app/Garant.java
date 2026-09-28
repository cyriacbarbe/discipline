package fr.discipline.app;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Garants : des proches qui valident chaque assouplissement. Tout passe par
 * SMS et par une page web (iPhone ou Android), sans serveur :
 * <ol>
 * <li>l'invitation part par SMS avec un lien ; la page du garant crée une clé
 * ECDSA qui ne quitte jamais son navigateur, et renvoie la partie publique
 * dans un lien par SMS ;</li>
 * <li>une demande part de même ; s'il accepte, sa page signe « ok.garant.demande »
 * et renvoie la signature dans un lien. Un appui dessus ouvre Discipline, qui
 * vérifie et applique.</li>
 * </ol>
 * Sans la clé du garant, impossible de fabriquer un accord.
 */
final class Garant {
    private Garant() {
    }

    static final String SITE = "https://discipline-garant.vercel.app/g/";
    /** Une demande sans réponse s'oublie au bout de deux jours. */
    static final long VALIDITE = 48 * 3_600_000L;
    /** Une nouvelle clé pour un garant déjà là n'entre en service qu'après ce délai. */
    static final long DELAI_NOUVELLE_CLE = 72 * 3_600_000L;
    /** En-tête SubjectPublicKeyInfo d'une clé P-256 : suivi des 65 octets bruts. */
    private static final byte[] ENTETE_CLE = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200");
    private static final Pattern REPONSE = Pattern.compile("#r=([a-z]\\.[A-Za-z0-9._-]+)");

    // ---- État (dans Donnees.garant) ---------------------------------------

    static JSONArray tableau(Donnees d, String cle) {
        JSONArray a = d.garant.optJSONArray(cle);
        if (a == null) {
            a = new JSONArray();
            try {
                d.garant.put(cle, a);
            } catch (Exception ignore) {
                // clé non nulle
            }
        }
        return a;
    }

    static List<JSONObject> liste(Donnees d, String cle) {
        JSONArray a = tableau(d, cle);
        List<JSONObject> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) {
                l.add(o);
            }
        }
        return l;
    }

    /** Les garants qui ont une clé : eux seuls peuvent accepter. */
    static List<JSONObject> garants(Donnees d) {
        activerClesDues(d);
        return liste(d, "liste");
    }

    static long pause(Donnees d) {
        return d.garant.optLong("pause");
    }

    /** Faut-il l'accord d'un garant pour assouplir, là, maintenant ? */
    static boolean actifs(Donnees d) {
        return !garants(d).isEmpty() && pause(d) <= Horloge.maintenant();
    }

    static JSONObject moi(Donnees d) {
        JSONObject m = d.garant.optJSONObject("moi");
        return m == null ? new JSONObject() : m;
    }

    static JSONObject trouver(Donnees d, String cle, String id) {
        for (JSONObject o : liste(d, cle)) {
            if (o.optString("id").equals(id)) {
                return o;
            }
        }
        return null;
    }

    static void retirer(Donnees d, String cle, String id) {
        JSONArray a = tableau(d, cle);
        for (int i = a.length() - 1; i >= 0; i--) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optString("id").equals(id)) {
                a.remove(i);
            }
        }
    }

    static void ajouter(Donnees d, String cle, JSONObject o) {
        retirer(d, cle, o.optString("id"));
        tableau(d, cle).put(o);
    }

    /** Une nouvelle clé (garant qui a perdu la sienne) remplace l'ancienne une fois son délai passé. */
    private static void activerClesDues(Donnees d) {
        for (JSONObject g : liste(d, "liste")) {
            if (!g.optString("cleSuivante").isEmpty() && g.optLong("cleA") <= Horloge.maintenant()) {
                try {
                    g.put("cle", g.optString("cleSuivante"));
                } catch (Exception ignore) {
                    // clé non nulle
                }
                g.remove("cleSuivante");
                g.remove("cleA");
                d.enregistrer();
            }
        }
    }

    /** Demandes encore valables ; les périmées s'effacent. */
    static List<JSONObject> demandes(Donnees d) {
        List<JSONObject> l = new ArrayList<>();
        boolean efface = false;
        for (JSONObject dm : liste(d, "demandes")) {
            if (dm.optLong("t") + VALIDITE < Horloge.maintenant()) {
                retirer(d, "demandes", dm.optString("id"));
                efface = true;
            } else {
                l.add(dm);
            }
        }
        if (efface) {
            d.enregistrer();
        }
        return l;
    }

    // ---- Envois --------------------------------------------------------------

    static String nouvelId() {
        String alphabet = "abcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom hasard = new SecureRandom();
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            s.append(alphabet.charAt(hasard.nextInt(alphabet.length())));
        }
        return s.toString();
    }

    private static String lien(String sorte, JSONObject contenu) {
        return SITE + "#" + sorte + "=" + Base64.encodeToString(contenu.toString().getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /** Invitation (ou nouvelle clé, pour un garant qui l'a perdue) : SMS avec le lien. */
    static void inviter(Context c, Donnees d, JSONObject personne) {
        JSONObject m = moi(d);
        try {
            String lien = lien("i", new JSONObject().put("g", personne.optString("id"))
                    .put("n", m.optString("nom")).put("p", m.optString("numero")));
            Confiance.sms(c, personne.optString("numero"), m.optString("nom") + " te demande d’être son garant "
                    + "sur Discipline, l’appli qui l’aide à se limiter sur son téléphone. Tout est expliqué ici : " + lien);
        } catch (Exception ignore) {
            // clés non nulles
        }
    }

    /** Enregistre la demande (si nouvelle) et l'envoie à ce garant par SMS. */
    static JSONObject demander(Context c, Donnees d, JSONObject demande, JSONObject garant) {
        JSONObject m = moi(d);
        try {
            if (trouver(d, "demandes", demande.optString("id")) == null) {
                tableau(d, "demandes").put(demande);
                d.enregistrer();
            }
            String lien = lien("d", new JSONObject().put("g", garant.optString("id")).put("d", demande.optString("id"))
                    .put("t", demande.optString("texte")).put("n", m.optString("nom")).put("p", m.optString("numero")));
            Confiance.sms(c, garant.optString("numero"), "Discipline : j’ai besoin de ton accord pour "
                    + demande.optString("texte") + ". Réponds ici : " + lien);
        } catch (Exception ignore) {
            // clés non nulles
        }
        return demande;
    }

    static JSONObject nouvelleDemande(JSONObject changement, String texte) {
        try {
            return new JSONObject().put("id", nouvelId()).put("t", Horloge.maintenant()).put("texte", texte)
                    .put("ch", changement);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- Réponses (lien discipline://garant?r=… ou message collé) -------------

    /** Retrouve la réponse dans un SMS collé tel quel. */
    static String extraire(String texte) {
        if (texte == null) {
            return null;
        }
        Matcher m = REPONSE.matcher(texte);
        if (m.find()) {
            return m.group(1);
        }
        String t = texte.trim();
        return t.matches("[a-z]\\.[A-Za-z0-9._-]+") ? t : null;
    }

    /** Traite « g.id.clé » (invitation acceptée) ou « a.id.demande.signature » (accord). Rend le message à montrer. */
    static String recevoir(Donnees d, String reponse) {
        String[] p = reponse == null ? new String[0] : reponse.split("\\.");
        if (p.length == 3 && p[0].equals("g")) {
            return cle(d, p[1], p[2]);
        }
        if (p.length == 4 && p[0].equals("a")) {
            return accord(d, p[1], p[2], p[3]);
        }
        return "Ce n’est pas une réponse de garant.";
    }

    private static String cle(Donnees d, String id, String cle) {
        if (octets(cle) == null || octets(cle).length != 65) {
            return "Clé abîmée : demande-lui de recommencer.";
        }
        try {
            JSONObject invite = trouver(d, "invites", id);
            if (invite != null) {
                retirer(d, "invites", id);
                ajouter(d, "liste", new JSONObject().put("id", id).put("nom", invite.optString("nom"))
                        .put("numero", invite.optString("numero")).put("cle", cle));
                d.enregistrer();
                return invite.optString("nom") + " est maintenant ton garant.";
            }
            JSONObject g = trouver(d, "liste", id);
            if (g == null) {
                return "Cette invitation n’existe plus.";
            }
            if (cle.equals(g.optString("cle")) || cle.equals(g.optString("cleSuivante"))) {
                return "Cette clé est déjà enregistrée.";
            }
            // sinon, n'importe qui pourrait remplacer la clé du garant par la sienne
            g.put("cleSuivante", cle).put("cleA", Horloge.maintenant() + DELAI_NOUVELLE_CLE);
            d.enregistrer();
            return "Nouvelle clé de " + g.optString("nom") + " : elle servira dans "
                    + Ui.duree(DELAI_NOUVELLE_CLE) + ", l’ancienne marche encore d’ici là.";
        } catch (Exception e) {
            return "Réponse illisible.";
        }
    }

    private static String accord(Donnees d, String id, String idDemande, String signature) {
        JSONObject g = null;
        for (JSONObject x : garants(d)) {
            if (x.optString("id").equals(id)) {
                g = x;
            }
        }
        if (g == null) {
            return "Ce garant n’est plus dans la liste.";
        }
        JSONObject demande = null;
        for (JSONObject dm : demandes(d)) {
            if (dm.optString("id").equals(idDemande)) {
                demande = dm;
            }
        }
        if (demande == null) {
            return "Cette demande n’existe plus (déjà appliquée, annulée ou trop vieille).";
        }
        if (!verifier(g.optString("cle"), "ok." + id + "." + idDemande, signature)) {
            return "Signature fausse : cet accord ne vient pas de " + g.optString("nom") + ".";
        }
        retirer(d, "demandes", idDemande);
        d.appliquer(demande.optJSONObject("ch"));
        return g.optString("nom") + " a accepté : " + demande.optString("texte") + ". C’est appliqué.";
    }

    // ---- Cryptographie -------------------------------------------------------

    static boolean verifier(String cleBrute, String message, String signature) {
        try {
            byte[] brute = octets(cleBrute);
            byte[] rs = octets(signature);
            if (brute == null || rs == null || brute.length != 65 || rs.length != 64) {
                return false;
            }
            byte[] spki = new byte[ENTETE_CLE.length + brute.length];
            System.arraycopy(ENTETE_CLE, 0, spki, 0, ENTETE_CLE.length);
            System.arraycopy(brute, 0, spki, ENTETE_CLE.length, brute.length);
            PublicKey cle = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(spki));
            Signature v = Signature.getInstance("SHA256withECDSA");
            v.initVerify(cle);
            v.update(message.getBytes(StandardCharsets.UTF_8));
            return v.verify(der(rs));
        } catch (Exception e) {
            return false;
        }
    }

    /** Le navigateur signe en r‖s (64 octets) ; Java attend du DER. */
    private static byte[] der(byte[] rs) {
        byte[] r = entier(rs, 0);
        byte[] s = entier(rs, 32);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(0x30);
        o.write(r.length + s.length + 4);
        o.write(0x02);
        o.write(r.length);
        o.write(r, 0, r.length);
        o.write(0x02);
        o.write(s.length);
        o.write(s, 0, s.length);
        return o.toByteArray();
    }

    private static byte[] entier(byte[] rs, int debut) {
        int i = debut;
        while (i < debut + 31 && rs[i] == 0) {
            i++;
        }
        boolean signe = (rs[i] & 0x80) != 0;
        byte[] e = new byte[debut + 32 - i + (signe ? 1 : 0)];
        System.arraycopy(rs, i, e, signe ? 1 : 0, debut + 32 - i);
        return e;
    }

    private static byte[] octets(String base64) {
        try {
            return Base64.decode(base64, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return b;
    }
}
