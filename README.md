# Townia

Plugin Paper pour organiser un serveur en villages avec claims, permissions par grade, activite et administration des mondes.

## Construire

```bash
./gradlew clean test
```

Le jar est genere dans `build/libs/` et peut etre copie dans le dossier `plugins/` d'un serveur Paper. Le projet cible Java 21 et l'API Paper `1.21.8`; adaptez la version `paper-api` dans `build.gradle` si votre serveur utilise une autre version.

## Fonctionnalites presentes

- `/village create`, `invite`, `join`, `open`, `close`, `claim`, `unclaim`, `trust` et `setrole`.
- Claims exclusifs par monde et chunk, avec protection du build, PVP, ramassage, drop et feu.
- Maire et vice-maires avec plafond dependant de la taille du village.
- Persistance YAML dans `plugins/Townia/data.yml`.
- `/world create`, `join` et `delete` protege par permission admin.
- Suivi du temps de jeu et detection AFK configurable.
- Point d'extension `SkinService` pour brancher SkinsRestorer ou un fournisseur officiel.

## Integrations a brancher

Discord, la restauration automatique des skins et la detection anti-cheat/Xray sont volontairement isoles derriere des services. Il faut choisir un fournisseur, ses permissions et sa politique de faux positifs avant de les activer sur un serveur public. La suppression de monde demande encore une confirmation explicite et une suppression du dossier par l'administrateur.
