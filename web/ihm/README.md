# IHM UGO2

Sources de l’interface Angular. Le résultat compilé est généré dans `../src/front/` et n’est pas versionné.

## Développement local

Le proxy versionné cible `http://localhost:8080`. Pour utiliser une autre API sans modifier un fichier suivi :

```bash
cp src/proxy.conf.json src/proxy.conf.local.json
# Modifier ensuite uniquement proxy.conf.local.json
./myserve.sh
```

`proxy.conf.local.json` est ignoré par Git. L’interface est disponible sur `http://localhost:4200/`.

## Build

```bash
npm ci
npm audit --omit=dev --audit-level=moderate
npm run build
```

Le build remplace `../src/front/`. Il doit être produit avant un déploiement, mais jamais ajouté à Git.

## Tests

```bash
npm test
```
