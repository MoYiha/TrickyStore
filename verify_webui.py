from playwright.sync_api import sync_playwright
import os
import time

def run():
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page()

        # Mock APIs
        page.route("**/api/resource_usage", lambda route: route.fulfill(
            status=200,
            content_type="application/json",
            body='''{
                "keybox_count": 5,
                "app_config_size": 1024,
                "global_mode": true,
                "rkp_bypass": true,
                "tee_broken_mode": false,
                "real_ram_kb": 1048576,
                "real_cpu": 15.5,
                "environment": "KernelSU"
            }'''
        ))
        page.route("**/api/language", lambda route: route.fulfill(status=404, body="{}"))
        page.route("**/api/stats", lambda route: route.fulfill(status=200, body='{"members": "1234", "banned": "0"}'))
        page.route("**/api/config", lambda route: route.fulfill(status=200, body="{}"))
        page.route("**/api/templates", lambda route: route.fulfill(status=200, body="[]"))
        page.route("**/api/packages", lambda route: route.fulfill(status=200, body="[]"))
        page.route("**/api/keyboxes", lambda route: route.fulfill(status=200, body="[]"))
        page.route("**/api/file*", lambda route: route.fulfill(status=200, body=""))
        page.route("**/api/cbox_status", lambda route: route.fulfill(status=200, body='{"locked":[], "unlocked":[], "server_status":[]}'))
        page.route("**/api/servers", lambda route: route.fulfill(status=200, body="[]"))


        cwd = os.getcwd()
        # Add token param
        page.goto(f"file://{cwd}/index.html?token=test-token")

        # Click the Info tab
        page.click("#tab_info")

        # Wait for tab active
        try:
            page.wait_for_selector("#info.active", timeout=5000)
            print("Tab switched successfully.")
        except Exception as e:
            print(f"Failed to switch tab: {e}")

        # Wait for table rows
        try:
            # Wait for tbody to have children
            page.wait_for_function("document.getElementById('resourceBody').children.length > 0", timeout=5000)
            print("Table populated.")

            # Verify description is present
            desc_count = page.locator(".res-desc").count()
            print(f"Found {desc_count} descriptions.")

            # Verify toggle size (check a computed style roughly or just existence of class)
            toggle_width = page.eval_on_selector(".toggle", "el => getComputedStyle(el).width")
            print(f"Toggle width: {toggle_width}")

        except Exception as e:
            print(f"Table verification failed: {e}")

        # Mobile View
        page.set_viewport_size({"width": 375, "height": 812})
        page.screenshot(path="verification_mobile.png", full_page=True)
        print("Mobile screenshot saved")

        browser.close()

if __name__ == "__main__":
    run()
