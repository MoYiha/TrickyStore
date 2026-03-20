from playwright.sync_api import sync_playwright

def verify_feature(page):
    page.goto("http://localhost:8000/test_ui.html")
    page.wait_for_timeout(500)

    # Use evaluate to make the panel visible, as click might not work well on this mock html
    page.evaluate("document.getElementById('keys').classList.add('active');")
    page.evaluate("document.getElementById('dashboard').classList.remove('active');")
    page.evaluate("document.getElementById('keys').style.display='block';")
    page.wait_for_timeout(500)

    # Click Add Server
    page.evaluate("document.getElementById('addServerForm').style.display='block';")
    page.wait_for_timeout(500)

    # Select Auth Type
    page.select_option("#srvAuthType", "BEARER")
    # Trigger onchange manually just in case
    page.evaluate("document.getElementById('srvAuthType').dispatchEvent(new Event('change'))")
    page.wait_for_timeout(500)
    page.screenshot(path="/home/jules/verification/verification_bearer.png")

    page.select_option("#srvAuthType", "BASIC")
    page.evaluate("document.getElementById('srvAuthType').dispatchEvent(new Event('change'))")
    page.wait_for_timeout(500)
    page.screenshot(path="/home/jules/verification/verification_basic.png")

    page.select_option("#srvAuthType", "API_KEY")
    page.evaluate("document.getElementById('srvAuthType').dispatchEvent(new Event('change'))")
    page.wait_for_timeout(500)
    page.screenshot(path="/home/jules/verification/verification_apikey.png")

    page.select_option("#srvAuthType", "NONE")
    page.evaluate("document.getElementById('srvAuthType').dispatchEvent(new Event('change'))")
    page.wait_for_timeout(500)
    page.screenshot(path="/home/jules/verification/verification_none.png")

if __name__ == "__main__":
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(record_video_dir="/home/jules/verification/video")
        page = context.new_page()
        try:
            verify_feature(page)
        finally:
            context.close()
            browser.close()
