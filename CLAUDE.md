# Discipline — blocage d'applications Android

Appli Android perso (pas destinée à être vendue) pour se limiter soi-même sur
son téléphone : bloquer des applications, des créneaux horaires, des sessions
limitées par jour, un déblocage par puce NFC. Java natif, même stack que
`cyranss/android` (Gradle, pas d'AndroidX au-delà du strict nécessaire).

## Dépôt et fabrication

- Dépôt Git propre à ce projet, indépendant de `cyranss/` ; remote `origin` =
  https://github.com/cyriacbarbe/discipline (public, branche `master`).
- Outils de compilation : les mêmes que Cyranss, dans
  `C:\Users\user\android-outils` (JDK 17, SDK Android 34, Gradle 8.7).
  ```bash
  cd android
  JAVA_HOME="C:\Users\user\android-outils\jdk-17.0.20.1+1" ./gradlew assembleDebug
  ```
  L'APK sort dans `app/build/outputs/apk/debug/app-debug.apk` — à copier sur
  le téléphone et installer (sources inconnues) pour tester.

## Mises à jour (bouton « Télécharger la mise à jour »)

- `MiseAJour.java` lit la dernière Release de `cyriacbarbe/discipline` sur
  GitHub : balise `v<versionCode>` + APK signé en pièce jointe. À chaque
  version : monter `versionCode`/`versionName` dans `app/build.gradle`,
  `./gradlew assembleRelease`, puis publier une Release `v<versionCode>` avec
  `app-release.apk`. Commiter toujours après chaque changement.
- À l'ouverture, une popup « Mise à jour disponible » (+ gros bouton vert en
  haut) propose le téléchargement ; l'APK et la Release portent la version dans
  leur nom (`discipline-0.4.apk`, « Discipline 0.4 »), jamais un nom fixe.
- Clé : `android/discipline.jks` + `signature.properties`, hors Git — à
  sauvegarder ailleurs, la perdre oblige à désinstaller l'appli.

## Comment ça bloque

- `BlocageAccessibilityService` (à activer à la main dans Accessibilité) :
  suit l'appli au premier plan, tient le `Journal` (temps réel par appli),
  demande toutes les 2 s au `Moteur` si une `Limite` bloque, puis ferme,
  ouvre une autre appli ou montre `BlocageActivity` ; affiche les bulles.
- `Donnees` : tout le réglage en un JSON (SharedPreferences `discipline_v1`).
  Un changement qui assouplit passe par `Ecran.garde` (anti-triche).
- Interface construite en Java (`Ui`, `Ecran`, `Choix`), pas de XML de mise
  en page ; thème sombre.

## À lire avant d'y toucher

- [docs/CAHIER_DES_CHARGES.md](docs/CAHIER_DES_CHARGES.md) — refonte v1
  (moteur de limites, accueil, stats) et, en fin de fichier, ses écarts.

## Fait (codé, tests sur téléphone en cours)

- v1.0 : refonte complète du cahier des charges (9 types de limites, ET/OU,
  exceptions, groupes, profils, vacances, anti-triche, stats).
- v1.1 : icônes dans les choix (carrousel pour les groupes, ✎ pour les modifier),
  journal gardé sans limite, historique repris d'Android (`Historique`,
  « Accès aux données d'utilisation » : détail ≈ 10 j dans le journal, totaux
  plus anciens dans `historique.txt`, importés une seule fois).
- v1.2 : se bloquer mieux (strict, frictions, sites, noir et blanc…),
  déclencheurs de profil, personne de confiance, bilan hebdo, widget — détail
  en fin de `docs/CAHIER_DES_CHARGES.md`.
- v1.3 : limites plus naturelles (intentions, réglettes, jauges), zéro permis,
  écran de blocage détaillé, remise à zéro, réglage du widget.
- v1.4 : widget réglé widget par widget (contenus, profil, concentration choisie,
  appuis, fond, opacité, taille, mode compact).
- v1.5 : réactiver une limite la remet à zéro, alerte qui nomme la limite, sessions
  = pauses d’horloge par-dessus les autres limites, rien ne compte écran verrouillé.
