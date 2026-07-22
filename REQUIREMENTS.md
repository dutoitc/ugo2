# Exigences UGO2

Ce document décrit la cible fonctionnelle et non fonctionnelle. Les écarts du code actuel, notamment l’authentification des mutations, les migrations et certains tests, sont suivis dans `TODO.md`.

## Objectif

Centraliser les statistiques vidéo de deux webTV indépendantes, rapprocher une même vidéo publiée sur plusieurs plateformes et conserver un historique fiable sans gonfler inutilement la base.

## Périmètre fonctionnel

- Collecter les vidéos et métriques YouTube, Facebook et Instagram.
- Prévoir TikTok/WordPress comme extensions, sans les considérer comme opérationnels tant que leurs collecteurs ne sont pas complets.
- Réconcilier plusieurs `source_video` vers une même `video` canonique.
- Afficher une liste paginée avec recherche, filtres et tri serveur.
- Afficher le détail d’une vidéo, ses dernières métriques et ses séries temporelles.
- Détecter et résoudre les doublons de réconciliation.
- Calculer les agrégats et tendances sans requêtes coûteuses par ligne.
- Exporter les données nettoyées et les anomalies à terme.

## Intégrité des données

- Toutes les dates métier sont stockées en UTC.
- `null` signifie « donnée inconnue/non reçue » ; `0` signifie une mesure réelle à zéro.
- Les vues cumulées ne diminuent jamais pour une même source.
- Un zéro temporaire ou une baisse reçue après une valeur positive est une anomalie de collecte.
- Les snapshots identiques ne sont pas stockés à chaque exécution.
- Un point de garde quotidien est conservé même sans variation importante.

## Architecture et tenancy

- Une base MariaDB, une configuration batch et un déploiement web par webTV.
- Le batch Java appelle l’API PHP ; il ne se connecte pas directement à la DB.
- Le front Angular consomme uniquement l’API PHP.
- Les configurations réelles restent hors Git. Le build Angular de déploiement est actuellement versionné dans `web/src/front/`.

## Sécurité cible

- Toutes les routes modifiant les données sont protégées par HMAC.
- Les signatures ont une durée de validité courte et une protection anti-rejeu.
- Les tokens, mots de passe, clés HMAC, clés SSH et fichiers de configuration réels ne sont jamais commités ni archivés.
- Les logs n’affichent ni token, ni secret, ni payload complet sensible.

## Performance

- Les appels plateformes sont batchés et soumis à une politique de polling dégressive.
- Les refresh analytiques ne sont pas exécutés plusieurs fois par lot ni dans le chemin critique de chaque requête d’ingestion.
- Les séries envoyées au front sont agrégées/downsamplées selon la période et la largeur d’affichage.
- Les listes évitent les requêtes N+1.

## Exploitation

- Chaque run possède un identifiant et un bilan par plateforme : lus, insérés, ignorés, erreurs, durée.
- Une page santé indique la dernière collecte réussie et l’âge des données par plateforme.
- Les opérations de nettoyage DB proposent un mode `dry-run` et un rapport avant modification.
- Les évolutions DB utilisent des migrations versionnées et non des scripts destructifs réexécutés manuellement.

## Critères minimaux de validation cible

- Tests unitaires batch et PHP sur auth, monotonie, zéro temporaire, parcimonie et tri.
- Build Angular réussi.
- Lint PHP réussi.
- Archive de revue contenant `files.txt`, sans build, média ni configuration réelle.
