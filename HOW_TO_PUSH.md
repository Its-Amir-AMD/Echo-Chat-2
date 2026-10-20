# نحوه‌ی اعمال این اصلاحات روی ریپازیتوری خودتان

من نمی‌توانم مستقیماً به حساب گیت‌هاب شما push کنم (به اعتبارنامه‌ی شما دسترسی
ندارم)، پس سه فایل برایتان آماده کردم. **روش اول (bundle) را توصیه می‌کنم** چون
تاریخچه‌ی commit را هم حفظ می‌کند.

## روش ۱ (توصیه‌شده): اعمال git bundle

این روش کل commit جدید را با پیام توضیحی کامل، روی شاخه‌ی `main` شما اعمال
می‌کند.

```bash
# 1) ریپازیتوری خودتان را کلون/باز کنید (اگر از قبل ندارید)
git clone https://github.com/Its-Amir-AMD/Echochat_amir.git
cd Echochat_amir

# 2) فایل echochat-fixes.bundle را کنار این پوشه قرار دهید، سپس:
git fetch echochat-fixes.bundle main:fixes-branch

# 3) تغییرات را مرور کنید (اختیاری اما پیشنهاد می‌شود)
git log fixes-branch -1
git diff main fixes-branch

# 4) اگر راضی بودید، merge یا rebase کنید روی main
git checkout main
git merge fixes-branch
git push origin main
```

## روش ۲: اعمال patch

اگر ترجیح می‌دهید فقط diff را به‌صورت یک patch روی برنچ فعلی‌تان اعمال کنید:

```bash
cd Echochat_amir
git am 0001-echochat-fixes.patch
git push origin main
```

(اگر `git am` به خاطر conflict شکست خورد، `git apply 0001-echochat-fixes.patch`
را امتحان کنید و بعد خودتان commit بگیرید.)

## روش ۳: جایگزینی دستی فایل‌ها (ساده‌ترین، بدون git)

اگر با git راحت نیستید، فایل `echochat-fixed-source.zip` را دانلود و از حالت
فشرده خارج کنید، و محتوای آن را روی پوشه‌ی پروژه‌ی فعلی‌تان کپی/جایگزین کنید
(فقط فایل‌هایی که نام مشابه دارند override می‌شوند). سپس:

```bash
git add -A
git commit -m "Fix responsive bugs, localization, add optional Firebase sync"
git push origin main
```

## قبل از push: این‌ها را حتماً بخوانید

- **من این پروژه را کامپایل نکرده‌ام** — محیط من به Android SDK یا سرورهای
  Gradle/Google Maven دسترسی شبکه‌ای ندارد. تمام ویرایش‌ها با مرور دستی دقیق
  syntax و امضای توابع انجام شده، اما توصیه‌ی جدی می‌کنم **قبل از push نهایی،
  یک‌بار در Android Studio خودتان Sync و Build بگیرید** تا مطمئن شوید.
- بک‌اند Firebase (`FirestoreSyncManager`, `FirebaseAuthManager`) کاملاً
  **اختیاری و بی‌اثر** است تا وقتی `google-services.json` واقعی وصل نکنید —
  اپ دقیقاً مثل قبل در حالت محلی کار می‌کند. راهنمای کامل فعال‌سازی در
  `app/FIREBASE_SETUP.md` داخل خود پروژه هست.
- خلاصه‌ی کامل همه‌ی تغییرات در پیام commit (`git log -1`) نوشته شده است.
