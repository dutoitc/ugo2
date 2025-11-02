Base de données
===============

Schéma simple:
![Diagramme](db-simple.png)

Tables:
- video: une vidéo diffusée. 
  Elle est identifiée sur une source et peut être rattachée à d'autres sources
- source_video: une vidéo identifiée sur une source (FB/YT/IG)
- metric_snapshot: les métriques (insights) d'une vidéo lors d'un point de mesure
- reconcile_override: non utilisé actuellement