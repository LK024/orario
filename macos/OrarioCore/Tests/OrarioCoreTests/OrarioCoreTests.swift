import Testing
@testable import OrarioCore

// Copia del CSV pubblicato dal foglio reale (vecchio ordine delle colonne)
private let sheetCSV = """
Giorno,1ª ora,2ª ora,3ª ora,4ª ora,5ª ora,6ª ora,7ª ora,8ª ora,9ª ora,,,
Lunedì,3B | Lab 2,5D | Lab Rob | Bonelli,,1E | Lab Ele,3E | N2.03,,,,,,,
Martedì,3D | Lab 4 | Bonavoglia,3B | Lab Rob,,,3E | Lab 2,5E | Lab 2,,,,,,
Mercoledì,,,5E | 2.05,1E | Lab 2 | Saporito,3E | Lab 4 | Saporito,3D | N2.02,,,,,,
Giovedì,,1E | Lab 1 | Saporito,,5D | 2.03,,,,,,,,
Venerdì,,,,,5D | 2.03,3D | Lab Ele,5E | Lab 3 | Bonelli,,1E | Lab 2,,,
,,,,,,,,,,,,
Giorno,Ore totali,Durata pranzo,Ora pranzo,Durata 1ª ora,Durata 2ª ora,Durata 3ª ora,Durata 4ª ora,Durata 5ª ora,Durata 6ª ora,Durata 7ª ora,Durata 8ª ora,Durata 9ª ora
Lunedì,6,0,0,60,60,60,50,50,50,,,
Martedì,6,0,0,60,60,60,50,50,50,,,
Mercoledì,6,0,0,60,60,60,50,50,50,,,
Giovedì,6,0,0,60,60,60,50,50,50,,,
Venerdì,9,35,6,60,50,50,50,50,50,50,50,50
""".replacingOccurrences(of: "\n", with: "\r\n")

@Test func parsesLessonsAndConfigs() throws {
    let schedule = try ScheduleParser.parse(sheetCSV)
    #expect(schedule.days.map(\.name) == ["Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì"])
    #expect(schedule.days[0].lessons[1] == Lesson(title: "5D", room: "Lab Rob", teacher: "Bonelli"))
    #expect(schedule.days[0].lessons[2] == nil)
    #expect(schedule.days[0].weekday == 1)
    #expect(schedule.days[4].config.totalHours == 9)
    #expect(schedule.days[4].lessons[8] == Lesson(title: "1E", room: "Lab 2"))
}

@Test func timelineMatchesTheOtherApps() throws {
    let friday = try ScheduleParser.parse(sheetCSV).days[4]
    let labels = friday.timeline().map { slot -> String in
        let label: String
        switch slot.kind {
        case .lesson(let hour, _): label = "\(hour + 1)"
        case .pause(let kind): label = kind.label
        }
        return "\(label) \(formatMinutes(slot.start))-\(formatMinutes(slot.end))"
    }
    #expect(labels == [
        "1 8:20-9:20", "2 9:20-10:10", "3 10:10-11:00", "Intervallo 11:00-11:20",
        "4 11:20-12:10", "5 12:10-13:00", "6 13:00-13:50", "Pranzo 13:50-14:25",
        "7 14:25-15:15", "8 15:15-16:05", "9 16:05-16:55",
    ])
}

@Test func findsColumnsByNameInAnyOrder() throws {
    let csv = """
    Giorno,1ª ora,2ª ora
    Lunedì,3B,5D
    Giorno,Ore totali,Inizio,Durata intervallo,Intervallo dopo ora,Durata pranzo,Ora pranzo,Durata 1ª ora,Durata 2ª ora
    Lunedì,2,8.10.00,10,1,30,1,55,45
    """
    let config = try ScheduleParser.parse(csv).days[0].config
    #expect(config == DayConfig(totalHours: 2, durations: [55, 45], lunchDuration: 30, lunchAfter: 1,
                                start: 8 * 60 + 10, breakDuration: 10, breakAfter: 1))
}

@Test func handlesQuotedCells() {
    let rows = ScheduleParser.parseCSV("a,\"b, c\",\"d \"\"e\"\"\"\nf,\"g\nh\"")
    #expect(rows == [["a", "b, c", "d \"e\""], ["f", "g\nh"]])
}

@Test func overridesApplyOnlyToTheirDate() throws {
    let thursday = try ScheduleParser.parse(sheetCSV).days[3]
    let date = LocalDate(year: 2026, month: 10, day: 22)
    let overrides = [
        OverrideKey(date: date, hour: 2): Override(date: date, hour: 2, lesson: Lesson(title: "2A")),
        OverrideKey(date: date, hour: 1): Override(date: date, hour: 1, lesson: nil),
    ]
    let effective = thursday.forDate(date, overrides: overrides)
    #expect(effective.lessons[2] == Lesson(title: "2A"))
    #expect(effective.lessons[1] == nil)
    #expect(thursday.forDate(date.adding(days: 7), overrides: overrides) == thursday)
}

@Test func pendingChangesWinOverTheSheet() {
    let date = LocalDate(year: 2026, month: 10, day: 22)
    let merged = mergeWithPending(
        remote: [Override(date: date, hour: 1, lesson: Lesson(title: "3B")), Override(date: date, hour: 3, lesson: Lesson(title: "5D"))],
        pending: [PendingChange(date: date, hour: 1, lesson: Lesson(title: "2A")), PendingChange(date: date, hour: 3, delete: true)]
    )
    #expect(merged[OverrideKey(date: date, hour: 1)]?.lesson == Lesson(title: "2A"))
    #expect(merged[OverrideKey(date: date, hour: 3)] == nil)
}

@Test func remoteRowsUseOneBasedHours() {
    let row = SubstitutionsAPI.Remote(date: "2026-10-22", hour: 2, title: "3B", room: "1.05", teacher: nil)
    #expect(SubstitutionsAPI.toOverride(row) == Override(date: LocalDate(year: 2026, month: 10, day: 22), hour: 1, lesson: Lesson(title: "3B", room: "1.05")))
}

@Test func weekStartsOnMondayAndWeekendShowsNextWeek() {
    #expect(LocalDate(year: 2026, month: 10, day: 22).weekday == 4)
    #expect(LocalDate(year: 2026, month: 10, day: 22).displayedWeekStart == LocalDate(year: 2026, month: 10, day: 19))
    #expect(LocalDate(year: 2026, month: 10, day: 24).displayedWeekStart == LocalDate(year: 2026, month: 10, day: 26))
}
