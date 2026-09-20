# Vortex Dread, remise à zéro: le ciel

Le mod repart d'un dossier vide. Ce qui existe aujourd'hui (tornade, vent, dégâts, débris, son,
OpenCL, 9000 lignes de Java et leurs tests) est effacé de l'arbre de travail et reste dans
l'historique git, récupérable au besoin. On ne reconstruit qu'une chose pour l'instant: le ciel.

Objectif de cette phase: les nuages de Minecraft disparaissent, remplacés par des nuages
volumétriques qui naissent, montent, s'étalent et meurent parce que la physique de l'atmosphère le
dit, pas parce qu'une texture défile. Aucun pack de shaders requis. Tous les joueurs d'un serveur
voient le même ciel.

## Ce qui a été décidé

Table rase du code, ciel entier en une couche, qualité réaliste réglable, état piloté par le serveur,
et évolution des nuages par simulation physique réelle.

Sur ce dernier point, une précision de conception qui n'était pas dans la question. Une grille de
simulation ne peut pas couvrir un ciel entier jusqu'à l'horizon à une résolution qui montrerait les
volutes: à 8 mètres par cellule, un ciel de 4 km demanderait un demi-milliard de cellules. Le montage
retenu est donc à deux étages, et c'est celui qu'emploient les productions qui font les deux:

- la physique tient la grille grossière et décide de tout ce qui se voit à l'oeil nu, où un nuage
  naît, à quelle vitesse sa tête monte, jusqu'où son enclume s'étale, quand il se dissout;
- le rendu sculpte le détail sous la taille de cellule avec du bruit érodant cette densité, sans
  jamais créer de nuage là où la physique n'en a pas mis.

La forme et la vie du nuage viennent de la physique. Le grain vient du bruit. Un nuage de beau temps
ne peut pas devenir un cumulonimbus sans que la simulation l'ait fait monter.

## Le modèle physique

Harris, Baxter, Scheuermann et Lastra, *Simulation of Cloud Dynamics on Graphics Hardware*,
Graphics Hardware 2003. C'est la référence qui tient exactement le problème: fluide, thermodynamique,
flottabilité et changement de phase de l'eau, écrit pour tourner sur carte graphique.

Variables d'état par cellule: vitesse `u`, température potentielle `θ`, rapport de mélange de la
vapeur `qv`, rapport de mélange de l'eau condensée `qc`. La pression est diagnostique, pas stockée
entre deux pas.

Un pas de simulation, dans l'ordre:

1. Advection semi-lagrangienne de `u`, `θ`, `qv`, `qc`. Inconditionnellement stable, c'est ce qui
   permet un pas de temps large.
2. Flottabilité, `B = g (θv / θv0 − qc)` avec `θv = θ (1 + 0.61 qv)`. La vapeur allège l'air, l'eau
   condensée le leste. C'est le terme qui fait monter une bulle chaude et humide.
3. Confinement de vorticité, `f = ε h (N × ω)`, qui remet les tourbillons que la grille grossière
   dissipe. Sans lui les têtes de cumulus sont molles.
4. Projection de pression: Poisson `∇²p = (1/δt) ∇·u'` résolu par Jacobi (le papier écarte le
   gradient conjugué comme mal adapté au matériel), Neumann pur au bord, puis `u = u' − δt ∇p`.
5. Condensation. Rapport de saturation `qvs(T,p) = (380.16/p) exp(17.67 T / (T + 243.5))`, T en
   Celsius, p en pascals, ajustement à 0,1 % entre -30 et 30 °C. Puis
   `Δqv = min(qvs − qv', qc')`, `qv += Δqv`, `qc −= Δqv`.
6. Chaleur latente. Ce qui se condense réchauffe l'air, `Δθ = −(L / (cp Π)) Δqv` avec
   L = 2,501e6 J/kg. C'est la boucle de rétroaction qui fait la différence entre un nuage plat et un
   cumulonimbus: condenser réchauffe, réchauffer allège, alléger fait monter, monter refroidit, donc
   condenser davantage.

