# IMPLEMENTATION_PLAN.md — Trame d'implémentation de Forge

Ce document découpe le MVP décrit dans `SPEC.md` §9 en séquence de construction technique. Il ne redéfinit ni les règles métier (`CLAUDE.md`), ni l'apparence (`DESIGN.md`), ni les fonctionnalités (`SPEC.md`) — il répond uniquement à la question *dans quel ordre construire, avec quelles définitions de "fini", et quels tests à chaque étape*.

À relire avant de démarrer une nouvelle phase, pas seulement une fois.

---

## 0. Principe de séquencement — ne pas confondre ordre technique et découpage produit

`SPEC.md` §9 et `CLAUDE.md` sont explicites : le MVP est **un seul livrable cohérent** (poids/séances/repas + Wear OS + Calendar + Alexa), pas un cœur minimal suivi d'ajouts. Les phases ci-dessous ne contredisent pas ce principe — elles décrivent l'ordre dans lequel le *code* doit exister pour que ce livrable compile et se teste (on ne peut pas écrire l'écran de séance avant que `WorkoutSession` existe dans `core-domain`), pas un ordre de mise en production partielle.

**Critère d'acceptation du MVP** : aucune phase n'est "terminée pour de bon" tant que le test d'intégration de la Phase 7 (§7) ne passe pas — celui qui vérifie qu'un passage en `CRITIQUE` déclenche notification Android **et** webhook Alexa dans le même événement.

---

## 1. Vue d'ensemble des phases

| Phase | Contenu | Modules touchés | Sortie testable |
|---|---|---|---|
| 0 | Bootstrap projet | racine, tous | Build Gradle vert, CI qui tourne |
| 1 | Règles métier pures | `core-domain` | Tests JVM unitaires, aucune dépendance Android |
| 2 | Persistance + Health Connect | `core-data` | DAO Room testés, lecture poids Health Connect |
| 3 | Clients IA (texte) | `core-ai` | Job hebdo Gemini → `WeeklyAnalysis` persistée |
| 4 | Sync écosystème | `core-sync` | Événements Calendar créés, webhook Alexa déclenché |
| 5 | App Android | `app` | Écrans DESIGN.md, notifications, WorkManager |
| 6 | App Wear OS | `wear` | Tile, complication, écran de séance |
| 7 | Validation croisée | tous | Scénario CRITIQUE bout-en-bout, checklist DESIGN.md §11 |
| 8 (Phase 2, hors MVP) | Voix Deepgram | `core-ai`, `app`, `wear` | TTS auto, STT log de séance |

---

## 2. Phase 0 — Bootstrap projet

**Objectif** : un projet Gradle multi-module qui compile, avec la structure de `SPEC.md` §3, avant d'écrire la moindre règle métier.

- [x] Projet Gradle Kotlin DSL (`settings.gradle.kts` déclare `app`, `wear`, `core-domain`, `core-data`, `core-ai`, `core-sync`).
- [x] Version catalog (`gradle/libs.versions.toml`) : Kotlin, Compose, Compose for Wear OS, Hilt, Room, WorkManager, Coroutines, Retrofit/Ktor.
- [x] `core-domain` configuré comme module **Kotlin JVM pur** (pas de plugin Android) — ça rend la règle de `CLAUDE.md` ("aucun import `android.*`") impossible à violer par accident plutôt que de compter sur la discipline seule.
- [x] `.gitignore` incluant `local.properties`, `*.keystore`, `/build`, `/.gradle`, `local.properties.enc` le cas échéant.

  **Corrigé** : `SPEC.md` §8 et `CLAUDE.md` affirmaient que `local.properties` était "déjà dans le `.gitignore`" alors qu'aucun `.gitignore` n'existait. Créé en premier, avant qu'une clé ne puisse être écrite sur disque dans ce repo.
- [x] `local.properties.example` (committé, sans valeurs) documentant les clés attendues : `GEMINI_API_KEY`, `DEEPGRAM_API_KEY` (placeholder, non utilisé avant Phase 2 produit), `GOOGLE_CALENDAR_OAUTH_CLIENT_ID`.
- [x] CI minimale (voir `DEPLOYMENT.md` §7) : build + tests unitaires `core-domain` sur chaque push.

**Définition de fini** : `./gradlew build` passe avec les 6 modules vides (juste un `AndroidManifest`/objet marqueur), CI verte.

---

## 3. Phase 1 — `core-domain` : règles métier pures

Aucune UI, aucune persistance réelle avant que ces règles existent et soient testées — c'est la fondation dont dépendent `core-data`, `app` et `wear`.

- [x] Entités de `SPEC.md` §4 (`WeightEntry`, `PlanTarget`, `WorkoutSession`, `ExerciseLog`, `SetLog`, `MealCheck`, `AdherenceState`, `WeeklyAnalysis`, `EscalationLevel`) — types purs, immuables.
- [x] Interfaces de repository (ports) définies ici, implémentées en `core-data` (ex. `WeightRepository`, `WorkoutRepository`) — `core-domain` déclare le contrat, ne l'implémente pas.
- [x] Moyenne mobile 7 jours sur le poids — fonction pure, testée avec séries lacunaires (jours sans pesée) et bruitées.
- [x] Règle d'ajustement calorique : +250 kcal si gain < 0,2 kg/sem. deux semaines de suite, −200 kcal si gain > 0,7 kg/sem. deux semaines de suite, borné à ±300 kcal (`CLAUDE.md` — valeurs non négociables sans demande explicite).
- [x] Machine à états d'escalade `À_JOUR → RETARD_1 → RETARD_2 → CRITIQUE`, retour à `À_JOUR` sur toute action valide — testée transition par transition, y compris les cas "action valide pendant `CRITIQUE`" et "deux manquements le même jour ne sautent pas d'état".
- [x] Règle de double progression : charge à monter seulement quand **toutes** les séries atteignent le haut de la fourchette de reps avec technique propre.

  **Décision toujours ouverte** : le domaine porte l'information (`SetLog.cleanTechnique`, **sans valeur par défaut** — un défaut à `true` trancherait en douce en faveur de "propre sauf mention contraire"). Reste à décider comment l'écran de séance la capture (case à cocher par série ? confirmation implicite ?) : à trancher avant la phase 5/6, pas pendant.
- [x] Détection de stagnation (aucune progression sur un exercice depuis 2 semaines).
- [x] Chaque règle ci-dessus arrive avec son test JVM pur **avant** d'être branchée à un repository ou une UI (règle de `CLAUDE.md`).

**Définition de fini** : 100 % des règles ci-dessus ont un test JVM vert, exécutable sans émulateur ni Robolectric.

---

## 4. Phase 2 — `core-data` : persistance + Health Connect

- [x] Schéma Room miroir des entités `core-domain`, avec mappers explicites (entité Room ≠ modèle domaine, pour ne pas faire fuiter Room dans `core-domain`).
- [x] Implémentation des repositories définis en Phase 1.
- [x] Import du plan structuré au premier lancement (`SPEC.md` §5.1) : asset JSON bundlé (nutrition + musculation), parsé et inséré en Room au premier run — pas de saisie manuelle du plan par l'utilisateur.
- [x] Intégration Health Connect en **lecture seule** pour le poids : lecture déclenchée par le rappel programmé (WorkManager), jamais de polling continu (`SPEC.md` §8 — contrainte batterie).
- [x] Tests DAO Room (base en mémoire) + test de la logique d'import du plan (idempotence : ne pas dupliquer si l'app est relancée).

