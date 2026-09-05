# Spécification fonctionnelle & design — App de coaching "prise de poids"

**Plateformes** : Android (téléphone) \+ Wear OS (Pixel Watch 2\) **IA** : Gemini (raisonnement/analyse), Deepgram (voix : TTS \+ STT) **Intégrations MVP** : Wear OS, Google Calendar, Alexa (relais vocal) **Usage** : application personnelle mono-utilisateur (toi), pas de multi-tenant

---

## 1\. Vision produit

Une app qui **exécute** le plan de prise de poids saine (nutrition \+ musculation) déjà défini, au lieu de se contenter de l'afficher. Le rôle du produit n'est pas "encore un tracker" mais un **coach qui compare en continu l'objectif à la réalité** (poids mesuré, séances loguées, repas cochés) et qui **agit** : il ajuste les calories recommandées, escalade les rappels quand tu dérailles, et te parle plutôt que de t'afficher un graphique de plus.

**Nom de travail** : `Forge` (à changer librement — évoque l'idée de construction progressive, sert de fil rouge pour le design).

### Ce que le produit n'est pas

- Pas un clone MyFitnessPal/Strong avec juste un thème différent.  
- Pas un dashboard passif : chaque écran doit permettre une action ou révéler un écart.  
- Pas dépendant du cloud pour fonctionner au quotidien (offline-first).

---

## 2\. Objectifs mesurables du produit

| Métrique | Cible |
| :---- | :---- |
| Jours avec pesée loguée / 7 | ≥ 6 |
| Séances complétées / séances planifiées | ≥ 90 % sur 8 semaines |
| Délai moyen entre dérapage et notification corrective | \< 24 h |
| Écart entre courbe de poids réelle et courbe cible en semaine 8 | ± 0,3 kg |
| Progression de charge sur les 6 exercices clés | mesurable chaque semaine (aucune stagnation \> 2 semaines sans alerte) |

---

## 3\. Architecture technique

Forge/

├── app/                  \# Module Android (Kotlin, Jetpack Compose)

├── wear/                 \# Module Wear OS (Compose for Wear, Tiles, Complications)

├── core-domain/          \# Logique métier pure (règles d'ajustement, progression, escalade)

├── core-data/            \# Room DB, repositories, Health Connect

├── core-ai/              \# Clients Gemini \+ Deepgram, pipeline voix

└── core-sync/            \# Google Calendar API, webhook Alexa (MVP)

**Principe directeur** : `core-domain` ne connaît ni Android ni les API externes — testable en JVM pur. C'est là que vivent les règles issues du plan (double progression, seuils d'ajustement calorique, logique d'escalade).

### Stack recommandée

- **UI** : Jetpack Compose \+ Compose for Wear OS (Material 3 Expressive)  
- **Persistance** : Room (source de vérité locale), **Health Connect** comme pont pour le poids/activité (balance connectée → Health Connect → Room)  
- **Async/scheduling** : WorkManager pour les rappels et le job d'analyse hebdo, Coroutines/Flow partout  
- **DI** : Hilt  
- **Réseau IA** : Ktor client ou Retrofit vers l'API Gemini (function calling activé) et l'API Deepgram (Aura pour TTS, Nova pour STT)  
- **Sync écosystème (MVP)** : Google Calendar API (OAuth) \+ relais webhook Alexa, pas de backend custom nécessaire  
- **Confidentialité** : toutes les données de poids/santé restent sur l'appareil par défaut ; aucune donnée envoyée à Gemini sans agrégation (on envoie des séries de chiffres et des labels d'exercices, jamais un export brut identifiant)

---

## 4\. Modèle de données (entités clés)

data class WeightEntry(val date: LocalDate, val kg: Float, val source: Source) // MANUAL | HEALTH\_CONNECT

data class PlanTarget(val weekIndex: Int, val targetDeltaKgMin: Float, val targetDeltaKgMax: Float)

data class WorkoutSession(val date: LocalDate, val dayTemplate: DayTemplate, val exercises: List\<ExerciseLog\>)

data class ExerciseLog(val name: String, val sets: List\<SetLog\>)          // SetLog(reps, weightKg)

data class MealCheck(val date: LocalDate, val slot: MealSlot, val done: Boolean) // slot: PETIT\_DEJ, COLLATION1...

data class AdherenceState(val streakDays: Int, val escalationLevel: EscalationLevel) // OK, RETARD\_1, RETARD\_2, CRITIQUE

data class WeeklyAnalysis(val weekIndex: Int, val summaryText: String, val audioUrl: String?, val recommendedAdjustmentKcal: Int)

---

## 5\. Fonctionnalités détaillées

### 5.1 Onboarding

