/**
 * Supplenze condivise tra le app Orario (Android, macOS, PWA).
 *
 * Lo script vive dentro il foglio dell'orario e risponde come app web:
 *   GET  ?token=…                      → elenco delle supplenze
 *   POST {token, changes: [...]}       → aggiunge, modifica o cancella righe
 *
 * Le supplenze stanno nella scheda "Supplenze", una riga per ora:
 *   Data | Ora | Classe | Aula | Docente
 * "Libera" nella colonna Classe indica un'ora resa libera solo per quel giorno.
 * Le righe non vengono mai cancellate da sole: la scheda fa da storico dell'anno.
 *
 * La chiave segreta NON sta qui (la repo è pubblica): va in Impostazioni progetto →
 * Proprietà script, con nome TOKEN. Istruzioni complete in README.md.
 */

const SHEET_NAME = 'Supplenze';
const HEADERS = ['Data', 'Ora', 'Classe', 'Aula', 'Docente'];
const FREE = 'Libera';

function doGet(e) {
  if (!authorized(e.parameter.token)) return json({ error: 'unauthorized' });
  return json({ substitutions: readAll() });
}

/**
 * changes: [{ date: "2026-10-22", hour: 2, title: "3B", room: "Lab 2", teacher: null, delete: false }]
 * hour parte da 1, come nel foglio. title vuoto o nullo = ora libera.
 */
function doPost(e) {
  const body = JSON.parse(e.postData.contents);
  if (!authorized(body.token)) return json({ error: 'unauthorized' });

  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    (body.changes || []).forEach(apply);
    sortRows();
  } finally {
    lock.releaseLock();
  }
  return json({ substitutions: readAll() });
}

/** Da eseguire una volta a mano: chiede l'autorizzazione e crea la scheda. */
function preparaFoglio() {
  getSheet();
}

function apply(change) {
  const sheet = getSheet();
  const rows = sheet.getLastRow() > 1 ? sheet.getRange(2, 1, sheet.getLastRow() - 1, 2).getValues() : [];
  const index = rows.findIndex(row => toIsoDate(row[0]) === change.date && Number(row[1]) === change.hour);

  if (change.delete) {
    if (index >= 0) sheet.deleteRow(index + 2);
    return;
  }
  const row = index >= 0 ? index + 2 : sheet.getLastRow() + 1;
  // Classe, Aula e Docente come testo: altrimenti Sheets trasforma un'aula "2.05" in un orario
  sheet.getRange(row, 3, 1, 3).setNumberFormat('@');
  sheet.getRange(row, 1, 1, HEADERS.length)
    .setValues([[fromIsoDate(change.date), change.hour, change.title || FREE, change.room || '', change.teacher || '']]);
}

function readAll() {
  const sheet = getSheet();
  if (sheet.getLastRow() < 2) return [];
  const range = sheet.getRange(2, 1, sheet.getLastRow() - 1, HEADERS.length);
  // Data e ora come valori, le colonne di testo come appaiono nel foglio
  const display = range.getDisplayValues();
  return range.getValues()
    .map((row, i) => [row[0], row[1], display[i][2], display[i][3], display[i][4]])
    .filter(row => toIsoDate(row[0]) && Number(row[1]) > 0)
    .map(([date, hour, title, room, teacher]) => {
      const name = String(title).trim();
      return {
        date: toIsoDate(date),
        hour: Number(hour),
        title: name && name.toLowerCase() !== FREE.toLowerCase() ? name : null,
        room: String(room).trim() || null,
        teacher: String(teacher).trim() || null,
      };
    });
}

function getSheet() {
  const spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = spreadsheet.getSheetByName(SHEET_NAME);
  if (!sheet) {
    // In fondo: la prima scheda resta l'orario, che è quella pubblicata in CSV
    sheet = spreadsheet.insertSheet(SHEET_NAME, spreadsheet.getNumSheets());
    sheet.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]).setFontWeight('bold');
    sheet.setFrozenRows(1);
    sheet.getRange('A2:A').setNumberFormat('dd/mm/yyyy');
    sheet.getRange('C2:E').setNumberFormat('@');
  }
  return sheet;
}

function sortRows() {
  const sheet = getSheet();
  if (sheet.getLastRow() > 2) {
    sheet.getRange(2, 1, sheet.getLastRow() - 1, HEADERS.length).sort([{ column: 1 }, { column: 2 }]);
  }
}

/** Accetta date vere del foglio e testi "2026-10-22" o "22/10/2026" scritti a mano. */
function toIsoDate(value) {
  if (value instanceof Date) return Utilities.formatDate(value, Session.getScriptTimeZone(), 'yyyy-MM-dd');
  const text = String(value).trim();
  let match = text.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (match) return text;
  match = text.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (match) return `${match[3]}-${match[2].padStart(2, '0')}-${match[1].padStart(2, '0')}`;
  return null;
}

function fromIsoDate(text) {
  const [year, month, day] = text.split('-').map(Number);
  return new Date(year, month - 1, day);
}

function authorized(token) {
  const expected = PropertiesService.getScriptProperties().getProperty('TOKEN');
  return Boolean(expected) && token === expected;
}

function json(value) {
  return ContentService.createTextOutput(JSON.stringify(value)).setMimeType(ContentService.MimeType.JSON);
}
