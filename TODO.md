# TODO / Roadmap UGO2

But : suivre proprement les performances vidéo de deux webTV, avec une base fiable, peu de bruit et aucun secret dans Git ou les archives de revue.

## MUST — intégrité, sécurité, exploitation

- [ ] **Activer réellement l’authentification HMAC sur toutes les routes qui modifient les données** : ingestion sources/métriques, réconciliation, overrides, résolution des doublons et refresh des vues. Le batch doit utiliser les méthodes signées du client API.
- [ ] Ajouter au schéma les tables nécessaires à l’authentification/idempotence, ou supprimer le code mort correspondant. Prévoir une protection anti-rejeu du nonce.
- [ ] Corriger `OverridesController` : variable `$pdo` non définie lors du refresh.
- [ ] Remplacer les scripts SQL destructifs (`DROP TABLE`) par des migrations versionnées, réexécutables et sauvegardables. Documenter clairement l’ordre d’installation/mise à jour.
- [ ] **Garantir la monotonie des vues cumulées par source** :
  - ignorer un `0` reçu après une valeur positive ;
  - ne jamais remplacer une valeur de vues par une valeur inférieure ;
  - conserver malgré tout les autres métriques valides du snapshot ;
  - journaliser l’anomalie avec plateforme, vidéo, ancienne et nouvelle valeur.
- [ ] Créer un job d’assainissement DB avec mode `dry-run` : supprimer les snapshots à zéro apparus après une valeur positive, détecter les régressions et produire un rapport avant toute correction.
- [ ] **Rendre le stockage parcimonieux réellement effectif** : insérer un snapshot seulement si une métrique utile change, si le delta de vues dépasse le seuil configuré, ou pour le point de garde quotidien. Ne pas stocker des dizaines de points identiques.
- [ ] Ne plus recalculer toutes les vues matérialisées à chaque lot d’ingestion. Déclencher un refresh différé/débouclé une fois par exécution de batch, avec verrou fiable et métriques de durée.
- [ ] Aligner `006_performances.sql`, `007_graph_views.sql` et `MaterializedViewsSql.php` : noms de tables, index et stratégie de refresh divergent actuellement.
- [ ] Vérifier les secrets avant chaque commit/archive : seuls les fichiers `*.tmpl` sont partageables. Scanner aussi l’historique Git si un token a déjà été commité.
- [ ] Corriger Instagram : en cas d’échec des insights, ne pas envoyer automatiquement `views=0`, `shares=0`, `reach=0`. Utiliser `null` pour « donnée inconnue ».

## SHOULD — performances et qualité du code

- [ ] Supprimer le N+1 de tendance dans la liste : `TrendService` exécute actuellement une requête par vidéo affichée. Calculer les tendances en une requête ou les préagréger.
- [ ] Utiliser réellement la politique de polling : YouTube/Facebook redécouvrent et relisent actuellement un historique très large à chaque run, alors que `PollingPolicy` n’est pas utilisé.
- [ ] Séparer clairement les étapes du batch : discovery, collecte des métriques, ingestion, réconciliation, refresh analytique. Une étape en erreur ne doit pas invalider les autres plateformes.
- [ ] Ajouter des tests back sur : monotonie, zéro temporaire, snapshot identique, tri de chaque colonne, pagination, auth HMAC et concurrence de refresh.
- [ ] Ajouter des tests front sur : tri, filtre, changement rapide de page, erreur API, données absentes et token expiré.
- [ ] Réduire le code mort/ancien : doublon `TeaserHeuristics`, fichiers Angular générés non utilisés et imports/require dupliqués.
- [ ] Ajouter des logs structurés par run (`run_id`, tenant, plateforme, durée, éléments lus/insérés/ignorés/anormaux), sans payload complet ni token.
- [ ] Exposer une page santé simple : dernier succès par plateforme, âge du dernier snapshot, durée du dernier batch, erreurs et token probablement expiré.

## SHOULD — IHM et graphes

- [x] Supprimer le fond jaune temporaire de la table.
- [x] Ajouter le tri serveur sur les colonnes : date, titre, total, YouTube, Facebook, Instagram et engagement.
- [ ] Afficher `—` / « indisponible » plutôt que `0` lorsqu’une donnée n’a pas été reçue ou que la collecte a échoué.
- [ ] Afficher la fraîcheur par plateforme et un avertissement si une source n’a plus été mise à jour.
- [ ] Limiter les graphes au nombre de points utile à l’écran et conserver les points significatifs : début, fin, changements, pics et garde quotidienne.
- [ ] Ne pas interpoler silencieusement des valeurs comme si elles avaient été mesurées. Distinguer visuellement les points réels des valeurs calculées.
- [ ] Ajouter des périodes rapides : 24 h, 7 j, 28 j, 6 mois, tout.
- [ ] Ajouter un filtre « type » (long, teaser, short/reel) et des comparaisons entre vidéos de même type/durée.
- [ ] Rendre la liste lisible sur mobile : colonnes prioritaires, titre aligné à gauche, en-tête fixe et choix de colonnes.

## DO — idées utiles

- [ ] **Mesurer l’efficacité des partages Facebook** : étudier d’abord ce que l’API permet réellement. Prévoir un modèle indépendant de l’API (`share_target`, groupe/page, date, URL partagée, campagne) et accepter une saisie/import manuel si les statistiques des groupes ne sont pas accessibles.
- [ ] Générer des liens distincts/UTM par groupe lorsque le lien pointe vers un site contrôlé, afin de mesurer au moins les clics et conversions par lieu de partage.
- [ ] Ajouter un tableau « meilleur rendement » : vues gagnées à 24 h/7 j, engagement, durée vue et rendement par plateforme/type de vidéo.
- [ ] Détecter automatiquement les vidéos sous-performantes ou anormalement performantes par rapport aux vidéos comparables.
- [ ] Ajouter export CSV/XLSX des données nettoyées et des anomalies de collecte.
- [ ] Ajouter une commande de maintenance : diagnostic DB, volume par table/source, doublons, zéros, régressions et estimation du gain après nettoyage.

## Décisions actuelles

- Deux webTV restent isolées : une configuration et une base par webTV. Pas de colonne `tenant_id` tant que ce modèle reste simple à exploiter.
- Les vues sont cumulatives et doivent être monotones. Une baisse reçue d’une API est traitée comme une anomalie de collecte, pas comme une nouvelle vérité.
- `null` signifie « inconnu/non reçu » ; `0` signifie une mesure réelle à zéro.
