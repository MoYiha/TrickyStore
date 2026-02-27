import http.server
import socketserver
import threading
import os
from playwright.sync_api import sync_playwright

PORT = 8000

def start_server():
    handler = http.server.SimpleHTTPRequestHandler
    with socketserver.TCPServer(("", PORT), handler) as httpd:
        print("serving at port", PORT)
        httpd.serve_forever()

# Start server in thread
t = threading.Thread(target=start_server, daemon=True)
t.start()

def run():
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page()

        # Mock APIs - matching against localhost
        # Playwright route patterns:
        # If I request http://localhost:8000/api/resource_usage, the pattern "**/api/resource_usage" should match.

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
        # Add CORS headers to mocks just in case, though same origin
        def fulfill_json(route, body):
            route.fulfill(
                status=200,
                content_type="application/json",
                body=body,
                headers={"Access-Control-Allow-Origin": "*"}
            )

        page.route("**/api/language", lambda route: fulfill_json(route, "{}"))
        page.route("**/api/stats", lambda route: fulfill_json(route, '{"members": "1234", "banned": "0"}'))
        page.route("**/api/config", lambda route: fulfill_json(route, "{}"))
        page.route("**/api/templates", lambda route: fulfill_json(route, "[]"))
        page.route("**/api/packages", lambda route: fulfill_json(route, "[]"))
        page.route("**/api/keyboxes", lambda route: fulfill_json(route, "[]"))
        page.route("**/api/cbox_status", lambda route: fulfill_json(route, '{"locked":[], "unlocked":[], "server_status":[]}'))
        page.route("**/api/servers", lambda route: fulfill_json(route, "[]"))

        # Navigate to localhost
        page.goto(f"http://localhost:{PORT}/index.html?token=test-token")

        # Click Info tab
        page.click("#tab_info")

        # Wait for table
        try:
            page.wait_for_selector("#resourceBody tr", timeout=5000)
            print("Table populated.")

            # Verify toggle width
            width = page.eval_on_selector(".toggle", "el => getComputedStyle(el).width")
            print(f"Toggle width: {width}")
            if width == "52px":
                print("SUCCESS: Toggle width is 52px")
            else:
                print(f"FAILURE: Toggle width is {width}, expected 52px")

            # Verify description class
            desc_count = page.locator(".res-desc").count()
            print(f"Descriptions found: {desc_count}")
            if desc_count > 0:
                 print("SUCCESS: Descriptions present")
            else:
                 print("FAILURE: No descriptions found")

        except Exception as e:
            print(f"Verification failed: {e}")

        # Screenshots
        page.set_viewport_size({"width": 375, "height": 812})
        page.screenshot(path="verification_mobile.png", full_page=True)
        print("Mobile screenshot saved")

        page.set_viewport_size({"width": 1280, "height": 720})
        page.screenshot(path="verification_desktop.png", full_page=True)
        print("Desktop screenshot saved")

        browser.close()

if __name__ == "__main__":
    run()
