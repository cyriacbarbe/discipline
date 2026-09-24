package fr.discipline.app;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ce qu'on regarde vraiment dans une appli : le site ouvert dans un
 * navigateur, les Shorts de YouTube, les Reels d'Instagram. Le journal les
 * note sous une clé à part :
 * <ul>
 * <li>« paquet#shorts » : une partie d'appli ;</li>
 * <li>« site:hote » ou « site:hote#partie » : un site, éventuellement sa
 * partie Shorts/Reels ou le mot-clé trouvé dans l'adresse.</li>
 * </ul>
 * {@link Cibles} relie un site à l'appli correspondante : bloquer YouTube
 * bloque aussi youtube.com.
 */
final class Sites {
    private Sites() {
    }

    /** Domaine → appli : une appli bloquée bloque aussi son site. */
    private static final Map<String, String> APPLIS = new HashMap<>();
    /** Navigateur → identifiant de sa barre d'adresse. */
    private static final Map<String, String> BARRES = new HashMap<>();
    /** Appli → {identifiant de vue, partie}. */
    private static final Map<String, String[][]> PARTIES = new HashMap<>();

    static {
        String[][] applis = {
                {"youtube.com", "com.google.android.youtube"}, {"youtu.be", "com.google.android.youtube"},
                {"instagram.com", "com.instagram.android"}, {"threads.net", "com.instagram.barcelona"},
                {"facebook.com", "com.facebook.katana"}, {"fb.com", "com.facebook.katana"},
                {"messenger.com", "com.facebook.orca"}, {"tiktok.com", "com.zhiliaoapp.musically"},
                {"x.com", "com.twitter.android"}, {"twitter.com", "com.twitter.android"},
                {"reddit.com", "com.reddit.frontpage"}, {"snapchat.com", "com.snapchat.android"},
                {"linkedin.com", "com.linkedin.android"}, {"pinterest.com", "com.pinterest"},
                {"pinterest.fr", "com.pinterest"}, {"netflix.com", "com.netflix.mediaclient"},
                {"twitch.tv", "tv.twitch.android.app"}, {"primevideo.com", "com.amazon.avod.thirdpartyclient"},
                {"disneyplus.com", "com.disney.disneyplus"}, {"spotify.com", "com.spotify.music"},
                {"dailymotion.com", "com.dailymotion.dailymotion"}, {"9gag.com", "com.ninegag.android.app"},
                {"leboncoin.fr", "fr.leboncoin"}, {"vinted.fr", "fr.vinted"},
                {"amazon.fr", "com.amazon.mShop.android.shopping"}, {"amazon.com", "com.amazon.mShop.android.shopping"},
                {"whatsapp.com", "com.whatsapp"}, {"telegram.org", "org.telegram.messenger"},
                {"discord.com", "com.discord"}, {"tinder.com", "com.tinder"}, {"bumble.com", "com.bumble.app"},
        };
        for (String[] a : applis) {
            APPLIS.put(a[0], a[1]);
        }
        String[][] barres = {
                {"com.android.chrome", "url_bar"}, {"com.chrome.beta", "url_bar"},
                {"com.brave.browser", "url_bar"}, {"com.microsoft.emmx", "url_bar"},
                {"com.vivaldi.browser", "url_bar"}, {"com.kiwibrowser.browser", "url_bar"},
                {"com.sec.android.app.sbrowser", "location_bar_edit_text"},
                {"org.mozilla.firefox", "mozac_browser_toolbar_url_view"},
                {"org.mozilla.firefox_beta", "mozac_browser_toolbar_url_view"},
                {"org.mozilla.focus", "mozac_browser_toolbar_url_view"},
                {"com.opera.browser", "url_field"}, {"com.opera.mini.native", "url_field"},
                {"com.duckduckgo.mobile.android", "omnibarTextInput"},
                {"com.ecosia.android", "url_bar"},
        };
        for (String[] b : barres) {
            BARRES.put(b[0], b[1]);
        }
        PARTIES.put("com.google.android.youtube", new String[][]{
                {"reel_recycler", "shorts"}, {"reel_player_page_container", "shorts"}, {"reel_watch_player", "shorts"}});
        PARTIES.put("com.instagram.android", new String[][]{
                {"clips_viewer_view_pager", "reels"}, {"clips_viewer_container", "reels"}});
    }

    /** Parties d'appli qu'on sait reconnaître, pour les proposer dans les choix. */
    static final String[][] PARTIES_CONNUES = {
            {"com.google.android.youtube#shorts", "YouTube Shorts"},
            {"com.instagram.android#reels", "Instagram Reels"},
    };

    /** Vaut-il la peine de regarder dans cette appli ? */
    static boolean inspecte(String paquet) {
        return BARRES.containsKey(paquet) || PARTIES.containsKey(paquet);
    }

