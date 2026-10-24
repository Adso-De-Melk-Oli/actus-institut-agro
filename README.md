# Actus | L'Institut Agro

**Le site : https://adso-de-melk-oli.github.io/actus-institut-agro/**

Les actualités et l'agenda de l'Institut Agro, de ses écoles (Montpellier, Rennes-Angers, Dijon) et de sa Fondation, réunis en un seul fil, avec un filtre par source et par type.

## Comment ça marche

- `index.html` : le site. Il lit `data/articles.json` ; le bouton Réactualiser recharge ce fichier.
- `scripts/collect.mjs` : le robot de collecte. Il lit les flux RSS quand ils existent, sinon les pages Actualités et Agenda, et respecte le `robots.txt` de chaque site.
- `.github/workflows/collect.yml` : relève les sites, enregistre les nouveautés puis envoie le digest à partir de 7 h. Une tâche Claude le lance vers 6 h 35 du lundi au vendredi ; des horaires GitHub servent de filet de sécurité (GitHub les retarde parfois de plusieurs heures). Une collecte peut aussi être lancée à la main depuis l'onglet **Actions** → **Relevé des actus** → **Run workflow**.

| Source | Méthode |
|---|---|
| Montpellier | Flux RSS actus et agenda |
| Fondation | Flux RSS + page Événements |
| Institut Agro | Pages Actualités et Agenda (pas de flux RSS) |
| Rennes-Angers | Pages Actualités et Agenda (flux RSS vide) |
| Dijon | Flux RSS (actus ; ses événements quand le flux en contient) |

## Digest du matin

Chaque jour ouvré, le workflow « Relevé des actus » relève les sites puis envoie le digest à partir de 7 h (`scripts/send.mjs`, par Gmail, photos jointes ; secrets `GMAIL_USER`, `GMAIL_APP_PASSWORD`, `DIGEST_TO`). Le digest contient les nouveautés depuis le précédent envoi, rangées par école, les événements du jour sur les campus (et à part ceux à distance) et ceux des 7 prochains jours.

- **Déclenchement** : une tâche Claude lance le relevé vers 6 h 35 ; des horaires GitHub servent de filet de sécurité, car GitHub retarde parfois ses horaires de plusieurs heures. Un lancement planifié ne fait rien si le digest du jour est déjà parti.
- **Relevé en retard** : le digest du jour part quand même, dès la fin du relevé.
- **`data/sent.json`** garde le dernier envoi : le digest suivant reprend exactement là où il s'arrête (pas de trou, pas de doublon).
- **Alerte** à l'administrateur quand une source est en échec deux jours de relevé de suite, puis un rappel par semaine.
- **Nouvelle source** : son historique rejoint le site mais n'est pas annoncé comme nouveau dans le digest.
- **Essai** : Actions → « Envoi du digest (essai) » → Run workflow envoie l'aperçu (`data/preview`) à l'administrateur seul.

Si une source échoue, ses contenus de la veille sont conservés et l'encadré « Sources suivies » du site affiche « Partiel ». Les actus de plus de 12 mois et les événements terminés depuis plus de 6 mois sont retirés automatiquement.

## Lancer en local

```sh
npm install
npm test          # lecture des dates
npm run collect   # met à jour data/articles.json
npx serve .       # puis ouvrir http://localhost:3000
```
