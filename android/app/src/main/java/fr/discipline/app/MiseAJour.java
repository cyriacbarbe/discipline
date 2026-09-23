package fr.discipline.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Pas de Play Store : la dernière Release GitHub sert de source. Sa balise est
 * "v<versionCode>" (ex. v3) et elle porte l'APK signé en pièce jointe.
 * Mettre à jour = télécharger l'APK dans le navigateur puis l'ouvrir : il
 * s'installe par-dessus, les réglages restent.
 */
final class MiseAJour {
    private static final String URL_DERNIERE_RELEASE =
            "https://api.github.com/repos/cyriacbarbe/discipline/releases/latest";

    private MiseAJour() {
    }

    /** {nom de la version, lien de l'APK} si une version plus récente est publiée, sinon null. À appeler hors du fil principal. */
    static String[] miseAJourSiDisponible() throws Exception {
        HttpURLConnection connexion = (HttpURLConnection) new URL(URL_DERNIERE_RELEASE).openConnection();
        connexion.setConnectTimeout(8000);
        connexion.setReadTimeout(8000);
        connexion.setRequestProperty("Accept", "application/vnd.github+json");
        try (BufferedReader lecteur = new BufferedReader(new InputStreamReader(connexion.getInputStream()))) {
            StringBuilder corps = new StringBuilder();
            String ligne;
            while ((ligne = lecteur.readLine()) != null) {
                corps.append(ligne);
            }
            JSONObject release = new JSONObject(corps.toString());
            int codeEnLigne = Integer.parseInt(release.getString("tag_name").replaceAll("[^0-9]", ""));
            if (codeEnLigne <= BuildConfig.VERSION_CODE) {
                return null;
            }
            JSONArray pieces = release.getJSONArray("assets");
            for (int i = 0; i < pieces.length(); i++) {
                JSONObject piece = pieces.getJSONObject(i);
                if (piece.getString("name").endsWith(".apk")) {
                    return new String[]{release.optString("name", release.getString("tag_name")),
                            piece.getString("browser_download_url")};
                }
            }
            return null;
        } finally {
            connexion.disconnect();
        }
    }
}
