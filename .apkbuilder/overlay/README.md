# Duties
package: com.duties  |  backdrop 2.0.0 (liquid glass)  |  Firebase Auth (Google)

## Before the Google login works
1. Firebase -> Project settings -> your Android app -> Add fingerprint -> paste the SHA-1 below.
2. Download google-services.json again and put it in app/ (or next to the zip in the repo root).
3. Build.

SHA-1:   B6:D4:2C:9A:E6:AC:27:FF:D8:DF:20:00:92:0D:C0:A0:AB:B3:2E:B6
SHA-256: 11:5C:9C:90:98:EC:9B:65:89:51:23:65:EA:15:2F:92:0C:4D:55:41:AA:4E:86:3C:06:D5:F2:C4:4E:36:68:FD

The keystore is keystore/duties.jks (fixed), so the SHA-1 stays the same on every GitHub build.

## Banner GIF (Realtime Database)
The banner can be an animated GIF (max 16 MB). It is stored in the Realtime Database, in pieces.
1. Firebase -> Build -> Realtime Database -> Create database.
2. Rules tab -> paste database.rules.json -> Publish.
3. Project settings -> download google-services.json again (it now has "firebase_url") and put it in app/.
4. Build.
