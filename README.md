# 📘 Android Text Editor with Kotlin Support  

![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue?logo=kotlin)  
![Android](https://img.shields.io/badge/Android-9%2B-brightgreen?logo=android)  
![Platform](https://img.shields.io/badge/Platform-Mobile-lightgrey)  
![License](https://img.shields.io/badge/License-Educational-important)  

A lightweight yet powerful **Text Editor for Android**, designed for writing, editing, and testing **Kotlin code** directly on mobile devices. This project was developed as part of the **IS2205 – Mobile Application Design and Development** coursework, with the goal of combining core text editing features and developer-oriented enhancements such as syntax highlighting and compiler integration.  

---

## 🚀 Features  

### ✍️ Core Editor Functionality  
- Create, open, save, and manage files (`.txt`, `.kt`, `.java`, etc.)  
- Automatic file saving  
- Basic editing: **copy, paste, cut, undo, redo**  
- Real-time **character and word counting**  
- **Find and Replace** with support for *whole word* and *case sensitivity* options  

### 🎨 Syntax Highlighting  
- **Default Kotlin highlighting**: keywords, comments, strings, and other syntax elements  
- **Configurable highlighting** for other languages via external JSON/XML configuration files  

### ⚡ Compiler Integration  
- ADB-based connection to a **desktop Kotlin compiler**  
- Compile Kotlin files directly from the app  
- Real-time **error reporting** with clear messages displayed in the editor  

### 🛠️ Error Handling & Integration  
- Robust error display for compilation issues  
- Clear visual status for **success** or **failure** builds  
- Simplified, reliable compilation process tailored for mobile  

---

## 🧑‍💻 Technologies Used  
- **Programming Language**: Kotlin (primary)  
- **Platform**: Android (API 28+, Android 9 or higher recommended)  
- **UI**: Jetpack Compose & Material3 Design  
- **Compiler Connection**: Android Debug Bridge (ADB)  

---

## 📂 Project Structure  
- **Editor Module** – Core editing features  
- **Syntax Module** – Kotlin and configurable syntax highlighting  
- **Compiler Module** – ADB integration with Kotlin compiler  
- **UI Layer** – Clean Material3 UI for better editing experience  

---

## 📦 Installation  
1. Clone this repository  
   ```bash
   git clone https://github.com/yomalkannangara/Text_Editor.git
   cd TextEditorApp
   ```
2. Open the project in **Android Studio**  
3. Build and run on an Android device (API 28+)  
4. (Optional) Connect via **ADB** to enable compiler integration  

---

## 👥 Team Members  
- 23020458-DYAKannangara 

---

## 📜 License  
This project is developed for educational purposes as part of the **IS2205 – Mobile Application Design and Development** course.  
