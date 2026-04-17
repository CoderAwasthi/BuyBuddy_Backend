# Monitoring & Metrics Guide

This guide explains how to access and monitor all the metrics implemented in the price-drop-service.

## Metrics We Implemented

### 1. **Deals Generated Counter**
- **Metric Name**: `deals.generated`
- **Type**: Counter
- **Increments When**: A deal is successfully calculated with price history
- **Purpose**: Track the number of deals analyzed

### 2. **Scraping Success Counter**
- **Metric Name**: `scraping.success`
- **Type**: Counter
- **Increments When**: A product price is successfully extracted from HTML
- **Purpose**: Monitor successful price scraping operations

### 3. **Scraping Failure Counter**
- **Metric Name**: `scraping.failure`
- **Type**: Counter
- **Increments When**: Price extraction fails (null result or exception)
- **Purpose**: Track scraping failures for troubleshooting

### 4. **Notifications Sent Counter**
- **Metric Name**: `notifications.sent`
- **Type**: Counter
- **Increments When**: A price drop notification is successfully saved
- **Purpose**: Monitor notification creation

## Accessing Metrics

### Prerequisites
The `spring-boot-starter-actuator` dependency is already added (we fixed this). This enables:
- Health checks
- Metrics endpoints
- Application info

### Health Check Endpoint
```bash
curl http://localhost:8080/actuator/health
```

**Response**:
```json
{
  "status": "UP",
  "components": {
    "mongoDB": {
      "status": "UP"
    },
    "diskSpace": {
      "status": "UP"
    }
  }
}
```

### Available Metrics Endpoint
```bash
curl http://localhost:8080/actuator/metrics
```

**Response**:
```json
{
  "names": [
    "deals.generated",
    "scraping.success",
    "scraping.failure",
    "notifications.sent",
    "jvm.memory.used",
    "jvm.memory.committed",
    "process.cpu.usage",
    ...
  ]
}
```

### Individual Metric Details

#### View Deals Generated
```bash
curl http://localhost:8080/actuator/metrics/deals.generated
```

**Response**:
```json
{
  "name": "deals.generated",
  "description": null,
  "baseUnit": null,
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 156.0
    }
  ],
  "availableTags": []
}
```

#### View Scraping Success
```bash
curl http://localhost:8080/actuator/metrics/scraping.success
```

**Response**:
```json
{
  "name": "scraping.success",
  "description": null,
  "baseUnit": null,
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 342.0
    }
  ],
  "availableTags": []
}
```

#### View Scraping Failure
```bash
curl http://localhost:8080/actuator/metrics/scraping.failure
```

**Response**:
```json
{
  "name": "scraping.failure",
  "description": null,
  "baseUnit": null,
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 18.0
    }
  ],
  "availableTags": []
}
```

#### View Notifications Sent
```bash
curl http://localhost:8080/actuator/metrics/notifications.sent
```

**Response**:
```json
{
  "name": "notifications.sent",
  "description": null,
  "baseUnit": null,
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 42.0
    }
  ],
  "availableTags": []
}
```

## All Actuator Endpoints

### Core Endpoints Available
```
GET  /actuator                          # Lists all available endpoints
GET  /actuator/health                   # Application health status
GET  /actuator/info                     # Application info
GET  /actuator/metrics                  # List all metrics
GET  /actuator/metrics/{metric.name}    # Details of a specific metric
GET  /actuator/env                      # Environment properties
GET  /actuator/configprops              # Configuration properties
```

### Full List of URLs
```bash
# General info
http://localhost:8080/actuator/health
http://localhost:8080/actuator/info
http://localhost:8080/actuator/env

# Metrics
http://localhost:8080/actuator/metrics
http://localhost:8080/actuator/metrics/deals.generated
http://localhost:8080/actuator/metrics/scraping.success
http://localhost:8080/actuator/metrics/scraping.failure
http://localhost:8080/actuator/metrics/notifications.sent

# JVM Metrics
http://localhost:8080/actuator/metrics/jvm.memory.used
http://localhost:8080/actuator/metrics/jvm.threads.live
http://localhost:8080/actuator/metrics/process.cpu.usage
```

## Monitoring Dashboard Examples

### Using cURL to Monitor Scraping Health
```bash
# Scraping Success Rate
TOTAL=$(($(curl -s http://localhost:8080/actuator/metrics/scraping.success | jq '.measurements[0].value') + \
         $(curl -s http://localhost:8080/actuator/metrics/scraping.failure | jq '.measurements[0].value')))
SUCCESS=$(curl -s http://localhost:8080/actuator/metrics/scraping.success | jq '.measurements[0].value')
SUCCESS_RATE=$(echo "scale=2; $SUCCESS * 100 / $TOTAL" | bc)
echo "Scraping Success Rate: $SUCCESS_RATE%"
```