Conditions aux limites: sol en adhérence, sommet en glissement libre, vent horizontal imposé sur les
côtés, bords périodiques pour `qv` (la vapeur entre depuis l'extérieur du domaine), `qc` nul aux
bords. Au sol, champs de température et d'humidité perturbés aléatoirement: c'est la seule source de
nuages, tout le reste en découle.

Ce modèle donne gratuitement ce que le mod veut plus tard. Une supercellule n'est pas un cas
particulier à coder, c'est ce qui sort de la simulation quand le cisaillement et l'humidité au sol
sont poussés.

## Les phases

### 1. Le socle

Dossier vide, `build.gradle.kts`, métadonnées, point d'entrée client et serveur, la config à
`config/oas/vortexdread.json`, la boucle de test hors-jeu. Rien de visible en jeu. Sortie: le mod se
charge et ne fait rien.

### 2. Tuer les nuages de Minecraft, fait

Les trois chemins envisagés se sont réduits à un seul, et pas celui qui était prévu. Deux sont morts
d'eux-mêmes en 1.21.11: `DimensionRenderingRegistry` n'existe plus dans fabric-api, et
`DimensionSpecialEffects` non plus, la hauteur des nuages étant passée en attribut d'environnement.

Il reste un interrupteur dans le jeu lui-même, et il est meilleur que les trois: `LevelRenderer` ne
planifie la passe des nuages que si l'alpha de `cloud_color` est strictement positif, et cette couleur
est un champ du type de dimension, donc pilotable par données sans une ligne de code. Ce qu'un pack de
données ne sait pas faire, c'est changer un champ: il remplace le fichier entier, donc éteindre une
couleur reviendrait à embarquer notre propre copie du `min_y` de l'overworld, de sa hauteur et de sa
hauteur logique, et à la porter à chaque mise à jour du jeu. Le mixin sur `addCloudsPass` fait la même
chose sans toucher aux données du monde, et annule la passe plutôt que le dessin, donc la cible de
rendu n'est même pas allouée.

Le piège annoncé n'en était pas un: Sodium 0.8.12 ne reprend pas le rendu des nuages, il réécrit la
méthode qui construit le maillage à l'intérieur du renderer du jeu, et ce maillage n'est jamais demandé.

Les trois configurations, vérifiées à la même position, même heure, même météo, captures à l'appui.
Sans rien: dalles blanches partout sur le témoin, ciel entièrement vide avec le mixin. Avec Sodium:
ciel vide. Avec Sodium et Iris: aucun nuage vanilla, avec ou sans le mixin, parce qu'Iris remplace le
pipeline entier dès qu'un pack de shaders est actif.

Et c'est là qu'est la vraie trouvaille, qui est une contrainte pour la phase 6 plutôt qu'un reste de
celle-ci. Sous Iris le ciel est plein de nuages volumétriques, et ce sont ceux du pack, dessinés dans
ses propres shaders. Aucun accrochage côté mod ne les annule. Un joueur sous pack de shaders aura donc
deux ciels superposés tant que la phase 6 ne traite pas le cas, et c'est exactement ce à quoi servait
le dossier `patches/photon/` de l'ancien mod.

### 3. La grille d'atmosphère, faite

La simulation en Java d'abord, sur le processeur, sans rien dessiner. Grille tournant avec le
joueur, résolution et étendue réglables. Les six étapes ci-dessus, chacune vérifiable seule par un
test: une bulle chaude monte, une colonne saturée condense, la masse d'eau totale se conserve, le
champ reste à divergence nulle après projection, la chaleur latente augmente bien la vitesse de
montée. C'est la phase la plus longue et celle qui décide de tout le reste.

Sortie obtenue, 45 tests au vert. Rien n'est posé dans le ciel à la main: le sol chauffe par taches
et le reste en découle. Le champ s'installe vers le pas 300, garde sa base plate aux rangées 13 à 15
soit la hauteur exacte de la couche mélangée, monte jusqu'à la rangée 20, et porte de 0,4 à 1,0 gramme
d'eau liquide par kilogramme d'air, ce qui est la fourchette d'un vrai cumulus. Il y a du nuage 79 %
du temps, et presque tout le trou est la mise en route.

Deux choses ont coûté cher et méritent d'être écrites. La grille est décalée, vitesses sur les faces
et pression aux centres: en stockant tout au centre, la divergence et le gradient tombent sur des
différences larges de deux cellules dont la composition est le laplacien d'une grille deux fois plus
grossière, la grille se scinde en huit réseaux indépendants, et le damier qui en sort est invisible
pour le solveur de pression. Et l'advection ne peut pas être la même pour l'eau et pour la chaleur.
Compter les flux aux faces conserve le total mais laisse un champ uniforme dériver de sa valeur fois
la divergence résiduelle du solveur; sur l'eau c'est une broutille, sur une température potentielle de
300 K c'est une boucle qui passe 200 K d'anomalie en deux minutes simulées. L'eau est donc comptée aux
faces, la chaleur est transportée par la forme qui laisse l'uniforme uniforme.

### 4. La carte graphique, faite

Le même pas, en OpenCL. Le repli processeur reste la référence, et un test de parité impose que les
deux donnent le même résultat chiffre pour chiffre. Si la grille visée ne tient pas le budget, c'est
la grille qui rétrécit, pas la physique qui se simplifie.

Le coût processeur est mesuré et il tranche la question: 14,7 ms par pas à 24x36x24, 61,7 à
48x40x48, 132 à 64x48x64 et 302,8 à la grille visée de 96x48x96. Le tick serveur en a 50 en tout,
donc la carte n'est pas une optimisation, c'est la condition pour que cette grille existe. La carte
fait le même pas en 2,32 ms, soit 130 fois mieux et 4,6 % d'un tick.

Sortie obtenue. Onze noyaux, la parité au bit sur les six champs après un pas et après quarante, le
banc de mesure, et le ciel branché sur le cycle de vie du serveur. La ligne d'amorçage nomme la carte
choisie et celle qu'elle a devancée. Vérifié sur un serveur dédié sans fenêtre: 1300 ticks, six pas,
la cadence exacte, aucun saut et aucune exception.

Trois choses ont coûté cher et ne se devinent pas. L'exponentielle de la bibliothèque OpenCL n'est
garantie qu'à quatre unités dans la dernière place et chaque fondeur dépense ce budget autrement,
donc elle est écrite à la main, terme pour terme, des deux côtés.

La division flottante simple a le droit de se tromper de deux unités et demie dans la dernière place,
et cette carte s'en sert: 33 474 désaccords sur 100 000 paires contre Java. Un bit suffit, parce que
les champs se nourrissent l'un l'autre et que l'écart double à chaque tour de boucle. L'option de
compilation qui réclame une division correctement arrondie est acceptée puis ignorée par ce pilote,
ce qui est pire que de ne rien demander. La double précision paraissait être la réponse et elle est
pire encore: cette carte n'a pas de diviseur 64 bits, le pilote en développe un en logiciel, et le
résultat diffère de Java sur 200 000 paires sur 200 000. Ce qui marche est de corriger le quotient
plutôt que de le réclamer: le produit fusionné est exact par spécification des deux côtés, il récupère
le reste que la division a jeté, et un pas de Newton sur ce reste tombe sur l'arrondi correct. Mesuré
sur un million de paires couvrant toute la plage d'exposants, plus les diviseurs que la simulation
utilise: aucun désaccord. La garantie a un plancher, 4e-31, sous lequel le reste tombe lui-même dans
les sous-normaux que la carte écrase à zéro, et la physique n'en approche pas à vingt-cinq ordres de
grandeur près.

Enfin le pas ne tourne pas sur le fil du serveur. Sur la grille visée le processeur met six fois le
budget d'un tick, donc un ciel avancé sur place gèle le serveur un tiers de seconde chaque fois qu'il
avance. Un seul fil porte le solveur, carte ou processeur, et c'est aussi ce qu'exige une file de
commandes OpenCL: ouverte, nourrie et libérée sur le même fil toute sa vie. La cadence se compte en
ticks et non en secondes d'horloge, parce qu'un client rejoue le ciel depuis la graine et que le même
numéro de tick doit donner le même numéro de pas sur toutes les machines.

### 5. Le réseau

Le serveur tient la grille et l'avance. Il n'envoie pas les cellules, ce serait absurde en volume: il
envoie la graine, les conditions au sol, le vent et l'heure, et chaque client rejoue la même
simulation. Déterminisme strict des deux côtés, donc arithmétique identique et aucun flottant qui
dépende de la machine. Un client qui arrive en cours de route reçoit un instantané compressé de la
densité pour se resynchroniser.

### 6. Le rendu

Raymarching dans un volume, en reprenant la chaîne du shader d'entonnoir existant, qui fait déjà
marche de vue, marche de lumière et qualité réglable, et qui est le meilleur point de départ
disponible.

- densité lue dans la grille physique, érodée par du bruit Worley-Perlin sous la taille de cellule;
- absorption par Beer-Lambert, plus le terme Powder de Horizon Zero Dawn qui corrige les nuages trop
  sombres;
- diffusion avant par Henyey-Greenstein à deux lobes, un pour le halo solaire, un pour les bords
  d'argent, sans quoi un coucher de soleil ne donne rien;
- rendu en demi-résolution et reprojection temporelle pour amortir le coût sur plusieurs images;
- bruit bleu spatiotemporel sur l'échantillonnage, contre le banding.

Réglage bas, moyen, haut dans la config, le haut visant la 9060 XT et le bas une machine modeste.

### 7. Vérification en jeu

Client et serveur dans une session sway invisible, captures à l'appui. Les preuves attendues: un
ciel sans nuage vanilla, un cumulus qui naît et grossit sur une séquence de captures espacées, une
enclume qui s'étale en altitude, le même ciel sur deux clients connectés au même serveur, et le
nombre d'images par seconde aux trois réglages.

## Ce qui n'est pas dans cette phase

Tornade, entonnoir, dégâts, débris, son, foudre, site web. Ils reviendront après, et la simulation
aura déjà de quoi les porter.

## Ordre de bataille

Les phases 1 et 2 sont courtes. La 3 est le gros morceau et ne se parallélise pas: tout en dépend.
La 4 suit la 3. La 5 et la 6 peuvent avancer ensemble une fois la 3 finie, la 6 pouvant démarrer sur
une grille remplie à la main avant que la 5 n'existe. La 7 ferme.

## Sources

- [Harris et al., Simulation of Cloud Dynamics on Graphics Hardware, 2003](http://markmark.net/cloudsim/harrisGH2003.pdf)
- [Harris, Real-Time Cloud Simulation and Rendering, thèse UNC](http://www.cs.unc.edu/xcms/wpfiles/dissertations/harris.pdf)
- [GPU Gems, Fast Fluid Dynamics Simulation on the GPU](https://developer.nvidia.com/gpugems/gpugems/part-vi-beyond-triangles/chapter-38-fast-fluid-dynamics-simulation-gpu)
- [Schneider, Nubis Cubed, Decima Engine 2023](https://d3d3g8mu99pzk9.cloudfront.net/AndrewSchneider/Nubis%20Cubed.pdf)
- [Optimisations for Real-Time Volumetric Cloudscapes](https://arxiv.org/pdf/1609.05344)
- [Meteoros, mise en oeuvre Vulkan du modèle Decima](https://github.com/AmanSachan1/Meteoros)
- [DimensionRenderingRegistry, Fabric API](https://maven.fabricmc.net/docs/fabric-api-0.100.1+1.21/net/fabricmc/fabric/api/client/rendering/v1/DimensionRenderingRegistry.html)
