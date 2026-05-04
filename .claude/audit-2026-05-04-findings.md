# Demo audit findings — 2026-05-04 (Pixel_7a AVD)

Branch: `claude/tender-haibt-6062c7`
Reviewer: Thomas, live via screen recording.

Scope tags:
- **DEMO** = fixable in `samples/android-demo/`
- **SDK** = fix needed in `sceneview/`, `arsceneview/`, or `sceneview-core/`

---

## gesture-editing — issues found

1. **[DEMO]** Pas de référence visuelle pour distinguer mouvement caméra vs mouvement node. Ajouter un 2e modèle **non-éditable** comme repère statique dans la scène.
2. **[DEMO]** Pas d'indicateur on-screen ("moving camera" / "moving node") pour clarifier ce que le geste affecte. Overlay textuel ou changement de couleur du node sélectionné.
3. **[SDK]** Toggle `Editable` n'a aucun effet visible — quand on désactive, l'objet reste manipulable. Vérifier la liaison entre l'état du toggle et la propriété `editable` du node, ou si c'est `EditableNode` qui ignore le flag.
4. **[SDK]** Bouton "Reset Position" ne semble pas faire grand-chose. Vérifier l'implémentation : est-ce qu'il reset bien position+rotation+scale au transform initial, ou est-ce que la transform "initiale" se met à jour à chaque interaction ?

**Verdict overall:** 🔴 Multiple SDK bugs — à fixer avant release v4.x avec demo associée.

---

## collision — works but needs cool features

État actuel : 5 shapes statiques (2 spheres + 3 cubes), tap pour highlight, Reset Colors. Ça **marche** mais c'est passif et ne montre pas la vraie valeur du système collision/hit-test.

**Cool features à ajouter (priorité haute pour visibility AI-first SDK) :**

