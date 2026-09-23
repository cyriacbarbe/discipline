# Discipline — blocage d'applications Android

Appli Android perso (pas destinée à être vendue) pour se limiter soi-même sur
son téléphone : bloquer des applications, des créneaux horaires, des sessions
limitées par jour, un déblocage par puce NFC. Java natif, même stack que
`cyranss/android` (Gradle, pas d'AndroidX au-delà du strict nécessaire).

## Dépôt et fabrication

- Dépôt Git propre à ce projet, indépendant de `cyranss/` (pas de remote
  configuré pour l'instant — à créer sur GitHub quand utile).
- Outils de compilation : les mêmes que Cyranss, dans
  `C:\Users\user\android-outils` (JDK 17, SDK Android 34, Gradle 8.7).
  ```bash
  cd android
  JAVA_HOME="C:\Users\user\android-outils\jdk-17.0.20.1+1" ./gradlew assembleDebug
  ```
  L'APK sort dans `app/build/outputs/apk/debug/app-debug.apk` — à copier sur
  le téléphone et installer (sources inconnues) pour tester.
- Pas de clé de signature pour l'instant (mode debug uniquement) : à ajouter
  le jour où l'appli doit se mettre à jour proprement sans tout réinstaller
  (voir comment `cyranss/android` le fait, même mécanique si besoin).

## Mises à jour (bouton « Télécharger la mise à jour »)

- `MiseAJour.java` lit la dernière Release de `cyriacbarbe/discipline` sur
  GitHub : balise `v<versionCode>` + APK signé en pièce jointe. À chaque
  version : monter `versionCode`/`versionName` dans `app/build.gradle`,
  `./gradlew assembleRelease`, puis publier une Release `v<versionCode>` avec
  `app-release.apk`. Commiter toujours après chaque changement.
- Clé : `android/discipline.jks` + `signature.properties`, hors Git — à
  sauvegarder ailleurs, la perdre oblige à désinstaller l'appli.

## Comment ça bloque

- `BlocageAccessibilityService` : un service d'accessibilité (à activer à la
  main dans Réglages > Accessibilité, Android ne permet pas de l'activer par
  code) qui regarde quelle application passe au premier plan. Si elle est
  dans la liste bloquée et dans le créneau configuré, il renvoie à l'accueil
  et ouvre `BlocageActivity`.
- `Regles` : les règles en SharedPreferences (liste des applications
  bloquées, créneau horaire optionnel). Un seul créneau global pour
  l'instant, pas encore par application.
- Liste des applications : lue par `MainActivity` via `queryIntentActivities`
  (déclaré dans `<queries>` du Manifest, pas besoin de la permission
  QUERY_ALL_PACKAGES).

## Fait

- Blocage par liste d'applications + un créneau horaire global (ex. 22h-7h).

## À venir (dans cet ordre, un morceau à la fois)

- **Sessions limitées** : ex. 4 fois par jour, débloquer 3 applications
  précises pour une durée donnée, compteur remis à zéro chaque jour.
- **Quota de temps par appli/jour** : nécessite `PACKAGE_USAGE_STATS`
  (permission spéciale à accorder dans Réglages > Accès à l'utilisation).
- **Déblocage par puce NFC** : scanner un tag posé quelque part (cuisine,
  bureau...) pour débloquer une ou plusieurs applications ; lecture NFC déjà
  standard en Android (`NfcAdapter`), pas de nouveau matériel si le
  téléphone a le NFC.
- **Créneaux par application** (pas juste un créneau global).
