import { DocumentStore } from "./documents.js";
import { createHost, post, requestLint } from "./host.js";

const store = new DocumentStore({ parent: document.getElementById("editor"), post, requestLint });
createHost(store);

// When the soft keyboard opens or closes the WebView is resized; keep the caret in view.
window.addEventListener("resize", () => requestAnimationFrame(() => store.keepCursorVisible()));

post({ type: "ready" });