**Définition de fini** : un poids lu depuis Health Connect (ou saisi manuellement) traverse repository → Room → relecture, et le plan pré-chargé est visible en base après premier lancement, sans doublon sur relance.

**Réserve** : le `plan.json` livré est un **contenu d'exemple en version 0**, le programme réel n'ayant pas encore été fourni. Le mécanisme est complet et testé ; le remplacer par le vrai programme sera une modification de données (version 1 du fichier), pas de code. Voir §11.

---

## 5. Phase 3 — `core-ai` : client Gemini (texte, MVP) + scaffolding Deepgram

- [x] Client HTTP (Retrofit ou Ktor) vers l'API Gemini, function calling activé, prompt structuré de `SPEC.md` §6.1.
- [x] Validation stricte du JSON retourné côté app : `kcal_adjustment` rejeté/tronqué s'il sort de [-300, +300] — Gemini ne peut jamais imposer un ajustement hors des règles de `core-domain` (garde-fou `SPEC.md` §6.3).
- [x] Job `WorkManager` du dimanche : compile la semaine (poids, séances, adhérence) depuis `core-data`, appelle Gemini, persiste `WeeklyAnalysis` — jamais d'appel bloquant l'UI.
- [x] `audio_script` stocké comme champ texte dans `WeeklyAnalysis` mais **non envoyé à Deepgram en MVP** — le pipeline TTS est Phase 2/8. Ne pas câbler Deepgram ici pour éviter du code mort en attendant la phase vocale.
- [x] Rejeu différé : si le job échoue par absence réseau, WorkManager le retente à la reconnexion (contrainte réseau sur le `WorkRequest`).

