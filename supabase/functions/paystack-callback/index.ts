import { serve } from "https://deno.land/std@0.203.0/http/server.ts"

serve(async (req) => {
  const html = `
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Payment Successful</title>
        <style>
            body { font-family: -apple-system, system-ui, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; background-color: #f8fafc; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }
            .card { background: white; padding: 2rem; border-radius: 1rem; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); text-align: center; max-width: 400px; width: 90%; }
            .icon { background: #d1fae5; color: #059669; width: 64px; height: 64px; border-radius: 50%; display: flex; justify-content: center; align-items: center; margin: 0 auto 1rem; font-size: 32px; }
            h1 { color: #0f172a; font-size: 1.5rem; margin-bottom: 0.5rem; }
            p { color: #64748b; margin-bottom: 1.5rem; }
            .btn { background: #0284c7; color: white; padding: 0.75rem 1.5rem; border-radius: 0.5rem; text-decoration: none; font-weight: 600; display: inline-block; }
        </style>
    </head>
    <body>
        <div class="card">
            <div class="icon">✓</div>
            <h1>Transaction Complete</h1>
            <p>Your payment has been processed successfully. You can now return to the app.</p>
            <a href="#" onclick="window.close(); return false;" class="btn">Return to App</a>
        </div>
        <script>
            // Automatically try to notify the app
            setTimeout(() => {
                // Some apps look for this in the URL
                window.location.hash = "finished";
            }, 1000);
        </script>
    </body>
    </html>
  `;

  return new Response(html, {
    headers: { "Content-Type": "text/html" },
  });
})
