# 🐷 Oinkonomics – Smart Budgeting Made Simple

**Oinkonomics** is a modern budgeting app designed to help you take control of your personal finances.  
Track expenses, organize budgets, and build better financial habits, all in one simple and intuitive interface.

---

## 🌟 Features

✅ **Budget Categories**  
Create and customize categories for your spending, like Groceries, Rent, Transport, and Entertainment.

💸 **Expense Management**  
Record your daily expenses with ease. Assign them to categories and monitor how your spending evolves over time.

📊 **Financial Overview**  
Get a clear snapshot of your spending habits and total expenses for each category.

🧩 **Modular Architecture (Work in Progress)**  
The app is built with scalability in mind, future modules will include income tracking, visual analytics, and cloud sync.

---

## 🧰 Tech Stack

| Component | Technology |
|------------|-------------|
| **Language** | Kotlin |
| **Framework** | Android (AndroidX) |
| **Build System** | Gradle (Kotlin DSL) |
| **IDE** | Android Studio |

---

## 🚀 Getting Started

### Prerequisites
Before you begin, ensure you have the following installed:
- [Android Studio](https://developer.android.com/studio)
- Android SDK (path configured in `local.properties`)
- Gradle 8+ (included via `gradlew` and `gradlew.bat`)

### Setup Instructions
1. **Clone the repository:**
   ```bash
   git clone https://github.com/yourusername/oinkonomics.git
   cd oinkonomics
2. **Open in Android Studio**
Let Gradle sync automatically.

3. **Configure Firebase**
   - Create a Firebase project and enable **Firestore** in *Native mode*.
   - Add an Android app that matches the `applicationId` defined in `app/build.gradle.kts` (`com.example.oinkonomics`).
   - Download the generated `google-services.json` file and place it in the `app/` directory.
   - Enable **Anonymous Authentication** under *Build → Authentication → Sign-in method* so the app can open a Firestore session without prompting users to log in.
   - Alternatively, initialise Firebase with a custom `FirebaseOptions` configuration before using the repository.

4. **Run the app**
Choose a device or emulator and click ▶️ Run. The app now persists users, categories, and expenses in Firestore.

### 🤝 Contributing
We welcome contributions! To contribute:
Fork the repository
Create a new branch (feature/your-feature-name)
Commit and push your changes
Open a Pull Request
Please follow clean code practices and include brief commit messages.

### 🧾 License
This project is licensed under the MIT License.
See the LICENSE file for full details.
