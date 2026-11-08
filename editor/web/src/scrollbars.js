import { ViewPlugin, EditorView } from "@codemirror/view";

const THICKNESS = 18;
const MIN_THUMB = 40;
const IDLE_MS = 900;
const OVERFLOW_EPSILON = 2;

export function computeThumb(viewport, content, offset, trackLength, minThumb = MIN_THUMB) {
  const range = content - viewport;
  if (!(viewport > 0) || !(content > 0) || !(trackLength > 0) || range <= OVERFLOW_EPSILON) return null;
  const length = Math.min(trackLength, Math.max(minThumb, Math.round((trackLength * viewport) / content)));
  const travel = Math.max(trackLength - length, 0);
  const ratio = Math.min(1, Math.max(0, offset / range));
  return { length, position: travel * ratio, travel, range };
}

export function offsetForThumbPosition(position, thumb) {
  if (!thumb || thumb.travel <= 0) return 0;
  const ratio = Math.min(1, Math.max(0, position / thumb.travel));
  return ratio * thumb.range;
}

function createBar(axis) {
  const track = document.createElement("div");
  track.className = `cm-bc-bar cm-bc-bar-${axis}`;
  const thumb = document.createElement("div");
  thumb.className = "cm-bc-thumb";
  track.appendChild(thumb);
  return { axis, track, thumb, thumbInfo: null, visible: false };
}

class ScrollbarPlugin {
  constructor(view) {
    this.view = view;
    this.scroller = view.scrollDOM;
    this.vertical = createBar("v");
    this.horizontal = createBar("h");
    this.bars = [this.vertical, this.horizontal];
    this.frame = 0;
    this.idleTimer = null;
    this.drag = null;

    for (const bar of this.bars) {
      view.dom.appendChild(bar.track);
      bar.onDown = (event) => this.startDrag(bar, event);
      bar.onMove = (event) => this.moveDrag(bar, event);
      bar.onEnd = (event) => this.endDrag(bar, event);
      bar.thumb.addEventListener("pointerdown", bar.onDown);
      bar.thumb.addEventListener("pointermove", bar.onMove);
      bar.thumb.addEventListener("pointerup", bar.onEnd);
      bar.thumb.addEventListener("pointercancel", bar.onEnd);
      bar.thumb.addEventListener("lostpointercapture", bar.onEnd);
    }

    this.onScroll = () => {
      this.markActive();
      this.schedule();
    };
    this.scroller.addEventListener("scroll", this.onScroll, { passive: true });

    this.observer = typeof ResizeObserver === "function" ? new ResizeObserver(() => this.schedule()) : null;
    if (this.observer) {
      this.observer.observe(this.scroller);
      this.observer.observe(view.contentDOM);
    }
    this.schedule();
  }

  update(update) {
    if (update.docChanged || update.geometryChanged || update.viewportChanged) this.schedule();
  }

  destroy() {
    if (this.frame) cancelAnimationFrame(this.frame);
    clearTimeout(this.idleTimer);
    this.scroller.removeEventListener("scroll", this.onScroll);
    if (this.observer) this.observer.disconnect();
    for (const bar of this.bars) {
      bar.thumb.removeEventListener("pointerdown", bar.onDown);
      bar.thumb.removeEventListener("pointermove", bar.onMove);
      bar.thumb.removeEventListener("pointerup", bar.onEnd);
      bar.thumb.removeEventListener("pointercancel", bar.onEnd);
      bar.thumb.removeEventListener("lostpointercapture", bar.onEnd);
      bar.track.remove();
    }
    this.drag = null;
  }

  schedule() {
    if (this.frame) return;
    this.frame = requestAnimationFrame(() => {
      this.frame = 0;
      this.sync();
    });
  }

  markActive() {
    for (const bar of this.bars) bar.track.classList.add("cm-bc-active");
    clearTimeout(this.idleTimer);
    if (this.drag) return;
    this.idleTimer = setTimeout(() => {
      for (const bar of this.bars) bar.track.classList.remove("cm-bc-active");
    }, IDLE_MS);
  }

  measure() {
    const scroller = this.scroller;
    const showVertical = scroller.scrollHeight - scroller.clientHeight > OVERFLOW_EPSILON;
    const showHorizontal = scroller.scrollWidth - scroller.clientWidth > OVERFLOW_EPSILON;
    const verticalTrack = scroller.clientHeight - (showHorizontal ? THICKNESS : 0);
    const horizontalTrack = scroller.clientWidth - (showVertical ? THICKNESS : 0);
    return {
      vertical: computeThumb(scroller.clientHeight, scroller.scrollHeight, scroller.scrollTop, verticalTrack),
      horizontal: computeThumb(scroller.clientWidth, scroller.scrollWidth, scroller.scrollLeft, horizontalTrack),
      verticalTrack,
      horizontalTrack,
      top: scroller.offsetTop,
      left: scroller.offsetLeft,
      height: scroller.clientHeight,
      width: scroller.clientWidth,
    };
  }

