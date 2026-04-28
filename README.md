<<<<<<< HEAD
# DocConvert Pro - Complete Document Conversion Suite

A full-stack document conversion application built with **Spring Boot 3.2**, **MySQL**, and optional **AWS S3** integration. Features user authentication, file management, OCR capabilities, and 16+ document conversion tools.

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen)
![Java](https://img.shields.io/badge/Java-17+-blue)
![MySQL](https://img.shields.io/badge/MySQL-8.0+-orange)
![License](https://img.shields.io/badge/License-MIT-green)

---

## 📋 Table of Contents

- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [Prerequisites](#-prerequisites)
- [Installation](#-installation)
- [Configuration](#-configuration)
- [Running the Application](#-running-the-application)
- [API Endpoints](#-api-endpoints)
- [Project Structure](#-project-structure)
- [Database Schema](#-database-schema)
- [Storage Limits](#-storage-limits)
- [Troubleshooting](#-troubleshooting)
- [License](#-license)

---

## ✨ Features

### Document Conversions (16 Tools)

| # | Conversion | Input | Output |
|---|------------|-------|--------|
| 1 | Word to PDF | .doc, .docx | .pdf |
| 2 | PDF to Word | .pdf | .docx |
| 3 | Compress PDF | .pdf | .pdf (smaller) |
| 4 | Excel to PDF | .xls, .xlsx | .pdf |
| 5 | PPT to PDF | .ppt, .pptx | .pdf |
| 6 | JPG to PDF | .jpg, .png | .pdf |
| 7 | PDF OCR | .pdf, .jpg | Extracted text |
| 8 | Merge PDF | Multiple .pdf | Single .pdf |
| 9 | Split PDF | .pdf | .pdf (selected pages) |
| 10 | Delete Pages | .pdf | .pdf (pages removed) |
| 11 | Extract Pages | .pdf | .pdf (selected pages) |
| 12 | Organize PDF | .pdf | .pdf (reordered) |
| 13 | Share PDF | .pdf | Shareable link |
| 14 | PDF Reader | .pdf | View in browser |
| 15 | PDF to PPT | .pdf | .pptx |
| 16 | PDF to JPG | .pdf | .jpg images |

### User Management
- ✅ User registration with email validation
- ✅ Secure login with JWT authentication
- ✅ Password encryption with BCrypt
- ✅ User-specific file isolation
- ✅ Activity logging

### File Management
- ✅ Upload files with drag & drop
- ✅ Download converted files
- ✅ Delete files with confirmation
- ✅ View file history
- ✅ Clear all history with one click
- ✅ Share files via link, email, or WhatsApp

### Storage
- ✅ Local file storage
- ✅ AWS S3 integration (optional)
- ✅ MySQL database for metadata
- ✅ **10 GB storage limit per user**

### Dashboard
- ✅ Total files count
- ✅ Total conversions count
- ✅ Storage usage display
- ✅ OCR scans count
- ✅ Quick action buttons

---

## 🛠 Tech Stack

### Backend
- **Java 17+**
- **Spring Boot 3.2.0**
- **Spring Security** with JWT
- **Spring Data JPA** with Hibernate
- **MySQL 8.0+**

### Frontend
- **HTML5 / CSS3 / JavaScript**
- **Tailwind CSS** (via CDN)
- **Responsive Design**

### Libraries
- **Apache POI** - Word, Excel, PowerPoint processing
- **Apache PDFBox** - PDF manipulation
- **iText 7/8** - PDF creation and editing
- **Tesseract OCR (Tess4J)** - Text extraction from images
- **AWS SDK** - S3 storage integration

---

## 📋 Prerequisites

Before running this application, ensure you have:

1. **Java 17 or higher**
   ```bash
   java -version
   ```

2. **Maven 3.6+** (or use included Maven Wrapper)
   ```bash
   mvn -version
   ```

3. **MySQL 8.0+**
   ```bash
   mysql --version
   ```

4. **Tesseract OCR** (for OCR functionality)
    - Windows: [Download from GitHub](https://github.com/UB-Mannheim/tesseract/wiki)
    - macOS: `brew install tesseract`
    - Linux: `sudo apt install tesseract-ocr tesseract-ocr-eng`

---

## 🚀 Installation

### Step 1: Clone or Download the Project

```bash
cd F:\add-ocr-file-conversion
```

### Step 2: Create MySQL Database

```bash
mysql -u root -p
```

```sql
CREATE DATABASE docconvert_db;
CREATE USER 'docconvert_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON docconvert_db.* TO 'docconvert_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

Or simply create the database (tables are auto-created):
```sql
CREATE DATABASE IF NOT EXISTS docconvert_db;
```

### Step 3: Configure Application

Edit `src/main/resources/application.properties`:

```properties
# MySQL Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/docconvert_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD

# JWT Secret (change this in production!)
jwt.secret=your-256-bit-secret-key-for-jwt-token-generation-change-this-in-production

# Tesseract OCR Path
# Windows:
tesseract.data.path=C:/Program Files/Tesseract-OCR/tessdata
# Linux:
# tesseract.data.path=/usr/share/tesseract-ocr/4.00/tessdata
# macOS:
# tesseract.data.path=/usr/local/share/tessdata
```

---

## ⚙️ Configuration

### application.properties

| Property | Description | Default |
|----------|-------------|---------|
| `server.port` | Server port | 8080 |
| `spring.datasource.url` | MySQL connection URL | localhost:3306/docconvert_db |
| `spring.datasource.username` | MySQL username | root |
| `spring.datasource.password` | MySQL password | (empty) |
| `jwt.secret` | JWT signing key | (change this!) |
| `jwt.expiration` | JWT token expiration (ms) | 86400000 (24 hours) |
| `spring.servlet.multipart.max-file-size` | Max upload size | 50MB |
| `app.upload.dir` | Upload directory | ./uploads |
| `app.converted.dir` | Converted files directory | ./converted |

### AWS S3 Configuration (Optional)

```properties
aws.access-key=YOUR_AWS_ACCESS_KEY
aws.secret-key=YOUR_AWS_SECRET_KEY
aws.s3.bucket-name=your-bucket-name
aws.s3.region=us-east-1
```

---

## 🏃 Running the Application

### Option 1: Using Maven Wrapper (Recommended)

**Windows (PowerShell):**
```powershell
cd F:\add-ocr-file-conversion
.\mvnw.cmd clean install -DskipTests
.\mvnw.cmd spring-boot:run
```

**Windows (CMD):**
```cmd
cd F:\add-ocr-file-conversion
mvnw.cmd clean install -DskipTests
mvnw.cmd spring-boot:run
```

**macOS / Linux:**
```bash
cd /path/to/project
chmod +x mvnw
./mvnw clean install -DskipTests
./mvnw spring-boot:run
```

### Option 2: Using Maven

```bash
mvn clean install -DskipTests
mvn spring-boot:run
```

### Option 3: Using IntelliJ IDEA

1. Open the project in IntelliJ IDEA
2. Right-click on `pom.xml` → **Add as Maven Project**
3. Navigate to `src/main/java/com/docconvert/DocConvertApplication.java`
4. Right-click → **Run 'DocConvertApplication'**

### Option 4: Build and Run JAR

```bash
mvn clean package -DskipTests
java -jar target/docconvert-pro-1.0.0.jar
```

---

## 🌐 Access the Application

Once started, open your browser:

```
http://localhost:8080
```

### First Time Setup:
1. Click **Sign Up** tab
2. Enter your details (Full Name, Email, Username, Password)
3. Click **Create Account**
4. Login with your credentials
5. Start converting documents!

---

## 📡 API Endpoints

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/signup` | Register new user |
| POST | `/api/auth/login` | Login and get JWT token |

### File Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/files/upload` | Upload a file |
| POST | `/api/files/upload-multiple` | Upload multiple files |
| GET | `/api/files` | List user's files |
| GET | `/api/files/download/{id}` | Download a file |
| DELETE | `/api/files/{id}` | Delete a file |
| POST | `/api/files/share` | Share a file |
| GET | `/api/files/stats` | Get storage stats |

### Conversions

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/convert/word-to-pdf` | Convert Word to PDF |
| POST | `/api/convert/pdf-to-word` | Convert PDF to Word |
| POST | `/api/convert/compress-pdf` | Compress PDF |
| POST | `/api/convert/excel-to-pdf` | Convert Excel to PDF |
| POST | `/api/convert/ppt-to-pdf` | Convert PPT to PDF |
| POST | `/api/convert/jpg-to-pdf` | Convert images to PDF |
| POST | `/api/convert/pdf-ocr` | Extract text from PDF/image |
| POST | `/api/convert/merge-pdf` | Merge multiple PDFs |
| POST | `/api/convert/split-pdf` | Split PDF by pages |
| POST | `/api/convert/delete-pages` | Delete pages from PDF |
| POST | `/api/convert/extract-pages` | Extract pages from PDF |
| POST | `/api/convert/pdf-to-ppt` | Convert PDF to PowerPoint |
| POST | `/api/convert/pdf-to-jpg` | Convert PDF to images |
| GET | `/api/convert/history` | Get conversion history |
| DELETE | `/api/convert/history` | Clear all conversion history |
| GET | `/api/convert/stats` | Get conversion stats |

### Public Sharing

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/share/public/{token}` | Download shared file |
| GET | `/api/share/public/{token}/info` | Get shared file info |

---

## 📁 Project Structure

```
docconvert-pro/
├── pom.xml                          # Maven configuration
├── mvnw                             # Maven Wrapper (Linux/Mac)
├── mvnw.cmd                         # Maven Wrapper (Windows)
├── README.md                        # This file
├── MYSQL_SETUP.md                   # MySQL setup guide
├── RUNNING_INSTRUCTIONS.md          # Running instructions
│
├── src/main/java/com/docconvert/
│   ├── DocConvertApplication.java   # Main application class
│   │
│   ├── config/
│   │   └── SecurityConfig.java      # Spring Security configuration
│   │
│   ├── controller/
│   │   ├── AuthController.java      # Authentication endpoints
│   │   ├── FileController.java      # File management endpoints
│   │   ├── ConversionController.java # Conversion endpoints
│   │   └── ShareController.java     # Public sharing endpoints
│   │
│   ├── dto/
│   │   ├── AuthDTOs.java            # Authentication DTOs
│   │   └── FileDTOs.java            # File operation DTOs
│   │
│   ├── entity/
│   │   ├── User.java                # User entity
│   │   ├── UserFile.java            # File entity
│   │   ├── ConversionHistory.java   # Conversion history entity
│   │   ├── SharedFile.java          # Shared file entity
│   │   ├── OcrResult.java           # OCR result entity
│   │   └── UserActivityLog.java     # Activity log entity
│   │
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── UserFileRepository.java
│   │   ├── ConversionHistoryRepository.java
│   │   ├── SharedFileRepository.java
│   │   ├── OcrResultRepository.java
│   │   └── UserActivityLogRepository.java
│   │
│   ├── security/
│   │   ├── JwtTokenProvider.java    # JWT token utilities
│   │   └── JwtAuthenticationFilter.java # JWT filter
│   │
│   └── service/
│       ├── UserService.java         # User management
│       ├── FileStorageService.java  # File storage operations
│       ├── ConversionService.java   # Document conversions
│       ├── S3Service.java           # AWS S3 integration
│       └── CustomUserDetailsService.java # Spring Security
│
├── src/main/resources/
│   ├── application.properties       # Application configuration
│   ├── schema.sql                   # Database schema (optional)
│   └── static/
│       └── index.html               # Frontend application
│
├── uploads/                         # Uploaded files (created at runtime)
└── converted/                       # Converted files (created at runtime)
```

---

## 🗄 Database Schema

The application automatically creates these tables:

### users
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| username | VARCHAR(50) | Unique username |
| email | VARCHAR(100) | Unique email |
| password | VARCHAR(255) | Encrypted password |
| full_name | VARCHAR(100) | User's full name |
| is_active | BIT | Account status |
| created_at | DATETIME | Registration date |
| updated_at | DATETIME | Last update date |

### user_files
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| user_id | BIGINT | Owner's user ID |
| file_name | VARCHAR(255) | Stored file name |
| original_name | VARCHAR(255) | Original file name |
| file_path | VARCHAR(500) | Local storage path |
| file_size | BIGINT | File size in bytes |
| file_type | VARCHAR(100) | File extension |
| mime_type | VARCHAR(100) | MIME type |
| s3_key | VARCHAR(500) | AWS S3 key (if used) |
| s3_url | VARCHAR(1000) | AWS S3 URL (if used) |
| is_converted | BIT | Is converted file |
| uploaded_at | DATETIME | Upload timestamp |

### conversion_history
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| user_id | BIGINT | User ID |
| conversion_type | VARCHAR(50) | Type of conversion |
| source_file_name | VARCHAR(255) | Source file name |
| converted_file_name | VARCHAR(255) | Output file name |
| status | ENUM | PENDING, PROCESSING, COMPLETED, FAILED |
| started_at | DATETIME | Start timestamp |
| completed_at | DATETIME | Completion timestamp |

### shared_files
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| file_id | BIGINT | Shared file ID |
| user_id | BIGINT | Owner user ID |
| share_token | VARCHAR(100) | Unique share token |
| share_url | VARCHAR(500) | Full share URL |
| expires_at | DATETIME | Expiration date |
| is_active | BIT | Share status |
| access_count | INT | Number of accesses |

### ocr_results
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| user_id | BIGINT | User ID |
| source_file_id | BIGINT | Source file ID |
| extracted_text | LONGTEXT | Extracted text content |
| confidence | DOUBLE | OCR confidence score |
| language | VARCHAR(20) | OCR language |
| processing_time_ms | BIGINT | Processing time |

### user_activity_log
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| user_id | BIGINT | User ID |
| activity_type | VARCHAR(50) | Activity type |
| description | TEXT | Activity description |
| ip_address | VARCHAR(50) | Client IP |
| user_agent | TEXT | Browser info |
| created_at | DATETIME | Timestamp |

---

## 💾 Storage Limits

| Feature | Limit |
|---------|-------|
| **Storage per user** | 10 GB |
| **Max file upload size** | 50 MB |
| **Supported formats** | PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX, JPG, PNG |

To change the storage limit, edit `FileStorageService.java`:

```java
// Current: 10 GB
private static final long MAX_STORAGE_PER_USER = 10L * 1024L * 1024L * 1024L;

// For 5 GB:
private static final long MAX_STORAGE_PER_USER = 5L * 1024L * 1024L * 1024L;

// For 20 GB:
private static final long MAX_STORAGE_PER_USER = 20L * 1024L * 1024L * 1024L;
```

---

## 🔧 Troubleshooting

### Error: "Access denied for user 'root'@'localhost'"

**Solution:** Update your MySQL password in `application.properties`:
```properties
spring.datasource.password=YOUR_ACTUAL_PASSWORD
```

### Error: "Port 8080 already in use"

**Solution:** Kill the process or change port:
```bash
# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Or change port in application.properties
server.port=8081
```

### Error: "Cannot delete file - foreign key constraint"

**Solution:** This is now handled automatically. The application clears references in:
- `shared_files`
- `conversion_history`
- `ocr_results`

### Error: "Tesseract not found"

**Solution:** Install Tesseract and set the correct path:
```properties
# Windows
tesseract.data.path=C:/Program Files/Tesseract-OCR/tessdata

# Verify installation
tesseract --version
```

### Error: "Java version not compatible"

**Solution:** Use Java 17 or higher:
```bash
java -version
# Should show 17 or higher

# Set JAVA_HOME
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.x
```

### Clear History Button Not Working

**Solution:** Make sure you've updated all files:
1. `ConversionHistoryRepository.java` - Added `deleteByUserId` method
2. `ConversionService.java` - Added `clearUserHistory` method
3. `ConversionController.java` - Added `DELETE /api/convert/history` endpoint
4. `index.html` - Added button and `clearAllHistory` function

---

## 📝 Recent Updates

### Version 1.0.0

- ✅ Initial release with 16 conversion tools
- ✅ User authentication with JWT
- ✅ MySQL database integration
- ✅ AWS S3 optional support
- ✅ 10 GB storage limit per user
- ✅ Clear all history button
- ✅ Fixed file deletion with foreign key handling
- ✅ Fixed file download authentication
- ✅ Responsive UI with Tailwind CSS

---

## 📄 License

This project is licensed under the MIT License.

---

## 🤝 Support

If you encounter any issues:

1. Check the [Troubleshooting](#-troubleshooting) section
2. Verify your MySQL connection
3. Ensure all prerequisites are installed
4. Check the application logs in the console

---

**Happy Converting! 🎉**
=======
# Transformo-Docs-Application
>>>>>>> 83c90c8830b04811b30d42dbf6a01fcc1a17a850
