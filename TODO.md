# TODO / Roadmap UGO2

Uniquement les travaux restant à réaliser, classés par priorité.

## MUST — sécurité, intégrité, cohérence

- [ ] Protéger toutes les mutations : HMAC côté serveur avec anti-rejeu/idempotence pour le batch, authentification distincte pour l’administration IHM, aucune mutation en `GET`, tests de rejet inclus.
- [ ] Empêcher toute fuite de secret : masquer tokens, clés, URL signées et corps sensibles dans les logs, erreurs API et états de santé ; automatiser le scan des commits et archives.
- [ ] Supprimer les références aux instances réelles (`CAPStv`, `Xplore`) et remplacer domaines, identifiants, logos, fixtures et configurations par des exemples neutres.
- [ ] Corriger les vulnérabilités npm Angular/ECharts, supprimer le chargement CDN redondant et valider `npm audit` + build.
- [ ] Sécuriser la résolution des doublons : valider les relations et lignes affectées, garantir la transaction et interdire toute suppression ambiguë.
- [ ] Uniformiser les contrats des routes d’écriture : JSON validé, réponse réellement sérialisée, codes HTTP stables et erreurs internes non exposées.
- [ ] Remplacer les scripts SQL destructifs et le DDL exécuté à la volée par des migrations versionnées, réexécutables et testées.
- [ ] Ajouter des tests d’intégration PHP/MariaDB pour HMAC, monotonie, parcimonie, verrou/atomicité du refresh, valeurs inconnues et tris.
- [ ] Ajouter l’assainissement DB en `dry-run` avec rapport, puis correction explicite des zéros prouvés anormaux.
- [ ] Aligner README, architecture, API, DB, spécification IHM et changelog sur le code ; retirer routes fictives et consignes de déploiement obsolètes ou destructives.

## SHOULD — fiabilité, performance, maintenabilité

- [ ] Supprimer les N+1 de tendance et de détail vidéo par des requêtes groupées ou des données pré-calculées.
- [ ] Rendre le refresh du rollup atomique afin qu’aucun lecteur ne voie une table vide ou partielle.
- [ ] Appliquer réellement la politique de polling selon l’âge et la stabilité des vidéos ; supprimer le code inutilisé associé.
- [ ] Distinguer token expiré, quota, permission, panne plateforme et réseau ; récupérer l’expiration disponible et conserver un court historique d’incidents.
- [ ] Définir une rétention : détail récent, puis consolidation journalière et hebdomadaire.
- [ ] Simplifier le backend PHP sans nouveau framework : séparer lecture, doublons et refresh, retirer réflexion, compatibilités mortes et debug commenté.
- [ ] Ajouter une CI GitHub : tests Java/PHP, MariaDB, lint PHP, audit et build Angular, scan de secrets.
- [ ] Retirer les routes `501` et clients inactifs, ou ne les réintroduire qu’avec un besoin, une implémentation et une documentation cohérents.

## COULD — évolutions fonctionnelles

- [ ] Afficher les deltas 24 h / 7 j par plateforme, avec filtres « données périmées » et « erreurs de collecte », sans N+1.
- [ ] Exporter en CSV les vidéos et la santé des collecteurs.
- [ ] Ajouter un contrôle DB hebdomadaire : régressions, zéros suspects, sources orphelines et doublons.
- [ ] Étudier les statistiques de groupes Facebook ; à défaut, utiliser des liens UTM et un import manuel des partages.
