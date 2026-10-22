# Actus | L'Institut Agro

**Le site : https://adso-de-melk-oli.github.io/actus-institut-agro/**

Les actualités et l'agenda de l'Institut Agro, de ses écoles (Montpellier, Rennes-Angers, Dijon) et de sa Fondation, réunis en un seul fil, avec un filtre par source et par type.

## Comment ça marche

- `index.html` : le site. Il lit `data/articles.json` ; le bouton Réactualiser recharge ce fichier.
- `scripts/collect.mjs` : le robot de collecte. Il lit les flux RSS quand ils existent, sinon les pages Actualités et Agenda, et respecte le `robots.txt` de chaque site.
- `.github/workflows/collect.yml` : lance le robot du lundi au vendredi à 7 h 33 (heure de Paris en été) et enregistre les nouveautés. Une collecte peut aussi être lancée à la main depuis l'onglet **Actions** → **Relevé des actus** → **Run workflow**.

| Source | Méthode |
|---|---|
| Montpellier | Flux RSS actus et agenda |
| Fondation | Flux RSS + page Événements |
| Institut Agro | Pages Actualités et Agenda (pas de flux RSS) |
| Rennes-Angers | Pages Actualités et Agenda (flux RSS vide) |
| Dijon | Non relevé : le site refuse les robots. Le site renvoie vers ses actus. |

Si une source échoue, ses contenus de la veille sont conservés et l'encadré « Sources suivies » du site affiche « Partiel ». Les actus de plus de 12 mois et les événements terminés depuis plus de 6 mois sont retirés automatiquement.

## Lancer en local

```sh
npm install
npm test          # lecture des dates
npm run collect   # met à jour data/articles.json
npx serve .       # puis ouvrir http://localhost:3000
```
