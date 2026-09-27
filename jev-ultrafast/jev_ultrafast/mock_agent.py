"""Offline mock agent so the inspector UI can be exercised without API keys or Chrome."""

from __future__ import annotations

import base64
import io
import time

from .model import action_space

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:  # pragma: no cover
    Image = ImageDraw = ImageFont = None


def _jpeg(title: str, subtitle: str) -> str:
    if Image is None:
        # 1x1 jpeg fallback
        return "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAn/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAGfAP/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAQUCf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQMBAT8Bf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQIBAT8Bf//Z"
    img = Image.new("RGB", (960, 640), (248, 250, 246))
    draw = ImageDraw.Draw(img)
    draw.rectangle((0, 0, 960, 64), fill=(46, 83, 68))
    draw.text((28, 20), "Jev Ultrafast · mock preview", fill=(252, 252, 248))
    draw.rectangle((48, 120, 420, 176), outline=(46, 83, 68), width=2)
    draw.text((64, 140), "Where from?", fill=(52, 62, 56))
    draw.rectangle((460, 120, 832, 176), outline=(46, 83, 68), width=2)
    draw.text((476, 140), "Where to?", fill=(52, 62, 56))
    draw.rectangle((48, 220, 280, 276), fill=(46, 83, 68))
    draw.text((88, 240), "Search", fill=(252, 252, 248))
    draw.text((48, 340), title, fill=(46, 83, 68))
    draw.text((48, 380), subtitle, fill=(100, 110, 104))
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=80)
    return base64.b64encode(buf.getvalue()).decode()


def _page(step: int) -> dict:
    titles = [
        ("Google Flights · mock", "Pick origin, destination, then search."),
        ("Origin focused", "TYPE_TEXT into Where from?"),
        ("Results visible", "Matching flights are on screen."),
    ]
    title, subtitle = titles[min(step, len(titles) - 1)]
    actions = [
        {
            "id": "e1",
            "kind": "fill",
            "label": "Where from?",
            "role": "combobox",
            "value": "" if step == 0 else "Zurich",
            "node": 10,
            "rect": {"x": 48, "y": 120, "w": 372, "h": 56},
        },
        {
            "id": "e2",
            "kind": "click",
            "label": "Where from?",
            "role": "combobox",
            "value": "" if step == 0 else "Zurich",
            "node": 10,
            "rect": {"x": 48, "y": 120, "w": 372, "h": 56},
        },
        {
            "id": "e3",
            "kind": "fill",
            "label": "Where to?",
            "role": "combobox",
            "value": "" if step < 2 else "London",
            "node": 20,
            "rect": {"x": 460, "y": 120, "w": 372, "h": 56},
        },
        {
            "id": "e4",
            "kind": "click",
            "label": "Where to?",
            "role": "combobox",
            "value": "" if step < 2 else "London",
            "node": 20,
            "rect": {"x": 460, "y": 120, "w": 372, "h": 56},
        },
        {
            "id": "e5",
            "kind": "click",
            "label": "Search",
            "role": "button",
            "value": "",
            "node": 30,
            "rect": {"x": 48, "y": 220, "w": 232, "h": 56},
        },
        {"id": "wait", "kind": "wait", "label": "Wait"},
        {"id": "done", "kind": "done", "label": "Done"},
        {"id": "blocked", "kind": "blocked", "label": "Blocked"},
    ]
    return {
        "url": "https://www.google.com/travel/flights?hl=en&mock=1",
        "title": title,
        "text": subtitle,
        "w": 960,
        "h": 640,
        "scroll": {"y": 0},
        "screenshot": _jpeg(title, subtitle),
        "actions": actions,
        "fingerprint": f"mock-fp-{step}",
    }


