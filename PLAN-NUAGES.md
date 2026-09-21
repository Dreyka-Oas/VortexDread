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
le dossier `patches/photon/` de l'ancien mod. Traité en phase 6, voir plus bas.

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
ticks et non en secondes d'horloge, parce que la cadence doit garder le même sens sur une carte, sur
un processeur et sur un serveur qui tourne en retard.

### 5. Le réseau, fait

Cette phase devait envoyer la graine et faire rejouer la simulation à chaque client, au motif
qu'envoyer les cellules serait absurde en volume. Le motif était faux de trois ordres de grandeur, et
c'est une mesure qui l'a montré. Un champ de 442 368 cellules fait 1,7 Mo de flottants, mais un ciel
est presque entièrement vide: une après-midi de beau temps a du nuage dans une cellule sur six cents
et le reste tient un zéro exact, ce qui est exactement ce que deflate avale le mieux.

Les chiffres, sur la grille visée. Beau temps: 3,1 ko emballé. Ciel couvert forcé, 9 % de cellules
mouillées, ce qui est le pire que les options permettent: 85 ko. Grille 128, couvert: le même ordre.
Contre un pas toutes les dix secondes, ça fait quelques centaines d'octets par seconde et par joueur.
Le plafond d'un paquet Minecraft est à 1 Mo, donc il reste neuf fois la marge.

Donc le serveur simule et le client reçoit. C'est moins de code qu'un rejeu, ça supprime la remise à
niveau d'un joueur qui arrive une heure plus tard, et un client dont le pilote refuse de compiler le
noyau voit exactement le même ciel que celui qui a une carte. La parité au bit de la phase 4 garde
tout son intérêt: c'est elle qui autorise la carte à remplacer le processeur côté serveur.

L'emballage tient en deux temps. Chaque valeur devient une fraction sur seize bits du pic du champ,
ce qui divise la taille par deux parce que trois des quatre octets d'un flottant sont du bruit de
mantisse que deflate ne peut pas modéliser. Puis le bloc entier est deflaté. Seize bits et non huit:
un niveau sur huit bits vaut un cinquième d'épaisseur optique en travers d'une cellule sur un cumulus
dense, ce qui se voit comme des marches sur le bord doux d'un nuage. L'écart mesuré au pire sur un
vrai ciel est de 0,0008 % du pic.

Le paquet se décrit lui-même: les tailles, l'arête de cellule et la cadence voyagent avec chaque
champ. Trente octets sur un paquet de milliers, et ça supprime la machine à états d'une poignée de
main. Rien à ordonner entre deux paquets, rien à renvoyer quand un opérateur recharge les options, et
un client connaît la forme du ciel dès le premier paquet reçu.

L'emballage coûte 15 ms au pire, donc il tourne sur le fil de calcul et pas sur le tick, et une fois
par pas et non une fois par joueur. Un client qui se connecte reçoit les deux derniers champs, le plus
vieux d'abord, pour avoir une paire à mélanger dès sa première image au lieu d'attendre le pas suivant.

Vérifié sur un serveur dédié sans fenêtre avec un client connecté dessus: 808 pas, 808 champs envoyés,
aucun saut, le plus gros à 4527 octets, et côté client une ligne nommant le pas reçu et la forme de la
grille. Le mélange entre deux pas, la chute de la paire quand le ciel du serveur repart de zéro et le
refus d'un paquet malformé ont chacun leur test.

### 6. Le rendu

Raymarching dans un volume. Le shader d'entonnoir archivé ne sert pas de point de départ comme prévu:
il s'appuie sur `MATRICES_FOG_SNIPPET` et `GLOBALS_SNIPPET`, privés en 1.21.11, et le programme part
donc de zéro sur les seuls morceaux publics.

Blaze3D n'a aucune texture en trois dimensions dans cette version. `GlConst` ne contient que
`GL_TEXTURE_2D` et les six faces de cube, `createTexture` refuse toute profondeur supérieure à un en
dehors du cas cubemap, et les formats se limitent à RGBA8, RED8, RED8I et DEPTH32. Le volume part donc
à plat, en tuiles, quatre altitudes par texel puisque l'image est RGBA de toute façon: une lecture
entre deux altitudes devient une seule prise trois fois sur quatre. Chaque tuile porte une bordure
d'un texel qui tient le bord opposé, sans quoi le filtrage matériel irait chercher l'altitude voisine
et perdrait l'enroulement horizontal. La grille de 96 par 48 par 96 tient dans 392 par 294, soit
461 ko contre 1,8 Mo à raison d'un canal par cellule.