- Import du plan structuré (le contenu nutrition/sport déjà défini est pré-chargé en base au premier lancement — pas de saisie manuelle du plan).  
- Saisie du poids de départ, connexion Health Connect, connexion Google Calendar (OAuth), configuration du webhook Alexa.

### 5.2 Suivi du poids

- Rappel quotidien à heure fixe (paramétrable, ex. 7h) : "Pesée du matin".  
- Lecture automatique depuis Health Connect si balance connectée ; sinon saisie manuelle en 1 tap depuis une **Tile Wear OS**.  
- Calcul automatique de la moyenne mobile 7 jours (évite le bruit quotidien — cf. plan).  
- Comparaison immédiate à la fourchette cible de la semaine (`PlanTarget`).

### 5.3 Suivi nutrition

- Checklist des 6 prises quotidiennes, cochables depuis le téléphone ou la montre.  
- Vue "batch cooking du dimanche" avec liste de courses générée depuis le plan.  
- Si 2 prises manquées dans la journée → déclenchement du moteur d'escalade (5.5).

### 5.4 Suivi entraînement

- Écran de séance active (téléphone \+ montre) : liste des exercices du jour, saisie rapide charge × reps par set.  
- **Règle de double progression** codée en dur dans `core-domain` : si toutes les séries atteignent le haut de la fourchette de reps → suggestion automatique d'augmenter la charge au prochain palier disponible sur tes haltères.  
- Détection de stagnation (aucune progression sur un exercice depuis 2 semaines) → remontée dans l'analyse hebdo.  
- Sur la montre : complication affichant l'exercice en cours \+ minuteur de repos automatique entre séries.

### 5.5 Moteur d'escalade des rappels

Machine à états simple, testée unitairement dans `core-domain` :

À\_JOUR ──(pesée/repas/séance manqués)──▶ RETARD\_1 (notification standard)

