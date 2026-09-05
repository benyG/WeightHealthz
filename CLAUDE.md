# CLAUDE.md

Instructions de projet pour Claude Code. À lire avant toute modification.

## Le projet

**Forge** — app Android + Wear OS (Pixel Watch 2) de coaching pour un programme de prise de poids saine. Ce n'est pas un tracker passif : l'app compare en continu l'objectif au réel (poids, séances, repas) et **agit** — ajuste les calories recommandées, escalade les rappels, relaie via Calendar/Alexa. Usage personnel mono-utilisateur, pas de multi-tenant, pas de compte/auth serveur.

**Spec complète** : `SPEC.md` à la racine — architecture détaillée, modèle de données, prompts Gemini. **Design complet** : `DESIGN.md` à la racine — palette, typographie, wireframes, ton, accessibilité. Ce fichier `CLAUDE.md` ne remplace ni l'un ni l'autre, il donne les règles d'exécution au quotidien. En cas de doute sur une fonctionnalité, `SPEC.md` fait autorité ; sur l'apparence, `DESIGN.md` fait autorité.

## Scope actuel : MVP

Le MVP inclut l'écosystème complet dès la première itération — ce n'est **pas** un cœur minimal suivi d'ajouts :
- Suivi poids/séances/repas + moteur d'ajustement et d'escalade
- App Android + Wear OS complet (Tile, complication, écran de séance)
- Analyse hebdo Gemini (texte)
- Google Calendar (création d'événements) + relais Alexa (webhook)

Seuls la vocalisation Deepgram Aura et la saisie vocale Deepgram STT sont en phase 2. Ne pas les implémenter avant que le reste du MVP soit stable.

## Structure des modules

```
app/            # Android, Jetpack Compose
wear/           # Wear OS, Compose for Wear
core-domain/    # Logique métier pure — AUCUNE dépendance Android
core-data/      # Room, Health Connect
core-ai/        # Clients Gemini + Deepgram
core-sync/      # Google Calendar API, webhook Alexa
```

**Règle stricte** : `core-domain` ne doit jamais importer `android.*` ni dépendre d'un autre module. Toute règle métier (ajustement calorique, escalade, double progression) se teste en JVM pur, sans émulateur ni Robolectric. Si une modification de `core-domain` nécessite un import Android, c'est que la logique est mal placée — la déplacer dans `core-data` ou `app`.

## Règles métier non-négociables

Ces valeurs viennent du plan original et ne doivent pas être "arrondies" ou réinterprétées sans qu'on te le demande explicitement :
- Ajustement calorique : **+250 kcal** si gain < 0,2 kg/semaine deux semaines de suite ; **−200 kcal** si gain > 0,7 kg/semaine deux semaines de suite. Borné à ±300 kcal côté validation.
- Cible de prise de poids : 0,3 à 0,5 kg/semaine.
- Moyenne mobile sur **7 jours**, jamais de comparaison sur le poids d'un seul jour.
- Escalade : `À_JOUR → RETARD_1 (jour 1) → RETARD_2 (jour 2) → CRITIQUE (jour 3)`. `RETARD_2` et `CRITIQUE` doivent déclencher le webhook Alexa, pas seulement une notification Android.
- Double progression : monter la charge seulement quand **toutes** les séries d'un exercice atteignent le haut de la fourchette de reps avec une technique propre.

Toute modification de ces règles doit être un choix explicite du prompt utilisateur, pas une supposition pendant un refactor.

## Design system — voir DESIGN.md

Toutes les règles d'apparence (palette, typographie, layout, mouvement, wireframes, ton rédactionnel, accessibilité, checklist anti-slop) vivent dans **`DESIGN.md`**, à la racine du repo — pas ici, pour n'avoir qu'une seule source de vérité. À relire avant de construire ou modifier un écran, pas seulement au démarrage du projet. En résumé : Forge est un carnet d'entraînement de précision, pas un dashboard SaaS générique — `DESIGN.md` §1 liste explicitement les patterns à éviter (fond crème+terracotta, fond noir+néon, cartes SaaS à ombre uniforme, labels en majuscules, numérotation décorative).

## Secrets et confidentialité

- Clés API Gemini/Deepgram : jamais en dur dans le code, jamais commit. Utiliser `local.properties` (déjà dans `.gitignore`) exposé via `BuildConfig`.
- Données de poids/santé : locales par défaut (Room), pas d'upload brut. Seules des séries agrégées et labels d'exercices partent vers Gemini — jamais un export identifiant complet.
- Si un `.env`, une clé, ou un token apparaît dans un diff que tu t'apprêtes à committer, arrête-toi et signale-le au lieu de committer.

## Conventions de code

- Kotlin idiomatique, Coroutines/Flow pour l'async, pas de callbacks imbriqués.
- Compose : state hoisting, pas de logique métier dans les composables — ils lisent des `State`/`Flow` exposés par un ViewModel qui appelle `core-domain`.
- Un test unitaire JVM pur dans `core-domain` pour toute nouvelle règle métier avant de la brancher à l'UI.
- Commits atomiques par fonctionnalité (ex. : "core-domain: règle d'escalade RETARD_1/2/CRITIQUE"), pas de commit fourre-tout multi-module.

## Quand demander plutôt que supposer

- Un choix de wireframe ambigu dans `SPEC.md` → demander, ne pas combler avec un défaut Material générique.
- Une règle métier qui semble incohérente une fois codée → signaler avant de la modifier silencieusement.
- Toute intégration externe non couverte par `SPEC.md` (nouvelle API, nouveau capteur) → demander avant d'ajouter une dépendance.