Aucune géométrie. Le volume fait six kilomètres de large et trois de haut, donc une boîte à sa taille
serait presque entièrement derrière le plan lointain. Le programme reprend le triangle plein écran du
jeu, `core/screenquad`, qui se construit à partir du seul indice de sommet, et toute la distance se
parcourt dans l'étage fragment où le plan lointain n'a pas voix. La direction du rayon se reconstruit
de la base de la caméra et de la diagonale de `ProjMat`, déjà un uniforme automatique: l'inverse de
chaque terme est la tangente du demi-angle sur son axe, donc pas un uniforme de plus à tenir.

La passe se glisse derrière celle du ciel et devant celle du terrain, et c'est toute l'histoire de
l'occlusion. Le nuage est à un kilomètre, le terrain à quelques centaines de mètres, donc le terrain
dessiné ensuite recouvre ce qui est derrière lui sans test de profondeur, sans lecture du tampon et
sans ordre à tenir. Le mixin déclare la cible principale en lecture et en écriture, ce qui suffit au
graphe d'images pour la placer là.

Les réglages d'une image passent par un bloc d'uniformes à nous, six emplacements de quatre flottants
et jamais trois: la règle std140 donne à un membre de trois la place de quatre mais laisse le suivant
démarrer dans le quart restant, et savoir si le code qui remplit le tampon est d'accord là-dessus est
une question à deux réponses selon le pilote.

La lumière est faite. À chaque échantillon mouillé, une seconde marche part vers le soleil, six pas
qui doublent de longueur, et compte l'eau en travers. La position du soleil, la couleur de la lumière
et celle du ciel viennent toutes de la sonde d'environnement de la caméra, donc un nuage vire à
l'orange au crépuscule parce que le monde a viré, pas parce qu'un fichier l'a décidé.

Trois corrections viennent avec, et chacune répare une chose que l'absorption seule rate:

- le terme Powder de Horizon Zero Dawn. Un vrai nuage s'assombrit vers son bord, parce qu'un bord
  mince n'a pas assez d'eau pour renvoyer la lumière; l'absorption seule fait exactement l'inverse et
  éclaire le bord plus que le coeur.
- l'approximation de la diffusion multiple, trois octaves qui divisent l'extinction et le poids par
  deux à chaque fois. Sans elle tout nuage sort couleur ardoise, parce que la diffusion simple
  ignore la plus grande partie de la lumière qu'un vrai nuage renvoie.
- l'intégrale exacte sur le pas plutôt qu'un rectangle en son milieu, et un décalage de départ par
  pixel tiré d'un bruit à gradient entrelacé. Le premier empêche une marche grossière de dessiner ses
  propres bandes, le second transforme la limite de pas en grain.

La marche est aussi bornée aux altitudes qui portent de l'eau, calculées à l'emballage, élargies
d'une cellule de chaque côté pour la pente du mélange vertical. Ça change tout: un nuage occupe
quelques centaines de mètres d'un volume de trois kilomètres, donc soixante-quatre pas étalés sur le
tout traversaient le nuage en deux, et c'est ce qui le découpait en lamelles.

L'érosion sous la taille de cellule est faite, et pas sous la forme annoncée. Worley coûte
vingt-sept distances de voisins par échantillon, ce que la marche ne paie pas; à sa place, deux
octaves de bruit en bourrelet, la valeur repliée en son milieu pour que des collines lisses
deviennent des crêtes. Le pas de fréquence est deux virgule dix-sept et non deux, sans quoi chaque
octave retombe sur le treillis du précédent et les coins s'empilent en grille visible.

Trois choses se sont apprises en la réglant, et chacune a coûté un essai.

La première est la forme de la morsure. Retirer le bruit proportionnellement à `1 - eau/pic` donne un
seuil et non une érosion: le champ est optiquement mince presque partout, une cellule à un dixième du
pic est le cas courant plutôt que le bord ténu, et la morsure moyenne dépasse alors ce qu'il y a à
mordre. Mesuré avant l'essai puis confirmé à l'image, tout ce qui était sous un cinquième du pic
disparaissait entièrement, quel que soit le bruit. La correction est de travailler sur la racine de
la fraction, qui est aussi l'échelle que l'atlas stocke, ce qui rend l'opération neutre là où le
bruit ne mord pas.

La deuxième est la longueur d'onde, qui a un plancher et pas seulement un plafond. Soixante-quatre
pas étalés sur une pente de deux ou trois kilomètres tombent à quarante ou cinquante mètres l'un de
l'autre, donc une onde sous cent mètres n'est pas échantillonnée deux fois par période et revient en
scintillement. Quatre-vingt-seize mètres, une onde et demie par cellule, et deux octaves au lieu de
trois pour la même raison.

