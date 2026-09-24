# Townia

Townia est un plugin Paper qui organise un serveur autour des villages, des claims, des permissions de mondes, de la gestion d'événements et de la supervision admin.

## Ce que propose le plugin

- gestion de villages avec création, invitations, adhésions, grades et permissions
- claims par chunk avec protection sur les actions de construction, interaction, PvP, PvE et ramassage
- homes de village avec téléportation, suppression et autorisation dans les claims du village
- gestion des mondes personnalisés via `/world`
- configuration des permissions par monde : PvP, PvE, casse, pose, interaction, mode de jeu, inventaire synchronisé, etc.
- support de dimensions et de retour `/leave`
- gestion d'un monde d'événement via `/event`
- suivi d'activité et temps de jeu
- persistance YAML locale dans le dossier du plugin

## Commandes principales

- `/village` : ouvre le menu du village
- `/claim` : revendique le chunk actuel
- `/unclaim` : retire le chunk actuel
- `/sethome` : place le home du village dans un claim du village
- `/home` : téléporte vers le home du village
- `/delhome` : supprime le home du village
- `/world` : gestion des mondes, permissions et paramètres
- `/event` : téléporte vers le monde d'événement
- `/leave` : retourne au monde principal si le monde le permet
- `/aide` : affiche le guide du joueur
- `/aide admin` : affiche l'aide spécifique aux admins

## Exemple de workflow pour un joueur

1. Créer son village via le menu `/village`.
2. Revendiquer des chunks avec `/claim` ou depuis le menu.
3. Définir un home dans un claim de son village.
4. Gérer les permissions et les membres depuis le menu village.
5. Utiliser `/home` pour revenir au village.

## Exemple de workflow pour un admin

1. Ouvrir `/world` pour créer ou supprimer des mondes.
2. Configurer les permissions de monde selon les besoins du serveur.
3. Régler le mode de jeu, l'inventaire synchronisé et les paramètres du monde.
4. Utiliser `/event` pour contrôler le monde d'événement.
5. Vérifier les réglages avec `/aide admin`.

## Construction

```bash
./gradlew clean test
```

Le fichier JAR est généré dans `build/libs/` puis à copier dans le dossier `plugins/` d'un serveur Paper.

## Notes de compatibilité

- le projet vise Java 21 et l'API Paper 1.21.x
- Multiverse-Core est utilisé comme dépendance pour la gestion des mondes
- le plugin sauvegarde ses données dans `plugins/Townia/`
