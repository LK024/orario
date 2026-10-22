# Orario per macOS

App nativa SwiftUI (macOS 26+) che legge lo stesso foglio Google della PWA e dell'app Android.

- **Griglia settimanale** come la pagina desktop della PWA: orari agli angoli, intervallo e pranzo,
  lezione in corso evidenziata col colore d'accento di sistema, tema chiaro e scuro automatici.
- **Barra dei menu**: la lezione in corso o la prossima di oggi; cliccando si apre la giornata
  (o il prossimo giorno di scuola). L'app resta attiva anche chiudendo la finestra.
- **Supplenze**: clic destro su un'ora → segna supplenza, libera solo quel giorno, ripristina.
  Sono sincronizzate con la scheda "Supplenze" del foglio (vedi [apps-script](../apps-script/README.md)).
- Si apre subito con i dati salvati e si aggiorna in background: all'avvio, ogni 15 minuti e
  al risveglio del Mac. ⌘R aggiorna a mano.

## Struttura

- `OrarioCore/`: pacchetto Swift con modello, lettura del CSV, orari, supplenze e client dello
  script, con i test (`swift test`). Stessa logica dell'app Android.
- `Orario/`: l'app SwiftUI.

## Sincronizzazione supplenze

Indirizzo e chiave dello script vanno in `Orario/Secrets.plist`, escluso da git:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>SyncURL</key>
    <string>https://script.google.com/macros/s/…/exec</string>
    <key>SyncToken</key>
    <string>la stessa chiave della proprietà TOKEN dello script</string>
</dict>
</plist>
```

Senza il file l'app funziona lo stesso, ma le supplenze restano su questo Mac.

## Compilare

Apri `Orario.xcodeproj` con Xcode e premi Run. Da terminale:

```bash
xcodebuild -project Orario.xcodeproj -scheme Orario -configuration Release -derivedDataPath build/DerivedData build
```

L'app è firmata "per l'esecuzione locale": va bene per uso personale, non per la distribuzione.
