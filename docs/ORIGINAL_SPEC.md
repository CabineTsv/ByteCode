> **Verbatim copy of the specification supplied by the project owner** (it was attached to the first
> chat as a text document). It is stored here so the requirements survive across chats. If this copy
> and the owner's own copy ever differ, the owner's copy wins.

ByteCode — Professional Android Mobile IDE

Project Overview

I want you to develop a fully functional Android code editor called ByteCode, inspired by Visual Studio Code but specifically designed, optimized, and adapted for mobile devices.

ByteCode should bring as much of the desktop VS Code experience as reasonably possible to Android while maintaining a clean, responsive, and professional mobile interface.

This is a real application development project, not a UI prototype or visual demonstration.

Every major feature must have a functional implementation. Prioritize stability, performance, maintainability, and user experience over implementing a large number of incomplete features.

---

1. Technology Stack and Architecture

Use the following technologies as the foundation of the project.

Android Application

- Kotlin as the primary programming language.
- Android SDK for native Android functionality.
- Gradle for dependency and build management.
- Material Design 3 principles where appropriate.
- Android Storage Access Framework for workspace and file management.

Code Editor Engine

Use CodeMirror 6 as the primary code editor engine, embedded inside an Android WebView.

CodeMirror 6 is preferred because of its modular architecture, extensibility, language support, and ability to provide a powerful editing experience without requiring an entirely custom editor implementation.

Requirements:

- Integrate CodeMirror 6 properly with the Android application.
- Use a local bundled version of the editor rather than depending on an external website.
- Establish a reliable communication bridge between Kotlin and JavaScript.
- Keep editor functionality separate from the native Android interface.
- Properly handle keyboard events, cursor positioning, text selection, scrolling, and touch interactions.
- Avoid unnecessary WebView reloads when switching files or tabs.
- Ensure the editor works offline.

Alternative Editor Engine

Monaco Editor may be evaluated as an alternative, but CodeMirror 6 should remain the primary choice.

Only use Monaco if its resource requirements and mobile compatibility are acceptable.

Do not integrate multiple editor engines unnecessarily.

Luau Runtime

Use the official Luau project as the foundation for the built-in Luau execution environment.

Official repository:

https://github.com/luau-lang/luau

Investigate the feasibility of integrating the Luau interpreter through:

- C++ using the Android NDK.
- JNI for communication between Kotlin and native code.
- A dedicated native runtime module.

The runtime should be independent of the editor engine.

Do not attempt to implement the entire Luau interpreter manually.

---

2. GitHub Repository and Build Automation

The entire project must be structured for hosting and development through GitHub.

Include:

- Complete Android application source code.
- Gradle configuration.
- Required assets and resources.
- CodeMirror 6 dependencies.
- Luau runtime integration.
- Documentation.
- GitHub Actions workflows.

Create a GitHub Actions workflow at:

".github/workflows/build.yml"

The workflow must:

- Support manual builds through "workflow_dispatch".
- Automatically build when changes are pushed.
- Support release builds.
- Set up the appropriate Java version.
- Configure the Android SDK.
- Install required Android NDK components if necessary.
- Build the APK using Gradle.
- Handle build failures correctly.
- Upload generated APK files as workflow artifacts.

Provide clear documentation explaining how to build the application locally and through GitHub Actions.

Do not include unnecessary dependencies or complicated build steps.

---

3. Main Application Interface

Design a professional IDE interface inspired by VS Code, adapted for Android.

The application should have three primary sections.

3.1 File Explorer

The file explorer should be located on the left side of the editor layout.

Features:

- Hierarchical file and folder tree.
- Expandable and collapsible directories.
- File and folder creation.
- File and folder deletion.
- Renaming.
- Copying and moving files.
- Importing existing files.
- Workspace directory selection.
- Context menus.
- Long-press actions.
- Language-specific file icons.
- File type recognition through extensions.

The explorer should support actual filesystem operations through Android's Storage Access Framework.

Do not use mock files or temporary UI representations.

3.2 Main Code Editor

The main editor should occupy the largest portion of the interface.

Its layout should include:

- File tabs positioned above the editor.
- Multiple simultaneously opened files.
- Tab switching.
- Tab closing.
- Unsaved changes indicators.
- Line numbers.
- Current-line highlighting.
- Syntax highlighting.
- Automatic indentation.
- Bracket matching.
- Code folding.
- Undo and redo.
- Find and replace.
- Text selection.
- Copy and paste.
- Configurable editor font.
- Configurable indentation size.
- Basic autocomplete.
- Keyboard shortcuts where practical.

