-- ============================================================
-- Employee Live Location Tracking System - Database Schema
-- Microsoft SQL Server
-- ============================================================

IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'EmployeeTrackerDB')
BEGIN
    CREATE DATABASE EmployeeTrackerDB;
END
GO

USE EmployeeTrackerDB;
GO

-- Drop tables if re-running (child tables first)
IF OBJECT_ID('dbo.EmployeeActivity', 'U') IS NOT NULL DROP TABLE dbo.EmployeeActivity;
IF OBJECT_ID('dbo.EmployeeStops', 'U') IS NOT NULL DROP TABLE dbo.EmployeeStops;
IF OBJECT_ID('dbo.EmployeeLocation', 'U') IS NOT NULL DROP TABLE dbo.EmployeeLocation;
IF OBJECT_ID('dbo.Users', 'U') IS NOT NULL DROP TABLE dbo.Users;
GO

-- ============================================================
-- Users Table
-- ============================================================
CREATE TABLE dbo.Users (
    UserId          BIGINT IDENTITY(1,1) NOT NULL,
    EmployeeId      NVARCHAR(50)  NOT NULL,
    Name            NVARCHAR(150) NOT NULL,
    Email           NVARCHAR(200) NOT NULL,
    Username        NVARCHAR(100) NOT NULL,
    Password        NVARCHAR(255) NOT NULL,
    Role            NVARCHAR(20)  NOT NULL,
    Status          NVARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT PK_Users PRIMARY KEY (UserId),
    CONSTRAINT UQ_Users_EmployeeId UNIQUE (EmployeeId),
    CONSTRAINT UQ_Users_Email UNIQUE (Email),
    CONSTRAINT UQ_Users_Username UNIQUE (Username),
    CONSTRAINT CK_Users_Role CHECK (Role IN ('ADMIN', 'EMPLOYEE')),
    CONSTRAINT CK_Users_Status CHECK (Status IN ('ACTIVE', 'INACTIVE'))
);
GO

CREATE INDEX IX_Users_Role ON dbo.Users (Role);
CREATE INDEX IX_Users_Status ON dbo.Users (Status);
GO

-- ============================================================
-- EmployeeLocation Table
-- ============================================================
CREATE TABLE dbo.EmployeeLocation (
    LocationId      BIGINT IDENTITY(1,1) NOT NULL,
    UserId          BIGINT NOT NULL,
    Latitude        DECIMAL(10, 7) NOT NULL,
    Longitude       DECIMAL(10, 7) NOT NULL,
    Accuracy        DECIMAL(10, 2) NULL,
    LocationTime    DATETIME2 NOT NULL,
    CONSTRAINT PK_EmployeeLocation PRIMARY KEY (LocationId),
    CONSTRAINT FK_EmployeeLocation_Users FOREIGN KEY (UserId)
        REFERENCES dbo.Users (UserId) ON DELETE CASCADE
);
GO

CREATE INDEX IX_EmployeeLocation_UserId ON dbo.EmployeeLocation (UserId);
CREATE INDEX IX_EmployeeLocation_LocationTime ON dbo.EmployeeLocation (LocationTime);
CREATE INDEX IX_EmployeeLocation_UserId_LocationTime ON dbo.EmployeeLocation (UserId, LocationTime);
GO

-- ============================================================
-- EmployeeStops Table
-- ============================================================
CREATE TABLE dbo.EmployeeStops (
    StopId          BIGINT IDENTITY(1,1) NOT NULL,
    UserId          BIGINT NOT NULL,
    Latitude        DECIMAL(10, 7) NOT NULL,
    Longitude       DECIMAL(10, 7) NOT NULL,
    StartTime       DATETIME2 NOT NULL,
    EndTime         DATETIME2 NULL,
    Duration        INT NULL,
    CONSTRAINT PK_EmployeeStops PRIMARY KEY (StopId),
    CONSTRAINT FK_EmployeeStops_Users FOREIGN KEY (UserId)
        REFERENCES dbo.Users (UserId) ON DELETE CASCADE
);
GO

CREATE INDEX IX_EmployeeStops_UserId ON dbo.EmployeeStops (UserId);
CREATE INDEX IX_EmployeeStops_StartTime ON dbo.EmployeeStops (StartTime);
CREATE INDEX IX_EmployeeStops_UserId_StartTime ON dbo.EmployeeStops (UserId, StartTime);
GO

