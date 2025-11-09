# 🐷 Oinkonomics

A concise, modern Android app for personal budgeting: track spending, manage categories, monitor debts, and keep subscriptions in check — all with a simple, persistent bottom navigation.

## Demo Video

<iframe width="560" height="315" src="https://www.youtube.com/embed/KPrnvvaC84I" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>

## Features
- **Home overview**: quick snapshot of recent activity and totals.
- **Categories (Piggy Banks)**: create budgets with max and spent amounts; edit anytime.
- **Expenses**: log purchases, assign to categories, optional receipt URI, date-based sorting.
- **Debts**: track total vs. paid, due dates, and progress.
- **Subscriptions**: list recurring charges with amount, icon, and next charge date.
- **Monthly goals**: set min/max spending goals stored per user.
- **Account**: authentication and profile area; returns to auth flow when session ends.

## Tech Stack
- Kotlin, AndroidX, Material Components
- Navigation Component, ViewBinding
- Firebase Firestore (coroutines), Gradle Kotlin DSL

## Getting Started
1. Clone and open in Android Studio:
   ```bash
   git clone https://github.com/yourusername/oinkonomics.git
   cd oinkonomics
   ```
2. Configure Firebase Firestore:
   - Create a Firebase project; enable Firestore (Native mode).
   - Add Android app with `applicationId` `com.example.oinkonomics`.
   - Place `google-services.json` into `app/`.
   - Or provide `FirebaseOptions` in code/resources.
3. Run on device/emulator (Gradle wrapper is included).

## Project Structure (key paths)
- `app/src/main/res/layout/` – screens and dialogs
- `app/src/main/res/menu/bottom_nav_menu.xml` – persistent bottom navigation
- `app/src/main/java/com/example/oinkonomics/` – activities, fragments, view models
- `app/src/main/java/com/example/oinkonomics/data/` – Firestore repository and models

## Contributing
Pull requests welcome. Use clear commit messages and keep changes scoped.

## License
MIT — see `LICENSE`.
