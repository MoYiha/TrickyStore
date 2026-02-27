from playwright.sync_api import sync_playwright
import os

def test_webui():
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page()

        path = os.path.abspath("verification/index.html")
        page.on("console", lambda msg: print(f"Console: {msg.text}"))

        # Override BEFORE navigation if possible, but for file:// we inject after load
        # But init() runs immediately.
        # We can try to reload or just mock for subsequent calls.

        page.goto(f"file://{path}")

        page.evaluate("""
            window.fetchAuth = async (url) => {
                console.log("Fetching " + url);
                if (!url) return { ok: false };
                if (url.includes('/api/resource_usage')) {
                    return {
                        ok: true,
                        json: async () => ({
                            keybox_count: 5,
                            app_config_size: 2048,
                            global_mode: true,
                            rkp_bypass: true,
                            tee_broken_mode: false
                        })
                    };
                }
                return { ok: false };
            };
        """)

        page.evaluate("switchTab('info')")

        page.wait_for_selector("#info.active", timeout=2000)
        page.wait_for_timeout(500)

        page.screenshot(path="verification/webui_info.png")
        browser.close()

if __name__ == "__main__":
    test_webui()
