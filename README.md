# EchoChat

Native Android messenger built with Kotlin, Jetpack Compose, Room, Firebase Authentication and Cloud Firestore.

**Current status:** cloud messaging MVP in `feat/cloud-auth-messenger-mvp`. It includes email/password registration with unique usernames, Google sign-in, password reset, cross-account public rooms, and membership-protected private conversations using usernames. Firebase must be configured and cross-device tests must pass before this can be treated as a release.

- [Android source and build instructions](echochat-fixed-source/README.md)
- [Firebase setup guide (Persian)](echochat-fixed-source/app/FIREBASE_SETUP.md)
- [Firestore security rules](firestore.rules)

Without Firebase configuration, the app displays setup instructions rather than presenting local demo content as a real server-connected messenger.
