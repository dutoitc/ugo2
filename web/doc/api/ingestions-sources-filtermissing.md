
### `POST /api/v1/sources:filterMissing`
#### Description:
Vérifie quelles sources sont absentes de la base avant un import massif.
#### Entrées tolérées:
```json
{ "platform":"YOUTUBE", "ids":[...] }  
{ "platform":"YOUTUBE", "platformIds":[...] }
{ "sources":[{"platform":"YOUTUBE","platformId":"..."}] }
```
#### Retour: IDs absents de source_video
```json
{
  "platform": "YOUTUBE",
  "requestedCount": 100,
  "existingCount": 98,
  "missingCount": 22,
  "missing": [44, 45]
}
```
