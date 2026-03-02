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
  <groupId>io.jvai</groupId>
  <artifactId>ai-rca-spring-boot-starter</artifactId>
  <version>0.0.1</version>
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
implementation 'io.jvai:ai-rca-spring-boot-starter:0.0.1'
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
  "rootCauses": [
    {
      "title": "Missing datasource configuration",
      "likelihood": "High",
      "category": "Configuration",
      "reasoning": "Spring Boot failed to create DataSource bean...",
      "diagnosticStep": "Check spring.datasource properties",
      "estimatedVerificationTime": "5 minutes"
    }
  ],
  "confidence": 0.82,
  "missingInformation": []
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
              class="io.jvai.ai.rca.spring.boot.core.context.RingBufferLogAppender" />
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