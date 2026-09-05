# DESIGN.md — Système de design de Forge

Ce fichier est la **source de vérité unique** pour tout ce qui touche à l'apparence et au ton de l'app. `CLAUDE.md` y renvoie plutôt que de dupliquer son contenu — si une valeur diverge entre ce fichier et un autre, celui-ci fait autorité pour le design. `SPEC.md` décrit le *quoi* (fonctionnalités) ; ce fichier décrit le *comment ça se présente*.

À relire avant de construire n'importe quel écran, pas seulement au démarrage du projet.

---

## 1. Principe directeur

Forge n'est pas un produit SaaS grand public, c'est un **carnet d'entraînement personnel de précision** — l'équivalent numérique d'un carnet de musculation posé sur un banc, pas d'un dashboard fintech. Chaque choix visuel doit découler de cette idée : instrument calibré, données mesurées, aucune décoration gratuite.

Conséquence directe : si un composant ne sert ni à afficher une mesure ni à déclencher une action, il ne devrait probablement pas exister.

### Ce qu'on évite explicitement (les "tells" d'un design générique)
- Fond crème (`#F4F1EA`-like) + accent terracotta.
- Fond quasi-noir + accent néon/vermillon.
- Cartes SaaS identiques, coins arrondis uniformes, même ombre grise sous chaque bloc.
- Bandeaux d'eyebrow en MAJUSCULES espacées au-dessus de chaque titre.
- Puces `A · B · C` séparées par des points médians, ou labels `MOT — fragment` avec tiret cadratin.
- Numérotation décorative (01/02/03) sur du contenu qui n'est pas réellement une séquence.
- Un seul mot accentué en gras/italique/couleur dans un titre.
- Flèche `→` systématique à la fin des libellés de bouton/lien.

Si un écran en cours de conception ressemble à l'un de ces patterns, c'est le signal pour reprendre — pas pour se justifier.

---

## 2. Palette

