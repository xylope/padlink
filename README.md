# PadLink : ton téléphone devient une manette Bluetooth pour PC

Le PC voit une **manette Xbox sans fil**, reconnue par Windows avec son propre pilote.
**Rien à installer ni à lancer sur le PC.** Elle marche dans Steam, Epic, Game Pass, les émulateurs et Moonlight.

```
PadLink.apk   -> à installer sur le téléphone
android/      -> code source de l'app (Kotlin)
```

## Installation
Copie `PadLink.apk` sur le téléphone, ouvre-le et autorise « Installer des applis inconnues ».

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

## Note

L'app se présente au PC avec l'identifiant d'une manette Xbox sans fil (Microsoft, 045E:0B13) pour être reconnue
sans pilote. Ce projet n'est ni affilié à Microsoft ni approuvé par Microsoft ; « Xbox » et « PlayStation » sont des marques de
leurs propriétaires respectifs.

## Licence

PadLink est un logiciel libre distribué sous licence **GNU GPL v3.0** (voir le fichier [LICENSE](LICENSE)).
Tu peux l'utiliser, l'étudier, le modifier et le redistribuer, à condition que les versions modifiées
restent sous la même licence, avec leur code source.
