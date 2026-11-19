<p align="center"><img src="docs/icon.svg" width="128" alt="Alibi icon"></p>

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

- **Language:** 100% [Kotlin](https://kotlinlang.org/)
- **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3, a `ViewModel` holding all screen state, English and Spanish strings
- **Map:** [OsmDroid](https://github.com/osmdroid/osmdroid) with OpenStreetMap tiles
- **Search:** [Photon](https://photon.komoot.io/) for search-as-you-type (biased toward the visible map area) and [Nominatim](https://nominatim.openstreetmap.org/) when you press search. Coordinates and map links are recognised offline.
- **Routing:** [OSRM](http://project-osrm.org/) car and foot profiles, including per-segment road speeds for Smart Drive
- **Location API:** Fused Location Provider and `LocationManager` test providers
- **Async:** Kotlin Coroutines and `StateFlow`

---

##  Architecture & Project Structure

```
app/src/main/java/com/alibi/spoofer/
├── MainActivity.kt                  # Entry point: sets the Compose content
├── Drawables.kt                     # Canvas-drawn map marker icons
├── service/
│   └── LocationSpooferService.kt    # Foreground service that plays a location or route, one fix per second
├── ui/
│   ├── SpooferViewModel.kt          # Screen state, route calculation, search, saved places
│   ├── SpooferScreen.kt             # Map + search + bottom sheet layout, permissions, dialogs
│   ├── ControlSheet.kt              # Status header, setup guidance, mode switch, saved places
│   ├── RouteEditor.kt               # Stops timeline and per-leg settings
│   ├── SearchBar.kt                 # Search field and results
│   ├── OsmMap.kt                    # OsmDroid map wrapper
│   ├── Dialogs.kt, Format.kt        # Dialogs and formatting helpers
│   └── theme/                       # Material 3 colours, type and shapes
└── util/
    ├── RouteCalculator.kt           # OSRM routing, polyline decoding, Smart Drive/Walk sampling
    ├── PlaceSearch.kt               # Photon + Nominatim geocoding
    ├── CoordinateParser.kt          # "lat, lng", comma decimals, map links, geo: URIs
    ├── Http.kt                      # Shared HTTP client with typed errors
    ├── CustomLocationRepository.kt  # Saved places (SharedPreferences)
    ├── Waypoint.kt                  # Waypoint and SimulatedRoute models
    └── LatLng.kt                    # Coordinate type and OsmDroid converters
```

How a route plays: the ViewModel builds a `SimulatedRoute` (one point per second, with waits stored as timers rather than repeated points) and hands it to the service in-process, so long routes aren't limited by Android's ~1 MB Intent size. When the route finishes, the location stays at the destination until you press Stop.

---

##  Privacy

- Saved places stay on the device. They are excluded from Google cloud backup (they can contain addresses like "Home") but move with a direct phone-to-phone transfer.
- Search text and route coordinates are sent to the public OpenStreetMap services listed above, identified only by the app name and version.
- No analytics, no accounts, no ads.

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
   git clone https://github.com/dani-uiu/alibi.git
   cd alibi
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
