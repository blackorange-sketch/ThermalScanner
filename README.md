# Thermal Scanner

Мінімальний прототип для Android — сканує всі `/sys/class/thermal/thermal_zoneN`,
показує `type` + поточну температуру, оновлюючи раз на 2 сек. Знизу — довідкові
дані з батареї (заряд, струм, напруга, оцінка потужності у ватах).

Мета: визначити, які thermal zones реально читаються без root на конкретному
пристрої (Snapdragon 8 Gen 2), щоб потім використати правильні назви зон
(cpu*, cpuss*, gpuss*, xo-therm тощо) в overlay-версії застосунку.

## Збірка

Відкрити папку в Android Studio (Giraffe/Koala чи новіше) — Gradle sync
підтягне все автоматично. Або з консолі, якщо встановлено Gradle wrapper:

```bash
./gradlew assembleDebug
```

APK з'явиться в `app/build/outputs/apk/debug/`.

> Примітка: у цьому архіві немає gradle-wrapper.jar (бінарний файл), тому
> перед збіркою з консолі виконай `gradle wrapper` один раз у відкритому
> Android Studio, або відкрий проєкт прямо в IDE — вона сама згенерує wrapper.

## Наступні кроки

- [ ] За результатами сканування на конкретному пристрої — зафіксувати
      відповідність зон (яка з них CPU, яка GPU) у налаштуваннях.
- [ ] Перенести логіку читання зон у сервіс.
- [ ] Додати floating overlay (`SYSTEM_ALERT_WINDOW` + `WindowManager`) поверх
      інших застосунків замість звичайної Activity.
- [ ] Додати CPU load через `/proc/stat`, RAM через `ActivityManager.MemoryInfo`.
- [ ] Налаштування розміру/прозорості/позиції вікна, збереження в SharedPreferences.

## Дозволи

Наразі дозволи на overlay ще не потрібні (це звичайна Activity, не floating
window). `BATTERY_STATS` в маніфесті додано про запас — на практиці для
використаних тут API він не обов'язковий, можна прибрати, якщо Android Studio
підсвітить його як зайвий.
