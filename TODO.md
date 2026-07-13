# TODO / Roadmap UGO2

But : suivre proprement les performances vidéo de deux webTV, avec une base fiable, peu de bruit et aucun secret dans Git ou les archives de revue.

## MUST — intégrité, sécurité et exploitation

- [x] Garantir la monotonie de `views_native` et `total_watch_seconds` par source ; conserver la dernière valeur connue quand une plateforme renvoie `null` et journaliser les régressions corrigées.
- [x] Rendre les snapshots parcimonieux : seuil absolu/relatif, changements d’engagement utiles et point de garde quotidien.
- [x] Ne rafraîchir les vues matérialisées qu’une fois en fin de `batch:run`, avec verrou DB, état, durée et erreur.
- [x] Corriger Instagram : ne jamais convertir un échec d’insights en `views=0`, `shares=0` ou `reach=0`.
- [x] Corriger `OverridesController` : le refresh n’utilise plus une variable `$pdo` inexistante.
- [x] Corriger les tris Total, YouTube, Facebook, Instagram et TikTok côté API et IHM.
- [x] Exposer `/health` : dernier snapshot/succès par plateforme, fraîcheur, dernier batch, refresh, erreurs et état probable du token.
- [x] Afficher les alertes de santé globalement dans l’IHM et une page Santé détaillée.
- [x] Alerter si un token Facebook/Instagram/YouTube est expiré ou refusé, et prévenir avant expiration lorsque la date est connue.
- [x] Supprimer le code mort évident : doublon `TeaserHeuristics`, fichiers Angular générés non utilisés et `require` dupliqué.
- [ ] Imposer réellement l’authentification HMAC sur tous les endpoints d’écriture. Ajouter des tests de rejet sans signature, signature invalide et rejeu du nonce.
- [ ] Ajouter une commande d’assainissement DB en `dry-run`, puis exécution : supprimer les anciens snapshots à zéro uniquement lorsqu’un point antérieur ou postérieur non nul prouve l’anomalie.
- [ ] Ajouter des tests d’intégration MariaDB pour monotonie, parcimonie, verrou de refresh et données inconnues.
- [ ] Remplacer les scripts SQL destructifs par des migrations versionnées et réexécutables.
- [ ] Vérifier les secrets avant chaque commit/archive et scanner l’historique Git si un token a déjà été commité.

## SHOULD — performances et qualité des données

- [ ] Distinguer dans la santé : token expiré, quota dépassé, permission retirée, panne API et erreur réseau.
- [ ] Interroger Meta pour obtenir automatiquement la date d’expiration lorsqu’elle est accessible, sans journaliser le token.
- [ ] Ajouter un historique court des incidents au lieu de conserver uniquement la dernière erreur.
- [ ] Supprimer le N+1 de tendance dans la liste ; calculer les tendances en lot ou les matérialiser.
- [ ] Rendre le refresh du rollup atomique par table temporaire + renommage pour éviter une table vide pendant le recalcul.
- [ ] Ajouter une politique de rétention : points détaillés récents, puis consolidation journalière/hebdomadaire.
- [ ] Aligner `002_views.sql`, `005_views_fallback_on_reach.sql`, `006_performances.sql`, `007_graph_views.sql` et `MaterializedViewsSql.php`.
- [ ] Utiliser réellement une politique de polling : limiter les anciennes vidéos stables et suivre plus souvent les nouvelles publications.

## DO — améliorations utiles

- [ ] Groupes Facebook : étudier les données réellement disponibles. Repli utile : liens UTM distincts par groupe et saisie/import manuel des partages.
- [ ] Afficher les deltas 24 h / 7 j par plateforme dans la liste, sans requête par ligne.
- [ ] Ajouter un filtre « données périmées » et un filtre « erreurs de collecte ».
- [ ] Ajouter un export CSV des vidéos et de la santé des collecteurs.
- [ ] Ajouter GitHub Actions : Maven, tests Java, lint PHP et build Angular.
- [ ] Ajouter un job hebdomadaire de vérification DB : régressions, zéros suspects, sources orphelines et doublons.
