# Orario per Android

App nativa (Kotlin + Jetpack Compose, Material 3) che legge lo stesso foglio Google della PWA.

- **Si apre subito:** mostra l'orario salvato sul telefono e lo aggiorna in background,
  all'apertura (al massimo ogni 15 minuti) e ogni 6 ore con WorkManager. Se il foglio non è
  cambiato, lo schermo non si ridisegna.
- **Edge-to-edge:** barra di stato e barra gestuale trasparenti.
- **Vista giornaliera:** si scorre tra i giorni con lo swipe; la lezione in corso è evidenziata
  con una barra di avanzamento, e gli intervalli si colorano quando sono in corso.
- **Recap settimanale:** griglia con una colonna per giorno; tocca una casella per aprire quel giorno.
- **Tema:** colori di sistema (Material You) oppure un colore a scelta; chiaro, scuro o di sistema.
- **Aggiornamento manuale:** trascina verso il basso, oppure usa Impostazioni → Aggiorna ora.

## Il foglio Google

L'app legge gli stessi due blocchi della PWA. Nel blocco degli orari (quello con "Ore totali")
puoi aggiungere tre colonne facoltative, in qualsiasi posizione dopo "Giorno":

| Colonna               | Esempio | Se manca |
|-----------------------|---------|----------|
| `Inizio`              | `8:20`  | 8:20     |
| `Durata intervallo`   | `20`    | 20       |
| `Intervallo dopo ora` | `3`     | 3        |

Le colonne si riconoscono dal nome, quindi aggiungerle non rompe la PWA.

I link del foglio si trovano in `gradle.properties` (`orario.sheetCsvUrl` e `orario.sheetEditUrl`).

## Compilare e installare

Apri la cartella `android/` con Android Studio e premi Run. Da terminale, con il telefono
collegato in debug USB:

```bash
./gradlew installRelease
```

La build release è firmata con la chiave di debug: va bene per uso personale, ma non per il Play Store.
