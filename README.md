# AudioDrop
<<<<<<< HEAD

A small Android app that accepts video links from YouTube and other yt-dlp-supported sites. It can save the full video or extract an MP3, with 320 kbps selected by default. You can paste a link or share one to AudioDrop from another app.

## Open and build

`AudioDrop.apk` in the project root is an installable, minified APK built from this source. It targets 64-bit ARM phones to keep the download smaller. It is signed with the local debug key for sideloading and is not a Play Store release.

To rebuild, use JDK 17, Android SDK 35, and Gradle 8.13 with Android Gradle Plugin 8.7.3 and Kotlin 2.0.21. This project has no checked-in Gradle wrapper, so install Gradle 8.13 and run `gradle wrapper --gradle-version 8.13` once; then run `gradlew.bat assembleDebug` on Windows. The build downloads the `youtubedl-android` and FFmpeg Android libraries from Maven Central.

## Notes

- MP3 conversion runs locally; the app does not send the URL to a conversion server.
- The app checks for a yt-dlp nightly update in the background on launch, at most once per week. yt-dlp recommends nightly for regular users because stable releases can become stale between releases.
- Download extraction skips format availability checks and limits extractor retries to reduce link scanning time. Its fast path uses concurrent fragments and chunked HTTP transfers where supported, then silently retries with a slower IPv4-oriented recovery profile if the fast attempt fails.
- History, Light/Black/System appearance, five accent colors, and the save folder are available from the bottom navigation and Settings tab. Horizontal swipes track the adjacent tab in real time and settle with an animation.
- Video opens a quality chooser and does not start until Download now is tapped. The initial 1080p choice sorts to 1080p or the highest available quality below it; other resolution caps and Best available are also offered.
- Download history stores the detected video resolution, MP3 bitrate, and file size. Select multiple entries and delete the chosen media files from their saved location.
- Downloads keeps a collapsing header with pinned sort and icon-only search controls; search expands into the title area. Sort options are themed and animate the existing list in place. Long-press a download to enter multi-select without moving the row; selection actions occupy the header. A force-swipe at the bottom reveals Archive and Clear actions, which hide again when scrolling up. Search hides those actions.
- History rows open media when tapped and show the downloaded thumbnail as artwork when the source provides one.
- The launcher uses the supplied music-download artwork as its adaptive icon.
- MP3 and video downloads embed source-provided title, artist/uploader, date, genre or album tags when available, plus the video thumbnail as cover art.
- Sharing a supported link to AudioDrop prompts for full video or audio-only MP3.
- A 320 kbps output setting does not restore detail that was absent in the original audio.
- The download engine dependency is licensed under GPL-3.0. Review its license obligations before redistributing a built app.
- Only save content you own or have permission to download, and follow the source service's terms.
=======
🎵AudioDrop is an Android music downloader that makes downloading audio from YouTube simple and convenient. Explore the APK, source files, and project resources for a lightweight music downloading app designed for easy use, fast downloads, and a clean experience.
>>>>>>> 35705024923e5c3dbdbcc636aaa82823d300615d
