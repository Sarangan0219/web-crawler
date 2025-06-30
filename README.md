# Web Crawler Service

A concurrent, domain-restricted web crawler written in Java using Spring Boot. 
It accepts a list of starting URLs and crawls all internal links within the same domain. 
The service is designed to be scalable, extensible, and production-ready with proper layered architecture.

##  Features

* ✅ **Multiple Input Methods**: JSON API requests and file uploads (txt, csv)
* ✅ **Async Crawling**: Non-blocking REST API with real-time status monitoring

##  Getting Started

### Prerequisites
* Java 17+
* Maven 3.6+


### Build and Run

```bash
# Build
mvn clean install

# Run
mvn spring-boot:run
```

The service will start on `http://localhost:8080`

## 🔗 API Endpoints

### 1. Start Crawl (JSON)
```http
POST /api/v1/crawlers
Content-Type: application/json

{
  "urls": ["https://example.com", "https://monzo.com"],
  "strategy": "SINGLE_DOMAIN",
  "maxPages": 50,
  "maxDepth": 3
}
```

### 2. Start Crawl (File Upload)
```http
POST /api/v1/crawlers/upload
Content-Type: multipart/form-data

file: urls.txt (one URL per line)
strategy: SINGLE_DOMAIN
maxPages: 50
maxDepth: 3
```

### 3. Check Crawl Status
```http
GET /api/v1/crawlers/{crawlId}/status
```

### 4. Stop Crawl
```http
POST /api/v1/crawlers/{crawlId}/stop
```

### 5. Get Crawl History
```http
GET /api/v1/crawlers/history?page=0&size=10&status=COMPLETED
```

### 6. Cleanup History
```http
POST /api/v1/crawlers/cleanup
```

## 🧠 Architecture & Design

### Layered Architecture
```
┌─────────────────┐
│   Controller    │ ← REST API Layer
├─────────────────┤
│    Service      │ ← Business Logic
├─────────────────┤
│   Repository    │ ← Data Access Layer
├─────────────────┤
│    Manager      │ ← Crawl Coordination
├─────────────────┤
│    Worker       │ ← URL Processing
└─────────────────┘
```

### Core Components

#### **Repository Pattern**
- `CrawlRepository` interface for data abstraction
- `InMemoryCrawlRepository` implementation (easily replaceable with DB)
- Follows SOLID principles for future extensibility

#### **Manager-Worker Pattern**
- **CrawlManager**: Manages crawl queue, domain scope, and task coordination
- **CrawlWorker**: Fetches URLs, extracts links, and reports completion


##  Configuration

### File Upload Support
Supports multiple file formats:
- **Plain Text**: One URL per line
- **CSV**: URLs in first column
- **Maximum File Size**: Configurable via Spring Boot properties

### Rate Limiting
- **Default Delay**: 1000ms between requests
- **Respectful Crawling**: Prevents server overwhelming
- **Configurable**: Can be adjusted per crawl strategy

## Sample Files

### URLs Text File (urls.txt)
```
https://example.com
https://another-domain.com
https://third-site.org
```

### URLs CSV File (urls.csv)
```csv
url,description
https://example.com,Main site
https://blog.example.com,Blog
```
