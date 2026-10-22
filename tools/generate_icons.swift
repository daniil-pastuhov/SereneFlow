// Generates SereneFlow app icons for iOS and Android from one artwork definition.
//
//   swift tools/generate_icons.swift
//
// Run from the repository root. Writes:
//   iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/  (1024px light, dark, tinted + Contents.json)
//   androidApp/src/main/res/mipmap-*/ic_launcher(_round).png        (legacy icons, API < 26)
//   androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher(_round).xml (adaptive icon)
//   androidApp/src/main/res/drawable/ic_launcher_foreground.xml / ic_launcher_background.xml
//                                             / ic_launcher_monochrome.xml
//   store-listing/google-play/icon-512.png    (Play Console app icon; Play rounds the corners)
//
// Artwork (1024-unit square): a sage-green gradient, a white progress ring that is three-quarters
// complete with a round head, and a five-petal lotus inside — the app's timer motif.

import AppKit
import CoreGraphics
import Foundation

// MARK: - Artwork geometry (1024-unit space)

let center = CGPoint(x: 512, y: 512)
let ringRadius: CGFloat = 300
let ringStroke: CGFloat = 64
let ringSweep: CGFloat = 270 // degrees, clockwise from 12 o'clock
let headRadius: CGFloat = 50

struct Petal {
    let angle: CGFloat  // degrees; -90 points straight up
    let length: CGFloat
    let width: CGFloat  // perpendicular control-point offset
    let alpha: CGFloat
}

let petalBase = CGPoint(x: 512, y: 650)
// Broad, rounded petals (control offset ≈ 0.7 × length) so the shape reads as a lotus, not a leaf.
let petals: [Petal] = [
    Petal(angle: -172, length: 150, width: 100, alpha: 0.60),
    Petal(angle: -8, length: 150, width: 100, alpha: 0.60),
    Petal(angle: -134, length: 215, width: 150, alpha: 0.80),
    Petal(angle: -46, length: 215, width: 150, alpha: 0.80),
    Petal(angle: -90, length: 270, width: 185, alpha: 1.00),
]

/// Quadratic petal: base → tip on both sides, control points offset perpendicular at mid-length.
func petalPoints(_ p: Petal) -> (base: CGPoint, tip: CGPoint, c1: CGPoint, c2: CGPoint) {
    let rad = p.angle * .pi / 180
    let dir = CGPoint(x: cos(rad), y: sin(rad))
    let perp = CGPoint(x: -dir.y, y: dir.x)
    let tip = CGPoint(x: petalBase.x + dir.x * p.length, y: petalBase.y + dir.y * p.length)
    let mid = CGPoint(x: petalBase.x + dir.x * p.length * 0.5, y: petalBase.y + dir.y * p.length * 0.5)
    let c1 = CGPoint(x: mid.x + perp.x * p.width, y: mid.y + perp.y * p.width)
    let c2 = CGPoint(x: mid.x - perp.x * p.width, y: mid.y - perp.y * p.width)
    return (petalBase, tip, c1, c2)
}

func ringPoint(degreesFromTop: CGFloat) -> CGPoint {
    let rad = (degreesFromTop - 90) * .pi / 180
    return CGPoint(x: center.x + ringRadius * cos(rad), y: center.y + ringRadius * sin(rad))
}

// MARK: - Palettes

struct Palette {
    let backgroundTop: NSColor?      // nil = transparent background
    let backgroundBottom: NSColor?
    let artwork: NSColor
    let track: NSColor
}

func rgb(_ hex: UInt32, _ alpha: CGFloat = 1) -> NSColor {
    NSColor(
        srgbRed: CGFloat((hex >> 16) & 0xFF) / 255,
        green: CGFloat((hex >> 8) & 0xFF) / 255,
        blue: CGFloat(hex & 0xFF) / 255,
        alpha: alpha
    )
}

let lightPalette = Palette(
    backgroundTop: rgb(0x7FB096), backgroundBottom: rgb(0x2F5D46),
    artwork: .white, track: NSColor.white.withAlphaComponent(0.28)
)
// iOS dark appearance: dark background, artwork in the brand green.
let darkPalette = Palette(
    backgroundTop: rgb(0x1E2A23), backgroundBottom: rgb(0x0C120F),
    artwork: rgb(0x8CC3A5), track: rgb(0x8CC3A5, 0.25)
)
// iOS tinted appearance: grayscale artwork on black; the system applies the tint.
let tintedPalette = Palette(
    backgroundTop: .black, backgroundBottom: .black,
    artwork: .white, track: NSColor.white.withAlphaComponent(0.3)
)

