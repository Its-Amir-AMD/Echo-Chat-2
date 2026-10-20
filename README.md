# EchoChat

Native Android messenger built with Kotlin, Jetpack Compose, Room, Firebase Authentication and Cloud Firestore.

**Current status:** cloud-auth/public-room MVP in the branch `feat/cloud-auth-messenger-mvp`. It adds email registration/login, Google login, password reset, a configured-backend gate and cross-account public-room messaging. This is not yet a full production messenger: private conversations and social feed/profile synchronization are still unfinished.

- [Android source and build instructions](echochat-fixed-source/README.md)
- [Firebase setup guide (Persian)](echochat-fixed-source/app/FIREBASE_SETUP.md)
- [Firestore security rules](firestore.rules)

A real Firebase project is required. Without its configuration, the app deliberately displays setup instructions instead of fake online users or demo conversations.
