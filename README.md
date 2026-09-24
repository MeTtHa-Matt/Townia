# Townia

Townia est un plugin Paper pour serveur survival qui combine gestion de villages, claims, mondes personnalisés, onboarding, permissions par monde et outils d’administration. Il a été conçu pour offrir une expérience claire pour les joueurs, tout en gardant une logique de modération et de sécurité côté staff.

## Fonctionnalités principales

- création et gestion de villages
- invitations, rôles, grades et permissions villageoises
- claims par chunk avec protection des interactions, constructions, dégâts et récoltes
- homes de village, téléportation, validation dans un claim du village
- interface de gestion d’actions et de droits pour les villages
- système de mondes personnalisés avec `/world`
- paramètres par monde : PvP, PvE, mode de jeu, permissions, inventaire synchronisé, activation du village, activation du leave, etc.
- gestion des dimensions du serveur avec protection du monde principal et création/suppression de mondes personnalisés
- retour vers le monde principal via `/leave`
- monde d’événement avec `/event`
- commandes d’aide, règles du serveur et guide de première connexion
- livre de règles personnalisable pour les nouveaux joueurs
- commerce entre joueurs avec interface dédiée et demande/acceptation/refus
- sauvegarde de l’inventaire selon le mode de synchronisation du monde
- logique de sécurité pour éviter les suppressions dangereuses et les actions non autorisées

## Commandes disponibles

### Joueurs

- `/village` : ouvre le menu principal du village
- `/claim` : revendique le chunk actuel
- `/unclaim` : retire le chunk actuel
- `/sethome` : place le home du village dans un claim valide
- `/home` : téléporte au home du village
- `/delhome` : supprime le home du village
- `/event` : téléporte vers le monde d’événement
- `/leave` : retourne au monde principal si le système est autorisé dans ce monde
- `/aide` : affiche le guide d’utilisation pour les joueurs
- `/regles` : réouvre le livre de règles du serveur
- `/commerce <joueur>` : propose un commerce
- `/commerce accept` : accepte une demande de commerce
- `/commerce refuse` : refuse une demande de commerce

### Admins

- `/world` : ouvre la gestion des mondes
- `/aide admin` : affiche l’aide admin

## Workflow type

### Pour un joueur

1. ouvrir `/village` et créer son village
2. revendiquer des chunks
3. configurer les permissions et les membres du village
4. définir un home dans un claim du village
5. utiliser `/home` pour revenir rapidement
6. consulter `/regles` et `/aide` en cas de besoin

### Pour un admin

1. ouvrir `/world`
2. créer, renommer ou supprimer des mondes selon les besoins
3. régler les permissions de monde, le PvP/PvE et les paramètres de jeu
4. gérer le mode d’inventaire des mondes
5. contrôler les mondes de jeu, d’événement ou de transition

## Structure technique

- Java 21
- Paper API 1.21.x
- Gradle
- Multiverse Core comme dépendance de gestion des mondes
- données persistées côté plugin, en local dans le dossier `plugins/Townia/`

## Construction

```bash
./gradlew clean test
```

Le JAR généré peut ensuite être déposé dans le dossier `plugins/` d’un serveur Paper.

## Points de sécurité et d’usage

- le monde principal est protégé contre les suppressions
- les suppressions de monde refusent les actions lorsque des joueurs sont présents
- les homes ne peuvent pas être définis hors d’un claim valide
- les suppressions de claim sur un home sont bloquées
- la validation des permissions par monde et par village est appliquée avant les actions de jeu

## Remarques

Ce plugin est pensé pour un usage de serveur survie avec un cadre visuel lisible, des menus accessibles et un système de monde modulaire. Le README est volontairement orienté « serveur réel » et reflète les outils réellement disponibles dans le projet.