La troisième est que la config du monde de test écrase les valeurs par défaut du code. Un essai
entier a mesuré d'anciens nombres pendant que la source en portait de nouveaux. Avant toute mesure,
lire `run/config/oas/vortexdread.json`, et se souvenir que `--both` en a deux, le client dans
`run/config` et le serveur dédié dans `run/server/config`.

La diffusion avant est faite, par deux lobes de Henyey et Greenstein mêlés à moitié, six dixièmes
vers l'avant et un octième et demi vers l'arrière. Le lobe est mis à l'échelle pour qu'une
gouttelette diffusant également dans toutes les directions réponde un, ce qui veut dire qu'éteindre
la diffusion ne peut pas changer la luminosité du ciel. Le quart de pi de la forme usuelle
appartient à une intégrale de luminance que cette marche n'écrit jamais.

Huit dixièmes serait la valeur honnête pour une gouttelette d'eau. Elle multiplie la lumière par
quarante-cinq quand on regarde le soleil à travers un nuage mince, ce qu'aucune image en huit bits
ne tient, d'où six.

Le lobe s'applique une fois par octave de la diffusion multiple et non une fois pour toutes, et
c'est la deuxième version qui est la bonne. Un photon qui a rebondi quatre fois a oublié par où il
entrait, donc le lobe qui le concerne est plus plat que celui de la lumière venue droit du soleil.
Appliqué en bloc, le pic avant se retrouvait collé sur la part diffuse et le ciel à quatre-vingt-dix
degrés du soleil descendait sous zéro virgule sept de ce qu'il vaut, plus sombre qu'aucun nuage.
Par octave, avec une excentricité divisée par deux à chaque fois, ce creux remonte à zéro virgule
huit et le pic avant s'adoucit de cinq à trois fois et demie.

Trois budgets d'échantillons sont réglables, bas, moyen, haut, par un seul nombre dans la config.
Chacun porte deux comptes plutôt qu'un: les pas descendus le long du regard, qui décident si un
nuage a un corps lisse ou des tranches, et les pas montés vers le soleil, qui décident de la
profondeur sur laquelle il s'ombre lui-même. Les seconds doublent leur portée à chaque fois, donc
leur nombre fixe une distance et non une finesse: quatre voient les sept cent cinquante premiers
mètres d'eau, huit en voient près de treize mille.

