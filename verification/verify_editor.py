from playwright.sync_api import sync_playwright
import os

def run(playwright):
    browser = playwright.chromium.launch(headless=True)
    page = browser.new_page()

    # Intercept requests
    def handle_route(route):
        url = route.request.url
        # print(f"Request: {url}")
        if "api/config" in url:
            route.fulfill(json={
                "global_mode": False, "tee_broken_mode": False, "rkp_bypass": False,
                "auto_beta_fetch": False, "auto_keybox_check": False, "random_on_boot": False,
                "drm_fix": False, "random_drm_on_boot": False,
                "files": ["target.txt"], "keybox_count": 0, "templates": []
            })
        elif "api/stats" in url:
            route.fulfill(json={"members": "123"})
        elif "api/templates" in url:
            route.fulfill(json=[])
        elif "api/packages" in url:
            route.fulfill(json=[])
        elif "api/keyboxes" in url:
            route.fulfill(json=[])
        elif "api/file" in url:
            route.fulfill(body="Initial Content")
        else:
            route.continue_()

    page.route("**/*", handle_route)

    # Load index.html via HTTP
    page.goto("http://localhost:8000/index.html?token=test")

    # Switch to Editor tab
    page.click("#tab_editor")

    # Wait for file to load
    try:
        page.wait_for_function("document.getElementById('fileEditor').value === 'Initial Content'", timeout=5000)
    except Exception as e:
        print(f"Timeout waiting for content. Content is: {page.eval_on_selector('#fileEditor', 'e => e.value')}")
        raise e

    # Type into editor
    page.fill("#fileEditor", "Initial Content + Modified")

    # Wait for UI update
    page.wait_for_timeout(500)

    # Check button state
    save_btn = page.locator("#saveBtn")

    # Assert text is "Save *"
    text = save_btn.inner_text()
    if text != "Save *":
        print(f"Error: Expected 'Save *', got '{text}'")
    else:
        print(f"Success: Button text is '{text}'")

    # Assert class contains 'primary'
    cls = save_btn.get_attribute("class")
    if "primary" not in cls:
         print(f"Error: Expected class 'primary', got '{cls}'")
    else:
         print(f"Success: Button class is '{cls}'")

    # Take screenshot
    page.screenshot(path="verification/after_change.png")

    print("Verification complete.")
    browser.close()

with sync_playwright() as p:
    run(p)