The editor should maintain file states when switching between tabs.

Avoid reinitializing the entire editor every time a user opens another file.

3.3 Console and Output Panel

Provide a dedicated bottom panel for:

- Terminal output.
- Runtime output.
- Compilation output.
- Errors.
- Warnings.
- Application logs.
- Language diagnostics.

Features:

- Expandable and collapsible panel.
- Adjustable panel height.
- Clear output.
- Multiple output channels.
- Clickable error messages that navigate to the relevant source location.

The console should adapt depending on the selected language and available execution environment.

---

4. Mobile-First Interface Design

The interface should resemble a desktop IDE while remaining comfortable to use on a touchscreen.

Requirements:

- Responsive layout.
- Proper handling of portrait and landscape orientations.
- Adjustable explorer width.
- Collapsible explorer.
- Mobile-friendly file tabs.
- Proper Android keyboard integration.
- Touch-friendly controls without oversized buttons.
- Smooth transitions.
- Consistent spacing and typography.

On smaller screens, provide an intelligent layout that allows the explorer and console to be hidden or revealed without losing the current editor state.

The application should not simply shrink a desktop interface to fit a phone.

---

5. Programming Language Support

ByteCode should support a broad range of programming languages commonly used in VS Code.

Prioritize:

Language| Extension
HTML| ".html"
CSS| ".css"
JavaScript| ".js"
TypeScript| ".ts"
JSON| ".json"
Python| ".py"
Java| ".java"
Kotlin| ".kt"
C| ".c"
C++| ".cpp", ".cc", ".h", ".hpp"
C#| ".cs"
PHP| ".php"
Ruby| ".rb"
Go| ".go"
Rust| ".rs"
SQL| ".sql"
Shell| ".sh"
Lua| ".lua"
Luau| ".luau"
Markdown| ".md"
YAML| ".yml", ".yaml"
XML| ".xml"

Use CodeMirror 6 language packages and extensions wherever possible.

Requirements:

- Correct syntax highlighting.
- Automatic language detection.
- Language-specific indentation.
- Language-specific file icons.
- Comment toggling.
- Bracket matching.
- Basic autocomplete.
- Language-aware editor configurations.

Do not manually recreate syntax highlighting for languages that already have reliable CodeMirror extensions.

Do not advertise features that have not actually been implemented.

---

6. Dedicated Luau Language Support

Luau must be treated as a first-class language in ByteCode.

It should not simply reuse standard Lua syntax highlighting.

Use ".luau" as its primary file extension.

Syntax Highlighting

Implement dedicated Luau syntax highlighting that recognizes:

Roblox globals and objects

- "game"
- "workspace"
- "script"
- "Instance"
- "Enum"
- "Vector3"
- "Vector2"
- "CFrame"
- "UDim"
- "UDim2"
- "Color3"
- "BrickColor"
- "RaycastParams"
- "TweenInfo"

Roblox methods and APIs

- "GetService"
- "WaitForChild"
- "FindFirstChild"
- "FindFirstChildOfClass"
- "IsA"
- "Clone"
- "Destroy"
- "Connect"
- "Disconnect"
- "GetChildren"
- "GetDescendants"
- "GetAttribute"
- "SetAttribute"

Luau-specific syntax

- Type annotations.
- Generic types.
- Exported types.
- Compound assignments.
- Type casts.
- Optional types.
- Union types.
- Intersection types.
- String interpolation.
- "continue".

Distinguish Roblox-specific globals, built-in Luau functions, keywords, types, methods, and ordinary user-defined variables.

Luau Language Intelligence

Implement:

- Autocomplete.
- Roblox API suggestions.
- Luau keyword suggestions.
- Type-aware suggestions where possible.
- Common Roblox code snippets.
- Basic syntax diagnostics.
- Bracket matching.
- Proper indentation.

Use official Luau specifications and Roblox API documentation as references.

Do not assume that standard Lua language extensions fully support Luau.

---

7. Custom Luau Execution Environment

ByteCode must have a dedicated Luau execution environment.

Standard terminal functionality cannot directly execute Roblox scripts with actual Roblox engine functionality.

Therefore, implement a separate Luau runtime using the official Luau interpreter.

Runtime Architecture

Prefer:

- Luau C++ runtime.
- Android NDK.
- JNI communication.
- Kotlin runtime manager.
- Dedicated console output handler.

