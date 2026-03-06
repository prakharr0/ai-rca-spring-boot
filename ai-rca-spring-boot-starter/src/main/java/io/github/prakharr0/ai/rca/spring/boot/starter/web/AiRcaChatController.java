package io.github.prakharr0.ai.rca.spring.boot.starter.web;

import io.github.prakharr0.ai.rca.spring.boot.core.chat.RcaChatService;
import io.github.prakharr0.ai.rca.spring.boot.core.model.chat.ChatAnswer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/ai-rca/chat")
public class AiRcaChatController {

    private final RcaChatService chatService;
    private final boolean chatUiEnabled;

    public AiRcaChatController(RcaChatService chatService, boolean chatUiEnabled) {
        this.chatService = chatService;
        this.chatUiEnabled = chatUiEnabled;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        ChatAnswer answer = chatService.chat(request.question(), request.toleranceSeconds(), ZoneId.systemDefault());
        return new ChatResponse(answer.answer(), answer.referencedEventIds(), answer.resolvedTime());
    }

    @GetMapping(value = "/ui", produces = MediaType.TEXT_HTML_VALUE)
    public String ui() {
        if (!chatUiEnabled) {
            return "<html><body><h3>AI RCA chat UI is disabled.</h3></body></html>";
        }

        return """
                <!DOCTYPE html>
                <html lang=\"en\">
                <head>
                  <meta charset=\"UTF-8\" />
                  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
                  <title>AI RCA Chat</title>
                  <style>
                    :root {
                      --bg:#f3f1ea;
                      --panel:#fffdf8;
                      --ink:#1c1b1a;
                      --accent:#125b50;
                      --muted:#7a756d;
                    }
                    * { box-sizing:border-box; }
                    body {
                      margin:0;
                      min-height:100vh;
                      font-family: "IBM Plex Sans", "Segoe UI", sans-serif;
                      background: radial-gradient(circle at top right, #d9e6d2 0%, var(--bg) 50%, #e9dfcf 100%);
                      color:var(--ink);
                      display:flex;
                      align-items:center;
                      justify-content:center;
                      padding:20px;
                    }
                    .chat-shell {
                      width:min(900px, 100%);
                      height:min(80vh, 780px);
                      background:var(--panel);
                      border:1px solid #d9d2c4;
                      border-radius:20px;
                      box-shadow:0 12px 35px rgba(18,91,80,0.18);
                      display:flex;
                      flex-direction:column;
                      overflow:hidden;
                    }
                    .header {
                      padding:16px 20px;
                      border-bottom:1px solid #e7e1d6;
                      background:linear-gradient(90deg, #125b50, #2f7f6d);
                      color:#f6f4ef;
                    }
                    .header h1 { margin:0; font-size:18px; letter-spacing:0.4px; }
                    .header p { margin:4px 0 0; font-size:13px; opacity:0.9; }
                    .messages {
                      flex:1;
                      overflow:auto;
                      padding:16px;
                      display:flex;
                      flex-direction:column;
                      gap:12px;
                    }
                    .msg {
                      max-width:85%;
                      padding:10px 12px;
                      border-radius:12px;
                      line-height:1.45;
                      white-space:pre-wrap;
                      animation:fadeIn .2s ease;
                    }
                    .user { align-self:flex-end; background:#d9efe8; }
                    .bot { align-self:flex-start; background:#f2ede1; border:1px solid #e8decb; }
                    .meta { font-size:12px; color:var(--muted); margin-top:6px; }
                    .composer {
                      border-top:1px solid #e7e1d6;
                      display:flex;
                      gap:8px;
                      padding:12px;
                    }
                    textarea {
                      flex:1;
                      min-height:44px;
                      max-height:140px;
                      resize:vertical;
                      border:1px solid #cfc8bb;
                      border-radius:10px;
                      padding:10px;
                      font:inherit;
                    }
                    button {
                      border:none;
                      background:var(--accent);
                      color:white;
                      padding:0 16px;
                      border-radius:10px;
                      cursor:pointer;
                      font-weight:600;
                    }
                    button:disabled { opacity:0.5; cursor:not-allowed; }
                    @keyframes fadeIn { from { opacity:0; transform:translateY(4px); } to { opacity:1; transform:translateY(0); } }
                  </style>
                </head>
                <body>
                  <div class=\"chat-shell\">
                    <div class=\"header\">
                      <h1>AI RCA Assistant</h1>
                      <p>Ask about incidents by time, cause, or exception type.</p>
                    </div>
                    <div id=\"messages\" class=\"messages\"></div>
                    <div class=\"composer\">
                      <textarea id=\"question\" placeholder=\"Example: exception occurred at 3 pm on 05 feb 2026, why?\"></textarea>
                      <button id=\"send\">Send</button>
                    </div>
                  </div>
                  <script>
                    const messages = document.getElementById('messages');
                    const question = document.getElementById('question');
                    const send = document.getElementById('send');

                    function append(role, text, meta) {
                      const div = document.createElement('div');
                      div.className = `msg ${role}`;
                      div.textContent = text;
                      if (meta) {
                        const m = document.createElement('div');
                        m.className = 'meta';
                        m.textContent = meta;
                        div.appendChild(m);
                      }
                      messages.appendChild(div);
                      messages.scrollTop = messages.scrollHeight;
                    }

                    async function ask() {
                      const q = question.value.trim();
                      if (!q) return;
                      append('user', q);
                      question.value = '';
                      send.disabled = true;
                      try {
                        const response = await fetch('/ai-rca/chat', {
                          method: 'POST',
                          headers: {'Content-Type': 'application/json'},
                          body: JSON.stringify({question: q})
                        });
                        const data = await response.json();
                        const ref = (data.referencedEventIds || []).length ? `Referenced events: ${data.referencedEventIds.join(', ')}` : 'No referenced events';
                        append('bot', data.answer || 'No answer', ref);
                      } catch (e) {
                        append('bot', 'Failed to reach /ai-rca/chat endpoint.');
                      } finally {
                        send.disabled = false;
                      }
                    }

                    send.addEventListener('click', ask);
                    question.addEventListener('keydown', (e) => {
                      if (e.key === 'Enter' && !e.shiftKey) {
                        e.preventDefault();
                        ask();
                      }
                    });

                    append('bot', 'Ask me about an incident time or RCA details.');
                  </script>
                </body>
                </html>
                """;
    }

    public record ChatRequest(String question, Integer toleranceSeconds) {
    }

    public record ChatResponse(String answer, List<String> referencedEventIds, java.time.Instant resolvedTime) {
    }
}
