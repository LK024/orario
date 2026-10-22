# Supplenze condivise (Google Apps Script)

Script da incollare nel foglio dell'orario. Salva le supplenze in una scheda "Supplenze"
del foglio e permette alle app di leggerle e modificarle, senza login Google nelle app.

| Data       | Ora | Classe | Aula  | Docente |
|------------|-----|--------|-------|---------|
| 22/10/2026 | 2   | 3B     | Lab 2 |         |
| 23/10/2026 | 4   | Libera |       |         |

- Una riga per ora; `Libera` nella colonna Classe = ora resa libera solo quel giorno.
- Le righe restano come storico dell'anno: a settembre puoi svuotare la scheda.
- Puoi aggiungere righe anche a mano dal foglio, per qualsiasi data.

## Installazione

1. Apri il foglio dell'orario → **Estensioni → Apps Script**.
2. Cancella il contenuto di `Codice.gs`, incolla quello di [`Supplenze.gs`](Supplenze.gs) e salva.
3. **Impostazioni progetto** (ingranaggio a sinistra) → **Proprietà script** → **Aggiungi proprietà**:
   nome `TOKEN`, valore la chiave segreta (la stessa di `orario.syncToken` in `android/local.properties`).
4. Torna all'editor, scegli la funzione `preparaFoglio` in alto e premi **Esegui**:
   Google chiede l'autorizzazione (è il tuo script sul tuo foglio) e crea la scheda "Supplenze".
5. **Esegui il deployment → Nuovo deployment** → tipo **App web**:
   - Esegui come: **Me**
   - Chi ha accesso: **Chiunque**
6. Copia l'**URL dell'app web** (finisce con `/exec`) in `android/local.properties` come `orario.syncUrl`.

"Chiunque" serve perché le app non fanno il login Google: senza la chiave lo script risponde
solo `unauthorized`. La chiave non va mai messa nella repo, che è pubblica.

Se modifichi lo script, ripubblicalo con **Gestisci deployment → modifica → Nuova versione**:
così l'URL resta lo stesso.