// MARK: - Rendering

enum Mask { case none, roundedSquare, circle }

func render(size: Int, palette: Palette, artworkScale: CGFloat = 1, mask: Mask = .none) -> Data {
    let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size, bitsPerSample: 8,
        samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB,
        bytesPerRow: 0, bitsPerPixel: 0
    )!
    let ctx = NSGraphicsContext(bitmapImageRep: rep)!
    let cg = ctx.cgContext
    let scale = CGFloat(size) / 1024

    // Flip to a top-left origin so geometry matches the Android vector coordinates.
    cg.translateBy(x: 0, y: CGFloat(size))
    cg.scaleBy(x: scale, y: -scale)

    let full = CGRect(x: 0, y: 0, width: 1024, height: 1024)
    switch mask {
    case .none: break
    case .roundedSquare: cg.addPath(CGPath(roundedRect: full, cornerWidth: 180, cornerHeight: 180, transform: nil)); cg.clip()
    case .circle: cg.addEllipse(in: full); cg.clip()
    }

    if let top = palette.backgroundTop, let bottom = palette.backgroundBottom {
        let gradient = CGGradient(
            colorsSpace: CGColorSpace(name: CGColorSpace.sRGB),
            colors: [top.cgColor, bottom.cgColor] as CFArray, locations: [0, 1]
        )!
        cg.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: 1024, y: 1024), options: [])
    }

    // Scale artwork about the centre (legacy Android icons need a little more padding).
    cg.translateBy(x: 512, y: 512)
    cg.scaleBy(x: artworkScale, y: artworkScale)
    cg.translateBy(x: -512, y: -512)

    // Ring track and progress arc.
    cg.setLineCap(.round)
    cg.setLineWidth(ringStroke)
    cg.setStrokeColor(palette.track.cgColor)
    cg.addEllipse(in: CGRect(x: center.x - ringRadius, y: center.y - ringRadius, width: ringRadius * 2, height: ringRadius * 2))
    cg.strokePath()

    cg.setStrokeColor(palette.artwork.cgColor)
    cg.addArc(center: center, radius: ringRadius, startAngle: -.pi / 2, endAngle: (-90 + ringSweep) * .pi / 180, clockwise: false)
    cg.strokePath()

    let head = ringPoint(degreesFromTop: ringSweep)
    cg.setFillColor(palette.artwork.cgColor)
    cg.fillEllipse(in: CGRect(x: head.x - headRadius, y: head.y - headRadius, width: headRadius * 2, height: headRadius * 2))

    for petal in petals {
        let p = petalPoints(petal)
        cg.setFillColor(palette.artwork.withAlphaComponent(petal.alpha * palette.artwork.alphaComponent).cgColor)
        cg.move(to: p.base)
        cg.addQuadCurve(to: p.tip, control: p.c1)
        cg.addQuadCurve(to: p.base, control: p.c2)
        cg.closePath()
        cg.fillPath()
    }

    ctx.flushGraphics()
    return rep.representation(using: .png, properties: [:])!
}

// MARK: - Android vector output

/// Maps 1024-unit artwork into the 108dp adaptive-icon viewport, well inside the 66dp safe zone
/// (launcher masks show roughly the inner 72dp, so the ring keeps clear of the edge).
let adaptiveScale: CGFloat = 25 / (ringRadius + ringStroke / 2)
func av(_ v: CGFloat) -> String { String(format: "%.2f", 54 + (v - 512) * adaptiveScale) }
func ap(_ p: CGPoint) -> String { "\(av(p.x)),\(av(p.y))" }
func alphaHex(_ a: CGFloat) -> String { String(format: "#%02XFFFFFF", Int((a * 255).rounded())) }