### Using Python to Monitor
```python
import requests
import json

BASE_URL = "http://localhost:8080/actuator/metrics"

def get_metric(metric_name):
    """Fetch a specific metric"""
    response = requests.get(f"{BASE_URL}/{metric_name}")
    return response.json()

# Get all custom metrics
metrics = {
    "deals.generated": get_metric("deals.generated"),
    "scraping.success": get_metric("scraping.success"),
    "scraping.failure": get_metric("scraping.failure"),
    "notifications.sent": get_metric("notifications.sent")
}

for metric_name, data in metrics.items():
    value = data["measurements"][0]["value"]
    print(f"{metric_name}: {value}")

# Calculate scraping success rate
success = metrics["scraping.success"]["measurements"][0]["value"]
failure = metrics["scraping.failure"]["measurements"][0]["value"]
total = success + failure
success_rate = (success / total * 100) if total > 0 else 0
print(f"\nScraping Success Rate: {success_rate:.2f}%")
```

## Configuration for Enhanced Monitoring

### Enable All Actuator Endpoints
Add to `application.yml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,env,configprops
  endpoint:
    health:
      show-details: always
```

### Enable Prometheus Export (Optional)
If you want Prometheus monitoring, add to `pom.xml`:
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Then access metrics in Prometheus format:
```bash
curl http://localhost:8080/actuator/prometheus
```

## Real-World Monitoring Scenarios

### Scenario 1: Track Deal Analysis Activity
```bash
# Check how many deals have been generated
curl -s http://localhost:8080/actuator/metrics/deals.generated | jq '.measurements[0].value'
# Output: 156
```
This tells you the service has analyzed 156 deals since startup.

### Scenario 2: Monitor Scraping Health
```bash
# Check scraping success vs failure
SUCCESS=$(curl -s http://localhost:8080/actuator/metrics/scraping.success | jq '.measurements[0].value')
FAILURE=$(curl -s http://localhost:8080/actuator/metrics/scraping.failure | jq '.measurements[0].value')
echo "Success: $SUCCESS, Failure: $FAILURE"
# Output: Success: 342, Failure: 18
```
A high failure rate indicates issues with website structure changes or network problems.

### Scenario 3: Monitor Notification Delivery
```bash
# Check how many notifications were sent
curl -s http://localhost:8080/actuator/metrics/notifications.sent | jq '.measurements[0].value'
# Output: 42
```
Indicates 42 price drop notifications have been created.

### Scenario 4: Application Health Check
```bash
# Verify the app is running and DB is connected
curl -s http://localhost:8080/actuator/health | jq '.'
```

## What Each Metric Tells You

| Metric | What It Means | Normal Range | Action if High | Action if Low |
|--------|---------------|-------------|-----------------|----------------|
| `deals.generated` | Deals analyzed | Varies by load | Normal | Check if products are being tracked |
| `scraping.success` | Successful scrapes | Should be high | Good! | Monitor network |
| `scraping.failure` | Failed scrapes | Should be low | Investigate HTML changes | Good! |
| `notifications.sent` | Price drops detected | Varies | Verify deals are legitimate | Check if prices are dropping |

## Integration with External Tools

### Grafana Setup (if using Prometheus)
1. Add Prometheus data source
2. Query: `deals_generated_total`
3. Create dashboard panels for each metric

### ELK Stack Integration
Send metrics to Logstash → Elasticsearch → Kibana for visualization

### CloudWatch (AWS)
If deployed on AWS, metrics can be sent to CloudWatch for native monitoring

## Troubleshooting Metrics

### No Metrics Showing
1. Check actuator is enabled: `curl http://localhost:8080/actuator`
2. Verify dependency: `spring-boot-starter-actuator` in pom.xml
3. Rebuild and restart: `mvn clean spring-boot:run`

### Metrics Not Incrementing
1. Check application is processing products
2. Verify no exceptions in logs
3. Call API endpoints to generate activity:
   ```bash
   curl -X POST http://localhost:8080/api/track -H "Content-Type: application/json" -d '{...}'
   curl http://localhost:8080/api/deals?category=electronics&subCategory=laptop
   ```

### High Memory Usage
Check JVM metrics:
```bash
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/jvm.memory.max
```

## Logging and Metrics Correlation

Application logs include context about metrics:
```
2024-01-15 10:30:15 INFO  - Retrieved top 10 deals
2024-01-15 10:30:16 DEBUG - Calculated deal for ASIN: B08N5WRWNW - Score: 8.5, Decision: BUY NOW
2024-01-15 10:30:17 INFO  - Processed product ASIN: B08N5WRWNW - Scraped price: 999.99
```

Check logs to correlate with metrics for detailed understanding of what's happening.

## Quick Monitoring Checklist

Before going to production, verify:
- [ ] Health endpoint returns UP
- [ ] All metrics endpoints are accessible
- [ ] Metrics increment when API is called
- [ ] No critical errors in logs
- [ ] Scraping success rate > 95%
- [ ] Deal generation is working
- [ ] Notifications are being sent

