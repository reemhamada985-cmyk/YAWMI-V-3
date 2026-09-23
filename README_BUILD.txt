YAWMY Android project — fixed offline build

This project is prepared for GitHub Actions; Android Studio is not required on the user PC.
The workflow downloads and bundles the complete offline content before building: Quran 604-page Hafs/KFQC SVGs, Sahih Bukhari, Sahih Muslim, adhkar datasets, and a CC0 adhan recording.

Build locally if desired with Gradle/JDK 17, or push the folder to GitHub and run Actions → Build YAWMY APK.


Build v1.0.3 includes: fixed adhan resource lookup, modern Android system-bar insets, updated Android Gradle Plugin 8.6.1, and offline-bundle verification before APK build.
