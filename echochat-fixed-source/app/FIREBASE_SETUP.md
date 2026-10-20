# راه‌اندازی بک‌اند ابری (Firebase) برای EchoChat

این پروژه از نظر کد برای اتصال به یک بک‌اند ابری واقعی (Firebase Authentication +
Cloud Firestore) آماده شده است، اما فعال‌سازی نهایی به یک حساب Google و پروژه‌ی
Firebase شخصی شما نیاز دارد که هیچ ابزار خودکاری نمی‌تواند از طرف شما بسازد.

بدون انجام این مراحل، اپ کاملاً عادی و بدون کرش در **حالت محلی** کار می‌کند
(تمام داده‌ها فقط روی همان دستگاه، در پایگاه‌داده‌ی Room، ذخیره می‌شوند) — دقیقاً
همان‌طور که پیش از این کار می‌کرد.

## چه چیزی از قبل آماده است

- `FirebaseAuthManager` (در `app/src/main/java/com/example/data/remote/`): ورود با
  Google از طریق Credential Manager.
- `FirestoreSyncManager` (همان پوشه): همگام‌سازی دوطرفه‌ی کانال‌ها و پیام‌ها بین
  Room محلی و Firestore، بدون نیاز به تغییر در UI یا ViewModel موجود.
- `EchoChatViewModel` به‌صورت خودکار بررسی می‌کند آیا یک پروژه‌ی Firebase واقعی
  وصل است (`FirebaseApp.getApps(context).isNotEmpty()`)؛ اگر نه، این دو سرویس
  اصلاً فعال نمی‌شوند و دکمه‌ی ورود در تنظیمات پروفایل نمایش داده نمی‌شود.

## مراحل فعال‌سازی

1. به [console.firebase.google.com](https://console.firebase.google.com) بروید و
   یک پروژه‌ی جدید بسازید.
2. داخل پروژه، یک اپ اندرویدی اضافه کنید با Application ID دقیقاً برابر:
   ```
   com.aistudio.echochat.vpkzqr
   ```
   (این مقدار در `app/build.gradle.kts` تحت `defaultConfig.applicationId` تعریف
   شده است.)
3. فایل `google-services.json` را دانلود کرده و داخل پوشه‌ی `app/` این ریپازیتوری
   (کنار `build.gradle.kts`) قرار دهید.
4. در Firebase Console:
   - از بخش **Authentication → Sign-in method**، provider «Google» را فعال کنید.
   - از همان صفحه، **Web client ID** را کپی کنید.
   - از بخش **Firestore Database**، یک پایگاه‌داده بسازید (Production mode کافی
     است) و Security Rules مناسب برای مسیر `users/{userId}/**` تنظیم کنید تا هر
     کاربر فقط به داده‌ی خودش دسترسی داشته باشد؛ نمونه:
     ```
     rules_version = '2';
     service cloud.firestore {
       match /databases/{database}/documents {
         match /users/{userId}/{document=**} {
           allow read, write: if request.auth != null && request.auth.uid == userId;
         }
       }
     }
     ```
5. در کد، مقدار `FirebaseAuthManager.DEFAULT_WEB_CLIENT_ID` (در
   `app/src/main/java/com/example/data/remote/FirebaseAuthManager.kt`) را با
   Web Client ID واقعی‌تان جایگزین کنید — یا بهتر، آن را از طریق فایل `.env`
   (که این پروژه با Secrets Gradle Plugin از قبل پشتیبانی می‌کند؛ نگاه کنید به
   `.env.example`) تزریق کنید و در سازنده‌ی `FirebaseAuthManager` پاس دهید.
6. پروژه را در Android Studio باز و Sync کنید؛ پلاگین `google-services` به‌طور
   خودکار فایل تازه را تشخیص می‌دهد.
7. برای App Check (که از قبل در `EchoChatApplication.kt` برای Firebase AI
   تنظیم شده)، یک دیباگ توکن در کنسول Firebase ثبت کنید تا در حالت دیباگ هم
   درخواست‌ها رد نشوند.

## محدودیت‌های شناخته‌شده‌ی پیاده‌سازی فعلی sync

- `FirestoreSyncManager` برای سادگی، هر بار که لیست محلی پیام‌های یک کانال
  تغییر می‌کند، تا ۵۰۰ پیام آخر آن کانال را دوباره به Firestore push می‌کند
  (نه فقط پیام تغییریافته). برای یک اپ شخصی/دمو کفایت می‌کند؛ برای مقیاس
  واقعی، بهتر است فقط رکوردهای تغییریافته (diff) ارسال شوند.
- پیام‌های دریافتی از Firestore با `OnConflictStrategy.IGNORE` در Room درج
  می‌شوند؛ یعنی اگر پیامی که از قبل محلی وجود دارد در دستگاه دیگری ویرایش شود
  (مثلاً reaction تغییر کند)، آن تغییر در دستگاه فعلی اعمال نمی‌شود. برای حل
  این مورد باید یک متد DAO جدید با `OnConflictStrategy.REPLACE` اضافه شود.
- پست‌ها، استوری‌ها و پروفایل فعلاً همگام‌سازی نمی‌شوند — فقط کانال‌ها و
  پیام‌های چت. گسترش این الگو به بقیه‌ی موجودیت‌ها ساده است (از همان الگوی
  push/pull استفاده کنید) اما در این نسخه پیاده نشده است.
