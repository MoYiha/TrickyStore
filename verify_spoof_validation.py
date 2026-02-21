from playwright.sync_api import sync_playwright

def run(playwright):
    browser = playwright.chromium.launch(headless=True)
    page = browser.new_page()

    # Go to the local server
    page.goto("http://localhost:8000/index.html?token=dummy")

    # Wait for page to load
    page.wait_for_load_state("networkidle")

    # Click Spoofing tab
    page.click("#tab_spoof")

    # Find IMEI input
    imei_input = page.locator("#inputImei")

    # Test 1: Invalid Length
    imei_input.fill("123")
    page.click("text=Apply System-Wide")

    # Check for notification
    notification = page.locator("#islandText")
    print(f"Notification text (Length test): {notification.inner_text()}")
    assert "Invalid IMEI" in notification.inner_text()

    page.screenshot(path="verification_length.png")

    # Test 2: Invalid Luhn
    # 352099001761482 is a valid IMEI.
    # 352099001761483 should be invalid.
    imei_input.fill("352099001761483")
    page.click("text=Apply System-Wide")

    # Check for notification
    print(f"Notification text (Luhn test): {notification.inner_text()}")
    assert "Invalid IMEI" in notification.inner_text()

    page.screenshot(path="verification_luhn.png")

    # Test 3: Valid IMEI
    # 352099001761482 is valid
    imei_input.fill("352099001761482")

    # Mock window.confirm to return false so we don't actually try to POST (which would fail 404)
    # But we want to ensure validation PASSED.
    # If validation passes, it calls confirm().

    dialog_triggered = False
    def handle_dialog(dialog):
        nonlocal dialog_triggered
        dialog_triggered = True
        print(f"Dialog message: {dialog.message}")
        dialog.dismiss()

    page.on("dialog", handle_dialog)

    page.click("text=Apply System-Wide")

    if dialog_triggered:
        print("Validation passed (Dialog triggered)")
    else:
        print(f"Validation failed unexpectedly: {notification.inner_text()}")

    browser.close()

with sync_playwright() as playwright:
    run(playwright)