-- ============================================================
-- EmployeeActivity Table (Activity Timeline support)
-- ============================================================
CREATE TABLE dbo.EmployeeActivity (
    ActivityId      BIGINT IDENTITY(1,1) NOT NULL,
    UserId          BIGINT NOT NULL,
    ActivityType    NVARCHAR(30) NOT NULL,
    Description     NVARCHAR(500) NULL,
    Latitude        DECIMAL(10, 7) NULL,
    Longitude       DECIMAL(10, 7) NULL,
    ActivityTime    DATETIME2 NOT NULL,
    ReferenceId     BIGINT NULL,
    CONSTRAINT PK_EmployeeActivity PRIMARY KEY (ActivityId),
    CONSTRAINT FK_EmployeeActivity_Users FOREIGN KEY (UserId)
        REFERENCES dbo.Users (UserId) ON DELETE CASCADE,
    CONSTRAINT CK_EmployeeActivity_Type CHECK (ActivityType IN ('LOGIN', 'LOGOUT', 'LOCATION_UPDATE', 'STOP'))
);
GO

CREATE INDEX IX_EmployeeActivity_UserId ON dbo.EmployeeActivity (UserId);
CREATE INDEX IX_EmployeeActivity_ActivityTime ON dbo.EmployeeActivity (ActivityTime);
CREATE INDEX IX_EmployeeActivity_UserId_ActivityTime ON dbo.EmployeeActivity (UserId, ActivityTime);
GO

-- ============================================================
-- Sample Data
-- Password for all users: password123
-- BCrypt hash generated with strength 10
-- ============================================================
INSERT INTO dbo.Users (EmployeeId, Name, Email, Username, Password, Role, Status)
VALUES
('EMP001', 'John Smith',    'john.smith@company.com',    'john.smith',    '$2b$10$1Db0m3B6vsejnV8/83mAfuZONBB2tn1m7582CDGXpS5GTWpex0ON.', 'EMPLOYEE', 'ACTIVE'),
('EMP002', 'Sarah Johnson', 'sarah.johnson@company.com', 'sarah.johnson', '$2b$10$1Db0m3B6vsejnV8/83mAfuZONBB2tn1m7582CDGXpS5GTWpex0ON.', 'EMPLOYEE', 'ACTIVE'),
('EMP003', 'Michael Brown', 'michael.brown@company.com', 'michael.brown', '$2b$10$1Db0m3B6vsejnV8/83mAfuZONBB2tn1m7582CDGXpS5GTWpex0ON.', 'EMPLOYEE', 'ACTIVE'),
('ADM001', 'Admin User',    'admin@company.com',         'admin',         '$2b$10$1Db0m3B6vsejnV8/83mAfuZONBB2tn1m7582CDGXpS5GTWpex0ON.', 'ADMIN',    'ACTIVE');
GO

-- Sample locations for today (adjust dates as needed when testing)
DECLARE @Today DATE = CAST(GETDATE() AS DATE);

INSERT INTO dbo.EmployeeLocation (UserId, Latitude, Longitude, Accuracy, LocationTime)
VALUES
(1, 28.6139390, 77.2090210, 15.00, DATEADD(HOUR, -3, GETDATE())),
(1, 28.6145000, 77.2100000, 12.00, DATEADD(HOUR, -2, GETDATE())),
(1, 28.6152000, 77.2115000, 10.00, DATEADD(MINUTE, -30, GETDATE())),
(2, 19.0760900, 72.8774260, 18.00, DATEADD(HOUR, -1, GETDATE())),
(2, 19.0770000, 72.8785000, 14.00, DATEADD(MINUTE, -20, GETDATE())),
(3, 12.9715990, 77.5945660, 20.00, DATEADD(HOUR, -5, GETDATE()));
GO

INSERT INTO dbo.EmployeeStops (UserId, Latitude, Longitude, StartTime, EndTime, Duration)
VALUES
(1, 28.6145000, 77.2100000, DATEADD(HOUR, -2, GETDATE()), DATEADD(HOUR, -1, GETDATE()), 3600);
GO

INSERT INTO dbo.EmployeeActivity (UserId, ActivityType, Description, Latitude, Longitude, ActivityTime, ReferenceId)
VALUES
(1, 'LOGIN', 'Employee logged in', NULL, NULL, DATEADD(HOUR, -4, GETDATE()), NULL),
(1, 'LOCATION_UPDATE', 'Location updated', 28.6139390, 77.2090210, DATEADD(HOUR, -3, GETDATE()), 1),
(1, 'STOP', 'Employee stopped', 28.6145000, 77.2100000, DATEADD(HOUR, -2, GETDATE()), 1),
(1, 'LOCATION_UPDATE', 'Location updated', 28.6152000, 77.2115000, DATEADD(MINUTE, -30, GETDATE()), 3);
GO

PRINT 'Database schema and sample data created successfully.';
GO