Execution Features

Support:

- Executing standalone Luau scripts.
- Capturing "print()" output.
- Capturing "warn()" output.
- Displaying runtime errors.
- Displaying execution status.
- Clearing console output.
- Separating runtime output from editor diagnostics.

Roblox Compatibility

Clearly distinguish between standalone Luau execution and Roblox engine execution.

The standalone runtime must not pretend to provide Roblox's actual engine APIs.

For example, "game:GetService()" should not be presented as functional unless a genuine compatible implementation exists.

If Roblox engine functionality is unavailable, display an understandable error or compatibility warning.

Structure the runtime so that additional integrations can be added later.

---

8. Execution Support for Other Languages

Provide execution functionality for languages that have compatible environments.

Potential implementations include:

- JavaScript runtime.
- Python runtime.
- Shell execution.
- C/C++ compilation.

Investigate suitable Android-compatible runtimes and toolchains.

Do not assume that every language can be executed natively on Android.

Clearly distinguish between:

- Syntax highlighting.
- Autocomplete.
- Language intelligence.
- Code execution.
- Compilation.
- External dependencies.

Only provide execution buttons when the necessary runtime or compiler is available.

---

9. Themes and Customization

ByteCode should have a polished, professional visual identity.

Design Direction

Use a dark-first design inspired by VS Code.

Suggested aesthetic:

- Deep charcoal backgrounds.
- Subtle borders.
- Consistent typography.
- Carefully selected accent colors.
- Minimal visual clutter.
- Clean file explorer.
- Professional editor tabs.
- Consistent iconography.

Avoid:

- Excessive gradients.
- Unnecessary glassmorphism.
- Oversized rounded cards.
- Random decorative elements.
- Inconsistent spacing.
- Generic AI-generated dashboard layouts.

Theme Support

Include:

- Dark theme.
- Light theme.
- Custom accent colors.
- Syntax highlighting themes.
- Configurable editor font size.
- Font family selection.
- Tab size configuration.
- Word wrapping settings.
- Line number settings.
- Automatic indentation settings.

Persist user preferences between application launches.

---

10. File Management and Persistence

All file operations must work with actual files.

Implement:

- Persistent workspaces.
- Workspace reopening.
- File creation.
- File editing.
- File saving.
- Automatic saving.
- Configurable autosave.
- Unsaved changes indicators.
- File importing.
- File exporting.
- File renaming.
- File deletion.
- Recovery of unsaved changes where possible.

Use Android's Storage Access Framework for filesystem access.

Avoid requesting "MANAGE_EXTERNAL_STORAGE" unless absolutely necessary.

Ensure that workspace permissions remain available when the application is reopened.

---

11. Editor Settings and Productivity Features

Create a dedicated settings interface.

Include configurable options for:

- Editor font size.
- Editor font family.
- Tab size.
- Insert spaces instead of tabs.
- Word wrapping.
- Line numbers.
- Current-line highlighting.
- Auto-closing brackets.
- Auto-closing quotes.
- Automatic indentation.
- Minimap if practical.
- Theme.
- Autosave behavior.

Additional productivity features:

- Global file search.
- Search within the current file.
- Find and replace.
- Go to line.
- Recently opened files.
- Command palette if practical.
- Keyboard shortcuts.
- File breadcrumbs if practical.

Prioritize features that genuinely improve the mobile coding experience.

---

12. Performance and Optimization

ByteCode should run smoothly on mid-range Android devices.

Requirements:

- Efficient memory usage.
- Fast startup.
- Minimal unnecessary background processes.
- Efficient handling of large files.
- Asynchronous file operations.
- No unnecessary editor reloads.
- Smooth scrolling.
- Responsive cursor movement.
- Efficient syntax highlighting.
- Proper WebView lifecycle management.
- Proper native resource disposal.

Avoid memory leaks between editor sessions and tabs.

The application should remain usable when several files are open simultaneously.

---

13. Project Architecture and Code Quality

Keep the application modular and maintainable.

Suggested structure:

ByteCode/
├── .github/
│   └── workflows/
│       └── build.yml
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       ├── assets/
│   │       │   └── editor/
│   │       ├── res/
│   │       └── AndroidManifest.xml
├── editor/
│   ├── core/
│   ├── languages/
│   ├── themes/
│   └── bridge/
├── luau/
│   ├── native/
│   ├── runtime/
│   └── integration/
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md