1. **[DEMO]** Drag-to-move des shapes + coloration **rouge si overlap**, **vert sinon** → collision detection visible en temps réel
2. **[DEMO]** Ray + hit marker visualisé au tap (ligne caméra→point d'impact + petite sphère/croix sur le hit) → matérialise ce que `hitTest` retourne
3. **[DEMO]** HUD avec hit info : world coords (x,y,z), normale, distance du point d'impact, nom du node touché → pédagogique, montre le payload collision
4. **[DEMO]** Toggle bounding volumes (AABB ou bounding sphere) → révèle les volumes utilisés par le moteur
5. **[DEMO]** 1-2 shapes animées qui bougent en boucle, collision events triggerent un flash visuel → demo "auto-play" sans interaction
6. **[SDK?]** Vérifier que l'API expose bien tout ce qui est nécessaire pour 1-5 (notamment hitTest avec normales + node ID + intersection world point). Si quelque chose manque, c'est un fix SDK.

**Verdict overall:** 🟡 Marche mais sous-vendu — ajouter les cool features avant release v4.x.

---

## debug-overlay — works but needs interactivity

État actuel : robot affiché, overlay top-left avec FPS / Frame ms / Nodes count, toggle Show Overlay. **Marche** mais on ne peut pas vraiment tester la fonctionnalité (1 node, FPS plafonné, rien à stresser).

**Features à ajouter :**

1. **[DEMO]** Slider ou boutons "Spawn N nodes" (ex: 1, 10, 100, 500, 1000) → voir le FPS chuter en temps réel, prouve l'utilité de l'overlay
2. **[DEMO]** Bouton "Clear" pour reset à 1 node
3. **[DEMO]** Ajouter quelques métriques **soigneusement** (sans surcharger) : triangle count total visible, draw calls, peut-être GPU memory si exposé
4. **[SDK?]** Vérifier que SceneView expose ces métriques via l'API — sinon, fix SDK pour ajouter l'API et brancher l'overlay dessus
5. **[DEMO]** Optionnel : graphe FPS sur les dernières 60 frames (mini-spark line) → matérialise les drops

**Note :** ne pas surcharger l'overlay — Thomas insiste : si trop chargé "on ne verra plus rien". Garder dense mais lisible.

**Verdict overall:** 🟡 Marche mais passif — features de stress-test manquent pour démontrer la valeur.

---

## secondary-camera — PiP supprimée à cause d'un bug SDK + bug d'UX

État actuel : titre "Camera Presets", 4 chips Top/Side/Front/Corner qui orbitent la caméra autour du casque. **Aucune Picture-in-Picture**, aucune 2nde caméra réelle.

**Historique :** la PiP était bien implémentée à l'origine (commit `75c6f134` Sprint 3, fix `7892c9c2`). Le commit **`d4312940` (2026-04-24)** l'a **supprimée** avec ce justificatif :

> *"PiP inset stayed empty because the second SceneView needed a separate ModelInstance (one instance can only attach to one Scene at a time) and the dual-Scene + sharedScene paths both failed in different ways on Pixel_7a Metal. Replace the PiP with a single SceneView that rotates the helmet to mimic the four named angles — same visual outcome, no second-render fragility."*

Conclusion : c'est un **vrai bug SDK masqué par un workaround**, pas un fix.

**Issues :**

1. **[SDK] CRITIQUE** Un `ModelInstance` ne peut être attaché qu'à un seul `Scene`. Bloque la PiP, le multi-viewport, le mini-map, tous les use-cases multi-camera. Le SDK doit soit :
   - Permettre de partager une `ModelInstance` entre plusieurs `Scene`
   - OU exposer un mode "shared scene with N cameras rendering to N surfaces"
   - OU au minimum simplifier le path "clone instance for second view"
2. **[DEMO]** Restaurer la **vraie PiP** une fois le SDK fixé. C'est ÇA le demo `secondary-camera`. Le current "Camera Presets" est un autre demo (et devrait être renommé `camera-presets` si on garde les 2).
3. **[DEMO]** Le chip d'angle **ne se désélectionne PAS quand l'utilisateur drag/rotate la caméra à la main**. Le `cameraPreset` state est figé sur le dernier chip cliqué, même si la caméra a bougé via gesture. Bug visuel : on lit "Front" sélectionné alors que la caméra est ailleurs.
   - Fix : détecter un geste utilisateur sur le SceneView (touch listener ou `CameraManipulator` callback), passer `cameraPreset` à null/"Custom" → tous les chips deviennent unselected.
4. **[DEMO]** Title hardcodé "Camera Presets" mais demo ID = "secondary-camera". Une fois la PiP restaurée, retitrer en "Secondary Camera (PiP)" — ce qui était le titre original.

**Verdict overall:** 🔴 Demo cassé en surface (workaround) — restaurer après fix SDK.

---

## video — pure 2D, ne montre pas la 3D

État actuel : un quad avec ce qui ressemble à un gradient bleu/violet plein cadre + bouton ⏸. Source : `rememberMediaPlayer(assetFileLocation = "videos/sample.mp4")`. Soit `sample.mp4` est un fichier de test trivial, soit le rendu est cassé. Quel que soit le contenu, on a juste l'impression que c'est de la 2D plate.

**Refonte :**

1. **[DEMO]** Remplacer le fichier local par une URL libre de droits avec son :
   - **Big Buck Bunny** (Blender Foundation, CC-BY 3.0)
   - URL : `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4`
   - Attribution courte dans le label/credits
   - Ou version plus light : `https://test-videos.co.uk/bigbuckbunny/mp4-h264` (480p/720p)
   - Streaming via `MediaPlayer.setDataSource(url)`, pas de bundle ; permission `INTERNET` à vérifier dans le manifest demo
2. **[DEMO]** Mettre la vidéo dans une **vraie scène 3D**, choisir parmi (recommandé : A pour v4, B/C backlog) :
   - **A** Quad + sol avec réflexion (`ReflectionProbeNode` ou plane miroir) + IBL ambiance cinema + slow camera orbit
   - **B** Cube avec les 6 faces qui jouent la même vidéo synchronisée (rotation auto, gestures override)
   - **C** Modèle 3D type vieux CRT TV / téléphone / écran cinéma avec sièges → environnement narratif
3. **[DEMO]** Ajouter au moins une **animation caméra** (orbit lent par défaut, stoppé au premier touch utilisateur) pour casser l'effet 2D
4. **[SDK?]** Vérifier que `VideoNode` + `MediaPlayer` supportent bien le streaming HTTP (pas que les assets locaux) — si limitation, fix SDK

**Verdict overall:** 🟡 Tech marche mais visuellement nul — refonte content + scène 3D nécessaire avant release.

---

## model-viewer — refonte complète, c'est le demo phare

État actuel : 1 seul modèle hardcodé (DamagedHelmet), auto-orbit caméra 20s avec handoff au touch, loading scrim. **Aucun contrôle utilisateur**. C'est un hello-world, pas une démo qui justifie le nom "Model Viewer".

**Référence :** `<model-viewer>` (Google web) — standard du marché : model picker, IBL picker, auto-rotate, AR button, animations, variants. SceneView en propose **0**.

**Refonte proposée par priorité :**

### P0 (must have pour v4 stable)
1. **[DEMO]** Model picker — chips ou dropdown pour switcher entre 4-6 GLB phares :
   - DamagedHelmet (current)
   - Avocado, Fox (animé), Lantern, BoomBox, FlightHelmet
   - Tous dans `assets/models/`
2. **[DEMO]** Environment / IBL picker — 4 HDRs au moins : studio, sunset, neutral, indoor (assets dispos dans `assets/environments/`)
3. **[DEMO]** Background toggle — skybox visible / solid color / transparent (use-case e-commerce critique)
4. **[DEMO]** Auto-rotate toggle — laisser l'user freezer le modèle
5. **[DEMO]** Camera reset button

### P1 (nice to have, peut suivre dans 4.1)
6. **[DEMO]** Animation play/pause pour modèles animés (Fox, Dragon)
7. **[DEMO]** AR button "View in AR" → deep-link `ar-placement?model=<id>` (passer le modèle sélectionné)
8. **[DEMO]** Wireframe toggle (debug)

### P2 (future)
9. **[DEMO]** Material variants (KHR_materials_variants — shoe, helmet)
10. **[DEMO]** Hotspots/annotations (use-case e-commerce / éducation)

**[SDK]** Vérifier que l'API expose bien :
- Variants matériaux (`KHR_materials_variants`)
- Wireframe rendering toggle
- Hotspots/annotations 3D ancrés à la surface
Si l'un manque, c'est un fix SDK à brancher.

**Verdict overall:** 🔴 Demo phare mais hello-world — refonte 4-6h de dev, prio P0 avant release v4.x stable.

---

## animation — 1 seul modèle 1 anim, picker absent, centrage cassé

État actuel : dragon (`animated_dragon.glb`, 1 animation), cadré trop haut (corps coupé en bas), Speed slider, Loop/Once chips. Le bug "Loop/Once doesn't work" + "model not centered" sont vrais.

**Bugs et manques :**

1. **[DEMO]** **Modèle mono-animation.** Faut le remplacer par un modèle multi-anim. Disponibles dans `samples/android-demo-assets/src/main/assets/models/` :
   - **`animated_toon_horse.glb`** — 4 anims : Walk, Trot, Gallop, Rest *(recommandé : progression "military-style" Walk→Run)*
   - `animated_bunny_detective.glb` — 6 anims : Walk, Talk, Jump, Investigate, Climb (variété max)
   - `fox.glb` — 3 anims : Survey, Walk, Run (canonical glTF sample)
   - `animated_shark.glb`, `animated_kawaii_meka.glb` — 3 anims chacun
2. **[DEMO]** **Aucun picker d'animation** dans la UI. Le code joue **toutes** les animations simultanément :
   ```kotlin
   for (i in 0 until node.animationCount) node.playAnimation(i, speed = speed, loop = loop)
   ```
   → Sur dragon (1 anim) ça marche, sur multi-anim ce serait du chaos. Refactor :
   - Ajouter `selectedAnim: Int` state + Row de chips (un par animation, label = nom de l'anim depuis le GLB)
   - Modifier le `LaunchedEffect` pour ne jouer **que** `selectedAnim`, pas la boucle entière
3. **[DEMO]** **Centrage cassé** : `ModelNode(modelInstance = ..., scaleToUnits = 0.5f)` sans `centerOrigin` → le dragon est positionné selon son origine native (tête en haut), le corps disparaît hors viewport. Ajouter `centerOrigin = Position(0f, 0f, 0f)`.
4. **[DEMO]** **Vérifier Loop/Once** : le code semble correct (LaunchedEffect re-run sur toggle), mais Thomas indique que "ça ne marche pas". Tester en live après le refactor multi-anim.
5. **[SDK?]** Si le bug Loop/Once est confirmé après UI fix (pas un artefact du joue-toutes), c'est un fix SDK dans `playAnimation`.

**Verdict overall:** 🔴 Picker absent + bugs centrage + Loop/Once à vérifier — refactor obligatoire.
