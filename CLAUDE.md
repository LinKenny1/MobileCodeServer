# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Mobile Code Server is an Android application that transforms old Android devices into remote code execution servers. The app enables execution of Python and Node.js code both locally through a native interface and remotely via a built-in HTTP server with REST API.

## Build Commands

### Basic Build Operations
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK  
./gradlew assembleRelease

# Install debug build to connected device
./gradlew installDebug

# Clean build artifacts
./gradlew clean
```

### Testing
```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests "*CodeExecutionServiceTest*"
```

### Development
```bash
# Lint check
./gradlew lint

# Generate lint report
./gradlew lintDebug

# Check dependencies for updates
./gradlew dependencyUpdates
```

## Architecture Overview

The application follows a service-oriented architecture with two main background services and a fragment-based UI:

### Core Services Architecture
- **CodeExecutionService**: Handles Python (via Chaquopy) and Node.js (via LiquidCore) code execution
- **HttpServerService**: Provides HTTP server functionality with REST API endpoints
- Both services use executor thread pools for concurrent code execution
- Process management through ConcurrentHashMap tracking running executions

### Service Communication Pattern
```
MainActivity → starts both services
    ↓
HttpServerService → binds to → CodeExecutionService
    ↓
Fragments → bind to services via ServiceConnection
```

### Python Integration (Chaquopy)
- Chaquopy configuration in `app/build.gradle` includes Flask, requests, numpy
- Python runtime initialized once per service lifecycle
- Code execution wrapped in executor futures for async processing
- Python interpreter runs in Android app context with library access

### Node.js Integration (LiquidCore)
- LiquidCore provides V8 JavaScript engine for Android
- Each execution creates new LiquidService instance
- Event-driven communication pattern using `LiquidCore.emit()`
- Code wrapped in try-catch for error handling

### HTTP Server Implementation
- Custom HTTP server using raw ServerSocket (not framework-based)
- Manual HTTP request parsing and response generation
- JSON API using Gson for serialization
- Embedded HTML web interface served from string literals
- CORS headers included for cross-origin requests

### UI Architecture
- Navigation Component with two main fragments:
  - `ServerStatusFragment`: Server management and status display
  - `CodeEditorFragment`: Local code editing and execution
- ViewBinding enabled for type-safe view references
- Service binding pattern for fragment-service communication

## Key Configuration Files

### Gradle Configuration
- `app/build.gradle`: Contains Chaquopy Python package installations
- NDK ABI filters limited to `arm64-v8a` and `x86_64` for size optimization
- Target SDK 34 with minimum SDK 24 (Android 7.0)

### Network Security
- `network_security_config.xml`: Permits cleartext traffic for local network communication
- Required for HTTP server functionality on local networks

### Permissions
- INTERNET and ACCESS_NETWORK_STATE for network operations
- WAKE_LOCK to prevent device sleep during code execution
- Storage permissions for potential file operations

## Development Notes

### Adding New Python Packages
Modify the `python` block in `app/build.gradle`:
```gradle
python {
    buildPython "/usr/bin/python3"
    pip {
        install "new-package-name"
    }
}
```

### HTTP API Endpoints
- `GET /`: Web interface (HTML served from string)
- `POST /execute`: Execute code with JSON payload `{"code": "...", "language": "python|nodejs"}`
- `GET /status`: Server status information
- `GET /processes`: List running processes
- `DELETE /processes/{id}`: Stop specific process

### Service Lifecycle Management
Both services are started from MainActivity but designed to run independently. Services use LocalBinder pattern for fragment communication. Process tracking uses UUID-based identifiers for execution management.

### Error Handling Patterns
- ExecutionResult data class encapsulates success/failure states
- Thread-safe process management with ConcurrentHashMap
- Executor service handles concurrent executions with proper cancellation support

### Memory Considerations
- Python interpreter initialized once per service instance
- LiquidCore creates new V8 contexts per execution
- Executor thread pools use cached thread pools for scalability
- Process futures stored in memory until completion or manual cleanup