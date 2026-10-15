# CRUD REST API

A small Java project that lets you **Create, Read, Update, and Delete (CRUD)** users through a REST API. User data is stored in a local SQLite database.

## What you need

- JDK 17 or newer. Check with `java -version` and `javac -version`.
- The SQLite JDBC driver in `lib\` (included with this project).

## Start the project

The easiest option on Windows is to open PowerShell in this project folder and run:

```powershell
.\serve.bat
```

This builds the Java files and starts the API on port 8080. Keep this window open while using the API. To choose another port:

```powershell
.\serve.bat 9090
```

Stop the server by pressing Ctrl+C.

To start the interactive menu instead, run:

```powershell
.\run.bat
```

The menu includes direct database operations, server controls, and a built-in HTTP client. The REST API itself is the main project; the menu is an optional way to explore it.

## Try the CRUD endpoints

The server runs at `http://localhost:8080`. The user API is `http://localhost:8080/api/users`.

| CRUD operation | HTTP request | What it does |
|---|---|---|
| Create | `POST /api/users` | Adds a user |
| Read all | `GET /api/users` | Lists users |
| Read one | `GET /api/users/1` | Finds the user whose ID is 1 |
| Update | `PUT /api/users/1` | Replaces that user's details |
| Delete | `DELETE /api/users/1` | Removes that user |

Use PowerShell in a second window while the server is running:

```powershell
# Read all users
Invoke-RestMethod -Uri http://localhost:8080/api/users -Method Get

# Create a user
$user = Invoke-RestMethod -Uri http://localhost:8080/api/users -Method Post `
  -ContentType "application/json" `
  -Body '{"name":"Ada Lovelace","email":"ada@example.com","age":36}'
$user

# Read the user that was just created
Invoke-RestMethod -Uri "http://localhost:8080/api/users/$($user.id)" -Method Get

# Update the user (PUT sends all three fields)
Invoke-RestMethod -Uri "http://localhost:8080/api/users/$($user.id)" -Method Put `
  -ContentType "application/json" `
  -Body '{"name":"Ada Byron","email":"ada@example.com","age":37}'

# Delete the user
Invoke-RestMethod -Uri "http://localhost:8080/api/users/$($user.id)" -Method Delete
```

The root address, `http://localhost:8080/`, displays a short service description. The API also accepts the shorter `/users` route.

## How the project is organized

```text
src/com/example/crud/
├── Main.java                 Starts the database and application
├── model/User.java           Represents a user
├── db/Database.java          Creates the SQLite database and connections
├── dao/UserDao.java          Runs SQL for CRUD operations
├── server/ApiServer.java     Starts the HTTP server and registers routes
├── server/UserHandler.java   Handles and validates API requests
├── util/Json.java            Reads and writes JSON
└── cli/Cli.java              Optional interactive menu
```

For example, when a `POST /api/users` request arrives, `UserHandler` reads and validates its JSON, `UserDao` inserts the values into SQLite, and the API returns the new user as JSON.

## Where the data is stored

The application creates `data\app.db` and the `users` table automatically. Data remains there when the program stops. The table has an automatically assigned `id` plus `name`, `email`, and `age`.

New and updated users need a non-empty name, an email containing `@`, and an age from 0 to 150. Email addresses must be unique. The API responds with `400` for invalid input, `404` if the requested user does not exist, and `409` if the email is already used.

## Optional: build and run with Java commands

From PowerShell in the project folder:

```powershell
$sources = Get-ChildItem -Path .\src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -cp ".\lib\*" -d .\out $sources
java -cp ".\out;.\lib\*" com.example.crud.Main server
```

To start the interactive menu using Java directly, replace the last command with:

```powershell
java -cp ".\out;.\lib\*" com.example.crud.Main
```

## Common problems

- **`java` or `javac` is not recognized:** Install a JDK and add its `bin` folder to `PATH`.
- **SQLite driver missing:** Check that the SQLite JDBC `.jar` is in `lib\`.
- **Port already in use:** Start with another port, for example `.\serve.bat 9090`.
- **Main class not found:** Run from the project folder and build before starting the application.
