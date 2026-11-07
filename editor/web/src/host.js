// The bridge between Kotlin and the editor.
//   Kotlin -> JS: window.ByteCodeHost.dispatch(jsonString)  with {"cmd": "...", ...}
//   JS -> Kotlin: window.AndroidHost.postMessage(jsonString) with {"type": "...", ...}
export function post(message) {
  try {
    if (window.AndroidHost && typeof window.AndroidHost.postMessage === "function") {
      window.AndroidHost.postMessage(JSON.stringify(message));
      return;
    }
  } catch (error) {
    // fall through to the console (running in a desktop browser during development)
  }
  console.debug("[host]", message);
}

const pendingLint = new Map();
let lintCounter = 0;

// Asks the app to lint `text` (Luau uses the native parser). Resolves with [] if the app does not answer.
export function requestLint(docId, language, text) {
  return new Promise((resolve) => {
    const requestId = `l${++lintCounter}`;
    const timer = setTimeout(() => {
      pendingLint.delete(requestId);
      resolve([]);
    }, 8000);
    pendingLint.set(requestId, (items) => {
      clearTimeout(timer);
      resolve(items);
    });
    post({ type: "lint", requestId, docId, language, text });
  });
}

function resolveLint(requestId, items) {
  const resolve = pendingLint.get(requestId);
  if (resolve) {
    pendingLint.delete(requestId);
    resolve(items);
  }
}

export function createHost(store) {
  const reportError = (error) => post({ type: "log", level: "error", message: String((error && error.stack) || error) });

  const handlers = {
    openDocument: (c) => store.open(c),
    activateDocument: (c) => store.activate(c.id),
    closeDocument: (c) => store.close(c.id),
    renameDocument: (c) => store.rename(c.id, c.name, c.language),
    markSaved: (c) => store.markSaved(c.id, c.requestId),
    requestText: (c) => store.requestText(c.id, c.requestId),
    applySettings: (c) => store.applySettings(c.settings),
    insertText: (c) => store.insertText(c.text),
    action: (c) => store.runAction(c.name),
    revealPosition: (c) => store.revealPosition(c.id, c.line, c.column, c.focus),
    focus: () => store.focus(),
    lintResult: (c) => resolveLint(c.requestId, c.items || []),
  };

  window.ByteCodeHost = {
    dispatch(json) {
      let command;
      try {
        command = JSON.parse(json);
      } catch (error) {
        reportError(error);
        return;
      }
      const handler = handlers[command.cmd];
      if (!handler) {
        post({ type: "log", level: "warn", message: `Unknown editor command: ${command.cmd}` });
        return;
      }
      try {
        const result = handler(command);
        if (result && typeof result.catch === "function") result.catch(reportError);
      } catch (error) {
        reportError(error);
      }
    },
  };

  window.addEventListener("error", (event) =>
    post({ type: "log", level: "error", message: `${event.message} (${event.filename}:${event.lineno})` }));
  window.addEventListener("unhandledrejection", (event) => reportError(event.reason));
}
