# نحوه‌ی اعمال این اصلاحات روی ریپازیتوری خودتان و گرفتن APK

من نمی‌توانم مستقیماً به حساب گیت‌هاب شما push کنم (به اعتبارنامه‌ی شما دسترسی
ندارم) و نمی‌توانم در این محیط یک APK واقعی بسازم (دسترسی شبکه به سرورهای
Android SDK/Gradle ندارم). راه‌حل: یک GitHub Actions workflow آماده کرده‌ام که
وقتی خودتان push کنید، گیت‌هاب به‌صورت خودکار APK را می‌سازد.

## مرحله‌ی ۱: اعمال تغییرات (یکی از این سه روش)

### روش ۱ (توصیه‌شده): git bundle

```bash
git clone https://github.com/Its-Amir-AMD/Echochat_amir.git
cd Echochat_amir
git fetch /path/to/echochat-fixes.bundle main:fixes-branch
git log fixes-branch -5          # مرور تغییرات (اختیاری)
git checkout main
git merge fixes-branch
git push origin main
```

### روش ۲: patch

```bash
cd Echochat_amir
git am /path/to/echochat-fixes.patch
git push origin main
```

### روش ۳: جایگزینی دستی (بدون git)

فایل `echochat-fixed-source.zip` را از حالت فشرده خارج کنید، محتوایش را روی
پوشه‌ی پروژه‌ی فعلی‌تان کپی/جایگزین کنید، سپس:

```bash
git add -A
git commit -m "Fix responsive bugs, localization, CI, optional Firebase sync"
git push origin main
```

## مرحله‌ی ۲: گرفتن APK از GitHub Actions (خودکار، بعد از push)

بعد از `git push origin main`:

1. به صفحه‌ی ریپازیتوری‌تان در گیت‌هاب بروید → تب **Actions**.
2. یک workflow run به اسم «Android CI» می‌بینید که به‌طور خودکار شروع شده.
   صبر کنید تا کامل شود (معمولاً ۵ تا ۱۰ دقیقه — این‌بار واقعاً روی سرورهای
   گیت‌هاب build می‌شود، نه در محیط من، پس به تمام سرورهای لازم دسترسی دارد).
3. وقتی سبز شد (✅)، روی همان run کلیک کنید. پایین صفحه بخش **Artifacts** را
   می‌بینید:
   - **`EchoChat-debug-apk`** — همیشه ساخته می‌شود، بدون نیاز به هیچ تنظیم
     اضافه. این را دانلود، از حالت zip خارج و مستقیماً روی گوشی نصب کنید
     (`adb install app-debug.apk`، یا فایل apk را روی گوشی بفرستید و نصب
     کنید — باید اجازه‌ی "نصب از منابع ناشناس" را بدهید).
   - **`EchoChat-release-apk`** — فقط اگر مراحل زیر را هم انجام داده باشید
     ظاهر می‌شود.

### برای گرفتن نسخه‌ی release امضاشده (اختیاری، برای انتشار در Google Play)

```bash
keytool -genkeypair -v -keystore my-upload-key.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 my-upload-key.jks > keystore.b64   # در مک: base64 my-upload-key.jks | tr -d '\n' > keystore.b64
```

سپس در گیت‌هاب: **Settings → Secrets and variables → Actions → New repository secret**
و این سه مورد را اضافه کنید:
- `ENCODED_KEYSTORE` ← محتوای `keystore.b64`
- `STORE_PASSWORD` ← رمزی که برای keystore انتخاب کردید
- `KEY_PASSWORD` ← رمزی که برای کلید انتخاب کردید

بعد یک push جدید بزنید (یا از تب Actions دکمه‌ی "Run workflow" را بزنید).
این‌بار artifact دوم (`EchoChat-release-apk`) هم ساخته می‌شود — این نسخه
امضاشده و قابل آپلود در Google Play است.

**فایل `.jks` و رمزها را جایی امن نگه دارید و هرگز commit نکنید** — هر
آپدیت بعدی اپ باید با همین کلید امضا شود.

## نکات مهم قبل از انتشار نهایی

- بک‌اند Firebase (`FirestoreSyncManager`, `FirebaseAuthManager`) کاملاً
  اختیاری و بی‌اثر است تا `google-services.json` واقعی وصل نکنید. راهنمای
  کامل در `app/FIREBASE_SETUP.md` داخل پروژه.
- خلاصه‌ی کامل همه‌ی تغییرات کد در پیام‌های commit است (`git log`).
- توصیه می‌کنم حتی بعد از دیدن ✅ در Actions، یک‌بار APK دیباگ را روی گوشی
  واقعی خودتان نصب و چند دقیقه با آن کار کنید قبل از رفتن به سراغ release.
