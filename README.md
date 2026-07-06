# Employee Live Location Tracking System

Enterprise-grade Spring Boot application for real-time employee GPS tracking, stop detection, activity timelines, and admin reporting.

## Project Overview

This system enables organizations to track employee locations in real time using browser GPS, visualize movement on OpenStreetMap via Leaflet.js, detect stops, calculate daily travel distance using the Haversine formula, and generate exportable reports.

**Technology Stack:**
- **Backend:** Java 21, Spring Boot 3.3.5, Spring MVC, Spring Data JPA, Spring Security
- **Frontend:** HTML5, CSS3, Bootstrap 5, JavaScript, Fetch API
- **Database:** Microsoft SQL Server
- **Maps:** Leaflet.js + OpenStreetMap
- **Authentication:** Session-based (HTTP Session + Cookies)
- **Build:** Maven

---

## Features

### Module 1 - Authentication
- Employee and Admin login
- Session-based authentication (no JWT)
- Role-based access control
- Secure logout with session invalidation

### Module 2 - Employee Dashboard
- Welcome message with employee name
- Current tracking status (Online, Moving, Stopped, Offline)
- Today's travelled distance
- Current latitude/longitude
- Last updated timestamp
- Large interactive Leaflet map
- Today's activity timeline
- Manual refresh location button
- Auto GPS update every 10 minutes

### Module 3 - Admin Dashboard (Same Page)
- Admin panel visible only for ADMIN role
- Total / Online / Offline employee counts
- Employee list with status, distance, last updated
- View Employee button to focus map on selected employee
- Live locations on shared Leaflet map

### Module 4 - GPS Tracking
- Browser geolocation permission request
- Captures latitude, longitude, accuracy, timestamp
- Auto-update every 10 minutes
- Manual refresh button
- All updates persisted to SQL Server

### Module 5 - Distance Calculation
- Haversine formula between consecutive location points
- Today's total distance displayed in km

### Module 6 - Stop Detection
- Detects stops when employee remains within ~30 meters for 10+ minutes
- Stores start time, end time, duration
- Stop history displayed on dashboard

### Module 7 - Activity Timeline
- Login time
- Location updates
- Stops
- Logout time

### Module 8 - Reports
- Today's locations, distance, and stops
- Employee-wise reports
- Date-wise reports
- Export to Excel (.xlsx)
- Print report support

---

## Folder Structure

```
employee-location-tracker/
├── pom.xml
├── README.md
├── sql/
│   └── schema.sql
└── src/
    └── main/
        ├── java/com/employeetracker/
        │   ├── EmployeeLocationTrackerApplication.java
        │   ├── config/
        │   │   ├── CustomUserDetailsService.java
        │   │   ├── PasswordConfig.java
        │   │   ├── SecurityConfig.java
        │   │   ├── TrackingProperties.java
        │   │   └── WebConfig.java
        │   ├── controller/
        │   │   ├── AdminController.java
        │   │   ├── AuthController.java
        │   │   └── LocationController.java
        │   ├── dto/
        │   ├── entity/
        │   ├── exception/
        │   ├── repository/
        │   ├── service/
        │   └── util/
        └── resources/
            ├── application.properties
            └── static/
                ├── index.html
                ├── dashboard.html
                ├── css/style.css
                └── js/
                    ├── api.js
                    ├── auth.js
                    ├── dashboard.js
                    └── map.js
```

---

## Database Setup

### 1. Install SQL Server
Install Microsoft SQL Server (Developer or Express edition) and SQL Server Management Studio (SSMS).

### 2. Enable SQL Server Authentication
- Open SSMS → Connect → Server Properties → Security
- Select **SQL Server and Windows Authentication mode**
- Restart SQL Server service

### 3. Create Login (if needed)
```sql
CREATE LOGIN tracker_user WITH PASSWORD = 'YourStrong@Password123';
ALTER SERVER ROLE sysadmin ADD MEMBER tracker_user;
```

### 4. Run SQL Script
Open `sql/schema.sql` in SSMS and execute the entire script. This will:
- Create `EmployeeTrackerDB` database
- Create tables: `Users`, `EmployeeLocation`, `EmployeeStops`, `EmployeeActivity`
- Add primary keys, foreign keys, indexes
- Insert sample users and location data

