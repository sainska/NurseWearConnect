import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { SmtpClient } from "https://deno.land/x/smtp@v0.7.0/mod.ts";

const SMTP_CONFIG = {
  hostname: "pro.eu.turbo-smtp.com",
  port: 587,
  username: "9c9522797c53c8cbb17a",
  password: "w5yAdQUqHsoxYhkIM7FG",
};

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })

  const report: any = {
    step1_network: "Pending",
    step2_auth: "Pending",
    details: ""
  }

  try {
    const client = new SmtpClient();

    // Step 1: Connect
    try {
        await client.connect({
          hostname: SMTP_CONFIG.hostname,
          port: SMTP_CONFIG.port,
          username: SMTP_CONFIG.username,
          password: SMTP_CONFIG.password,
        });
        report.step1_network = "SUCCESS";
        report.step2_auth = "SUCCESS";
        report.details = "Successfully connected and authenticated with Turbo-SMTP EU Gateway.";
    } catch (err) {
        report.step1_network = "FAILED";
        report.details = `Connection/Auth Error: ${err.message}`;
        throw err;
    }

    await client.close();

  } catch (error: any) {
    console.error("Test Error:", error.message)
  }

  return new Response(JSON.stringify(report, null, 2), {
    headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    status: report.step2_auth === "SUCCESS" ? 200 : 500,
  })
})
