import Foundation

enum Config {
    /// Foglio Google con l'orario (stessi link della PWA e dell'app Android)
    static let sheetCSVURL = URL(string: "https://docs.google.com/spreadsheets/d/e/2PACX-1vTZ_6t0reKL-nFDetOjKelXAUJsPYYUmvYbDY-pM3Sz2Sn_Vfi5FQeFrKuq13lWPGFurd8l9Z0mb-jo/pub?output=csv")!
    static let sheetEditURL = URL(string: "https://docs.google.com/spreadsheets/d/1xEUNMLQwEq2fwvvZ5wKgq4wFBE4T25C1FseqDapnrgk/edit?usp=sharing")!

    /// Indirizzo e chiave dello script delle supplenze, letti da Secrets.plist (escluso da git).
    /// Senza il file la sincronizzazione è spenta e le supplenze restano su questo Mac.
    static let secrets: [String: String] = {
        guard let url = Bundle.main.url(forResource: "Secrets", withExtension: "plist"),
              let values = NSDictionary(contentsOf: url) as? [String: String] else { return [:] }
        return values
    }()

    static var syncURL: URL? { secrets["SyncURL"].flatMap(URL.init(string:)) }
    static var syncToken: String { secrets["SyncToken"] ?? "" }
}
