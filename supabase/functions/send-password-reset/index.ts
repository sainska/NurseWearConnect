import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { SmtpClient } from "https://deno.land/x/smtp@v0.7.0/mod.ts";

// Updated SMTP Configuration with NEW European Gateway
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
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const body = await req.json()
    console.log("Processing Request for:", body.record?.email)

    const { email, code } = body.record
    if (!email || !code) throw new Error('Invalid record data')

    const client = new SmtpClient();

    console.log(`Connecting to EU Gateway: ${SMTP_CONFIG.hostname}:${SMTP_CONFIG.port}...`)

    await client.connect({
      hostname: SMTP_CONFIG.hostname,
      port: SMTP_CONFIG.port,
      username: SMTP_CONFIG.username,
      password: SMTP_CONFIG.password,
    });

    console.log("Authenticated with EU Gateway. Sending mail...")

    await client.send({
      from: "security@nursewearconnect.com",
      to: email,
      subject: `${code} is your reset code`,
      html: `
        <div style="font-family: sans-serif; padding: 20px; border: 1px solid #eee; border-radius: 10px;">
          <h2 style="color: #0D9488;">NurseWear Connect</h2>
          <p>Your password reset code is: <b style="font-size: 24px;">${code}</b></p>
          <p>This code expires in 15 minutes.</p>
          <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;">
          <p style="font-size: 12px; color: #999;">&copy; 2026 NurseWear Connect. Delivering Quality Healthcare Apparel.</p>
        </div>
      `,
    });

    await client.close();
    console.log("Email dispatched successfully via EU Gateway.")

    return new Response(JSON.stringify({ success: true }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 200,
    })

  } catch (error: any) {
    console.error("SMTP ERROR (EU Gateway):", error.message)
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