---

## Configure application.properties

Edit `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=EmployeeTrackerDB;encrypt=true;trustServerCertificate=true
spring.datasource.username=sa
spring.datasource.password=YourStrong@Password123
```

Adjust:
- `localhost:1433` → your SQL Server host and port
- `username` / `password` → your SQL Server credentials

**Tracking settings (optional):**
```properties
tracking.stop.radius-meters=30
tracking.stop.duration-minutes=10
tracking.online.threshold-minutes=15
tracking.auto-update.interval-minutes=10
```

---

## How to Run the Project

### Prerequisites
- Java 21 JDK
- Maven 3.9+
- Microsoft SQL Server
- Modern browser with GPS support (Chrome recommended)

### Steps

1. **Clone/extract** the project
2. **Run** `sql/schema.sql` against SQL Server
3. **Configure** `application.properties` with your DB credentials
4. **Build and run:**

```bash
mvn clean package -DskipTests
mvn spring-boot:run
```

Or run the JAR directly:
```bash
java -jar target/employee-location-tracker-1.0.0.jar
```

5. **Open browser:** http://localhost:8080

---

## Default Login Credentials

| Role     | Username       | Password     |
|----------|----------------|--------------|
| Admin    | admin          | password123  |
| Employee | john.smith     | password123  |
| Employee | sarah.johnson  | password123  |
| Employee | michael.brown  | password123  |

---

## REST API Endpoints

### Authentication
| Method | Endpoint           | Description        |
|--------|--------------------|--------------------|
| POST   | /api/auth/login    | Login              |
| POST   | /api/auth/logout   | Logout             |
| GET    | /api/auth/me       | Current user info  |

### Employee APIs
| Method | Endpoint                | Description              |
|--------|-------------------------|--------------------------|
| POST   | /api/location/save      | Save GPS location        |
| GET    | /api/location/current   | Current location         |
| GET    | /api/location/history   | Location history by date |
| GET    | /api/location/distance  | Today's distance         |
| GET    | /api/location/stops     | Today's stops            |
| GET    | /api/location/activities| Today's activity timeline|

### Admin APIs
| Method | Endpoint                      | Description              |
|--------|-------------------------------|--------------------------|
| GET    | /api/admin/employees          | List all employees       |
| GET    | /api/admin/employee/{id}      | Employee details         |
| GET    | /api/admin/live-locations     | All live locations       |
| GET    | /api/admin/summary            | Online/offline summary   |
| GET    | /api/admin/report             | Generate report          |
| GET    | /api/admin/report/export      | Export report to Excel   |

---

## Troubleshooting Guide

### Cannot connect to SQL Server
- Verify SQL Server is running: `services.msc` → SQL Server (MSSQLSERVER)
- Check TCP/IP is enabled in SQL Server Configuration Manager
- Confirm port 1433 is open
- Use `trustServerCertificate=true` in JDBC URL for local dev

### Login fails with correct credentials
- Re-run `sql/schema.sql` to reset sample users
- Passwords are BCrypt-hashed; default is `password123`

### GPS not working
- Use HTTPS or localhost (browsers require secure context for geolocation in production)
- Allow location permission when prompted
- Employee role is required for GPS save (`/api/location/save`)

### Map not loading
- Check internet connection (OpenStreetMap tiles require network)
- Ensure Leaflet CDN is not blocked by firewall

### 403 Forbidden on API calls
- Session may have expired; log in again
- CSRF token is sent automatically via cookies for POST requests

### Build fails
- Ensure Java 21 is installed: `java -version`
- Set `JAVA_HOME` to JDK 21 path
- Run `mvn clean compile` to see detailed errors

### Hibernate validate fails on startup
- Run `sql/schema.sql` first
- Ensure `spring.jpa.hibernate.ddl-auto=validate` matches existing schema

---

## IntelliJ IDEA / STS Setup

1. **File → Open** → select project folder
2. Wait for Maven import to complete
3. Set Project SDK to **Java 21**
4. Run `EmployeeLocationTrackerApplication` main class
5. For browser GPS testing, use Chrome on `http://localhost:8080`

---

## License

Internal enterprise use. All rights reserved.
