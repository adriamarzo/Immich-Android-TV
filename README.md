# Immich Android TV

Immich is a self hosted backup solution for photos and videos. Current features include:

- Upload and view videos and photos
- Auto backup when the app is opened
- Selective album(s) for backup
- Multi-user support
- Album and Shared albums

More info here: https://github.com/immich-app/immich

## Compatibility

This app is compatible with Immich server **v3.0.0 and newer**. Immich 3.0.0 introduced several
breaking API changes (album asset loading via `search/metadata`, search visibility defaults,
error response format); this app has been adapted accordingly. No additional API key permissions
are required for v3 compared to earlier versions — the existing `asset.read`/`album.read`
permissions listed below already cover the endpoints this app uses.

This Android TV app will allow you to view those uploaded photos and videos. Current features
include:

| Features                                                                       | Status       |
|:-------------------------------------------------------------------------------|--------------|
| Sign in by entering API key                                                    | Done         |
| Album fetching + Lazy loading                                                  | Done         |
| Showing the photos inside an album                                             | Done         |
| Showing people, random, recent or seasonal photos                              | Done         |
| Slideshow of the photos and videos with a configured interval                  | Done         |
| Setting the app as the screensaver                                             | Done         |
| Setting the albums to show in the screensaver                                  | Done         |
| Configure the interval of the screensaver                                      | Done         |
| Add generic sorting of albums and photos                                       | Done         |
| Add sorting for specific album (select last item in row and press right again) | Done         |
| Showing the 4K thumbnail instead of the full image to speed up loading         | Done         |
| Showing the EXIF data and improving the slideshow view                         | Done         |
| Configure whether to play sound with videos                                    | Done         |
| Smarter merging of portrait photos (same people, same date, same city)         | Todo         |
| Add transitions to slideshow                                                   | Done         |
| Add places/tags view                                                           | Todo         |
| Add background media playing info to screensaver                               | Todo         |
| Casting capabilities                                                           | Out of scope |
| Searching in and for albums                                                    | Todo         |
| Dependency injection with Hilt/Dagger                                          | Todo         |

## Required API Permissions

Create a dedicated API key in Immich and grant it exactly these permissions, and nothing else:

- `asset.read`
- `asset.view`
- `asset.download`
- `album.read`
- `person.read`
- `memory.read`
- `folder.read`
- `timeline.read`

Never use the `all` permission. The app only reads from the server, so a read-only key limits the damage if the key ever leaks.

To check that a key really is read-only, run `scripts/verify-readonly-key.sh`. It asks for the server URL and the key
interactively (the key is never passed as an argument or printed), and reports PASS or FAIL for each check. It checks that
reads work, and that the key is denied on admin users, API keys, the system config, and on changing or deleting assets and
albums. Those checks use random ids, so they change nothing even if a key turns out to be capable, in which case they fail.

## Excluded albums are not a privacy control

Albums excluded in the settings stay out of the home screen, album and person grids, search and the
screensaver. Timeline does not apply this filter, and home screen channels, which are off by default, do
not either. Do not rely on exclusions to keep photos from someone who can use the TV.

## Building a release

Gradle is only run through the Docker wrapper, never directly on the host.

1. Generate a signing key outside the repository and keep it safe (if you lose it you cannot update the app).
   The wrapper mounts `~/.cache/immich-build` as `/work` in the container, so keep the key there:

   ```bash
   mkdir -p ~/.cache/immich-build
   keytool -genkeypair -v -keystore ~/.cache/immich-build/release.jks -alias immich \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Build, passing the signing settings through the `RELEASE_*` variables (paths are as seen inside the container;
   the passwords are read from the prompt, not typed on the command line):

   ```bash
   export RELEASE_KEYSTORE_PATH=/work/release.jks RELEASE_KEY_ALIAS=immich
   read -s "RELEASE_KEYSTORE_PASSWORD?Store password: "; export RELEASE_KEYSTORE_PASSWORD   # zsh; in bash: read -s -p ...
   export RELEASE_KEY_PASSWORD="$RELEASE_KEYSTORE_PASSWORD"
   scripts/docker-gradle.sh assembleRelease
   ```

   The APK is written to `~/.cache/immich-build/src/app/build/outputs/apk/release/`. Without the `RELEASE_*`
   variables the wrapper signs with a throwaway key, which is fine for testing only.

Keystores (`*.jks`, `*.keystore`) are git-ignored; never commit them.

## Credits and license

This is a fork of [giejay/Immich-Android-TV](https://github.com/giejay/Immich-Android-TV); all credit for the original
project goes to its author. It is released under the GPL-3 license, the same as the original.

## Screenshots

|                                                                                    |                                                                      |                                                                                    |
|:----------------------------------------------------------------------------------:|:--------------------------------------------------------------------:|:----------------------------------------------------------------------------------:|
|        ![Alt text](/screenshots/homescreen-1.png?raw=true "Album overview")        |      ![Alt text](/screenshots/photos.png?raw=true "All photos")      |      ![Alt text](/screenshots/sorting-options.png?raw=true "Sorting options")      |
|         ![Alt text](/screenshots/home-edit.png?raw=true "Edit homescreen")         | ![Alt text](/screenshots/settings-view.png?raw=true "View settings") | ![Alt text](/screenshots/settings-screensaver.png?raw=true "Screensaver settings") |
| ![Alt text](/screenshots/screensaver-portrait.png?raw=true "Screensaver portrait") |        ![Alt text](/screenshots/people.png?raw=true "People")        |             ![Alt text](/screenshots/seasonl.png?raw=true "Seasonal")              |

## Build steps

1. Clone the project
2. Build apk with `./gradlew assembleRelease`

## Support the project

You can support the project in several ways. The first one is by creating nice descriptive bug
reports if you find any: https://github.com/giejay/Immich-Android-TV/issues/new/choose.
<br><br>Even better is creating a PR: https://github.com/giejay/Immich-Android-TV/pulls.

## FAQ

#### I'n not able to set the app as a screensaver

1. Enable development mode on the device (click the build number or "Android TV OS Build" 7 times in
   the System->About settings).
2. Go to System -> Developer Options and enable USB Debugging.
3. If you don't have ADB installed on your PC, follow these
   instructions: https://www.xda-developers.com/install-adb-windows-macos-linux/
4. After downloading/installing ADB on the PC, connect to the device using it's IP: adb connect
   192.168.xx.xx.
5. If you have an Amazon Fire TV, first execute this command and reboot after: 'adb shell settings put secure amazon_ambient_enabled 0'
6. Execute the following command: 'adb shell settings put secure
   screensaver_components io.github.adriamarzo.immichtv/nl.giejay.android.tv.immich.screensaver.ScreenSaverService'
7. Done!

## Fonts

The interface uses [Figtree](https://github.com/erikdkennedy/figtree), bundled in the app so that no font is
downloaded at runtime. Its licence (SIL Open Font License 1.1) is in `mediaslider/mediaslider/OFL-figtree.txt`.

## Landscapes screensaver

The default screensaver asks your server's smart search for landscape terms and shows only landscape-format
photos where Immich detected no face. It does not guarantee that no person ever appears: a person with no detected
face can still come through. Add such a photo to an album excluded in the settings.