This is a suggested structure. Adjust it when necessary to maintain a sensible architecture.

Separate:

- Native Android UI.
- CodeMirror editor.
- JavaScript and Kotlin bridge.
- Language configurations.
- Syntax highlighting.
- Language intelligence.
- Workspace management.
- File operations.
- Terminal.
- Luau runtime.
- Application settings.

Avoid excessively large source files.

Use meaningful class names, function names, and variable names.

Write readable, maintainable code.

Do not unnecessarily minify or compress source code.

---

14. Testing and Verification

Before considering ByteCode complete, verify the following:

Application

- Application launches successfully.
- No startup crashes.
- Layout adapts to different screen sizes.
- Settings persist correctly.

File Explorer

- Files can be created.
- Files can be renamed.
- Files can be deleted.
- Directories expand and collapse.
- Files can be imported.
- Workspaces reopen correctly.

Editor

- Multiple tabs work.
- File switching preserves editor state.
- Saving works.
- Autosave works.
- Syntax highlighting works.
- Automatic indentation works.
- Undo and redo work.
- Find and replace work.

Luau

- ".luau" files are recognized.
- Luau syntax highlighting works.
- Roblox globals are recognized.
- Autocomplete works where implemented.
- Standalone Luau execution works.
- Runtime output appears correctly.
- Runtime errors are displayed correctly.

Build System

- Gradle builds successfully.
- APK generation works.
- GitHub Actions workflow runs successfully.
- Generated APK is uploaded as an artifact.

Fix compilation errors, missing imports, unresolved dependencies, and runtime crashes before delivering the project.

Never claim that something has been tested if it has not.

---

15. Documentation

Create a comprehensive README containing:

- Project overview.
- Screenshots.
- Features.
- Supported languages.
- Technology stack.
- Architecture overview.
- Development setup.
- Build instructions.
- GitHub Actions instructions.
- Luau runtime explanation.
- Known limitations.
- Future development plans.

Include dependency licenses and attribution where required.

---

16. Development Approach

Do not attempt to generate the entire application in one enormous source file.

Develop the project in logical stages.

Stage 1: Foundation

- Android project setup.
- Gradle configuration.
- Main application structure.
- Basic navigation.
- Initial UI.

Stage 2: Editor Integration

- CodeMirror 6 integration.
- Kotlin and JavaScript bridge.
- Syntax highlighting.
- Editor tabs.
- Editor settings.

Stage 3: File Management

- File explorer.
- Workspace management.
- Android Storage Access Framework.
- File operations.
- Persistent storage.

Stage 4: Language Support

- Language configurations.
- File extension recognition.
- Language icons.
- Luau-specific syntax highlighting.
- Autocomplete and snippets.

Stage 5: Execution Environment

- Luau native runtime.
- JNI integration.
- Console output.
- Runtime error handling.
- Additional language execution where practical.

Stage 6: Polish

- Themes.
- Animations.
- Performance optimization.
- UI refinements.
- Testing.

Stage 7: Release

- GitHub Actions workflow.
- APK generation.
- README.
- Final verification.

Maintain a working build throughout development.

Do not move to the next stage while the current stage contains unresolved critical issues.

---

17. Important Development Rules

These requirements are mandatory:

1. Do not create a UI-only prototype.
2. Do not use placeholder functionality for major features.
3. Do not create buttons that do nothing.
4. Do not simulate successful file operations.
5. Do not pretend unsupported languages can execute.
6. Do not pretend standalone Luau has Roblox engine compatibility.
7. Do not sacrifice code quality for implementation speed.
8. Do not put the entire application into one source file.
9. Do not leave unresolved compilation errors.
10. Do not claim successful builds without actually verifying them.
11. Do not unnecessarily introduce dependencies.
12. Keep the application usable offline wherever possible.
13. Prioritize Android compatibility and touchscreen usability.
14. Keep the architecture extensible for future features.

If a feature cannot be implemented properly, establish a functional foundation and document its limitations instead of presenting an incomplete implementation as finished.

---

FINAL OBJECTIVE

Create ByteCode, a genuinely functional, professional-grade mobile IDE for Android.

It should combine the flexibility of VS Code, the mobile usability of a native Android application, the extensibility of CodeMirror 6, and dedicated Luau language and runtime support.

The final result should feel like an actual coding application that developers would want to use, not an AI-generated mockup.

Prioritize functionality, reliability, performance, and professional design above everything else.