func androidForeground(trackAlpha: CGFloat, monochrome: Bool) -> String {
    let r = String(format: "%.2f", ringRadius * adaptiveScale)
    let start = ringPoint(degreesFromTop: 0)
    let end = ringPoint(degreesFromTop: ringSweep)
    let stroke = String(format: "%.2f", ringStroke * adaptiveScale)
    let headR = String(format: "%.2f", headRadius * adaptiveScale)
    let headD = String(format: "%.2f", headRadius * adaptiveScale * 2)
    let trackTop = CGPoint(x: center.x, y: center.y - ringRadius)
    let trackBottom = CGPoint(x: center.x, y: center.y + ringRadius)

    var paths = """
        <!-- Ring track -->
        <path
            android:strokeColor="\(alphaHex(trackAlpha))"
            android:strokeWidth="\(stroke)"
            android:pathData="M\(ap(trackTop)) A\(r),\(r) 0 1,1 \(ap(trackBottom)) A\(r),\(r) 0 1,1 \(ap(trackTop))" />
        <!-- Progress arc (three quarters, clockwise from 12 o'clock) -->
        <path
            android:strokeColor="#FFFFFFFF"
            android:strokeWidth="\(stroke)"
            android:strokeLineCap="round"
            android:pathData="M\(ap(start)) A\(r),\(r) 0 1,1 \(ap(end))" />
        <!-- Progress head -->
        <path
            android:fillColor="#FFFFFFFF"
            android:pathData="M\(av(end.x - headRadius)),\(av(end.y)) a\(headR),\(headR) 0 1,0 \(headD),0 a\(headR),\(headR) 0 1,0 -\(headD),0" />

    """
    for petal in petals {
        let p = petalPoints(petal)
        let color = monochrome ? "#FFFFFFFF" : alphaHex(petal.alpha)
        paths += """
            <path
                android:fillColor="\(color)"
                android:pathData="M\(ap(p.base)) Q\(ap(p.c1)) \(ap(p.tip)) Q\(ap(p.c2)) \(ap(p.base)) Z" />

        """
    }
    return """
    <?xml version="1.0" encoding="utf-8"?>
    <!-- Generated by tools/generate_icons.swift; edit the script, not this file. -->
    <vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="108dp"
        android:height="108dp"
        android:viewportWidth="108"
        android:viewportHeight="108">
    \(paths)</vector>

    """
}

let androidBackground = """
<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/generate_icons.swift; edit the script, not this file. -->
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient
        android:angle="315"
        android:startColor="#FF7FB096"
        android:endColor="#FF2F5D46"
        android:type="linear" />
</shape>

"""

let adaptiveIcon = """
<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/generate_icons.swift; edit the script, not this file. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>

"""

// MARK: - Write files

let fm = FileManager.default
func write(_ data: Data, _ path: String) throws {
    try fm.createDirectory(atPath: (path as NSString).deletingLastPathComponent, withIntermediateDirectories: true)
    try data.write(to: URL(fileURLWithPath: path))
    print("wrote \(path)")
}
func write(_ text: String, _ path: String) throws { try write(Data(text.utf8), path) }

let iosDir = "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
try write(render(size: 1024, palette: lightPalette), "\(iosDir)/AppIcon.png")
try write(render(size: 1024, palette: darkPalette), "\(iosDir)/AppIcon-Dark.png")
try write(render(size: 1024, palette: tintedPalette), "\(iosDir)/AppIcon-Tinted.png")
try write("""
{
  "images" : [
    { "filename" : "AppIcon.png", "idiom" : "universal", "platform" : "ios", "size" : "1024x1024" },
    { "appearances" : [ { "appearance" : "luminosity", "value" : "dark" } ],
      "filename" : "AppIcon-Dark.png", "idiom" : "universal", "platform" : "ios", "size" : "1024x1024" },
    { "appearances" : [ { "appearance" : "luminosity", "value" : "tinted" } ],
      "filename" : "AppIcon-Tinted.png", "idiom" : "universal", "platform" : "ios", "size" : "1024x1024" }
  ],
  "info" : { "author" : "xcode", "version" : 1 }
}

""", "\(iosDir)/Contents.json")
try write("""
{
  "info" : { "author" : "xcode", "version" : 1 }
}

""", "iosApp/iosApp/Assets.xcassets/Contents.json")

let res = "androidApp/src/main/res"
let densities: [(String, Int)] = [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]
for (density, size) in densities {
    try write(render(size: size, palette: lightPalette, artworkScale: 0.86, mask: .roundedSquare), "\(res)/mipmap-\(density)/ic_launcher.png")
    try write(render(size: size, palette: lightPalette, artworkScale: 0.80, mask: .circle), "\(res)/mipmap-\(density)/ic_launcher_round.png")
}
try write(androidForeground(trackAlpha: 0.28, monochrome: false), "\(res)/drawable/ic_launcher_foreground.xml")
try write(androidForeground(trackAlpha: 0.35, monochrome: true), "\(res)/drawable/ic_launcher_monochrome.xml")
try write(androidBackground, "\(res)/drawable/ic_launcher_background.xml")
try write(adaptiveIcon, "\(res)/mipmap-anydpi-v26/ic_launcher.xml")
try write(adaptiveIcon, "\(res)/mipmap-anydpi-v26/ic_launcher_round.xml")

// Full-bleed square: Play Console applies its own corner mask and shadow.
try write(render(size: 512, palette: lightPalette), "store-listing/google-play/icon-512.png")