Sous un pack de shaders, le mod s'efface. La mesure qui a tranché: avec Photon actif, le passage du
mod s'exécute quand même, et Iris le dit lui-même, `Missing program vortexdread:pipeline/cloud in
override list`. Autrement dit le pack intercepte la création du pipeline, ne trouve aucun programme
de remplacement pour le nôtre, et laisse passer une version que ses propres passes d'après vont
traiter comme de la géométrie ordinaire, par-dessus les nuages volumétriques qu'il dessine déjà.

Le choix retenu est le retrait plutôt qu'un correctif par pack. Un correctif rend le plus beau
résultat, c'est ce que faisait `patches/photon/`, mais il faut l'écrire et le maintenir pack par pack
et il modifie des fichiers dans le dossier du joueur. Le retrait tient en une question posée une fois
par image, à travers `IrisApi.isShaderPackInUse` atteint par réflexion pour ne pas compiler contre un
mod que presque personne n'a. Le nom du paquet a changé une fois, donc les deux sont essayés, et une
recherche qui échoue vaut absence: refuser de dessiner sur un doute rendrait le ciel à personne.

Les deux accrochages demandent, pas seulement le nôtre. Annuler le passage de nuages vanilla sous un
pack laisserait sans aucun nuage le joueur dont le pack lit cette géométrie. Et une ligne de journal
part à chaque changement d'état, parce que sinon le joueur voit les nuages du pack, croit voir les
nôtres, et ouvre son rapport de bug contre la mauvaise moitié de son jeu.

Reste à faire: demi-résolution, reprojection temporelle et bruit bleu sur l'échantillonnage.

Deux mesures qui ne concernent pas le rendu mais que le rendu a rendues visibles.

L'extinction de cent par unité de rapport de mélange et par mètre est la bonne valeur physique: un
vrai cumulus porte environ quatre dixièmes de gramme d'eau par kilogramme d'air et devient opaque en
vingt-cinq mètres. Un premier relevé donnait quinze fois moins et laissait croire à un défaut de la
physique. Un relevé plus tardif, au pas cent seize plutôt qu'au pas cent huit, donne quatre
dixièmes de millième, exactement la valeur réelle, sur une bande mouillée de quatre couches au lieu
de deux. Le champ atteint donc bien la bonne densité, il lui faut seulement le temps que la
convection monte. Ce qu'il faut en retenir est méthodologique: une mesure prise sur un ciel encore
jeune décrit le ciel jeune et rien d'autre.

Le treillis de points réguliers a disparu avec l'érosion. Il n'était ni le filtrage ni la
quantification mais le champ lui-même, mesuré à seize dixièmes de millième de cellules mouillées,
soit une quinzaine par couche de neuf mille, donc des cellules isolées que le marcheur dessinait
fidèlement en billes lisses. Deux octaves de bourrelet à quatre-vingt-seize mètres les cassent en
lambeaux et le ciel ressemble enfin à un champ de cumulus de beau temps.

Reste un rayage fin qui n'est pas celui-là et qu'il ne faut pas confondre avec lui. Il ne touche que
les nuages lointains, suit la direction des rayons projetée à l'écran, et laisse intacts les nuages
sous la caméra. C'est la marche qui s'allonge: le pas vaut la traversée divisée par soixante-quatre,
donc il grandit avec l'obliquité, et le tramage par pixel convertit la bande en filet plutôt que de
la supprimer. Le hachage du bruit a été corrigé au passage pour une raison indépendante, un produit
à trois termes qui ne laissait que six bits de mantisse et donc soixante-quatre valeurs distinctes,
mais cette correction n'a rien changé au rayage, ce qui écarte le bruit comme cause.

Le réglage de qualité a servi à le prouver plutôt qu'à le supposer. Même monde, même pas, même
point de vue à mille blocs: à soixante-quatre pas le filet court sur toute la bande lointaine, à
cent vingt-huit il faiblit nettement. Ce n'est donc pas un motif d'écran ni un défaut du champ, c'est
la marche qui manque des pas. La demi-résolution reste la réponse économique, puisqu'elle achète les
pas ailleurs, mais elle n'est plus urgente: la 570 tient soixante images en haute qualité.

Le coût, mesuré sur la RX 570 et non sur la 9060 XT, puisque c'est la carte des essais. En moyenne
qualité, soixante images par seconde partout, centile bas à cinquante et un vu de mille blocs et à
treize au sol face au soleil bas, qui est le pire cas puisque le lobe avant y allume tout. En haute
qualité, soixante encore, centile bas descendu à trente-sept et vingt. Le doublement des pas coûte
donc environ un quart du centile bas et ne coûte rien à la moyenne. À lire en sachant que cette
carte tient sur une seule ligne PCIe et que l'écran est piloté par l'autre, donc chaque image finie
retraverse ce fil avant d'être affichée. Le chiffre
mesure le couple carte plus fil, ce qui est justement ce qu'une machine modeste subit.

Une leçon de la première vérification, qui coûte une heure à qui la répète: le client de test a Photon
actif dans Iris. Trois captures de ciel volumétrique convaincant plus tard, c'était le sien, et ses
propres options parlent de `CLOUDS_CUMULUS_CONGESTUS`. Le marcheur du mod se photographie pack coupé.
La superposition des deux ciels reste le problème annoncé plus haut.

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

Les phases 1 à 5 sont faites. La 6 commence sur un champ qui arrive déjà côté client, donc elle n'a
plus rien à attendre. Un point à trancher en l'ouvrant: l'interopérabilité OpenCL et OpenGL est morte
en chemin, puisque le champ traverse le réseau au lieu de rester sur la carte du serveur, donc le
client téléverse une texture 3D depuis la mémoire hôte comme n'importe quel autre mod.

## Sources

- [Harris et al., Simulation of Cloud Dynamics on Graphics Hardware, 2003](http://markmark.net/cloudsim/harrisGH2003.pdf)
- [Harris, Real-Time Cloud Simulation and Rendering, thèse UNC](http://www.cs.unc.edu/xcms/wpfiles/dissertations/harris.pdf)
- [GPU Gems, Fast Fluid Dynamics Simulation on the GPU](https://developer.nvidia.com/gpugems/gpugems/part-vi-beyond-triangles/chapter-38-fast-fluid-dynamics-simulation-gpu)
- [Schneider, Nubis Cubed, Decima Engine 2023](https://d3d3g8mu99pzk9.cloudfront.net/AndrewSchneider/Nubis%20Cubed.pdf)
- [Optimisations for Real-Time Volumetric Cloudscapes](https://arxiv.org/pdf/1609.05344)
- [Meteoros, mise en oeuvre Vulkan du modèle Decima](https://github.com/AmanSachan1/Meteoros)
- [DimensionRenderingRegistry, Fabric API](https://maven.fabricmc.net/docs/fabric-api-0.100.1+1.21/net/fabricmc/fabric/api/client/rendering/v1/DimensionRenderingRegistry.html)
