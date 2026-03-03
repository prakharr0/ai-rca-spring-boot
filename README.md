# 🔍 AI RCA Spring Boot Starter

> AI-powered Root Cause Analysis (RCA) for Spring Boot applications.

Automatically analyzes exceptions using AI and returns structured, ranked root cause hypotheses.

---

## ✨ Features

- Automatic exception interception
- Structured AI-based root cause analysis
- Ranked hypotheses with likelihood levels
- Diagnostic isolation steps
- Spring Boot auto-configuration
- Actuator endpoint integration
- Zero required Java configuration

---

## 📦 Modules
```
ai-rca-spring-boot/
 ├── ai-rca-spring-boot-core
 └── ai-rca-spring-boot-starter
```

### `ai-rca-spring-boot-core`
- Context collection
- Prompt building
- AI analyzer implementation
- RCA result models

### `ai-rca-spring-boot-starter`
- Auto-configuration
- Global exception handler
- Actuator endpoint
- Bean wiring

---

## 🚀 Installation

### Maven
```xml
<dependency>
  <groupId>io.github.prakharr0</groupId>
  <artifactId>ai-rca-spring-boot-starter</artifactId>
  <version>0.0.3</version>
</dependency>

<!-- AI Model Dependency-->
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>

<!-- OR -->

<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>
```

### Gradle
```groovy
implementation 'io.github.prakharr0:ai-rca-spring-boot-starter:0.0.3'
```

---

## ⚙️ Configuration

### 1️⃣ OpenAI API Key
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_KEY}
      chat:
        options:
          model: ${OPENAI_MODEL}
          temperature: ${AI_TEMP}
```
### OR

### 1️⃣ Claude API Key
```yaml
spring:
   anthropic:
      api-key: ${ANTHROPIC_KEY}
      chat:
         options:
            model: ${CLAUDE_MODEL} # >=claude-sonnet-4-6 RECOMMENDED
            temperature: ${AI_TEMP}
            max-tokens: ${CLAUDE_MAX_TOKENS} # >=8192 RECOMMENDED
```

### 2️⃣ Enable RCA
```yaml
ai:
  rca:
    enabled: true
```

### 3️⃣ Expose Actuator Endpoint
```yaml
management:
  endpoints:
    web:
      exposure:
        include:
          - health
          - info
          - ai-rca
```

Access:
```
http://localhost:8080/actuator/ai-rca
```

---

## 🧠 Example Output
```json
{
   "b9d20b91e14037020f64324e776988aa20ae7d096e4dd02caa32e2b967a9b688": {
      "analysisConfidence": 0.95,
      "knownPattern": "Arithmetic error in business logic",
      "missingInformation": [],
      "rootCauses": [
         {
            "rank": 1,
            "title": "Hardcoded or unguarded division by zero in controller method",
            "likelihood": "High",
            "category": "Code",
            "reasoning": "The stack trace pinpoints the exception directly at ExceptionThrowingController.java:11 inside throwEx(), indicating an integer division operation with a zero divisor at that exact line. No intermediate service or repository layer is involved, confirming the fault is isolated to controller logic.",
            "diagnosticStep": "Inspect line 11 of ExceptionThrowingController.java to identify the division expression and trace the source of the zero-valued denominator.",
            "estimatedTimeToVerify": "< 2 minutes"
         },
         {
            "rank": 2,
            "title": "Zero-valued input parameter passed to division operation",
            "likelihood": "Medium",
            "category": "Code",
            "reasoning": "The controller method throwEx() may accept a request parameter used as a divisor without null or zero validation, allowing a caller to trigger the exception by passing zero. This is consistent with the exception originating at the controller entry point with no upstream processing.",
            "diagnosticStep": "Check the method signature of throwEx() for request parameters and verify whether zero-value input validation is absent.",
            "estimatedTimeToVerify": "< 5 minutes"
         },
         {
            "rank": 3,
            "title": "Intentional exception-throwing endpoint for testing error handling",
            "likelihood": "Low",
            "category": "Code",
            "reasoning": "The controller is named ExceptionThrowingController, strongly suggesting it may be a test or demo endpoint deliberately coded to throw an ArithmeticException. If intentional, this is not a production defect but a test artifact deployed to a non-test environment.",
            "diagnosticStep": "Review the class-level intent and any @RequestMapping annotations on ExceptionThrowingController to determine if it is a deliberate fault-injection endpoint.",
            "estimatedTimeToVerify": "< 2 minutes"
         }
      ]
   }
}
```

---

## 🔍 How It Works

When an exception occurs:

1. Global exception handler intercepts error
2. Context snapshot is collected:
    - Exception type
    - Root cause
    - Trimmed stack trace
    - Recent logs 
    - Java version
    - Spring Boot version
    - Active profiles
    - Deployment metadata
3. Structured AI prompt is generated
4. AI returns ranked hypotheses
5. Result is cached and exposed via Actuator

---

## 📝 Log Context Capture

File `src/main/resources/logback.xml`:
```xml
<configuration>
    <appender name="RING_BUFFER"
              class="context.io.github.prakharr0.ai.rca.spring.boot.core.RingBufferLogAppender" />
    <root level="INFO">
        <appender-ref ref="RING_BUFFER"/>
    </root>
</configuration>
```
---

## 🛡 Design Principles

- No code suggestions
- No automatic fixes
- Only ranked hypotheses
- Deterministic JSON output
- Production-safe design

---

---

## 🏗 Requirements

| Dependency  | Version |
|-------------|---------|
| Java        | 21+     |
| Spring Boot | 3+      |
| Spring AI   | 2+      |

---

## 🔨 Build From Source
```bash
mvn clean install
```

---

## 🧩 Usage in Another Project

1. Add dependency
2. Add Spring AI dependency for OpenAI/Anthropic
3. Configure API key
4. Enable endpoint
5. Run application
6. Trigger exception
7. Visit `/actuator/ai-rca`

No additional Java configuration required.

---

---

## 🤝 Contributing

Pull requests are welcome.  
Please open an issue first to discuss major changes.

---

## 📜 License

MIT License

Copyright (c) 2026 Prakhar Rathi

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.