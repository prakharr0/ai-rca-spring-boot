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
                <html lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>AI RCA &mdash; Root Cause Assistant</title>
                  <style>
                    :root {
                      --bg: #0d1117;
                      --surface: #161b22;
                      --surface2: #21262d;
                      --border: #30363d;
                      --text: #e6edf3;
                      --muted: #8b949e;
                      --accent: #58a6ff;
                      --green: #3fb950;
                      --red: #f85149;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    html, body { height: 100%; overflow: hidden; }
                    body {
                      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                      background: var(--bg);
                      color: var(--text);
                      display: flex;
                    }
                    /* ── SIDEBAR ── */
                    #sb {
                      width: 258px;
                      flex-shrink: 0;
                      background: var(--surface);
                      border-right: 1px solid var(--border);
                      display: flex;
                      flex-direction: column;
                      overflow-y: auto;
                      height: 100vh;
                    }
                    .logo {
                      padding: 15px 14px;
                      border-bottom: 1px solid var(--border);
                      display: flex;
                      align-items: center;
                      gap: 10px;
                    }
                    .logo-icon {
                      width: 34px; height: 34px;
                      background: linear-gradient(135deg, #1f6feb, #58a6ff);
                      border-radius: 8px;
                      display: flex; align-items: center; justify-content: center;
                      font-size: 16px; flex-shrink: 0;
                    }
                    .logo-name { font-size: 13px; font-weight: 700; }
                    .logo-sub  { font-size: 11px; color: var(--muted); margin-top: 2px; }
                    .sl {
                      font-size: 10px; font-weight: 700; letter-spacing: 1px;
                      text-transform: uppercase; color: var(--muted);
                      padding: 14px 14px 6px;
                    }
                    .eg-btn {
                      display: block;
                      width: calc(100% - 20px);
                      margin: 0 10px 5px;
                      padding: 7px 9px;
                      background: var(--surface2);
                      border: 1px solid var(--border);
                      border-radius: 6px;
                      color: var(--text);
                      font-size: 12px; text-align: left;
                      cursor: pointer; line-height: 1.4;
                      font-family: inherit;
                      transition: border-color .15s;
                    }
                    .eg-btn:hover { border-color: var(--accent); }
                    .ep-link {
                      display: flex; align-items: center; gap: 8px;
                      padding: 5px 14px;
                      font-size: 12px; color: var(--accent);
                      text-decoration: none;
                      font-family: "Fira Code", "Consolas", monospace;
                      transition: color .15s;
                    }
                    .ep-link:hover { color: #79c0ff; }
                    .ep-badge {
                      font-size: 9px; font-weight: 700;
                      font-family: -apple-system, sans-serif;
                      padding: 1px 4px; border-radius: 3px; flex-shrink: 0;
                    }
                    .get  { background: #0f2d1a; color: #3fb950; }
                    .post { background: #1a2540; color: #58a6ff; }
                    .ep-plain {
                      display: flex; align-items: center; gap: 8px;
                      padding: 5px 14px;
                      font-size: 12px; color: var(--muted);
                      font-family: "Fira Code", "Consolas", monospace;
                    }
                    .sett { padding: 6px 14px; display: flex; flex-direction: column; gap: 4px; }
                    .sett label { font-size: 11px; color: var(--muted); }
                    .sett input {
                      background: var(--bg); border: 1px solid var(--border);
                      border-radius: 5px; color: var(--text);
                      padding: 5px 8px; font-size: 12px; width: 100%;
                      font-family: inherit;
                    }
                    .sett input:focus { outline: none; border-color: var(--accent); }
                    .sb-foot {
                      margin-top: auto;
                      padding: 12px 14px;
                      border-top: 1px solid var(--border);
                      font-size: 11px; color: var(--muted); line-height: 1.7;
                    }
                    /* ── MAIN ── */
                    #main {
                      flex: 1; display: flex; flex-direction: column;
                      height: 100vh; min-width: 0;
                    }
                    #topbar {
                      padding: 10px 16px;
                      border-bottom: 1px solid var(--border);
                      background: var(--surface);
                      display: flex; align-items: center;
                      justify-content: space-between;
                      flex-shrink: 0;
                    }
                    .tb-left { display: flex; align-items: center; gap: 10px; }
                    .tb-title { font-size: 14px; font-weight: 600; }
                    .tb-status { display: flex; align-items: center; gap: 5px; font-size: 11px; color: var(--muted); }
                    .dot {
                      width: 7px; height: 7px;
                      background: var(--green); border-radius: 50%;
                      animation: pulse 2.5s infinite;
                    }
                    @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.25} }
                    #clear-btn {
                      background: none; border: 1px solid var(--border);
                      color: var(--muted); padding: 4px 10px;
                      border-radius: 5px; font-size: 11px;
                      cursor: pointer; font-family: inherit;
                      transition: color .15s, border-color .15s;
                    }
                    #clear-btn:hover { color: var(--text); border-color: var(--muted); }
                    /* ── MESSAGES ── */
                    #msgs {
                      flex: 1; overflow-y: auto;
                      padding: 16px;
                      display: flex; flex-direction: column; gap: 14px;
                    }
                    .mrow { display: flex; gap: 10px; animation: msgIn .2s ease; }
                    .mrow.user { flex-direction: row-reverse; }
                    @keyframes msgIn { from{opacity:0;transform:translateY(6px)} to{opacity:1;transform:translateY(0)} }
                    .av {
                      width: 28px; height: 28px; border-radius: 6px;
                      display: flex; align-items: center; justify-content: center;
                      font-size: 13px; flex-shrink: 0; margin-top: 2px;
                    }
                    .av.u { background: #1a3760; }
                    .av.b { background: #0f2d1a; }
                    .av.e { background: #2d1117; }
                    .mbubble { max-width: 78%; display: flex; flex-direction: column; gap: 3px; }
                    .mcontent {
                      padding: 9px 12px; border-radius: 8px;
                      font-size: 13.5px; line-height: 1.65;
                      word-break: break-word;
                    }
                    .user .mcontent {
                      background: #162033;
                      border: 1px solid #1f6feb33;
                      border-radius: 8px 2px 8px 8px;
                    }
                    .bot .mcontent {
                      background: var(--surface);
                      border: 1px solid var(--border);
                      border-left: 2px solid var(--green);
                      border-radius: 2px 8px 8px 8px;
                    }
                    .err .mcontent {
                      background: #1e0d0d;
                      border: 1px solid var(--border);
                      border-left: 2px solid var(--red);
                      border-radius: 2px 8px 8px 8px;
                    }
                    /* Markdown styles */
                    .mcontent pre {
                      background: var(--bg);
                      border: 1px solid var(--border);
                      border-radius: 6px;
                      padding: 10px 12px; overflow-x: auto;
                      margin: 7px 0; font-size: 12px; line-height: 1.5;
                    }
                    .mcontent code {
                      font-family: "Fira Code", "Consolas", "Monaco", monospace;
                      font-size: 12px;
                      background: var(--surface2);
                      border: 1px solid var(--border);
                      padding: 1px 5px; border-radius: 4px; color: #79c0ff;
                    }
                    .mcontent pre code {
                      background: none; border: none; padding: 0;
                      color: #e6edf3; font-size: 12px;
                    }
                    .mcontent strong { color: #f0f6fc; }
                    .mcontent h3 { font-size: 13px; color: #f0f6fc; margin: 8px 0 4px; }
                    .mcontent ul { padding-left: 18px; margin: 5px 0; }
                    .mcontent li { margin: 3px 0; }
                    .mmeta {
                      display: flex; align-items: center; gap: 8px;
                      font-size: 11px; color: var(--muted); padding: 0 3px;
                    }
                    .user .mmeta { flex-direction: row-reverse; }
                    .copy-btn {
                      background: none; border: none;
                      color: var(--muted); font-size: 11px;
                      cursor: pointer; padding: 1px 4px; border-radius: 3px;
                      opacity: 0; font-family: inherit;
                      transition: opacity .15s, color .15s;
                    }
                    .mrow:hover .copy-btn { opacity: 1; }
                    .copy-btn:hover { color: var(--text); }
                    .copy-btn.ok { color: var(--green); opacity: 1; }
                    .ev-chips {
                      display: flex; flex-wrap: wrap; gap: 4px;
                      margin-top: 5px; padding: 0 3px;
                      font-size: 11px; color: var(--muted);
                      align-items: center;
                    }
                    .ev-chip {
                      font-size: 10px; padding: 2px 7px;
                      background: #0f1f35;
                      border: 1px solid #1f6feb33; border-radius: 20px;
                      color: var(--accent);
                      font-family: "Fira Code", "Consolas", monospace;
                      cursor: pointer; transition: background .15s;
                      title: "Click to copy";
                    }
                    .ev-chip:hover { background: #1a2f50; }
                    .res-time {
                      font-size: 11px; color: var(--muted);
                      padding: 0 3px 2px;
                      display: flex; align-items: center; gap: 4px;
                    }
                    /* Typing dots */
                    .td-wrap { display: flex; gap: 4px; align-items: center; padding: 2px 0; }
                    .td-wrap span {
                      width: 6px; height: 6px; background: var(--muted);
                      border-radius: 50%; animation: tda 1.2s infinite;
                    }
                    .td-wrap span:nth-child(2) { animation-delay: .2s; }
                    .td-wrap span:nth-child(3) { animation-delay: .4s; }
                    @keyframes tda { 0%,60%,100%{transform:scale(1);opacity:.4} 30%{transform:scale(1.4);opacity:1} }
                    /* ── COMPOSER ── */
                    #composer {
                      padding: 12px 16px;
                      border-top: 1px solid var(--border);
                      background: var(--surface);
                      display: flex; gap: 10px; align-items: flex-end;
                      flex-shrink: 0;
                    }
                    #qin {
                      flex: 1; background: var(--bg);
                      border: 1px solid var(--border); border-radius: 8px;
                      color: var(--text); padding: 9px 12px;
                      font-size: 13.5px; font-family: inherit;
                      resize: none; min-height: 42px; max-height: 120px;
                      line-height: 1.5; overflow-y: auto;
                      transition: border-color .15s;
                    }
                    #qin:focus { outline: none; border-color: var(--accent); }
                    #qin::placeholder { color: var(--muted); }
                    #send-btn {
                      height: 42px; padding: 0 18px;
                      background: var(--accent); border: none;
                      border-radius: 8px; color: #fff;
                      font-size: 13px; font-weight: 600;
                      cursor: pointer; white-space: nowrap; font-family: inherit;
                      transition: background .15s, color .15s;
                    }
                    #send-btn:hover { background: #79c0ff; color: #0d1117; }
                    #send-btn:disabled { opacity: .4; cursor: not-allowed; }
                    ::-webkit-scrollbar { width: 5px; height: 5px; }
                    ::-webkit-scrollbar-track { background: transparent; }
                    ::-webkit-scrollbar-thumb { background: var(--border); border-radius: 3px; }
                    @media (max-width: 640px) { #sb { display: none; } }
                  </style>
                </head>
                <body>
                  <aside id="sb">
                    <div class="logo">
                      <div class="logo-icon">&#128269;</div>
                      <div>
                        <div class="logo-name">AI RCA</div>
                        <div class="logo-sub">Root Cause Assistant</div>
                      </div>
                    </div>
                    <div class="sl">Example Queries</div>
                    <button class="eg-btn" data-q="What exceptions occurred in the last hour?">What exceptions occurred in the last hour?</button>
                    <button class="eg-btn" data-q="Why did the exception occur at 3pm today?">Why did the exception occur at 3pm today?</button>
                    <button class="eg-btn" data-q="What is the root cause of the most recent failure?">Root cause of the most recent failure?</button>
                    <button class="eg-btn" data-q="How many exceptions happened in the past 24 hours?">How many exceptions in the past 24 hours?</button>
                    <div class="sl">API Endpoints</div>
                    <a class="ep-link" href="/ai-rca/events" target="_blank"><span class="ep-badge get">GET</span>/ai-rca/events</a>
                    <a class="ep-link" href="/ai-rca/events/at" target="_blank"><span class="ep-badge get">GET</span>/ai-rca/events/at</a>
                    <a class="ep-link" href="/actuator/ai-rca" target="_blank"><span class="ep-badge get">GET</span>/actuator/ai-rca</a>
                    <div class="ep-plain"><span class="ep-badge post">POST</span>/ai-rca/chat</div>
                    <div class="sl">Settings</div>
                    <div class="sett">
                      <label for="tol">Time tolerance (seconds)</label>
                      <input type="number" id="tol" value="1800" min="60" max="86400" />
                    </div>
                    <div class="sb-foot">
                      <div>ai-rca-spring-boot</div>
                      <div style="color:var(--accent)">v0.0.5</div>
                      <div style="margin-top:5px;font-size:10px;line-height:1.8">
                        &#9166; Enter &mdash; send<br>
                        &#8679; Shift+Enter &mdash; new line
                      </div>
                    </div>
                  </aside>

                  <main id="main">
                    <div id="topbar">
                      <div class="tb-left">
                        <div class="tb-title">RCA Chat</div>
                        <div class="tb-status"><div class="dot"></div>Active</div>
                      </div>
                      <button id="clear-btn" onclick="clearChat()">Clear chat</button>
                    </div>
                    <div id="msgs"></div>
                    <div id="composer">
                      <textarea id="qin" placeholder="Ask about an incident, exception type, or time range..."></textarea>
                      <button id="send-btn">Send</button>
                    </div>
                  </main>

                  <script>
                    const msgs   = document.getElementById('msgs');
                    const qin    = document.getElementById('qin');
                    const sendBtn = document.getElementById('send-btn');
                    let typingEl = null;

                    function esc(s) {
                      return String(s)
                        .replace(/&/g, '&amp;')
                        .replace(/</g, '&lt;')
                        .replace(/>/g, '&gt;')
                        .replace(/"/g, '&quot;');
                    }

                    function renderMd(text) {
                      if (!text) return '';
                      const parts = text.split('```');
                      const out = [];
                      for (let i = 0; i < parts.length; i++) {
                        if (i % 2 === 1) {
                          const raw = parts[i].replace(/^[a-z]*\\n/, '');
                          out.push('<pre><code>' + esc(raw.trim()) + '</code></pre>');
                        } else {
                          let t = esc(parts[i]);
                          t = t.split('`').map((p, j) => j % 2 === 1 ? '<code>' + p + '</code>' : p).join('');
                          t = t.split('**').map((p, j) => j % 2 === 1 ? '<strong>' + p + '</strong>' : p).join('');
                          const lines = t.split('\\n');
                          const res = [];
                          let inList = false;
                          for (const ln of lines) {
                            const tr = ln.trim();
                            if (tr.startsWith('- ') || tr.startsWith('* ')) {
                              if (!inList) { res.push('<ul>'); inList = true; }
                              res.push('<li>' + tr.slice(2) + '</li>');
                            } else {
                              if (inList) { res.push('</ul>'); inList = false; }
                              if (tr === '') res.push('<p style="height:6px"></p>');
                              else if (tr.startsWith('### ') || tr.startsWith('## ')) res.push('<h3>' + tr.replace(/^#{2,3} /, '') + '</h3>');
                              else res.push(ln + '<br>');
                            }
                          }
                          if (inList) res.push('</ul>');
                          out.push(res.join(''));
                        }
                      }
                      return out.join('');
                    }

                    function ts() {
                      return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
                    }

                    function addMsg(role, html, extra) {
                      const row = document.createElement('div');
                      row.className = 'mrow ' + role;
                      const isUser = role === 'user';
                      const icon   = isUser ? '&#128100;' : (role === 'err' ? '&#9888;' : '&#129302;');
                      const avCls  = isUser ? 'u' : (role === 'err' ? 'e' : 'b');
                      let metaHtml = `<div class="mmeta"><span>${ts()}</span>`;
                      if (!isUser) metaHtml += `<button class="copy-btn" onclick="copyMsg(this)">copy</button>`;
                      metaHtml += '</div>';
                      row.innerHTML = `
                        <div class="av ${avCls}">${icon}</div>
                        <div class="mbubble">
                          <div class="mcontent">${html}</div>
                          ${extra || ''}
                          ${metaHtml}
                        </div>`;
                      msgs.appendChild(row);
                      msgs.scrollTop = msgs.scrollHeight;
                    }

                    function copyMsg(btn) {
                      const text = btn.closest('.mbubble').querySelector('.mcontent').innerText;
                      navigator.clipboard.writeText(text).then(() => {
                        btn.textContent = 'copied!';
                        btn.classList.add('ok');
                        setTimeout(() => { btn.textContent = 'copy'; btn.classList.remove('ok'); }, 2000);
                      });
                    }

                    function showTyping() {
                      typingEl = document.createElement('div');
                      typingEl.className = 'mrow bot';
                      typingEl.innerHTML = `
                        <div class="av b">&#129302;</div>
                        <div class="mbubble">
                          <div class="mcontent">
                            <div class="td-wrap"><span></span><span></span><span></span></div>
                          </div>
                        </div>`;
                      msgs.appendChild(typingEl);
                      msgs.scrollTop = msgs.scrollHeight;
                    }

                    function hideTyping() {
                      if (typingEl) { typingEl.remove(); typingEl = null; }
                    }

                    function clearChat() {
                      msgs.innerHTML = '';
                      addMsg('bot', '<p>Chat cleared. Ask about an incident, exception type, or time range.</p>', null);
                    }

                    async function ask() {
                      const q = qin.value.trim();
                      if (!q || sendBtn.disabled) return;
                      addMsg('user', '<p>' + esc(q) + '</p>', null);
                      qin.value = '';
                      qin.style.height = 'auto';
                      sendBtn.disabled = true;
                      showTyping();

                      const tol = parseInt(document.getElementById('tol').value) || 1800;
                      try {
                        const res = await fetch('/ai-rca/chat', {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify({ question: q, toleranceSeconds: tol })
                        });
                        if (!res.ok) throw new Error('HTTP ' + res.status);
                        const data = await res.json();

                        let extra = '';
                        if (data.referencedEventIds && data.referencedEventIds.length > 0) {
                          const chips = data.referencedEventIds.map(id => {
                            const short = id.length > 12 ? id.substring(0, 12) + '...' : id;
                            return `<span class="ev-chip" title="${esc(id)}" onclick="navigator.clipboard.writeText('${esc(id)}')">${esc(short)}</span>`;
                          }).join('');
                          extra += `<div class="ev-chips">&#128279; ${chips}</div>`;
                        }
                        if (data.resolvedTime) {
                          const d = new Date(data.resolvedTime);
                          extra += `<div class="res-time">&#128336; ${d.toLocaleString()}</div>`;
                        }

                        hideTyping();
                        addMsg('bot', renderMd(data.answer || 'No answer returned.'), extra);
                      } catch (e) {
                        hideTyping();
                        addMsg('err', '<p>&#9888; Failed: ' + esc(e.message) + '</p>', null);
                      } finally {
                        sendBtn.disabled = false;
                        msgs.scrollTop = msgs.scrollHeight;
                      }
                    }

                    qin.addEventListener('input', () => {
                      qin.style.height = 'auto';
                      qin.style.height = Math.min(qin.scrollHeight, 120) + 'px';
                    });
                    qin.addEventListener('keydown', e => {
                      if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); ask(); }
                    });
                    sendBtn.addEventListener('click', ask);

                    document.querySelectorAll('.eg-btn').forEach(b => {
                      b.addEventListener('click', () => { qin.value = b.dataset.q; qin.focus(); });
                    });

                    addMsg('bot',
                      '<p>Hello! I can help you investigate exceptions and incidents in your Spring Boot application.</p>' +
                      '<p style="margin-top:6px">Ask about a specific time, exception type, or HTTP endpoint &mdash; or click an example query on the left to get started.</p>',
                      null
                    );
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
