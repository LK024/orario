import Foundation

/// Scarica l'orario dal CSV pubblicato del foglio.
public struct ScheduleSource: Sendable {
    public let csvURL: URL

    public init(csvURL: URL) {
        self.csvURL = csvURL
    }

    public func fetch() async throws -> Schedule {
        var components = URLComponents(url: csvURL, resolvingAgainstBaseURL: false)!
        // Evita copie in cache del CSV, come fa la PWA
        components.queryItems = (components.queryItems ?? []) + [URLQueryItem(name: "t", value: "\(Int(Date().timeIntervalSince1970))")]
        let (data, response) = try await URLSession.shared.data(from: components.url!)
        try Self.check(response)
        return try ScheduleParser.parse(String(decoding: data, as: UTF8.self))
    }

    static func check(_ response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }
    }
}

/// Client dello script Apps Script della scheda "Supplenze" (vedi apps-script/README.md).
public struct SubstitutionsAPI: Sendable {
    public let url: URL?
    public let token: String

    public init(url: URL?, token: String) {
        self.url = url
        self.token = token
    }

    public var isEnabled: Bool { url != nil && !token.isEmpty }

    public enum APIError: Error { case disabled, script(String) }

    /// Riga del foglio come la restituisce lo script: `hour` parte da 1.
    struct Remote: Codable {
        let date: String
        let hour: Int
        let title: String?
        let room: String?
        let teacher: String?
    }

    struct Change: Codable {
        let date: String
        let hour: Int
        let title: String?
        let room: String?
        let teacher: String?
        let delete: Bool
    }

    struct Request: Codable {
        let token: String
        let changes: [Change]
    }

    struct Response: Codable {
        let substitutions: [Remote]?
        let error: String?
    }

    public func fetch() async throws -> [Override] {
        guard let url else { throw APIError.disabled }
        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "token", value: token)]
        let (data, response) = try await URLSession.shared.data(from: components.url!)
        return try Self.decode(data, response)
    }

    /// Invia le modifiche e restituisce l'elenco aggiornato.
    public func push(_ changes: [PendingChange]) async throws -> [Override] {
        guard let url else { throw APIError.disabled }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("text/plain;charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(Request(token: token, changes: changes.map {
            Change(date: $0.date.description, hour: $0.hour + 1, title: $0.lesson?.title,
                   room: $0.lesson?.room, teacher: $0.lesson?.teacher, delete: $0.delete)
        }))
        let (data, response) = try await URLSession.shared.data(for: request)
        return try Self.decode(data, response)
    }

    static func decode(_ data: Data, _ response: URLResponse) throws -> [Override] {
        try ScheduleSource.check(response)
        let decoded = try JSONDecoder().decode(Response.self, from: data)
        if let error = decoded.error { throw APIError.script(error) }
        return (decoded.substitutions ?? []).compactMap(Self.toOverride)
    }

    static func toOverride(_ remote: Remote) -> Override? {
        guard let date = LocalDate(iso: remote.date) else { return nil }
        let lesson = remote.title.map { Lesson(title: $0, room: remote.room, teacher: remote.teacher) }
        return Override(date: date, hour: remote.hour - 1, lesson: lesson)
    }
}