    /**
     * Clé du journal pour ce qu'affiche l'appli, ou null si on ne sait pas
     * (barre d'adresse masquée, page qui se charge…).
     */
    static String cle(AccessibilityNodeInfo racine, String paquet, Collection<String> mots) {
        String barre = BARRES.get(paquet);
        if (barre != null) {
            String adresse = texte(racine, paquet + ":id/" + barre);
            return adresse == null ? null : cleDeLAdresse(adresse, mots);
        }
        String[][] parties = PARTIES.get(paquet);
        for (int i = 0; parties != null && i < parties.length; i++) {
            List<AccessibilityNodeInfo> trouves = racine.findAccessibilityNodeInfosByViewId(paquet + ":id/" + parties[i][0]);
            if (trouves != null && !trouves.isEmpty()) {
                return paquet + "#" + parties[i][1];
            }
        }
        return paquet;
    }

    private static String texte(AccessibilityNodeInfo racine, String id) {
        List<AccessibilityNodeInfo> trouves = racine.findAccessibilityNodeInfosByViewId(id);
        if (trouves == null || trouves.isEmpty() || trouves.get(0).getText() == null) {
            return null;
        }
        String t = trouves.get(0).getText().toString().trim();
        return t.isEmpty() ? null : t;
    }

    /** « https://m.youtube.com/shorts/abc » → « site:youtube.com#shorts ». */
    static String cleDeLAdresse(String adresse, Collection<String> mots) {
        String u = adresse.toLowerCase(Locale.ROOT);
        int schema = u.indexOf("://");
        String reste = schema >= 0 ? u.substring(schema + 3) : u;
        int coupe = reste.length();
        for (char c : new char[]{'/', '?', '#', ':'}) {
            int k = reste.indexOf(c);
            if (k >= 0 && k < coupe) {
                coupe = k;
            }
        }
        String hote = reste.substring(0, coupe);
        String chemin = reste.substring(coupe);
        boolean estHote = hote.contains(".") && !hote.contains(" ");
        hote = estHote ? normaliser(hote) : "recherche";
        String partie = null;
        String appli = paquet(hote);
        if ("com.google.android.youtube".equals(appli) && chemin.startsWith("/shorts")) {
            partie = "shorts";
        } else if ("com.instagram.android".equals(appli) && chemin.startsWith("/reel")) {
            partie = "reels";
        } else {
            for (String mot : mots) {
                if (!mot.contains(".") && !mot.isEmpty() && u.contains(mot.toLowerCase(Locale.ROOT))) {
                    partie = mot.toLowerCase(Locale.ROOT);
                    break;
                }
            }
        }
        if (!estHote && partie == null) {
            return null; // recherche tapée sans mot-clé visé : on reste sur le navigateur
        }
        return "site:" + hote + (partie == null ? "" : "#" + partie);
    }

    /** « www.m.youtube.com » → « youtube.com ». */
    static String normaliser(String site) {
        String s = site.trim().toLowerCase(Locale.ROOT);
        int schema = s.indexOf("://");
        if (schema >= 0) {
            s = s.substring(schema + 3);
        }
        int barre = s.indexOf('/');
        if (barre >= 0) {
            s = s.substring(0, barre);
        }
        for (String prefixe : new String[]{"www.", "m.", "mobile."}) {
            if (s.startsWith(prefixe)) {
                s = s.substring(prefixe.length());
            }
        }
        return s;
    }

    /** Appli correspondant à ce site (ou à l'un de ses domaines parents), ou null. */
    static String paquet(String hote) {
        String h = hote;
        while (h.contains(".")) {
            String p = APPLIS.get(h);
            if (p != null) {
                return p;
            }
            h = h.substring(h.indexOf('.') + 1);
        }
        return null;
    }

    /** Ce site (« hote ») est-il visé par cette entrée de liste (« youtube.com ») ? */
    static boolean couvre(String entree, String hote) {
        String e = normaliser(entree);
        return e.contains(".") && (hote.equals(e) || hote.endsWith("." + e));
    }

    /** Nom lisible d'une clé du journal qui n'est pas un simple paquet. */
    static String nom(String cle, String nomAppli) {
        String partie = cle.contains("#") ? cle.substring(cle.indexOf('#') + 1) : null;
        String suffixe = partie == null ? "" : partie.equals("shorts") ? " Shorts"
                : partie.equals("reels") ? " Reels" : " (« " + partie + " »)";
        if (cle.startsWith("site:")) {
            String hote = Cibles.base(cle).substring(5);
            return (hote.equals("recherche") ? "Recherche web" : hote) + suffixe;
        }
        return nomAppli + suffixe;
    }
}