RETARD\_1 ──(2e jour sans action)──▶ RETARD\_2 (notification insistante \+ vibration montre \+ proposition d'appel vocal Gemini)

RETARD\_2 ──(3e jour)──▶ CRITIQUE (notification \+ lecture vocale forcée au prochain déverrouillage du téléphone \+ option webhook Alexa)

Toute action valide ──▶ retour à À\_JOUR

### 5.6 Analyse hebdomadaire (Gemini \+ Deepgram)

- Chaque dimanche, un `WorkManager` job compile la semaine (poids, séances, adhérence) et appelle Gemini avec un prompt structuré (voir §6).  
- Gemini renvoie : un texte d'analyse, une recommandation d'ajustement calorique chiffrée (suit les règles du plan : \+250 kcal si \<0,2 kg/sem., \-200 kcal si \>0,7 kg/sem.), et un focus musculation (quel exercice progresse, lequel stagne).  
- Ce texte est envoyé à **Deepgram Aura (TTS)** → fichier audio joué automatiquement (notification avec lecture vocale, pas juste un texte à lire).

### 5.7 Rappels vocaux

- Les rappels critiques (5.5) et les rappels de séance peuvent être **vocalisés** via Deepgram Aura plutôt que simplement notifiés — surtout utile sur la montre où lire est moins pratique qu'écouter.  
- Option **saisie vocale** (Deepgram Nova STT) : "Terminé ma séance, développé couché 4x10 à 20kg" → parsing → log automatique, utile mains occupées en salle.

### 5.8 Intégrations écosystème (MVP)

Ces trois intégrations font partie du MVP : le produit n'a de sens que si l'écosystème autour de toi relaie activement le programme, pas seulement l'app seule.

- **Wear OS (Pixel Watch 2\)** : déjà couvert en §5.2/5.4 (Tile de pesée, complication d'écart, écran de séance, minuteur de repos) — c'est un canal de premier ordre, pas un module secondaire ajouté après coup.  
- **Google Calendar** : chaque séance et chaque créneau repas du plan est créé comme événement récurrent dès l'onboarding (écriture, pas juste lecture), avec rappel natif Calendar en plus des notifications Forge. Objectif : que le programme existe aussi dans l'agenda que tu regardes déjà tous les jours, pas seulement dans l'app.  
- **Alexa** : un webhook (skill "notify" type IFTTT/Notify-My-Alexa) relaie en voix, via l'enceinte à la maison, (a) les rappels de repas/séance à heure fixe et (b) tout passage en `RETARD_2`/`CRITIQUE` de la machine d'escalade (§5.5). Pas de skill Alexa custom développée en interne — le relais webhook suffit et évite le coût de certification Alexa Skills Kit pour un usage mono-utilisateur.

---

## 6\. Spécification IA

### 6.1 Prompt Gemini — analyse hebdomadaire (squelette)

Tu es un coach en prise de masse. Voici les données brutes de la semaine {n} :

\- Poids moyen 7j : {avg\_kg} (cible : {target\_min}–{target\_max})

\- Historique 8 semaines : {series}

\- Séances complétées : {done}/{planned}

\- Charges par exercice (kg × reps, cette semaine vs semaine précédente) : {exercise\_deltas}

Règles d'ajustement à respecter strictement : \[règles du plan, cf. §7 du plan original\]

Réponds en JSON : { "summary": string (3 phrases max, ton direct, pas de flatterie),

"kcal\_adjustment": int, "focus\_exercise": string, "audio\_script": string (≤60 secondes à l'oral) }

- `audio_script` est le seul champ envoyé à Deepgram — séparé du `summary` écrit pour garder un ton oral naturel (phrases courtes, pas de listes à puces à l'oral).

### 6.2 Pipeline voix

Données semaine → Gemini (JSON) → audio\_script → Deepgram Aura → fichier .mp3 local

                                                              → notification Android avec lecture auto

                                                              → (option) push vers webhook Alexa

### 6.3 Garde-fous

- Gemini n'a jamais le droit d'inventer une règle d'ajustement hors de celles codées dans `core-domain` — le prompt les rappelle explicitement et le JSON de sortie est validé côté app avant application (`kcal_adjustment` borné à \[-300, \+300\]).  
- Pas d'appel Gemini en temps réel bloquant l'UI : toujours en tâche de fond (WorkManager), résultat consultable ensuite.

---

## 7\. Spécification design — anti "AI slop"

### Principe

Le sujet est un carnet d'entraînement de précision, pas un SaaS grand public. Le design doit évoquer un **instrument de mesure calibré** (salle de muscu, atelier, carnet de suivi) plutôt qu'un dashboard fintech générique. On évite explicitement : fond crème \+ accent terracotta, fond quasi-noir \+ accent néon, cartes SaaS toutes identiques à coins arrondis uniformes, labels en MAJUSCULES espacées, préfixes numérotés décoratifs (01/02/03) là où le contenu n'est pas réellement une séquence.

### Palette (à utiliser telle quelle, ce sont des choix, pas des suggestions à réinterpréter)

| Rôle | Nom | Hex |
| :---- | :---- | :---- |
| Fond | Graphite chaud | `#1E1B17` |
| Surface | Charbon | `#28241D` |
| Texte principal | Os | `#EDE7D9` |
| Texte secondaire | Sable éteint | `#A79E8C` |
| Accent primaire (progression positive) | Laiton | `#C39A3C` |
| État "on track" | Mousse | `#6E8F5C` |
| État "retard/critique" | Brique | `#A8462D` |

Pas de dégradé décoratif. Le laiton et le brique ne servent **que** pour des états réels (progression, alerte) — jamais en décoration pure.

### Typographie

- **Titres/nombres clés (poids, charges)** : une slab serif à forte présence type *Zilla Slab* ou *Roboto Slab* en Medium/Bold — l'aspect "gravé" convient à des chiffres mesurés.  
- **Corps de texte/UI** : une grotesque neutre type *Inter* ou *IBM Plex Sans*, Regular/Medium seulement (pas de Light).  
- Pas de troisième famille. Pas d'italique/gras isolé sur un seul mot dans un titre.

### Layout

- Grille façon **carnet réglé** : fines règles horizontales (1px, couleur sable éteint à 20 % d'opacité) séparant les blocs, plutôt que des cartes à ombre portée.  
- Écran d'accueil : un seul élément dominant — l'écart actuel à la courbe cible, exprimé en grand nombre signé (`+1,8 kg` / `−0,3 kg` selon retard ou avance), pas décoratif mais réellement l'info la plus importante du produit. Tout le reste (streak, prochaine séance, prochain repas) en liste réglée en dessous, alignée à gauche.  
- Bordures à angle droit (0 border-radius) pour les blocs de données ; un radius léger (4–6px) uniquement pour les éléments tapables (boutons, chips de repas) — la différence de traitement encode "ceci est de la donnée" vs "ceci est actionnable".  
- Numérotation (01, 02...) réservée aux écrans où c'est une vraie séquence : le déroulé d'une séance (exercice 1 sur 5), pas la liste des repas de la journée (qui n'a pas d'ordre imposé strict).

### Wireframes (ASCII)

**Accueil (téléphone)**

┌────────────────────────────┐

│ Forge · Semaine 4/8         │

├────────────────────────────┤

│                              │

│        \+1.8 kg               │  ← gros chiffre, signé, seul élément dominant

│   objectif : \+1.2 à \+2.0 kg   │

│                              │

├────────────────────────────┤

│ Pesée du jour        ✓ 82.4kg│

│ Séance : Haut du corps   →   │

│ Repas restants : 2/6      →   │

├────────────────────────────┤

│ ▸ Écouter l'analyse (0:52)   │

└────────────────────────────┘

**Séance active (Wear OS, un exercice)**

┌──────────────┐

│ 3/6  Squat    │

│ gobelet       │

│               │

│  série 2/4    │

│  10 reps      │

│  @ 16 kg      │

│               │

│ \[+2kg\] \[Fait\] │

└──────────────┘

**Complication cadran**

\[ \+1.8 \]   ← écart au poids cible, tap → ouvre pesée du jour

### Ton rédactionnel (voix de l'app)

- Voix active, direct, sans flatterie ni exclamation. "Pesée manquante depuis 2 jours" et non "Oups, on dirait qu'il manque quelque chose \! 😊".  
- Les messages d'alerte disent le fait et l'action : "Retard sur 2 séances cette semaine. Prochaine séance recommandée aujourd'hui." — jamais culpabilisant, jamais vague.  
- Le script audio (Gemini→Deepgram) tutoie, phrases courtes, pas de liste énumérée à l'oral.

---

## 8\. Exigences non-fonctionnelles

- **Offline-first** : logging poids/repas/séances fonctionne sans réseau ; les appels Gemini/Deepgram sont différés et rejoués dès que la connexion revient.  
- **Batterie** : pas de polling Health Connect en continu — lecture déclenchée par rappel programmé, pas de service foreground permanent.  
- **Confidentialité** : clé API Gemini/Deepgram stockées côté app (BuildConfig / Keystore), jamais commit dans le repo. Ajouter `local.properties` au `.gitignore`.  
- **Accessibilité** : contraste AA minimum sur la palette ci-dessus (à vérifier, le laiton sur graphite est limite — prévoir une variante plus claire pour le texte sur accent).

---

## 9\. Scope MVP vs Phase 2

**MVP (à livrer en premier) — écosystème complet dès le départ**

- Suivi poids \+ séances \+ repas, moteur d'ajustement et d'escalade (§5.2–5.5).  
- App Android \+ **Wear OS complet** (Tile de pesée, complication d'écart, écran de séance actif, minuteur de repos).  
- Analyse hebdo Gemini en texte.  
- **Google Calendar** : création automatique des événements séances/repas à l'onboarding, rappels natifs Calendar.  
- **Alexa** : relais webhook des rappels programmés et des escalades `RETARD_2`/`CRITIQUE` vers l'enceinte à la maison.

L'idée du MVP n'est plus "juste le cœur du tracking" mais "le programme relayé partout où tu es susceptible de le voir/entendre" — montre au poignet, agenda que tu consultes déjà, voix à la maison. Sans ces trois canaux, le risque de dérailler reste élevé même avec un bon moteur de suivi.

**Phase 2 (uniquement ce qui reste)**

- Vocalisation Deepgram Aura des rappels et de l'analyse hebdo (le texte Gemini existe déjà en MVP, seule la synthèse vocale est différée).  
- Saisie vocale Deepgram STT (log d'une séance à la voix).

---

## 10\. Prompt de démarrage pour Claude Code

À coller tel quel dans Claude Code une fois le repo GitHub créé (vide, avec juste ce fichier `SPEC.md` à la racine) :

Voici la spécification fonctionnelle et design complète de l'app "Forge" (SPEC.md à la racine

du repo). Mets en place la structure de modules Gradle décrite en §3 (app, wear, core-domain,

core-data, core-ai, core-sync), avec Kotlin \+ Jetpack Compose \+ Compose for Wear OS.

Commence uniquement par le scope MVP (§9), qui inclut l'écosystème complet dès cette

première itération (Wear OS \+ Calendar \+ Alexa ne sont pas des extras) :

1\. core-domain : implémente WeightEntry, PlanTarget, la logique de moyenne mobile 7 jours,

   la machine à états d'escalade (§5.5) et la règle de double progression (§5.4), avec tests

   unitaires JVM purs.

2\. core-data : Room DB pour les entités du §4, intégration Health Connect en lecture pour le poids.

3\. app : écran d'accueil suivant le wireframe et la palette du §7 (Compose, Material 3), écran

   de pesée, écran de séance.

4\. wear : Tile de pesée rapide \+ complication d'écart à l'objectif \+ écran de séance actif

   (wireframes §7).

5\. core-sync : intégration Google Calendar (OAuth \+ création des événements séances/repas à

   l'onboarding, §5.8) et relais Alexa via webhook (déclenché par la machine d'escalade §5.5).

Traite ces cinq points comme un seul livrable cohérent, pas comme un cœur puis des ajouts :

un rappel d'escalade CRITIQUE doit, dès cette itération, notifier l'app ET déclencher le

webhook Alexa en même temps.

Respecte strictement la palette, la typographie et les principes de layout du §7 — ne

réintroduis pas de style SaaS générique (cartes à ombre uniforme, dégradés, labels en

majuscules). Pose-moi des questions si un choix du wireframe est ambigu plutôt que de

combler avec un défaut générique.  
