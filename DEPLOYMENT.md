# DEPLOYMENT.md — Architecture de déploiement de Forge

Ce document décrit comment Forge est construit, signé, distribué et opéré. Il n'y a **aucun backend applicatif** à déployer : l'app est mono-utilisateur, offline-first, et toutes les intégrations externes (Gemini, Deepgram, Google Calendar, Alexa) sont des appels sortants directement depuis l'app vers des API tierces — pas de serveur intermédiaire à héberger.

À lire avec `IMPLEMENTATION_PLAN.md` (l'ordre de construction) et `SPEC.md` §3/§8 (contraintes techniques déjà posées).

---

## 1. Contraintes du contexte

- **Mono-utilisateur** : pas de multi-tenant, pas de compte/auth serveur, pas d'infrastructure à faire scaler.
- **Deux cibles de déploiement** : une app Android (téléphone) et une app Wear OS (Pixel Watch 2), issues du même monorepo, partageant `core-domain`/`core-data`/`core-ai`/`core-sync`.
- **Offline-first** : le fonctionnement quotidien (pesée, repas, séance) ne dépend d'aucun service distant. Seuls le job hebdo Gemini, la sync Calendar et le relais Alexa nécessitent le réseau, et tous les trois tolèrent un différé (WorkManager).
- **Zéro serveur à opérer** : pas de choix d'hébergeur, de base de données cloud, ni de scaling à prévoir pour ce projet.

---

## 2. Vue d'ensemble

```
                         ┌─────────────────────────┐
                         │   Téléphone Android      │
                         │   module "app"           │
                         │   (Room = source de      │
                         │    vérité locale)        │
                         └───────────┬──────────────┘
                                     │ Wear OS Data Layer API
                                     │ (sync locale, pas d'internet requis)
                         ┌───────────▼──────────────┐
                         │   Pixel Watch 2           │
                         │   module "wear"           │
                         └───────────────────────────┘

Appels sortants HTTPS depuis "app" (jamais depuis "wear" directement) :
  → Health Connect (API on-device, pas de réseau)
  → Gemini API           (job hebdo WorkManager, données agrégées uniquement)
  → Google Calendar API  (OAuth natif Android, écriture d'événements)
  → Webhook Alexa        (Notify-My-Alexa / IFTTT — voir §11)
  → Deepgram API         (Phase 2 uniquement, hors MVP)
```

Aucun de ces appels ne transite par un serveur possédé par le projet : Forge parle directement aux API des fournisseurs.

---

## 3. Build système

- **Gradle multi-module**, Kotlin DSL, un unique `settings.gradle.kts` déclarant les 6 modules de `SPEC.md` §3.
- **Version catalog** (`gradle/libs.versions.toml`) centralisant les versions Compose, Compose for Wear OS, Hilt, Room, WorkManager, Retrofit/Ktor — un seul point de mise à jour.
- `core-domain` compilé comme module Kotlin/JVM pur (pas de plugin Android appliqué) : la contrainte "aucun `android.*` dans `core-domain`" (`CLAUDE.md`) devient une erreur de compilation plutôt qu'une règle à faire respecter par relecture.
- Deux types de build seulement : `debug` (clés API de test, logs verbeux) et `release` (clés prod, minification). Pas d'environnement "staging" — inutile sans backend ni multi-utilisateur.

---

## 4. Gestion des secrets et de la configuration

- **Local (dev)** : `local.properties` (jamais commit — couvert par le `.gitignore` créé en Phase 0), lu par Gradle et exposé via `BuildConfig` :
  - `GEMINI_API_KEY` (module `core-ai`)
  - `DEEPGRAM_API_KEY` (module `core-ai` ; réservé, non consommé avant la Phase 2 produit)
  - `GOOGLE_CALENDAR_OAUTH_CLIENT_ID` (module `core-sync`)
- `local.properties.example` committé (sans valeurs réelles) pour documenter les clés attendues à toute personne qui clone le repo.
- **CI** : les mêmes clés sont stockées comme secrets GitHub Actions chiffrés, injectées en variables d'environnement au moment du job — jamais affichées dans les logs. Chaque clé est lue d'abord dans `local.properties`, puis dans l'environnement : le poste de dev et la CI utilisent donc le même code de build sans fichier généré à la volée.
- **Une clé absente ne casse pas le build** : elle produit une chaîne vide dans `BuildConfig`, ce qui permet à la CI de compiler les six modules sans secret. L'échec devient alors explicite à l'exécution de l'appel réseau, là où il est diagnosticable, plutôt qu'à la compilation.
- Aucune clé n'est jamais codée en dur dans le code source, quel que soit le module.

---

## 5. Signature (keystore)

- Un keystore de release unique, généré une fois, conservé hors du repo (gestionnaire de mots de passe ou coffre chiffré local) — jamais dans Git, même chiffré, sauf si explicitement voulu et documenté comme tel.
- Pour les builds locaux de dev : keystore de debug par défaut d'Android Studio, suffisant tant que l'app n'est pas distribuée hors des appareils de test.
- Pour les builds CI de release : keystore encodé en base64 + mot de passe + alias stockés comme secrets GitHub Actions, décodés dans le job juste avant `assembleRelease`/`bundleRelease`.
- Le SHA-1 du certificat de release (et celui de debug, pour le développement) doit être enregistré côté Google Cloud Console pour que l'OAuth Calendar fonctionne sur les deux variantes de build (§10).

---

## 6. Distribution

Sans multi-tenant ni Play Store grand public visé, plusieurs options sont possibles — **à trancher avec l'utilisateur**, car chacune implique un compte/coût différent :

| Option | Avantages | Inconvénients |
|---|---|---|
| **ADB direct** (`adb install`, ou sideload via Wi-Fi vers la montre) | Zéro compte, zéro coût, itération immédiate | Pas d'OTA, nécessite un câble/PC à chaque mise à jour, faisable pour le développement mais pénible en usage quotidien |
| **Play Console — piste de test interne** | Mise à jour automatique du téléphone, et surtout **installation automatique de l'app Wear associée** depuis le téléphone (mécanisme natif Play pour les apps Wear OS) | Nécessite un compte développeur Google Play (frais unique), une fiche store minimale même en mode privé |
| **Firebase App Distribution** | Pas de fiche store nécessaire, plus léger que Play Console | Ne gère pas nativement le déploiement de l'app Wear liée — sideload de la montre resterait manuel |

**Recommandation** : ADB direct pendant les Phases 0–6 de `IMPLEMENTATION_PLAN.md` (itération rapide, pas de friction de publication) ; passage à la piste de test interne Play Console une fois la Phase 7 validée, pour bénéficier de l'installation automatique de l'app Wear et des mises à jour OTA en usage réel. **Ce choix reste à confirmer** — voir §14.

---

## 7. CI/CD (GitHub Actions)

Pas de déploiement serveur à orchestrer — le pipeline se limite à build/test/(optionnellement) publier des artefacts binaires.

- **Sur chaque push/PR** :
  1. `./gradlew :core-domain:test` — tests JVM purs, rapides, sans émulateur (doivent rester verts en quelques secondes).
  2. `./gradlew :core-data:testDebugUnitTest` (+ tests Room en mémoire).
  3. `./gradlew lint` sur `app` et `wear`.
  4. `./gradlew assembleDebug` pour `app` et `wear` — vérifie que tout compile.
- **Sur tag/branche de release** (une fois la distribution choisie en §6) :
  - `assembleRelease`/`bundleRelease` signés via les secrets de §5.
  - Si Play Console retenu : upload vers la piste interne via le plugin Gradle Play Publisher.
  - Sinon : artefacts attachés à une GitHub Release pour installation manuelle.
- Aucun job de déploiement de "backend" — il n'y en a pas.

---

## 8. Flux réseau détaillés (égress)

| Appelant | Destination | Déclencheur | Données transmises |
|---|---|---|---|
| `app` | Health Connect (on-device, pas de réseau) | Rappel programmé (pas de polling) | — |
| `app` | Gemini API | Job `WorkManager` hebdomadaire | Séries agrégées (moyennes, deltas de charge, compteurs) — jamais un export brut identifiant, conformément à `SPEC.md` §3/§8 |
| `app` | Google Calendar API | Onboarding (création) + éventuelles mises à jour | Métadonnées d'événements (titres, horaires récurrents) |
| `app` | Webhook Alexa (Notify-My-Alexa/IFTTT) | Rappels programmés + transitions `RETARD_2`/`CRITIQUE` | Texte du message vocal à relayer |
| `app` | Deepgram API | Phase 2 uniquement | `audio_script` textuel → flux audio |
| `wear` | — | — | Aucun appel réseau direct ; passe par `app` via la Data Layer API |

---

## 9. OAuth Google Calendar

- Créer un projet Google Cloud dédié (à faire par l'utilisateur, compte Google personnel — décision hors du code).
- Écran de consentement OAuth en mode "Test" (suffisant pour un usage mono-utilisateur, pas besoin de validation Google).
- Client OAuth de type **Android** (pas "Web"), lié au nom de package de l'app + empreintes SHA-1 debug **et** release (§5) — ce flux n'utilise pas de client secret côté app.
- Scope minimal : écriture d'événements (`calendar.events`), pas d'accès lecture large à l'agenda existant au-delà de ce qui est nécessaire pour éviter les doublons à l'onboarding.

---

## 10. OAuth / clés Gemini

- Clé API Gemini généré depuis Google AI Studio ou Google Cloud (selon le produit choisi par l'utilisateur), stockée uniquement via `local.properties`/secrets CI (§4).
- Aucune rotation automatisée nécessaire vu le volume d'appels (un job par semaine) — rotation manuelle en cas de suspicion de fuite.

---

## 11. Relais Alexa — décision de fournisseur

`SPEC.md` §5.8 exclut explicitement le développement d'une Alexa Skill custom (coût de certification disproportionné pour un usage mono-utilisateur) et mentionne deux relais possibles à titre d'exemple : **Notify-My-Alexa** et un **Applet IFTTT**. Aucun des deux n'est tranché.

| Option | Fonctionnement | Point d'attention |
|---|---|---|
| **Notify-My-Alexa** | Service tiers dédié à la notification vocale Alexa via une simple requête HTTP | Dépendance à la disponibilité continue d'un service tiers de niche |
| **IFTTT Applet** | Webhook IFTTT → action Alexa "Annoncer" | Compte IFTTT (gratuit avec limites, ou payant selon le nombre d'automatisations actives) |

Ce choix engage un compte externe et une URL de webhook à traiter comme un secret. Contrairement aux clés API du §4, cette URL n'est **pas** dans `BuildConfig` : elle est saisie à l'onboarding (`SPEC.md` §5.1) et stockée dans les préférences de l'app — elle peut ainsi changer sans rebuild et ne se retrouve pas figée dans l'APK. Le fournisseur reste **à trancher avec l'utilisateur avant d'implémenter `core-sync`** (Phase 4 de `IMPLEMENTATION_PLAN.md`), conformément à `CLAUDE.md` §"Quand demander plutôt que supposer".

---

## 12. Wear OS — spécificités de déploiement

- L'app `wear` est empaquetée pour être installée automatiquement depuis le téléphone via Play (si Play Console retenu, §6) — mécanisme standard "Wear app bundled in phone APK/AAB".
- En développement, sideload direct vers la montre via `adb connect` en Wi-Fi (la Pixel Watch 2 supporte le débogage sans fil) — pas besoin de câble USB dédié à la montre.
- Aucune dépendance réseau propre à `wear` : elle communique avec `app` via la Data Layer API (sync locale Bluetooth/Wi-Fi), et fonctionne pour la saisie de base (pesée, séance) même si le téléphone est temporairement hors de portée, la sync se faisant dès reconnexion.

---

## 13. Versioning, migrations et rollback

- Pas de backend à faire rollback — un rollback consiste à réinstaller un APK/AAB signé précédent (historique Play Console, ou artefact GitHub Release archivé).
- Migrations de schéma Room : en phase de développement solo pré-1.0, une stratégie destructive (`fallbackToDestructiveMigration`) est acceptable tant que l'app n'a pas d'utilisateurs au-delà de l'usage personnel de développement — **mais ce point doit devenir un choix explicite** (migrations réelles vs stratégie destructive assumée) une fois l'app utilisée quotidiennement avec des données réelles à ne pas perdre. À trancher avant la mise en usage réel, pas par défaut silencieux.

---

## 14. Monitoring et logs

- Pas de service d'analytics/crash cloud par défaut — cohérent avec la contrainte de confidentialité de `SPEC.md`/`CLAUDE.md` (données de santé locales par défaut).
- Logs de debug locaux uniquement (Logcat), rien envoyé à un tiers.
- Si un outil de crash reporting cloud (ex. Firebase Crashlytics) est souhaité plus tard, c'est une **nouvelle intégration externe non couverte par `SPEC.md`** — à proposer et faire valider explicitement avant ajout, pas à introduire au fil d'un refactor.

---

## 15. Décisions ouvertes nécessitant une validation utilisateur

Ces points ont un impact structurant sur l'architecture de déploiement et ne sont pas tranchés par `SPEC.md` — ne pas les combler par un choix par défaut :

1. **Canal de distribution** (§6) : ADB uniquement pour l'instant, ou mise en place immédiate de la piste de test interne Play Console ?
2. **Fournisseur du relais Alexa** (§11) : Notify-My-Alexa ou IFTTT ?
3. **Mise en place de la CI GitHub Actions** (§7) : dès la Phase 0, ou après un premier prototype local fonctionnel ?
4. **Stratégie de migration Room** (§13) : accepter une stratégie destructive tant que l'usage reste expérimental, ou exiger des migrations réelles dès la première version installée avec des données réelles ?
