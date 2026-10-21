# ساخت APK و انتشار تغییرات Echo-Chat-2

این راهنما برای همین مخزن است و از PHP/MySQL استفاده می‌کند؛ Firebase و فایل `google-services.json` لازم نیستند.

## دریافت سورس

```bash
git clone https://github.com/Its-Amir-AMD/Echo-Chat-2.git
cd Echo-Chat-2
```

## بیلد محلی

پیش‌نیازها: Android Studio، JDK 21 و SDK مناسب پروژه.

در ویندوز:

```powershell
cd echochat-fixed-source
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

در Linux/macOS:

```bash
cd echochat-fixed-source
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

APK دیباگ در `app/build/outputs/apk/debug/app-debug.apk` ساخته می‌شود. نسخهٔ Release در `app/build/outputs/apk/release/` قرار می‌گیرد. اگر کلید خصوصی تنظیم نشده باشد، فایل Release با پسوند `unsigned` ساخته می‌شود و برای انتشار فروشگاهی مناسب نیست.

## گرفتن APK از GitHub Actions

1. تغییرات را در شاخهٔ مخزن push کنید یا Pull Request بسازید.
2. تب **Actions** را باز کنید و اجرای **Android CI** مربوط به آخرین commit را انتخاب کنید.
3. پس از سبز شدن تست‌ها، lint و build، artifact به نام `EchoChat-APK-builds` را دانلود کنید.
4. برای نصب و تست روی گوشی از `app-debug.apk` استفاده کنید. APK دیباگ برای انتشار در فروشگاه نیست.

## تنظیم امضای Release در GitHub Actions

فقط اگر صاحب پروژه می‌خواهی Release امضاشده بسازی:

1. یک upload keystore را روی دستگاه امن خود بساز و نسخهٔ خصوصی آن را خارج از Git نگه دار.
2. محتوای base64 فایل keystore را در Secret مخزن با نام `ENCODED_KEYSTORE` قرار بده.
3. سه Secret دیگر تعریف کن: `STORE_PASSWORD`، `KEY_PASSWORD` و در صورت نیاز `KEY_ALIAS` (پیش‌فرض alias برابر `upload` است).
4. یک commit جدید push کن یا workflow را دوباره اجرا کن.

اگر هیچ‌کدام از Secretهای امضا تنظیم نشده باشند، CI به‌صورت شفاف Release unsigned می‌سازد. کلید و رمزهای امضا را هرگز در سورس، ZIP عمومی یا لاگ‌های workflow قرار نده. برای به‌روزرسانی اپ منتشرشده، باید از همان کلید معتبر قبلی استفاده شود.

## راه‌اندازی بک‌اند PHP

طبق [server/README.md](server/README.md) API را روی HTTPS راه‌اندازی کن، فایل SQL در `server/database/schema.sql` را وارد کن و فایل پیکربندی خصوصی PHP را خارج از web root بساز. در ارتقاهای بعدی، `schema.sql` را دوباره import کن تا جدول `auth_attempts` اضافه شود؛ جداول موجود با `CREATE TABLE IF NOT EXISTS` حفظ می‌شوند.

قبل از انتشار عمومی، تست دو حساب/دو دستگاه و بازبینی امنیت سرور الزامی است. این پروژه هنوز MVP است و در وضعیت فعلی امکاناتی مانند آپلود رسانه، push notifications، read receipts و رمزنگاری سرتاسری را ارائه نمی‌کند.
