# Cahier des charges — Discipline v1 (refonte)

Décidé le 24/09/2026. Refonte complète livrée **d'un coup** (pas par étapes),
**on repart de zéro** : les règles et badges de la v0.5 ne sont pas convertis.
Style **sombre et sobre** : grosses cartes arrondies, vert = ok, rouge = bloqué.

## Modèle

**Limite = cibles + conditions (combinées ET/OU) + exceptions + action.**

- **Cibles** : des applis et/ou des groupes.
- **Groupes** : une appli peut être dans plusieurs groupes, et c'est **affiché**
  (pastilles sur l'appli). Une limite sur un groupe compte le **temps cumulé**
  du groupe. À l'installation d'une appli, on propose de l'ajouter à un groupe.
- **Combiner** : un bouton « Combiner avec une autre règle » demande **ET ou
  OU**, avec un petit curseur entre les deux conditions pour basculer.
- **Interrupteur** par limite, **mode vacances** global (jusqu'à une date),
  **profils** (ensembles de limites activés d'un coup : Travail, Weekend…).

## Types de limites (chacun est un type distinct)

1. **Temps par période** : pas plus de X min par heure, jour ou semaine.
2. **Ouvertures par période** : pas plus de N ouvertures par heure, jour ou
   semaine.
3. **Durée de session** : jamais plus de X min d'affilée.
4. **Sessions autorisées** : N sessions par heure, jour ou semaine, avec une
   durée maximale par session.
5. **Pause après usage** : après avoir fermé l'appli, attendre X min.
6. **Pause proportionnelle** : attente = temps passé × coefficient.
7. **Friction à l'ouverture** : compte à rebours de X s avant d'accéder à
   l'appli.
8. **Blocage immédiat** : « bloque-moi ça pendant X », lancé à la main.
9. **Badge NFC** : l'appli reste fermée tant que la puce n'est pas bipée. La
   **durée du déblocage est choisie dans la règle** : une session, X min ou
   jusqu'à la fin de la période.

**Périodes**
- **Heure** : heure pile **ou** fenêtre glissante de 60 min, au choix dans la
  règle.
- **Jour** : l'heure de début des 24 h se choisit dans la règle.
- **Semaine** : le jour et l'heure de début se choisissent dans la règle.

**Exceptions et variantes** : jours de la semaine, plages horaires,
dates/périodes, **valeur différente par jour** (1 h en semaine, 3 h le
dimanche). Imbriquer, par exemple 1 h par jour **et** 10 min par heure, se
fait avec ET.

## Quand la limite est atteinte

- **Action** : fermer l'appli (retour à l'accueil), **ou** ouvrir une autre
  appli choisie, **ou** afficher un écran de blocage.
- **Écran de blocage vraiment libre** : texte libre (nouveau ou pris dans la
  bibliothèque des anciens messages), image, variables du jour (`{temps}`,
  `{ouvertures}`…), message tiré au hasard dans une sélection, « de nouveau
  disponible dans 2 h 14 ».
- **Rallonge réglable par règle** : aucune, ou +X min N fois par période,
  éventuellement après une attente ou un bip NFC. Les rallonges sont comptées
  dans les stats.
- **Bulles de temps restant** : un petit bandeau superposé qui disparaît après
  3 s. Les seuils se choisissent dans la règle (ex. 15, 5 et 1 min). Demande
  la permission « afficher par-dessus les autres applis ».

## Accueil

1. **Carré** : temps passé sur le téléphone aujourd'hui, puis le temps sur le
   groupe **« Suivies »** (applis ou groupes choisis, qu'ils aient une règle ou
   non) et le détail des applis les plus utilisées, avec la comparaison à la
   semaine précédente.
2. **Lignes des limites en vigueur**, avec leur interrupteur. Un clic ouvre le
   détail : compteurs du jour (ouvertures bloquées ou autorisées, temps
   utilisé et restant, rallonges), graphe sur 7 et 30 jours.
3. Bouton **« Ajouter une limite »**, qui ouvre la page de choix du type.

## Paramètres globaux

- Tolérance de session : si on revient dans l'appli en moins de X s, c'est la
  même session. **5 s par défaut.**
- Heure de début de la journée pour l'accueil.
- Gestion des groupes, de la bibliothèque de messages et des profils.
- **Onglet anti-triche, fonctionnel, réglé sur « Libre » par défaut.** Options :
  délai avant d'assouplir ou de supprimer une règle (durcir reste immédiat),
  NFC exigé pour modifier, alerte si l'accessibilité est coupée, et plus tard
  un administrateur de l'appareil contre la désinstallation.

## Hors périmètre (plus tard)

- Blocage des sites web dans les navigateurs, à faire avec la connexion au PC.

## Livré en v1.0 (24/09/2026) — écarts et simplifications

- **Mesure du temps** : l'appli tient son propre journal (`files/journal.txt`,
  35 jours) à partir du service d'accessibilité ; la permission « accès à
  l'utilisation » n'est plus demandée.
- **Bulles** : affichées par le service d'accessibilité
  (`TYPE_ACCESSIBILITY_OVERLAY`), sans permission de superposition.
- **Anti-triche** : toute modification, désactivation ou suppression d'une
  limite *active* compte comme un assouplissement (pas de comparaison fine
  des réglages) ; créer ou activer est immédiat. Idem pour retirer des applis
  d'un groupe utilisé, passer à un profil qui éteint des limites, partir en
  vacances, relâcher l'anti-triche. Administrateur de l'appareil : pas encore.
- **NFC** : n'importe quel badge enregistré débloque toutes les règles
  « badge NFC » (pas de badge attitré par règle).
- **Rallonge** : suspend toute la limite pendant X min.
- **Nouvelle appli installée** : proposée dans une carte de l'accueil
  (« Ajouter à un groupe »), au prochain passage sur l'accueil.
- **Comparaison de l'accueil** : aujourd'hui face à la moyenne par jour des
  7 jours précédents.
- **Friction et badge** passent toujours par l'écran de blocage, quelle que
  soit l'action choisie.
