# Kill any existing server
kill  2>/dev/null || true

# Start server
python3 -m http.server 8000 > server.log 2>&1 &
PID=20746
sleep 3

# Write verification script
cat <<PY > /home/jules/verification/verify_cbox.py
from playwright.sync_api import Page, expect, sync_playwright
import os

def test_webui(page: Page):
    try:
        page.goto("http://localhost:8000/index.html")

        # Check tabs
        print("Checking tabs...")
        expect(page.locator("#tab_guide")).to_be_visible()
        expect(page.locator("#tab_keys")).to_be_visible()

        # Click guide tab
        print("Checking guide...")
        page.locator("#tab_guide").click()
        expect(page.locator("#guide")).to_be_visible()
        expect(page.get_by_text("Encrypted Keybox Distribution")).to_be_visible()

        # Click keys tab
        print("Checking keys...")
        page.locator("#tab_keys").click()
        expect(page.locator("#keys")).to_be_visible()
        expect(page.get_by_text("Remote Servers")).to_be_visible()

        # Check upload section
        print("Checking upload...")
        expect(page.get_by_text("Upload Keybox / CBOX")).to_be_visible()

        page.screenshot(path="/home/jules/verification/verification.png")
        print("Verification successful!")
    except Exception as e:
        print(f"Verification failed: {e}")
        page.screenshot(path="/home/jules/verification/failure.png")
        raise e

if __name__ == "__main__":
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        try:
            test_webui(page)
        finally:
            browser.close()
PY

# Run verification
python3 /home/jules/verification/verify_cbox.py

# Cleanup
kill 20746
