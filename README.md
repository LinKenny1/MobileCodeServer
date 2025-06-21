# Mobile Code Server

Turn your old Android phones into powerful code execution servers! This Android app allows you to run Python and Node.js code remotely on your device, making it perfect for IoT projects, automation, and distributed computing.

## Features

- **Python Execution**: Run Python code with support for popular libraries (Flask, NumPy, requests)
- **Node.js Execution**: Execute JavaScript/Node.js code using LiquidCore
- **Web Interface**: Access your server from any device on the network
- **Process Management**: Monitor and control running processes
- **REST API**: Programmatic access to code execution
- **Mobile Dashboard**: Native Android interface for local management

## Network Server Capabilities

The app includes a built-in HTTP server that provides:

- **Web Interface** (`/`): User-friendly web interface for code execution
- **Code Execution API** (`POST /execute`): Execute Python or Node.js code
- **Status API** (`GET /status`): Check server status
- **Process Management** (`GET /processes`, `DELETE /processes/{id}`): Monitor and control processes

## Installation

1. Download the APK or build from source
2. Install on Android device (API 24+)
3. Grant necessary permissions
4. Launch the app

## Usage

### Mobile Interface
1. Open the app on your Android device
2. Navigate to "Server Status" to start the HTTP server
3. Use "Code Editor" to write and execute code locally

### Web Interface
1. Start the server from the mobile app
2. Note the IP address and port (default: 8080)
3. Open `http://[device-ip]:8080` in any web browser
4. Write and execute Python or Node.js code remotely

### API Usage

Execute Python code:
```bash
curl -X POST http://[device-ip]:8080/execute \
  -H "Content-Type: application/json" \
  -d '{"code": "print(\"Hello from Python!\")", "language": "python"}'
```

Execute Node.js code:
```bash
curl -X POST http://[device-ip]:8080/execute \
  -H "Content-Type: application/json" \
  -d '{"code": "console.log(\"Hello from Node.js!\")", "language": "nodejs"}'
```

## Technical Details

- **Python Runtime**: Chaquopy (CPython for Android)
- **Node.js Runtime**: LiquidCore (V8 JavaScript engine)
- **HTTP Server**: Custom implementation using OkHttp
- **UI Framework**: Android Jetpack with Material Design
- **Minimum SDK**: API 24 (Android 7.0)

## Security Considerations

- Server runs on local network only
- No authentication by default (add your own if needed)
- Code execution is sandboxed within the Android app context
- Network security config allows cleartext traffic for local development

## Project Structure

```
app/src/main/java/com/mobileserver/app/
├── MainActivity.kt              # Main activity
├── CodeExecutionService.kt      # Python/Node.js execution service
├── HttpServerService.kt         # HTTP server implementation
├── CodeEditorFragment.kt        # Code editor interface
└── ServerStatusFragment.kt      # Server management interface
```

## Building from Source

1. Clone this repository
2. Open in Android Studio
3. Sync project with Gradle files
4. Build and run on device or emulator

## Contributing

Feel free to submit issues, feature requests, or pull requests to improve the app!

## License

This project is open source. See LICENSE file for details.