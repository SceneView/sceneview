# Pixel 9 review — 2026-05-05

Mode scribe. Thomas dicte, l'agent note. Fixes développés en bloc à la fin via agents Opus parallèles.

Branch : `claude/tender-haibt-6062c7` (worktree tender-haibt-6062c7)
Device : Pixel 9 (46110DLAQ005QS) — wireless 192.168.1.108

## Ordre de review

1. animation
2. ar-placement
3. gesture-editing
4. video
5. debug-overlay
6. ar-face
7. ar-pose
8. ar-streetscape
9. ar-rerun

---

## 1. animation

Status : reviewé

Retours :
- Caméra doit rester manipulable par gestures même quand un cinematic shot est actif (shots ne doivent pas locker la caméra)
- Mode "Free" : retour explicite à la manip user (libère la caméra)
- UI : ajouter un header "Model" au-dessus de la liste des animations

---

## 2. ar-placement

Status : reviewé — OK

Retours : RAS, validé tel quel.

---

## 3. gesture-editing

Status : reviewé

Retours :
- Zoom hypersensible — paraît trop rapide, semble venir du SDK lui-même (problème aussi présent sur ar-placement)
- Toggle "Editable" ne fait rien — comportement identique on/off
- Ajouter des axes 3D type Blender à l'origine (0,0,0) pour visualiser positions et mouvements
- Étoffer la demo avec plus de paramètres — actuellement ne sert pas à grand-chose, à élaborer
- Modèle avocado mal centré et pas top — à remplacer ou recentrer

---

## 4. video

Status : reviewé

Retours :
- Globalement OK, rendu vidéo sympa
- Idée : tester la vidéo sur un cube (multi-face) ou autre forme
- Reflets / matériau réfléchissant à explorer
- Carte blanche créative pour rendre la demo plus classe (effets, environnement, surfaces)
- Bouton mute/unmute, son désactivé par défaut
- Mouvements de caméra cinematiques qui zooment sur l'écran à des moments

---

## 5. debug-overlay

Status : reviewé

Retours :
- Zoom de base pas bon — la boule unique n'est pas visible (caméra trop loin/proche selon le count)
- Affichage live de l'ajout des boules (progress visible) — actuellement chargement long après clic sur un chiffre
- Toggle "Show Overlay" inutile — à supprimer ou repenser
- Carte blanche pour ajouts pertinents

Suggestions agent (à valider lors du dev) :
- Auto-fit caméra à la bounding box des nodes spawned
- Graphe FPS/frame time historique
- Compteur draw calls / triangles
- Bouton "stress test" qui ramp progressivement de 1 à N

Observation capture : FPS 119.9 / 8.3ms / 1000 nodes, pattern type geode (spiral/grid), perf OK.

---

## 6. ar-face

Status : reviewé

Retours :
- Image caméra pâle/délavée (exposition AR à corriger)
- Mesh invisible sur le visage — bug connu, fix `35e5990d` compile-only n'a pas résolu le rendering, à investiguer plus profondément (vertex normals/tangents, material face mesh, ARCore Augmented Faces config)

Capture confirmée : surexposition, contre-jour fenêtre, "Tracking 1 face(s)" OK, aucun mesh.

---

## 7. ar-pose

Status : reviewé

Retours :
- Globalement OK
- Image caméra avec couleurs bizarres (exposition AR à corriger — même problème que ar-face)
- Ajouter des axes 3D type Blender (cohérent avec gesture-editing)

---

## 8. ar-streetscape

Status : reviewé

Retours :
- Écran noir total (pas de feed caméra) même en extérieur
- Pill "Scanning environment..." s'affiche bien (progrès vs handoff précédent qui notait absence)
- Bug critique : feed caméra absent — à investiguer (Streetscape API init ? Permission ? Compatibilité Pixel 9 / region ?)

---

## 9. ar-rerun

Status : reviewé

État actuel :
- Le stream part mais aucun résultat visible sur le device
- L'utilisateur ne sait pas pourquoi on tombe sur cet écran ni sur quoi on se connecte (UX manque d'explication)

Améliorations demo :
- Vrai rendu visible sur le device — pas juste un "ça enregistre"
- Idée : affichage replay à la fin de l'enregistrement
- Idée : serveur local embarqué (viewer Rerun in-app ou WebView vers viewer hébergé)
- Faire en sorte que le sample démontre quelque chose, pas juste "connexion OK"

Sujet futur (mémoire `project_rerun_hosted_idea.md`) :
- Serveur Rerun hébergé payant via gateway Stripe (Free 100/mo, Pro 10k @ €9, Team 100k @ €29) — à discuter, pas à attaquer maintenant

---

# Récap fixes à développer (Phase 2)

## Fixes critiques (rendering / data)
1. **ar-face mesh** — invisible malgré tracking OK. Investiguer vertex normals/tangents, material face mesh, ARCore Augmented Faces config (fix `35e5990d` compile-only insuffisant)
2. **ar-streetscape** — feed caméra absent (écran noir total), même en extérieur. Streetscape API init / permissions / compatibilité Pixel 9
3. **AR camera exposure** — image pâle/délavée sur ar-face et ar-pose (couleurs bizarres)

## Fixes UX / interaction
4. **Camera gestures pendant cinematic shots (animation)** — gestures doivent rester actifs même quand un shot est sélectionné
5. **Free mode (animation)** — retour explicite à la manip user
6. **Editable toggle (gesture-editing)** — actuellement no-op
7. **Zoom hypersensible (SDK)** — gesture-editing + ar-placement, zoom trop rapide

## Améliorations UI
8. **Header "Model" (animation)** — au-dessus de la liste des animations
9. **Axes 3D Blender-style** — gesture-editing + ar-pose (origine 0,0,0)
10. **Bouton mute/unmute (video)** — son désactivé par défaut
11. **Caméra cinematics (video)** — mouvements qui zooment sur l'écran
12. **Vidéo sur cube / reflets (video)** — créativité demandée
13. **Auto-fit camera (debug-overlay)** — bounding box des nodes spawned
14. **Affichage live ajout boules (debug-overlay)** — progress visible pendant le chargement
15. **Supprimer toggle "Show Overlay" (debug-overlay)** — inutile
16. **Modèle avocado (gesture-editing)** — mal centré, à remplacer ou recentrer
17. **Étoffer gesture-editing** — plus de paramètres, demo trop pauvre

## Suggestions agent (à valider lors du dev)
- Graphe FPS/frame time historique (debug-overlay)
- Compteur draw calls / triangles (debug-overlay)
- Bouton "stress test" ramp 1 → N (debug-overlay)

## ar-rerun (gros chantier UX)
18. Vrai rendu sur device (replay end-of-recording, viewer in-app ou WebView)
19. UX : expliquer à quoi ça se connecte, pourquoi cet écran

## Demos OK (rien à faire)
- ar-placement (RAS)
- ar-pose (modulo exposure + axes communs)