| Rôle | Nom | Hex | Usage |
|---|---|---|---|
| Fond | Graphite chaud | `#1E1B17` | Fond d'écran unique, jamais de dégradé |
| Surface | Charbon | `#28241D` | Blocs légèrement surélevés (rare — préférer les règles à la superposition de surfaces) |
| Texte principal | Os | `#EDE7D9` | Corps de texte, chiffres clés |
| Texte secondaire | Sable éteint | `#A79E8C` | Labels, métadonnées, texte de règle (à 20 % d'opacité pour les séparateurs) |
| Accent primaire | Laiton | `#C39A3C` | Progression positive, éléments interactifs actifs |
| État "on track" | Mousse | `#6E8F5C` | Uniquement quand la donnée réelle confirme qu'on est dans la cible |
| État "retard/critique" | Brique | `#A8462D` | Uniquement pour `RETARD_1/2` et `CRITIQUE` (cf. CLAUDE.md) |

**Règles d'usage** :
- Laiton et brique sont des **indicateurs d'état réel**, jamais des choix esthétiques. Un bouton neutre n'est pas laiton par défaut — il l'est seulement s'il représente une action liée à la progression.
- Pas de dégradé, nulle part, y compris sur les graphiques.
- Mousse et brique ne coexistent jamais sur un même élément (pas de "orange qui vire au vert au survol").

---

## 3. Typographie

| Rôle | Famille | Graisses autorisées |
|---|---|---|
| Chiffres clés (poids, charges, écarts) | Zilla Slab ou Roboto Slab | Medium, Bold |
| Corps de texte / UI / labels | Inter ou IBM Plex Sans | Regular, Medium |

- Deux familles maximum. Pas de Light nulle part (manque de présence sur fond sombre).
- Pas d'italique ni de gras isolé sur un seul mot dans un titre pour créer un accent artificiel — si un mot doit ressortir, c'est par la taille du bloc entier, pas par un traitement typographique ponctuel.
- Longueur de ligne : sous 80 caractères pour le corps de texte.
- Les nombres (poids, kcal, charges) utilisent des chiffres tabulaires (`tnum`) pour ne pas sauter en largeur quand ils changent.

---

## 4. Iconographie et imagerie

- Icônes en trait fin géométrique, épaisseur de trait cohérente avec le poids de la slab utilisée pour les titres — pas de pack d'icônes générique mixte (Material + Feather + emoji dans le même écran).
- Aucune illustration, mascotte ou emoji dans l'UI. Le ton est celui d'un instrument, pas d'une app de bien-être.
- Les seuls "visuels" du produit sont les chiffres eux-mêmes et les courbes de données (poids, charges) — pas de photographie stock.

---

## 5. Layout

- Grille façon **carnet réglé** : séparateurs horizontaux 1px en sable éteint à 20 % d'opacité entre les blocs, plutôt que des cartes à ombre portée.
- Alignement à gauche par défaut (pas de centrage de blocs de contenu — le centrage est réservé aux états vides/erreur, cf. §8).
- **Border-radius** : 0 sur les blocs de données (ce sont des faits, pas des objets à manipuler) ; 4–6px uniquement sur les éléments tapables (boutons, chips, champs de saisie). Cette distinction *encode* une information : donnée vs action.
- Un seul élément dominant par écran. L'écran d'accueil, par exemple, n'a qu'une hiérarchie visuelle forte (l'écart au poids cible) — tout le reste est en liste réglée secondaire.
- Numérotation (1, 2, 3...) réservée aux séquences réelles : le déroulé d'une séance (exercice 3 sur 6). Jamais sur une checklist de repas, qui n'a pas d'ordre imposé.

---

## 6. Mouvement

- Une seule séquence orchestrée au chargement (ex. : l'écart au poids cible qui s'incrémente depuis 0 à l'ouverture de l'app) — pas de fade-and-slide-up systématique sur chaque section.
- Le changement d'état d'escalade (À_JOUR → RETARD_1 → ...) s'anime sur la couleur du chiffre concerné, pas sur une carte entière qui apparaît/disparaît.
- Pas d'animation au survol/tap généralisée sur chaque élément de liste — réserver le mouvement à ce qui confirme une action (coche d'un repas, validation d'une série).
- Respecter `reduced motion` : toute animation non essentielle doit avoir un équivalent statique.

---

## 7. Écrans de référence (wireframes ASCII)

### 7.1 Accueil (téléphone)
```
┌────────────────────────────┐
│ Forge · Semaine 4/8         │
├────────────────────────────┤
│                              │
│        +1.8 kg               │  ← seul élément dominant, chiffre signé
│   objectif : +1.2 à +2.0 kg   │
│                              │
├────────────────────────────┤
│ Pesée du jour        ✓ 82.4kg│
│ Séance : Haut du corps   →   │
│ Repas restants : 2/6      →   │
├────────────────────────────┤
│ ▸ Écouter l'analyse (0:52)   │
└────────────────────────────┘
```

### 7.2 Checklist repas (téléphone)
```
┌────────────────────────────┐
│ Aujourd'hui · 4 repas restants│
├────────────────────────────┤
│ ✓ Petit-déjeuner              │
│ ○ Collation 1                 │
│ ○ Déjeuner                    │
│ ○ Collation 2                 │
│ ○ Dîner                       │
├────────────────────────────┤
│ Variation du jour : beurre    │
│ d'amande au lieu de cacahuète │
└────────────────────────────┘
```
Pas de numérotation ici — les repas n'ont pas d'ordre strict entre eux, seulement des horaires indicatifs.

### 7.3 Séance active (Wear OS)
```
┌──────────────┐
│ 3/6  Squat    │  ← ici la numérotation est légitime : vraie séquence
│ gobelet       │
│               │
│  série 2/4    │
│  10 reps      │
│  @ 16 kg      │
│               │
│ [+2kg] [Fait] │
└──────────────┘
```

### 7.4 Complication cadran
```
[ +1.8 ]   ← écart au poids cible, tap → ouvre pesée du jour
```

### 7.5 Analyse hebdomadaire (téléphone)
```
┌────────────────────────────┐
│ Semaine 4 · Analyse          │
├────────────────────────────┤
│ Moyenne 7j : 82.4 kg          │
│ Cible : +1.2 à +2.0 kg        │
│ Écart : +1.8 kg — dans la cible│
├────────────────────────────┤
│ Développé couché : 4x10 @20kg │
│  (+2kg vs semaine 3)          │
│ Squat gobelet : stagnation    │
│  depuis 2 semaines            │
├────────────────────────────┤
│ Recommandation : aucun ajust- │
│ ement calorique cette semaine │
├────────────────────────────┤
│ ▸ Écouter (0:52)              │
└────────────────────────────┘
```

### 7.6 Onboarding — connexion écosystème
```
┌────────────────────────────┐
│ Connecter l'écosystème        │
├────────────────────────────┤
│ Health Connect      Connecté ✓│
│ Google Calendar     Connecter │
│ Alexa (webhook)     Connecter │
├────────────────────────────┤
│ Chaque connexion ajoute un    │
│ canal de rappel — l'app       │
│ fonctionne sans, en moins     │
│ complet.                      │
└────────────────────────────┘
```

---

## 8. États vides, de chargement et d'erreur

Les moments où il n'y a pas encore de données sont des occasions de direction, pas de décoration :
- **État vide (aucune pesée encore loguée)** : "Pas encore de pesée cette semaine. Pèse-toi ce matin pour démarrer le suivi." — jamais une illustration de bienvenue, jamais de ton enjoué.
- **Erreur (échec sync Health Connect)** : "Synchronisation impossible. Vérifie que Health Connect est installé et autorisé." — dit le fait, dit l'action, pas d'excuse ("Oups !").
- **Chargement (analyse Gemini en cours)** : texte factuel court, ex. "Analyse de la semaine en cours." — pas d'animation de personnage, un simple indicateur de progression sobre.

---

## 9. Ton rédactionnel (voix de l'app)

- Voix active, directe, sans flatterie ni exclamation. *"Pesée manquante depuis 2 jours"*, jamais *"Oups, on dirait qu'il manque quelque chose !"*
- Un message d'alerte dit le fait et l'action dans la même phrase : *"Retard sur 2 séances cette semaine. Prochaine séance recommandée aujourd'hui."*
- Le vocabulaire reste cohérent d'un écran à l'autre : si un bouton dit "Valider la série", la confirmation qui suit dit "Série validée" — jamais un synonyme différent.
- Le script audio (Gemini → Deepgram, phase 2) tutoie, phrases courtes, aucune liste énumérée à l'oral.

---

## 10. Accessibilité

- Contraste AA minimum sur toutes les paires texte/fond de la palette. Le laiton (`#C39A3C`) sur graphite (`#1E1B17`) est à la limite pour du texte de petite taille — l'utiliser uniquement pour des chiffres en grande taille (≥ 24sp) ou prévoir une variante éclaircie (`#D9B65C`) pour le texte courant.
- Focus visible clavier/D-pad sur tous les éléments interactifs (pertinent pour la navigation sur Wear OS avec la couronne).
- Respect de `reduced motion` (cf. §6).
- Cibles tactiles ≥ 48dp sur Wear OS malgré l'écran réduit — ne pas sacrifier la zone de tap pour gagner en densité visuelle.

---

## 11. Checklist d'auto-critique avant de livrer un écran

À passer en revue avant de considérer un écran terminé :
- [ ] Un seul élément domine visuellement l'écran — pas trois blocs de même poids.
- [ ] Aucune couleur de la palette n'est utilisée en dehors de son rôle défini (§2).
- [ ] Le border-radius distingue bien donnée (0) et action (4–6px).
- [ ] Aucune numérotation décorative sur du contenu non séquentiel.
- [ ] Aucun texte en MAJUSCULES espacées, aucun `→` en fin de libellé, aucun point médian `·` utilisé comme séparateur de metadata.
- [ ] Le texte d'état vide/erreur dit le fait + l'action, sans ton d'excuse.
- [ ] Si un mouvement a été ajouté, il répond à une action de l'utilisateur ou est la seule séquence orchestrée de l'écran — pas un fade générique par bloc.
