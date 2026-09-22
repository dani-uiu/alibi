# Alibi - Mock Location & Route Spoofer 

**Alibi** is an open-source, privacy-first Android application designed for advanced GPS mock location, realistic multi-stop route simulation, and anti-detection telemetry spoofing. Powered by Jetpack Compose, Material 3, OpenStreetMap (OsmDroid), and OSRM routing engine, Alibi delivers smooth, realistic location emulation for testing location-aware applications.

---

##  Key Features

  **Instant Single-Point Teleportation**
  - Spoof your location to any coordinate worldwide with one tap or by entering precise latitude and longitude.

  **Smart Drive Mode**
  - Emulates realistic vehicular dynamics using OSRM driving routes.
  - Automatically simulates acceleration, deceleration at traffic lights and major intersections, highway cruising, and cornering speed reduction.

  **Smart Walk Mode**
  - Simulates natural human walking behavior with realistic stride variation, lateral gait sway (~0.3m amplitude), street crosswalk pauses, and micro-pauses (e.g., checking phone, waiting at intersections).

  **Multi-Stop Route Simulation**
  - Plan complex routes with unlimited waypoints and custom per-leg travel modes (Car, Walking, or Direct Line).
  - Configure customizable stationary wait durations at individual stops.

  **Anti-Detection GPS Jitter & Telemetry**
  - Micro GPS noise injection (~0.5–1.2m jitter), realistic speed variance, dynamic accuracy fluctuation (1.0–2.5m), and calculated bearings to match true hardware GPS sensors and bypass simple mock detection heuristics.

  **Screen-Off Background Reliability**
  - Built with an Android Foreground Service and Partial WakeLock management, ensuring continuous, uninterrupted location spoofing even when the device screen is off or the app is in the background.

  **Nominatim Place Search & Custom Favorites**
  - Search global places and addresses using OpenStreetMap's Nominatim geocoding service.
  - Save frequently used coordinates as custom favorites with emojis for instant recall.

---

##  Open Source Tech Stack

- **Language:** 100% Modern [Kotlin](https://kotlinlang.org/)
- **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material Design 3 (M3)
- **Map & GIS:** [OsmDroid](https://github.com/osmdroid/osmdroid) (OpenStreetMap for Android)
- **Routing Engine:** [OSRM (Open Source Routing Machine)](http://project-osrm.org/) API with foot & vehicle profiles
- **Geocoding:** [Nominatim Search Engine](https://nominatim.openstreetmap.org/)
- **Location API:** Google Play Services Fused Location Provider Client & Android `LocationManager`
- **Asynchronous Execution:** Kotlin Coroutines & `StateFlow`

---

##  Architecture & Project Structure

The project follows clean Android architectural guidelines with clear package separation under `com.alibi.spoofer`:

```
app/src/main/java/com/alibi/spoofer/
├── MainActivity.kt                  # Main UI activity & Jetpack Compose entry point
├── Drawables.kt                     # Custom canvas-rendered map marker drawables
├── service/
│   └── LocationSpooferService.kt    # Foreground service handling GPS mock provider updates
├── ui/
│   ├── IconPreview.kt               # Adaptive icon preview component
│   └── theme/                       # Material 3 design tokens (Color, Theme, Type)
└── util/
    ├── CustomLocationRepository.kt  # Persistent storage for saved favorite locations
    ├── LatLng.kt                    # Geographic coordinate data structures & OsmDroid converters
    ├── NominatimSearch.kt           # Geocoding network client
    ├── RouteCalculator.kt           # Route interpolation, polyline decoding, & Smart Drive/Walk algorithms
    └── Waypoint.kt                  # Route waypoint data models
```

---

##  How to Build & Run

### Prerequisites
- Android Studio Ladybug (2024.2.1) or newer
- JDK 11 or higher
- Android SDK 35/37
- Physical Android device or Emulator running Android 7.0 (API level 24) or higher

### Steps to Run

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/alibi/spoofer.git
   cd spoofer
   ```

2. **Enable Developer Options on Android Device:**
   - Go to **Settings > About Phone** and tap **Build Number** 7 times.
   - Go to **Settings > System > Developer Options**.
   - Scroll down to **Select mock location app** and select **Alibi**.

3. **Build and Run via Gradle:**
   ```bash
   ./gradlew clean assembleDebug testDebugUnitTest
   ```
   Or open the project in Android Studio and click **Run 'app'** (`Shift + F10`).

---

##  License

Distributed under the MIT License. See `LICENSE` for more details.
