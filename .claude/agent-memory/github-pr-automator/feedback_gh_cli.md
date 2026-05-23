---
name: feedback-gh-cli-not-installed
description: GitHub CLI (gh) is not installed on this machine — fall back to manual PR URL
metadata:
  type: feedback
---

The `gh` CLI is not installed on this Windows 11 machine (PoojaSoni). Neither `where.exe gh` nor searches in Program Files / AppData find it.

**Why:** Tool is simply not set up in this environment.

**How to apply:** Skip `gh pr create` steps entirely. After pushing, always provide the manual PR URL in the format `https://github.com/<owner>/<repo>/compare/<branch>?expand=1` and instruct the user to open it in their browser.
