📘 Android Text Editor with Kotlin Support

A lightweight yet powerful Text Editor for Android, designed for writing, editing, and testing Kotlin code directly on mobile devices. This project was developed as part of the IS2205 – Mobile Application Design and Development coursework, with the goal of combining core text editing features and developer-oriented enhancements such as syntax highlighting and compiler integration.

🚀 Features
✍️ Core Editor Functionality

Create, open, save, and manage files (.txt, .kt, .java, etc.)

Automatic file saving

Basic editing: copy, paste, cut, undo, redo

Real-time character and word counting

Find and Replace with support for whole word and case sensitivity options

🎨 Syntax Highlighting

Default Kotlin highlighting: keywords, comments, strings, and other syntax elements

Configurable highlighting for other languages via external JSON/XML configuration files

⚡ Compiler Integration

ADB-based connection to a desktop Kotlin compiler

Compile Kotlin files directly from the app

Real-time error reporting with clear messages displayed in the editor

🛠️ Error Handling & Integration

Robust error display for compilation issues

Clear visual status for success or failure builds

Simplified, reliable compilation process tailored for mobile

🧑‍💻 Technologies Used

Programming Language: Kotlin (primary)

Platform: Android (API 28+, Android 9 or higher recommended)

UI: Jetpack Compose & Material3 Design

Compiler Connection: Android Debug Bridge (ADB)

📂 Project Structure

Editor Module – Core editing features

Syntax Module – Kotlin and configurable syntax highlighting

Compiler Module – ADB integration with Kotlin compiler

UI Layer – Clean Material3 UI for better editing experience