**Définition de fini** : un `WeeklyAnalysis` complet et borné est produit chaque dimanche (ou à la demande en debug), consultable en base, sans appel réseau synchrone depuis l'UI.

**Deux écarts assumés, à confirmer** :
- *Sortie structurée plutôt que function calling* : `SPEC.md` §3 évoque le function calling, mais §6.1 demande un JSON conforme à un contrat, pas un outil de l'app invoqué par le modèle. Le `responseSchema` de Gemini est le mécanisme fait pour ça, et le schéma reprend littéralement le contrat de §6.1.
- *Garde-fou plus strict que la lettre de §6.3* : borner à ±300 ne suffit pas, un modèle qui renvoie 150 kcal reste dans les bornes tout en ayant inventé une règle. L'ajustement enregistré est donc toujours celui des règles du plan ; celui du modèle est borné, comparé, et le désaccord journalisé. Le prompt annonce le chiffre calculé pour que le résumé ne le contredise pas.

**Reporté en phase 5** : la planification du job (WorkManager) et la date de début de programme saisie à l'onboarding — l'index de semaine se déduit pour l'instant du premier import du plan.

---

## 6. Phase 4 — `core-sync` : Google Calendar + relais Alexa

Livrée en deux temps : l'agenda d'abord, la voix une fois son fournisseur tranché.

**Temps 1**