  sync() {
    const m = this.measure();
    this.apply(this.vertical, m.vertical, () => {
      const style = this.vertical.track.style;
      style.top = `${m.top}px`;
      style.height = `${m.verticalTrack}px`;
      style.right = "0px";
      this.vertical.thumb.style.height = `${m.vertical.length}px`;
      this.vertical.thumb.style.transform = `translate3d(0, ${m.vertical.position}px, 0)`;
    });
    this.apply(this.horizontal, m.horizontal, () => {
      const style = this.horizontal.track.style;
      style.top = `${m.top + m.height - THICKNESS}px`;
      style.left = `${m.left}px`;
      style.width = `${m.horizontalTrack}px`;
      this.horizontal.thumb.style.width = `${m.horizontal.length}px`;
      this.horizontal.thumb.style.transform = `translate3d(${m.horizontal.position}px, 0, 0)`;
    });
  }

  apply(bar, info, write) {
    bar.thumbInfo = info;
    const visible = info !== null;
    if (visible !== bar.visible) {
      bar.visible = visible;
      bar.track.classList.toggle("cm-bc-on", visible);
    }
    if (visible) write();
  }

  startDrag(bar, event) {
    if (event.pointerType === "mouse" && event.button !== 0) return;
    if (!bar.thumbInfo) return;
    event.preventDefault();
    event.stopPropagation();
    try {
      bar.thumb.setPointerCapture(event.pointerId);
    } catch {
      return;
    }
    const vertical = bar.axis === "v";
    this.drag = {
      bar,
      id: event.pointerId,
      startPointer: vertical ? event.clientY : event.clientX,
      startPosition: bar.thumbInfo.position,
    };
    bar.track.classList.add("cm-bc-dragging");
    this.markActive();
  }

  moveDrag(bar, event) {
    const drag = this.drag;
    if (!drag || drag.bar !== bar || drag.id !== event.pointerId) return;
    event.preventDefault();
    const vertical = bar.axis === "v";
    const pointer = vertical ? event.clientY : event.clientX;
    const m = this.measure();
    const info = vertical ? m.vertical : m.horizontal;
    if (!info) return;
    const target = offsetForThumbPosition(drag.startPosition + (pointer - drag.startPointer), info);
    if (vertical) this.scroller.scrollTop = target;
    else this.scroller.scrollLeft = target;
  }

  endDrag(bar, event) {
    const drag = this.drag;
    if (!drag || drag.bar !== bar) return;
    if (event.pointerId !== undefined && drag.id !== event.pointerId) return;
    this.drag = null;
    bar.track.classList.remove("cm-bc-dragging");
    this.markActive();
    this.schedule();
    try {
      bar.thumb.releasePointerCapture(drag.id);
    } catch {
      return;
    }
  }
}

const scrollbarTheme = EditorView.baseTheme({
  ".cm-bc-bar": {
    position: "absolute",
    display: "none",
    zIndex: "6",
    pointerEvents: "none",
    overflow: "hidden",
  },
  ".cm-bc-bar.cm-bc-on": { display: "block" },
  ".cm-bc-bar-v": { width: `${THICKNESS}px` },
  ".cm-bc-bar-h": { height: `${THICKNESS}px` },
  ".cm-bc-thumb": {
    position: "absolute",
    left: "0",
    top: "0",
    pointerEvents: "auto",
    touchAction: "none",
    userSelect: "none",
    willChange: "transform",
  },
  ".cm-bc-bar-v .cm-bc-thumb": { width: `${THICKNESS}px` },
  ".cm-bc-bar-h .cm-bc-thumb": { height: `${THICKNESS}px` },
  ".cm-bc-thumb::after": {
    content: '""',
    position: "absolute",
    borderRadius: "999px",
    backgroundColor: "var(--bc-muted, #8a919c)",
    opacity: "0.38",
    transition: "opacity 160ms ease, width 120ms ease, height 120ms ease",
  },
  ".cm-bc-bar-v .cm-bc-thumb::after": { top: "2px", bottom: "2px", right: "3px", width: "4px" },
  ".cm-bc-bar-h .cm-bc-thumb::after": { left: "2px", right: "2px", bottom: "3px", height: "4px" },
  ".cm-bc-active .cm-bc-thumb::after": { opacity: "0.8" },
  ".cm-bc-dragging .cm-bc-thumb::after": { opacity: "1", backgroundColor: "var(--bc-accent, #4ea1ff)" },
  ".cm-bc-bar-v.cm-bc-dragging .cm-bc-thumb::after": { width: "7px" },
  ".cm-bc-bar-h.cm-bc-dragging .cm-bc-thumb::after": { height: "7px" },
  ".cm-scroller": { scrollbarWidth: "none" },
  ".cm-scroller::-webkit-scrollbar": { display: "none" },
});

export function editorScrollbars() {
  return [ViewPlugin.fromClass(ScrollbarPlugin), scrollbarTheme];
}
