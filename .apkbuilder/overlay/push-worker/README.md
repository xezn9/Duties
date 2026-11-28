# Duties push Worker (مجاني)

1. Firebase Console > Project settings > Service accounts > Generate new private key (ينزل ملف JSON).
2. Cloudflare (dash.cloudflare.com) > Workers & Pages > Create > Hello World > Deploy > Edit code:
   امسح كل شي والصق محتوى `worker.js` > Deploy.
3. Worker > Settings > Variables and Secrets:
   - FIREBASE_PROJECT_ID = duties-a11bf        (Text)
   - DB_URL = https://duties-a11bf-default-rtdb.firebaseio.com   (Text — انسخه من تبويب Realtime Database)
   - SERVICE_ACCOUNT = محتوى ملف الـ JSON كله   (Secret)
4. انسخ رابط الـ Worker وحطه بـ `PushConfig.WORKER_URL` داخل `app/src/main/java/com/duties/Push.kt`.
5. انشر `database.rules.json` الجديد بـ Firebase (Realtime Database > Rules) — لازم تنشره من جديد حتى تشتغل طلبات الانضمام للقنوات الخاصة (عقدة `requests`).
