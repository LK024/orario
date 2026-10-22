// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "OrarioCore",
    platforms: [.macOS(.v26)],
    products: [
        .library(name: "OrarioCore", targets: ["OrarioCore"]),
    ],
    targets: [
        .target(name: "OrarioCore"),
        .testTarget(name: "OrarioCoreTests", dependencies: ["OrarioCore"]),
    ]
)
