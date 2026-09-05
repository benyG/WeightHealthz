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

- [ ] OAuth Google Calendar (flux natif Android, pas de client secret côté app) — création d'événements récurrents séances/repas à l'onboarding, écriture idempotente (ne pas recréer les événements si l'onboarding est relancé).
- [ ] Client webhook Alexa (relais type Notify-My-Alexa/IFTTT, pas de skill Alexa custom — `SPEC.md` §5.8) — voir `DEPLOYMENT.md` §11 pour le choix de fournisseur, à trancher avec l'utilisateur avant implémentation.
- [ ] `core-sync` s'abonne aux événements de la machine d'escalade de `core-domain` (ex. `Flow<EscalationEvent>`) plutôt que de dupliquer la logique d'état — c'est `core-domain` qui décide des transitions, `core-sync` ne fait que réagir à `RETARD_2`/`CRITIQUE`.
- [ ] Rappels programmés (repas/séance à heure fixe) relayés vers Alexa en plus de la notification Android, pas en remplacement.

**Définition de fini** : un événement de test `RETARD_2`/`CRITIQUE` émis par `core-domain` déclenche un appel webhook observable (log/mock en test), et un onboarding de test crée les événements Calendar attendus sans doublon sur ré-exécution.

---

## 7. Phase 5 — `app` (Android/Compose)

- [ ] ViewModels par écran, qui exposent des `State`/`Flow` — aucune règle métier dans les composables (`CLAUDE.md`).
- [ ] Écrans conformes aux wireframes `DESIGN.md` §7 : Accueil, Pesée, Checklist repas, Séance active, Analyse hebdo, Onboarding écosystème.
- [ ] Canaux de notification distincts pour `RETARD_1` (standard), `RETARD_2` (insistante + vibration), `CRITIQUE` (lecture forcée au prochain déverrouillage — nécessite d'évaluer `fullScreenIntent` vs notification haute priorité, à valider avant implémentation si le choix a un impact UX notable).
- [ ] WorkManager : rappel de pesée quotidien, vérification d'escalade, déclenchement du job hebdo (Phase 3).
- [ ] Checklist d'auto-critique `DESIGN.md` §11 passée avant de considérer un écran terminé — pas seulement en fin de projet.

**Définition de fini** : chaque écran de `DESIGN.md` §7.1–§7.2, §7.5–§7.6 existe, alimenté par de vraies données `core-data`/`core-domain`, et passe la checklist §11.

---

## 8. Phase 6 — `wear` (Wear OS/Compose for Wear)

- [ ] Tile de pesée rapide (saisie manuelle 1 tap) — `DESIGN.md` §7.3/§7.4.
- [ ] Complication affichant l'écart au poids cible, tap → ouvre la pesée du jour.
- [ ] Écran de séance active : liste des exercices, saisie charge × reps par set, minuteur de repos automatique entre séries.
- [ ] `wear` dépend de `core-domain`/`core-data` directement (comme `app`), pas de `app` lui-même — les deux apps sont des consommateurs parallèles du même cœur, pas l'un un plugin de l'autre.
- [ ] Cibles tactiles ≥ 48dp partout (`DESIGN.md` §10).

**Définition de fini** : la Tile écrit un poids qui apparaît dans l'historique Room, et une série saisie sur la montre déclenche la règle de double progression identique à celle du téléphone (même code `core-domain`, pas de logique dupliquée).

---

## 9. Phase 7 — Validation croisée (porte de sortie du MVP)

Cette phase n'ajoute pas de fonctionnalité — elle vérifie que l'ensemble fonctionne comme **un seul système**, conformément à `SPEC.md` §10.

- [ ] **Test d'intégration obligatoire** : simuler un passage en `CRITIQUE` (3 jours sans action) et vérifier dans le même scénario que (a) la notification Android critique est postée **et** (b) le webhook Alexa est appelé — dans le même événement, pas dans deux itérations séparées.
- [ ] Test offline-first : logging poids/repas/séances en mode avion fonctionne ; les appels Gemini/Calendar/Alexa se rejouent à la reconnexion.
- [ ] Vérification batterie : aucun service foreground permanent, aucune lecture Health Connect hors rappel programmé.
- [ ] Revue de la checklist `DESIGN.md` §11 sur l'ensemble des écrans livrés (téléphone + montre), pas écran par écran isolément.

**Définition de fini du MVP** : ce test d'intégration passe, et les trois canaux (app, Calendar, Alexa) sont actifs simultanément dès la première version installée — pas de "on ajoutera Alexa plus tard".

---

## 10. Phase 8 — Phase 2 produit (hors scope MVP, ne pas anticiper)

À ne pas commencer avant que la Phase 7 soit validée (`CLAUDE.md`) :
- Vocalisation Deepgram Aura : `audio_script` (déjà produit en Phase 3) → fichier audio → lecture automatique en notification.
- Saisie vocale Deepgram Nova (STT) : parsing d'un log de séance dicté à la voix.

---

## 11. Décisions à trancher avant/pendant l'implémentation (ne pas combler par défaut)

- **Contenu réel du programme** (Phase 2, §4) : `SPEC.md` §5.1 suppose le plan nutrition/musculation pré-chargé, mais il n'est nulle part dans le repo. `core-data/src/main/assets/plan.json` porte un contenu d'exemple en version 0 pour que les écrans aient de quoi s'afficher. Fournir le vrai programme (en version 1) est la seule chose qui manque pour que la phase 2 soit réellement utile.
- Modalité de saisie de la "technique propre" pour la double progression (Phase 1/5/6, §3).
- **Nombre de prises quotidiennes** : `SPEC.md` §5.3 et `DESIGN.md` §7.1 en comptent six ("Repas restants : 2/6"), mais la checklist dessinée en `DESIGN.md` §7.2 n'en liste que cinq. `MealSlot` suit `SPEC.md` (six prises, la sixième nommée `COLLATION_3` faute de mieux) ; le libellé réel viendra du plan importé en phase 2, mais l'écart entre les deux documents est à trancher.
- Mécanisme de notification `CRITIQUE` : `fullScreenIntent` vs notification haute priorité classique (Phase 5, §7).
- Fournisseur du relais webhook Alexa : Notify-My-Alexa vs IFTTT Applet (Phase 4, §6 — détaillé dans `DEPLOYMENT.md` §11).

Ces trois points sont volontairement laissés ouverts ici plutôt que tranchés par une supposition, conformément à `CLAUDE.md` §"Quand demander plutôt que supposer".
