# Changelog

Les changements notables du projet sont consignés ici selon [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/).

## [Unreleased]

### Sécurité

- Masquage centralisé des tokens, clés, en-têtes d’autorisation, URL signées, mots de passe d’URL et corps sensibles dans les logs Java/PHP, erreurs API et états de santé.
- Réponses `500` rendues génériques et suppression du point de diagnostic public `phpinfo()`.
- Scan Gitleaks automatisé sur l’historique complet et sur l’archive de revue.

### Modifié

- Remplacement des domaines, identifiants, configurations, fixtures et visuels liés à des instances réelles par des exemples neutres.
- Mise à niveau vers Angular 20.3 et ECharts 6.1 ; suppression de `ngx-echarts` inutilisé et du chargement ECharts par CDN.
- Ajout d’une CI frontend avec audit des dépendances de production et build Angular.
- Alignement du README, de l’architecture, de l’API, du modèle DB et de la spécification IHM sur le code présent.
- Conservation du déploiement SSH/rsync avec build Angular préalable et suppression distante explicitement optionnelle.

### Supprimé

- Routes d’agrégats non implémentées qui répondaient `501`.
- Anciennes mutations `GET` de refresh et point de diagnostic public `phpinfo()`.
- Build Angular versionné dans `web/src/front/` ; il est désormais produit localement.

### Corrigé

- La liste Angular ouvre toujours le détail avec l’identifiant numérique attendu par `/v/:id`.
- `npm audit --omit=dev` ne remonte plus de vulnérabilité.

## [0.2.0-skeleton] - 2025-09-01

### Ajouté

- Premier squelette Java 21/Spring Boot et configuration d’exemple.

## [0.1.0-docs] - 2025-09-01

### Ajouté

- Documentation initiale des besoins, de la configuration, du scheduling et de la roadmap.

[Unreleased]: https://github.com/dutoitc/ugo2/compare/0.2.0-skeleton...HEAD
[0.2.0-skeleton]: https://github.com/dutoitc/ugo2/releases/tag/0.2.0-skeleton
[0.1.0-docs]: https://github.com/dutoitc/ugo2/releases/tag/0.1.0-docs
