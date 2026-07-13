# Architecture UGO2

## Vue d’ensemble

```text
YouTube / Facebook / Instagram
              |
              v
      batch/ — Java 21 / Spring Boot
      - découvre les vidéos
      - collecte les métriques
      - doit signer les appels HMAC
              |
              v
      web/src/back/ — API PHP 8
      - ingestion et validation
      - réconciliation multi-plateformes
      - lecture, agrégats et séries temporelles
              |
              v
      MariaDB — une base par webTV
      - video : vidéo canonique
      - source_video : publication sur une plateforme
      - metric_snapshot : mesure horodatée
      - vues/tables analytiques : derniers états et graphes
              |
              v
      web/ihm/ — Angular
      - liste et tri des vidéos
      - détail et graphes
      - résolution des doublons
```

## Modules

| Chemin | Responsabilité |
|---|---|
| `batch/` | Clients API, collecte, mapping et envoi vers l’API web. Aucune connexion directe à MariaDB. |
| `web/src/back/` | API PHP, authentification des ingestions, accès DB, réconciliation et calculs. |
| `web/ihm/` | Sources Angular. Le build généré va dans `web/src/front/` et ne doit pas être archivé pour une revue de code. |
| `web/sql/` | Schéma, vues, index et tables analytiques. À convertir en migrations versionnées. |
| `web/doc/` | Documentation API et DB. |
| `config/` et `*.tmpl` | Exemples sans secret. Les configurations réelles restent hors Git. |

## Flux principal

1. Le batch collecte les sources puis les métriques d’une webTV.
2. Il envoie des lots JSON à l’API PHP. La signature HMAC existe dans le client, mais son usage systématique reste à finaliser (voir `TODO.md`).
3. L’API upsert les sources et snapshots.
4. La réconciliation rattache les publications multi-plateformes à une même `video`.
5. Les agrégats alimentent la liste et les graphes Angular.

## Tenancy

UGO2 utilise actuellement une isolation simple :

- une base MariaDB par webTV ;
- une configuration batch par webTV ;
- une configuration/déploiement web par webTV.

Ce modèle évite les erreurs de filtrage entre tenants. Une base partagée avec `tenant_id` n’est utile que si l’exploitation de plusieurs instances devient trop coûteuse.

## Principes de données

- Toutes les dates DB sont en UTC ; affichage en `Europe/Zurich`.
- `null` = métrique inconnue ou non reçue ; `0` = mesure réelle à zéro.
- Les vues cumulées doivent être monotones par `source_video`.
- Les snapshots doivent rester parcimonieux : changement significatif ou garde quotidienne.
- Les configurations réelles, tokens, clés privées et builds générés ne doivent jamais entrer dans Git ni dans une archive de revue.
