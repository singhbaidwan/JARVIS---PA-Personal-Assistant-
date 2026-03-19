// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "jarvis-agent",
    platforms: [
        .macOS(.v12),
    ],
    products: [
        .executable(name: "jarvis-agent", targets: ["JarvisAgent"]),
    ],
    targets: [
        .executableTarget(
            name: "JarvisAgent",
            path: "Sources"
        ),
    ]
)
