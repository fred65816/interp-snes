# Interp SNES

## Présentation

Interp SNES est un interpréteur et compilateur de code assembleur 65816 utilisé par la console Super Nintendo (SNES). Il a été conçu comme projet de session dans le cadre du cours [INF600E - Création de langages informatiques](https://etudier.uqam.ca/cours?sigle=INF600E) du Baccalauréat en informatique et génie logiciel de l'UQAM. Il a été conçu avec [SableCC](https://sablecc.org/), un générateur de compilateur codé en Java par Étienne Gagnon, qui est aussi coordinateur et enseignant du cours INF600E.

Interp SNES n'est pas complet, il peut compiler de texte à binaire une quinzaine d'instructions du 65816 (pour environ 40 variations de celles-ci alors que le langage complet comprend 256 variations d'instructions). Il supporte aussi différents types (booléen, string et int), les étiquettes (*labels*), étiquettes anonymes (*anonymous labels*), instructions *if*, boucles *while*, plus de 20 opérations arithmétiques, les macros (avec paramètres ou sans paramètre) et plus encore. Le fichier [details.pdf](details.pdf) comporte une présentation détaillée du projet ainsi que des explications et exemples concernant ses caractéristiques.

## Usage

Pour utiliser Interp SNES, il faut en premier lieu compiler les classe Java de la grammaire [assembler.sablecc](interp-snes/assembler.sablecc). Pour ce faire il faut utiliser une commande telle que `path/to/sablecc assembler.sablecc` dans le dossier `interp-snes`. Il faut ensuite compiler le code java du projet, ce qui dans mon cas était fait avec [l'IDE Eclipse](https://www.eclipse.org/ide/). Finalement, pour compiler un exemple dans le dossier `interp-snes/exemples`, lancer à la racine du dossier `interp-snes` une commande telle que `java -cp bin assembler.Interp exemples/nomdufichier.txt`.

## Exemples et tests

Le projet contient 5 fichiers d'exemples pour illustrer ce qu'il est possible de faire et 37 fichiers de tests qui peuvent être tous lancés avec la commande `./tests.sh` à la racine du dossier `interp-snes`. Un test compile le code du fichier texte dans le dossier `interp-snes/tests` et le compare ensuite avec le résultat binaire attendu correspondant dans le dossier `interp-snes/expected`.

Voici par exemple le test #33 et sa sortie attendue:

**Test 33_prog_a**
```
macro macro_a(int var_a, bool var_b) {
	int var_c = 2;
	
	label_a:
	sta.l {var_c}	// 8F 02 00 00
	
	namespace nsa {
		label_a:
		if(var_a - var_c > 1 && var_b) {
			sta.b {var_a + var_c}		// 85 06
			lda label_a+{$200000},x		// BF 07 00 20
			bne label_a					// D0 F8
		}
		lda.w $100000		// AD 00 00
		beq label_b			// F0 1C
		macro_b(var_a);
	}
}

macro macro_b(int var_a) {
	while(var_a > 0) {
		lda.b {$80 + var_a}			// A5 8?
		sta.l {$200000 + var_a}		// 8F 0? 00 20
		var_a = var_a - 1;
	}
}

// déclarations
int var_a = 4;
bool var_b = true;

lda.w {var_a}			// AD 04 00
macro_a(var_a, var_b);
var_a = var_a - 1;
lda.l {var_a}			// AF 03 00
label_b:
```

**Sortie attendue de 33_prog_a avec tests.sh:**
```
Program 33_prog_a
--------------------
000000: AD 04 00
000003: 8F 02 00 00
000007: 85 06
000009: BF 07 00 20
00000D: D0 F8
00000F: AD 00 00
000012: F0 1C
000014: A5 84
000016: 8F 04 00 20
00001A: A5 83
00001C: 8F 03 00 20
000020: A5 82
000022: 8F 02 00 20
000026: A5 81
000028: 8F 01 00 20
00002C: AF 03 00 00
TEST 33_prog_a: PASSED
```
