package rathi.prakhar.ai.rca.spring.boot.core.prompt;

import rathi.prakhar.ai.rca.spring.boot.core.context.ContextSnapshot;

public class UserPromptBuilder {

    private static final String OUTPUT_SCHEMA = """
            Respond with this exact JSON structure:
            {
              "analysisConfidence": <0.0-1.0>,
              "missingInformation": ["<string>"],
              "knownPattern": "<string or null>",
              "rootCauses": [
                {
                  "rank": <int>,
                  "title": "<string>",
                  "likelihood": "<High|Medium|Low>",
                  "category": "<Configuration|Code|Infrastructure|Dependency|Environment>",
                  "reasoning": "<max 2 sentences>",
                  "diagnosticStep": "<max 1 sentence>",
                  "estimatedTimeToVerify": "<e.g. < 5 minutes>"
                }
              ]
            }
            """;

    public static String build(ContextSnapshot c) {

        return """
You are given structured production failure data.

===============================
EXCEPTION SUMMARY
===============================

Exception Type: 
%s

Root Cause Type: 
%s

Root Cause Message: 
%s

===============================
STACK TRACE (trimmed to relevant frames)
===============================
%s

===============================
RECENT LOG CONTEXT (last 50 lines before failure)
===============================
%s
      
===============================
APPLICATION CONTEXT
===============================  
Spring Boot Version: %s
Java Version: %s
Active Profiles: %s
Packaging: %s
Deployment Environment: %s
Database: %s
Web Stack: %s
Build Tool: %s


===============================
TASK
===============================

Perform structured root cause analysis.

PHASE 1:
List the TOP 3 most probable root causes ranked by likelihood.

For each root cause:
    - Short title
- Likelihood: High | Medium | Low
- Category: Configuration | Code | Infrastructure | Dependency | Environment
- Technical reasoning (specific to the stack trace)
- Fastest diagnostic step to confirm or eliminate
- Estimated verification time (rough estimate)

PHASE 2:
Before finalizing:
    - Remove any hypothesis that contradicts the stack trace.
    - Ensure ranking reflects realistic production probability.

PHASE 3:
Provide:
    - Overall confidence score between 0.0 and 1.0
    - If confidence < 0.6, list missing information that would increase certainty.
    
Identify if the failure pattern matches any common known Spring Boot misconfiguration patterns.
                 If yes, label it explicitly.

IMPORTANT:
Do NOT suggest fixes.
Do NOT give code changes.
Focus only on ranked root causes and diagnostics.

Output strictly valid JSON using the schema below.
%s
""".formatted(
                c.exceptionType(),
                c.rootCauseType(),
                c.rootCauseMessage(),
                truncate(c.stackTrace(), 1000),
                truncate(c.recentLogs(), 500),
                c.springVersion(),
                c.javaVersion(),
                c.activeProfiles(),
                c.packaging(),
                c.deploymentEnvironment(),
                c.database(),
                c.webStack(),
                c.buildTool(),
                OUTPUT_SCHEMA
        );
    }

    private static String truncate(String value, int maxChars) {
        if (value == null || value.isBlank()) return "N/A";
        if (value.length() <= maxChars) return value;
        return value.substring(0, maxChars) + "\n... [truncated]";
    }
}










