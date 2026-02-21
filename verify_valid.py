from playwright.sync_api import sync_playwright

def run(playwright):
    browser = playwright.chromium.launch(headless=True)
    page = browser.new_page()
    page.goto("http://localhost:8000/index.html?token=dummy")
    page.wait_for_load_state("networkidle")

    # Click Spoofing tab
    page.click("#tab_spoof")

    imei_input = page.locator("#inputImei")

    # Valid IMEI: 352099001761481
    imei_input.fill("352099001761481")

    dialog_triggered = False
    def handle_dialog(dialog):
        nonlocal dialog_triggered
        dialog_triggered = True
        print(f"Dialog message: {dialog.message}")
        dialog.dismiss() # Dismiss "Overwrite?" dialog

    page.on("dialog", handle_dialog)

    page.click("text=Apply System-Wide")

    notification = page.locator("#islandText")

    if dialog_triggered:
        print("Validation passed (Dialog triggered)")
    else:
        print(f"Validation failed unexpectedly: {notification.inner_text()}")

    page.screenshot(path="verification_valid.png")
    browser.close()

with sync_playwright() as playwright:
    run(playwright)