def _decision(step: int) -> dict:
    # Scripted choices that show operation + target probabilities in the inspector.
    script = [
        {
            "choice": "e1",
            "operation": "TYPE_TEXT",
            "target": "1",
            "confidence": 0.91,
            "target_confidence": 0.88,
            "probabilities": {"e1": 0.88, "e3": 0.07, "e5": 0.05},
            "operation_probabilities": {
                "TYPE_TEXT": 0.72,
                "CLICK": 0.18,
                "WAIT": 0.06,
                "DONE": 0.03,
                "BLOCKED": 0.01,
            },
            "target_probabilities": {"1": 0.88, "2": 0.09, "3": 0.03},
            "latency_ms": 42,
            "usage": {"mock": True},
            "request": {"mode": "mock", "step": step},
            "text": "Zurich",
            "text_helper": "mock text helper",
        },
        {
            "choice": "e3",
            "operation": "TYPE_TEXT",
            "target": "2",
            "confidence": 0.86,
            "target_confidence": 0.81,
            "probabilities": {"e1": 0.05, "e3": 0.81, "e5": 0.14},
            "operation_probabilities": {
                "TYPE_TEXT": 0.69,
                "CLICK": 0.21,
                "WAIT": 0.05,
                "DONE": 0.04,
                "BLOCKED": 0.01,
            },
            "target_probabilities": {"1": 0.08, "2": 0.81, "3": 0.11},
            "latency_ms": 38,
            "usage": {"mock": True},
            "request": {"mode": "mock", "step": step},
            "text": "London",
            "text_helper": "mock text helper",
        },
        {
            "choice": "DONE",
            "operation": "DONE",
            "target": None,
            "confidence": 0.93,
            "target_confidence": None,
            "probabilities": {"DONE": 0.93, "WAIT": 0.05, "BLOCKED": 0.02},
            "operation_probabilities": {
                "DONE": 0.93,
                "WAIT": 0.05,
                "CLICK": 0.01,
                "TYPE_TEXT": 0.01,
                "BLOCKED": 0.0,
            },
            "target_probabilities": {},
            "latency_ms": 29,
            "usage": {"mock": True},
            "request": {"mode": "mock", "step": step},
        },
    ]
    return script[min(step, len(script) - 1)]


class MockAgent:
    """Drop-in stand-in for Agent used by the loopback inspector."""

    def __init__(self, url, goals, *, record_dir=None, screenshots=False):
        task = goals.strip() if isinstance(goals, str) else "\n".join(goals).strip()
        if not task:
            raise ValueError("Supply a task")
        self.step = 0
        self.state = dict(
            browser=None,
            goal=task,
            page=_page(0),
            decision=None,
            history=[],
            status="ready",
            plan=[task],
            plan_index=0,
            decisions=[],
            text_calls=[],
            elapsed_ms=0,
            started_at=time.perf_counter(),
            record=False,
            mock=True,
        )

    def snapshot(self):
        return {
            **{k: v for k, v in self.state.items() if k != "browser"},
            "elements": action_space(self.state["page"]["actions"])[0],
        }

    def close(self):
        self.state["status"] = "idle"

    def command(self, name, body=None):
        body = body or {}
        state = self.state
        if name == "tick":
            self.command("predict", {})
            return self.command("act", {"fingerprint": state["page"]["fingerprint"]})
        if name == "predict":
            if state["status"] in {"done", "blocked"}:
                raise ValueError("This run has stopped. Start a fresh demo.")
            decision = _decision(self.step)
            state["decision"] = decision
            state["decisions"].append(
                {
                    **decision,
                    "fingerprint": state["page"]["fingerprint"],
                    "elapsed_ms": round((time.perf_counter() - state["started_at"]) * 1000),
                }
            )
            state["status"] = "predicted"
            state["elapsed_ms"] = round((time.perf_counter() - state["started_at"]) * 1000)
            return self.snapshot()
        if name == "act":
            decision, page = state["decision"], state["page"]
            if not decision or body.get("fingerprint") != page["fingerprint"]:
                raise ValueError("Observe and choose before acting")
            state["decision"] = None
            selected = decision["choice"]
            if selected == "DONE":
                state["status"] = "done"
                state["plan_index"] = 1
                state["elapsed_ms"] = round((time.perf_counter() - state["started_at"]) * 1000)
                return self.snapshot()
            if selected == "BLOCKED":
                state["status"] = "blocked"
                return self.snapshot()
            state["history"].append(
                {
                    "step": len(state["history"]) + 1,
                    "action": f"{decision['operation']} · {selected}",
                    "text": decision.get("text"),
                    "text_helper": decision.get("text_helper"),
                    "latency_ms": decision["latency_ms"],
                    "probability": decision.get("target_confidence") or decision["confidence"],
                    "page_changed": True,
                }
            )
            self.step += 1
            state["page"] = _page(self.step)
            state["status"] = "ready"
            state["elapsed_ms"] = round((time.perf_counter() - state["started_at"]) * 1000)
            return self.snapshot()
        raise ValueError(f"Unknown command: {name}")