- [x] Écriture des événements séances/repas dans l'agenda à l'onboarding, idempotente (marquage `CUSTOM_APP_PACKAGE`/`CUSTOM_APP_URI` : relancer l'onboarding constate les événements déjà posés au lieu d'en créer d'autres).
- [x] `core-sync` réagit aux événements de la machine d'escalade de `core-domain` sans dupliquer la logique d'état : c'est `EscalationLevel.requiresVoiceRelay` qui décide, ici on ne fait qu'obéir.
- [x] Interface `VoiceRelay` et logique de relais, testées ; rappels programmés relayés **en plus** de la notification Android, pas à sa place.
- [x] Messages d'escalade partagés avec les autres canaux (`EscalationMessage` dans `core-domain`), avec un test qui vérifie que le texte vocal est exactement celui de la notification — `DESIGN.md` §9 exige le même vocabulaire partout.

**Écart assumé** : l'agenda passe par `CalendarContract` et non par l'API Google Calendar en OAuth prévue par `SPEC.md` §3. Comparatif, justification et condition de retour en arrière dans `DEPLOYMENT.md` §9. Isolé derrière `CalendarSync`, donc réversible en une classe.

**Temps 2**

- [x] Client webhook du relais vocal : **Notify My Alexa** (`DEPLOYMENT.md` §11). Le code d'accès se saisit à l'onboarding et l'écran envoie une annonce de test dans la foulée — c'est le seul canal dont l'écran ne peut pas constater l'arrivée autrement.

**Définition de fini** : atteinte. L'agenda pose les événements du programme, l'enceinte reçoit les passages en `RETARD_2` et `CRITIQUE`. Reste hors périmètre, faute d'une décision de confort : relayer aussi les rappels de repas et de séance à heure fixe (`DEPLOYMENT.md` §11).

---

## 7. Phase 5 — `app` (Android/Compose)

- [x] ViewModels par écran, qui exposent des `State`/`Flow` — aucune règle métier dans les composables (`CLAUDE.md`).
- [x] Écrans conformes aux wireframes `DESIGN.md` §7 : Accueil, Pesée, Checklist repas, Analyse hebdo, Onboarding écosystème.
- [x] Écran de séance active : exercices du jour, saisie charge × reps série par série, case « technique propre » par série, charge proposée par la double progression et palier gagné pour la prochaine séance. `DESIGN.md` ne dessine que la version montre (§7.3) ; l'écran téléphone en reprend la hiérarchie dans la mise en page réglée du reste de l'app.
- [x] Canaux de notification distincts pour `RETARD_1` (standard), `RETARD_2` (insistante + vibration), `CRITIQUE`. **Écart assumé** : pas de `fullScreenIntent`. Depuis Android 14, `USE_FULL_SCREEN_INTENT` n'est accordée d'office qu'aux apps d'appel et de réveil ; une app de coaching serait rétrogradée en notification haute priorité de toute façon. Le canal `CRITIQUE` porte donc importance maximale, vibration et contournement du mode Ne pas déranger — c'est la lecture forcée réellement atteignable.
- [x] WorkManager : rappel de pesée quotidien, vérification d'escalade, déclenchement du job hebdo (Phase 3).
- [x] Checklist d'auto-critique `DESIGN.md` §11 passée sur chaque écran livré (résultats en §9).

**Définition de fini** : atteinte. Tous les écrans téléphone existent, alimentés par de vraies données `core-data`/`core-domain`, et passent la checklist §11.

---

## 8. Phase 6 — `wear` (Wear OS/Compose for Wear)

- [x] Tile de pesée rapide (saisie manuelle 1 tap) — `DESIGN.md` §7.3.
- [x] Complication affichant l'écart au poids cible, tap → ouvre la pesée du jour.
- [x] Écran de séance active sur la montre (`DESIGN.md` §7.3) : exercices du jour, réglage reps × charge au palier du râtelier, case « technique propre » par série, minuteur de repos de 90 s avec vibration. Le transport passe par la Data Layer, décrit dans `DEPLOYMENT.md` §12 : le téléphone publie la séance résolue (charge proposée et séries déjà loguées comprises), la montre renvoie chaque série avec sa position — ce qui rend un renvoi depuis la file d'attente inoffensif et donne la règle de fusion quand les deux écrans ont servi.
- [x] `wear` dépend de `core-domain` seul, pas de `app`. **Correction du plan initial** : la ligne d'origine prévoyait aussi `core-data`. C'était une erreur — une base Room sur la montre serait une seconde source de vérité pour le poids, et il faudrait ensuite réconcilier deux historiques. La montre saisit, met en file d'attente (`PendingWeights`) et transmet au téléphone par la Data Layer ; le téléphone reste le seul à écrire en base. `core-domain` suffit pour partager les règles, qui sont pures.
- [x] Cibles tactiles ≥ 48dp partout (`DESIGN.md` §10).

**Définition de fini** : atteinte. Un poids saisi sur la montre part vers le téléphone, qui l'écrit dans l'historique Room, et l'écart revient sur le cadran ; une série saisie sur la montre suit le même chemin et alimente la même règle de double progression que le téléphone, sans logique dupliquée — `core-domain` est le seul endroit où elle existe.

---

## 9. Phase 7 — Validation croisée (porte de sortie du MVP)

Cette phase n'ajoute pas de fonctionnalité — elle vérifie que l'ensemble fonctionne comme **un seul système**, conformément à `SPEC.md` §10.

- [x] **Test d'intégration obligatoire** : `EscalationConvergenceTest` simule trois jours sans action et vérifie dans le même scénario que la notification `CRITIQUE` est postée **et** que le relais vocal reçoit le **même message**, dans le même événement. La logique a été extraite du worker (`EscalationRunner`) précisément pour que cette exigence se teste sans émulateur ni WorkManager.
- [x] Test offline-first : le job hebdomadaire porte la contrainte réseau qui le fait différer et rejouer à la reconnexion (`WeeklyAnalysisWorkerTest`) ; la saisie de poids, de repas et de séance passe par Room, sans réseau.
- [x] Vérification batterie : aucun `startForeground` ni permission de service au premier plan dans le dépôt ; Health Connect n'a qu'un seul appelant, le rappel de pesée programmé ; aucun appel réseau direct depuis `app`, tout passe par WorkManager.
- [x] Revue de la checklist `DESIGN.md` §11 sur les écrans livrés.

**Ce que la revue §11 a trouvé, et corrigé** :
- *Écran de pesée* : le message d'erreur de saisie était en brique, couleur que §2 réserve aux niveaux de retard. Passé en os — une saisie invalide n'est pas un état du programme, et le bouton désactivé porte déjà le signal. **La palette de `DESIGN.md` ne définit aucune couleur d'erreur** : c'est un manque à combler si un jour une erreur doit crier.
- *Écran d'analyse* : aucun élément ne dominait, contrairement à l'item 1 de la checklist. La moyenne 7 jours est devenue le chiffre dominant, le texte de Gemini la commente au lieu de la remplacer.
- *Contradiction interne à `DESIGN.md`* : les wireframes §7 emploient le point médian comme séparateur (« Forge · Semaine 4/8 ») et la flèche en fin de libellé (« Séance : Haut du corps → »), que la checklist §11 interdit explicitement. La checklist a été suivie, étant présentée comme la porte de sortie avant livraison d'un écran. **À trancher.**
- *Mouvement* : §6 autorise une animation confirmant une action (coche d'un repas). Elle n'a pas été ajoutée — non par oubli, mais parce qu'une animation invérifiable ici vaut moins qu'une absence assumée.

**Dette d'outillage constatée en CI** : lint plante en analysant les sources de test de `app` — « Unexpected failure during lint analysis of `EscalationConvergenceTest.kt` (this is a bug in lint or one of the libraries it depends on) », résolution `RAW_FIR → SUPER_TYPES`. Le code compile et les tests passent ; c'est l'outil qui tombe. `app` pose donc `lint { ignoreTestSources = true }`, ce qui sort les sources de test du périmètre de lint **et rien d'autre** : lint garde toute sa sévérité sur ce qui est livré. À rouvrir à la prochaine montée d'AGP.

**Définition de fini du MVP** : le test de convergence passe et tous les canaux sont branchés. Ce qui reste ne dépend plus du code : le contenu réel du programme (`plan.json` est un exemple en version 0), les clés API à fournir, et la vérification sur appareils réels — aucun test automatisé ne peut constater qu'une enceinte a parlé ni qu'une montre a vibré.

---

## 10. Phase 8 — Phase 2 produit (hors scope MVP, ne pas anticiper)

À ne pas commencer avant que la Phase 7 soit validée (`CLAUDE.md`) :
- Vocalisation Deepgram Aura : `audio_script` (déjà produit en Phase 3) → fichier audio → lecture automatique en notification.
- Saisie vocale Deepgram Nova (STT) : parsing d'un log de séance dicté à la voix.

---

## 11. Décisions à trancher avant/pendant l'implémentation (ne pas combler par défaut)

- **Contenu réel du programme** (Phase 2, §4) : `SPEC.md` §5.1 suppose le plan nutrition/musculation pré-chargé, mais il n'est nulle part dans le repo. `core-data/src/main/assets/plan.json` porte un contenu d'exemple en version 0 pour que les écrans aient de quoi s'afficher. Fournir le vrai programme (en version 1) est la seule chose qui manque pour que la phase 2 soit réellement utile.
- ~~Modalité de saisie de la "technique propre" pour la double progression~~ — **tranché** : une case par série, cochée à la main, décochée par défaut. Un oubli coûte alors un palier non accordé ; l'inverse accorderait une montée de charge que personne n'a jugée méritée. La case reste modifiable après coup sur chaque série loguée.
- **Nombre de prises quotidiennes** : `SPEC.md` §5.3 et `DESIGN.md` §7.1 en comptent six ("Repas restants : 2/6"), mais la checklist dessinée en `DESIGN.md` §7.2 n'en liste que cinq. `MealSlot` suit `SPEC.md` (six prises, la sixième nommée `COLLATION_3` faute de mieux) ; le libellé réel viendra du plan importé en phase 2, mais l'écart entre les deux documents est à trancher.
- Mécanisme de notification `CRITIQUE` : `fullScreenIntent` vs notification haute priorité classique (Phase 5, §7).
- ~~Fournisseur du relais webhook Alexa~~ — **tranché** : Notify My Alexa (`DEPLOYMENT.md` §11).
- Faut-il relayer par la voix les rappels de repas et de séance à heure fixe, en plus des passages en retard ? `SPEC.md` §5.8 le prévoit ; c'est une question de confort — être annoncé à chaque créneau du programme, ou seulement quand ça dérape.

Les points restants sont volontairement laissés ouverts ici plutôt que tranchés par une supposition, conformément à `CLAUDE.md` §"Quand demander plutôt que supposer".
