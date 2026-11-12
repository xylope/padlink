# PadLink : ton téléphone devient une manette Xbox pour PC

> [!IMPORTANT]
> **🤖 Ce projet a été entièrement généré par une intelligence artificielle.**
>
> Le code de l'application Android, l'interface, les icônes, les tests et toute la documentation (y compris ce README)
> ont été écrits par une IA, **Claude (Anthropic)** via l'outil **Claude Code**, à partir de simples demandes en français.
> **Aucune ligne de code n'a été écrite à la main.** Le rôle humain s'est limité à l'idée, aux demandes et aux essais.
>
> C'est un **projet démonstratif** : un début d'application, testé et fonctionnel, qui montre ce qu'une IA peut produire.

## Le but du projet

Montrer qu'un **début d'application entièrement autonome**, générée par IA, peut transformer un téléphone Android
en **vraie manette de jeu pour PC** :

- **Rien à installer sur le PC.** Pas de programme, pas de serveur, pas de pilote. Le téléphone se connecte
  directement en Bluetooth, comme une manette du commerce.
- **Le PC voit une manette Xbox sans fil.** L'app simule l'identité et le format de données d'une manette Xbox
  Bluetooth. Windows la reconnaît avec le pilote qu'il possède déjà.
- **Reconnue par les jeux et les plateformes sans réassigner les touches** : Steam, GOG, Epic Games, Xbox Game Pass,
  les émulateurs et Moonlight voient une manette Xbox standard, avec les boutons déjà au bon endroit.

### Comment ça marche, en bref
Le téléphone se présente en **Bluetooth Low Energy** comme un périphérique de jeu (HID over GATT), avec
l'identifiant d'une manette Xbox sans fil (Microsoft 045E:0B13) et le même format de rapport (joysticks, gâchettes,
boutons, croix, vibrations). Windows charge son pilote Xbox intégré (XInput) : pour les jeux, c'est une manette Xbox
comme une autre.

## Fonctionnalités
- Manette complète : 2 joysticks, croix, A/B/X/Y, LB/RB, gâchettes analogiques LT/RT, View/Menu, bouton Xbox, L3/R3
- Vibrations du jeu renvoyées au téléphone, niveau de batterie visible sur le PC
- **Profils** : 6 profils par défaut (Standard Xbox, Standard PS4, FPS, Course, Combat, Rétro) et tes propres profils
- **Éditeur de disposition** : déplacer, redimensionner, changer la fonction des boutons, en ajouter, en masquer
- Gyroscope (visée ou volant), turbo, maintien, joysticks flottants, zone morte et courbe réglables
- Apparence PS4 (✕ ○ □ △) en option
- Appairage Bluetooth chiffré, aucune permission Internet

## Installation
Télécharge `PadLink.apk` depuis la page [Releases](../../releases/latest) sur ton téléphone (Android 8 ou plus),
ouvre-le et autorise « Installer des applis inconnues ».

## Première connexion (une seule fois par PC)
1. Dans l'app : **Lancer la manette**, puis autorise « Appareils à proximité ».
2. Sur le PC : Paramètres › Bluetooth et appareils › **Ajouter un appareil** › Bluetooth, puis choisis ton téléphone.
3. Vérifie que le code est le même sur le PC et le téléphone, et accepte des deux côtés.

Ensuite, il suffit de lancer la manette : le PC se reconnecte tout seul.

- Le PC doit avoir le Bluetooth 4.0 ou plus (une clé USB Bluetooth suffit), avec Windows 10 ou 11 à jour.
- Si le téléphone était déjà appairé à ce PC pour autre chose, supprime-le d'abord des appareils Bluetooth du PC.

## Utilisation

| Fonction | Où |
|---|---|
| Menu (disposition, profil, réglages, éteindre) | Bouton ☰ dans la pastille en haut, ou geste retour |
| Changer de profil | Touche le nom du profil dans la pastille |
| Déplacer / redimensionner | Menu › Modifier la disposition : glisser pour déplacer, pincer à 2 doigts pour la taille |
| Agrandir ou réduire tout d'un coup | Éditeur : « Tout + » / « Tout − » |
| Fonction d'un bouton, nom, turbo, maintien | Éditeur : sélectionne un bouton, puis Fonction / Nom / Turbo / Maintien |
| Enregistrer | Éditeur › Enregistrer : donne un nom, puis « Remplacer » ou « Nouveau profil ». La disposition est ensuite figée |
| Zone morte, courbe, opacité, vibrations, turbo, gyroscope | Accueil › ⋯ sur un profil › Réglages |
| Dupliquer, renommer, exporter, importer, supprimer | Accueil › ⋯ sur un profil (Importer en haut de la liste) |

**Deux catégories de profils**
- **Par défaut** : Standard Xbox, Standard PS4, FPS (visée au gyroscope), Course (direction par inclinaison), Combat, Rétro.
  Ils sont protégés : ni renommés ni supprimés. « Restaurer l'original » les remet comme au départ.
  Une disposition modifiée à partir d'un profil par défaut s'enregistre dans **Mes profils**.
- **Mes profils** : tes profils, créés, dupliqués, importés ou enregistrés depuis l'éditeur.

**Style PS4** : les boutons s'affichent en ✕ ○ □ △. Le PC voit toujours une manette Xbox : ✕ = A, ○ = B, □ = X, △ = Y.
Avec Moonlight / Sunshine, mets « Emulated Gamepad Type » sur DS4 dans Sunshine pour avoir une vraie manette PS4 sur le PC de jeu.

## Sécurité
- L'appairage Bluetooth est chiffré et doit être accepté sur le téléphone (code identique sur les deux écrans).
- Toutes les données de la manette exigent ce lien chiffré. Un seul PC est connecté à la fois.
- Un appareil qui se connecte sans s'appairer est éjecté au bout de 45 s.
- Seuls l'état de la manette, les vibrations et le niveau de batterie circulent. Le PC ne peut envoyer que des vibrations au téléphone.
- L'app n'a aucune permission Internet ou réseau et n'utilise pas la localisation. Aucune sauvegarde cloud.
- Conseils : n'accepte que les appairages que tu as lancés toi-même, garde Android à jour, et éteins la manette quand tu ne joues pas.

## Compiler l'APK

Prérequis : JDK 17 ou plus et le SDK Android (API 36).

```
cd android
gradlew.bat assembleDebug      (Windows)
./gradlew assembleDebug        (Linux / macOS)
```

L'APK se trouve ensuite dans `android/app/build/outputs/apk/debug/`.

Pour une version signée, crée `android/keystore.properties` (jamais publié, voir `.gitignore`) :

```
storeFile=keystore/ma-cle.jks
storePassword=...
keyAlias=...
keyPassword=...
```

puis lance `gradlew assembleRelease`.

## Avertissements

- **Code généré par IA**, fourni tel quel, sans garantie (voir la licence). Relis-le avant de l'utiliser ou de t'en inspirer.
- L'app se présente au PC avec l'identifiant d'une manette Xbox sans fil (Microsoft, 045E:0B13) pour être reconnue
  sans pilote. Ce projet n'est **ni affilié à Microsoft ni approuvé par Microsoft**, ni par Sony ; « Xbox » et
  « PlayStation » sont des marques de leurs propriétaires respectifs.

## Licence

PadLink est un logiciel libre distribué sous licence **GNU GPL v3.0** (voir le fichier [LICENSE](LICENSE)).
Tu peux l'utiliser, l'étudier, le modifier et le redistribuer, à condition que les versions modifiées
restent sous la même licence, avec leur code source.
