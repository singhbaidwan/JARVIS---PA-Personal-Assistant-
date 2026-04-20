const form = document.getElementById("prompt-form");
const promptInput = document.getElementById("prompt-input");
const chatLog = document.getElementById("chat-log");
const apiUrlInput = document.getElementById("api-url");
const statusText = document.getElementById("status-text");
const avatarShell = document.getElementById("avatar-shell");
const sendButton = document.getElementById("send-button");

function pushMessage(role, text, kind = "") {
  const entry = document.createElement("div");
  entry.className = `bubble ${role}${kind ? ` ${kind}` : ""}`;
  entry.textContent = text;
  chatLog.appendChild(entry);
  chatLog.scrollTop = chatLog.scrollHeight;
}

function setWorkingState(isWorking, label) {
  sendButton.disabled = isWorking;
  statusText.textContent = `Status: ${label}`;
  avatarShell.classList.toggle("speaking", isWorking);
}

async function sendPrompt(prompt) {
  const endpoint = apiUrlInput.value.trim();
  if (!endpoint) {
    throw new Error("LLM API URL is empty");
  }

  const payload = {
    prompt,
    system_prompt: "You are JARVIS, an assistant that is concise and practical.",
    temperature: 0.3,
    max_tokens: 300,
  };

  const response = await fetch(endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });

  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const detail = typeof body.detail === "string" ? body.detail : `HTTP ${response.status}`;
    throw new Error(detail);
  }

  if (typeof body.response !== "string" || !body.response.trim()) {
    throw new Error("Empty response from LLM endpoint");
  }
  return body.response.trim();
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const prompt = promptInput.value.trim();
  if (!prompt) {
    return;
  }

  pushMessage("user", prompt);
  promptInput.value = "";
  setWorkingState(true, "thinking");

  try {
    const answer = await sendPrompt(prompt);
    pushMessage("jarvis", answer);
    setWorkingState(false, "ready");
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown error";
    pushMessage("jarvis", message, "error");
    setWorkingState(false, "error");
  }
});

pushMessage("jarvis", "Avatar online. Connect jarvis-ai and send your first prompt.");
