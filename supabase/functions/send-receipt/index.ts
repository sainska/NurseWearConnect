import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { SmtpClient } from "https://deno.land/x/smtp@v0.7.0/mod.ts";

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
const SMTP_USER = Deno.env.get('SMTP_USER')
const SMTP_PASS = Deno.env.get('SMTP_PASS')

// Updated SMTP Configuration with NEW European Gateway
const SMTP_CONFIG = {
  hostname: "pro.eu.turbo-smtp.com",
  port: 587,
  username: SMTP_USER,
  password: SMTP_PASS,
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
    const supabase = createClient(SUPABASE_URL!, SUPABASE_SERVICE_ROLE_KEY!)
    const { orderId, email } = await req.json()

    if (!orderId || !email) throw new Error('Missing orderId or email')

    const { data: order, error: orderError } = await supabase
      .from('orders')
      .select(`*, profiles:user_id(full_name), order_items(*, products(name))`)
      .eq('id', orderId)
      .single()

    if (orderError || !order) throw new Error(`Order not found`)

    const customerName = order.profiles?.full_name || 'Valued Customer'
    const itemsHtml = order.order_items.map((item: any) => `
        <tr>
          <td style="padding: 12px 0; border-bottom: 1px solid #E2E8F0;">
            <div style="font-weight: 600; color: #1E293B;">${item.products?.name}</div>
            <div style="font-size: 12px; color: #64748B;">Size: ${item.size}</div>
          </td>
          <td style="padding: 12px 0; border-bottom: 1px solid #E2E8F0; text-align: center;">${item.quantity}</td>
          <td style="padding: 12px 0; border-bottom: 1px solid #E2E8F0; text-align: right; font-weight: 600;">KES ${item.unit_price.toLocaleString()}</td>
        </tr>
    `).join('')

    const htmlContent = `
    <!DOCTYPE html>
    <html>
    <body style="margin: 0; padding: 0; background-color: #F8FAFC; font-family: sans-serif;">
        <div style="max-width: 600px; margin: 0 auto; padding: 40px 20px;">
            <div style="background-color: #FFFFFF; border-radius: 16px; overflow: hidden; border: 1px solid #E2E8F0;">
                <div style="background-color: #0D9488; padding: 32px; text-align: center;">
                    <h1 style="color: #FFFFFF; margin: 0; font-size: 24px; font-weight: 800;">NURSE WEAR CONNECT</h1>
                </div>
                <div style="padding: 40px;">
                    <h2 style="font-size: 20px; font-weight: 700; color: #1E293B;">Order Confirmation</h2>
                    <p>Hi ${customerName}, thank you for your order! Here is your digital receipt.</p>
                    <table style="width: 100%; border-collapse: collapse; margin-bottom: 32px;">
                        <thead>
                            <tr>
                                <th style="text-align: left; color: #94A3B8;">Item</th>
                                <th style="text-align: center; color: #94A3B8;">Qty</th>
                                <th style="text-align: right; color: #94A3B8;">Price</th>
                            </tr>
                        </thead>
                        <tbody>${itemsHtml}</tbody>
                    </table>
                    <div style="text-align: right; font-size: 18px; font-weight: 700; color: #0D9488;">
                        Total: KSh ${order.total_amount.toLocaleString()}
                    </div>
                </div>
            </div>
        </div>
    </body>
    </html>
    `

    const client = new SmtpClient();
    await client.connect({
      hostname: SMTP_CONFIG.hostname,
      port: SMTP_CONFIG.port,
      username: SMTP_CONFIG.username,
      password: SMTP_CONFIG.password,
    });

    await client.send({
      from: "receipts@nursewearconnect.com",
      to: email,
      subject: `Order Confirmation #${orderId.slice(-8).toUpperCase()}`,
      content: htmlContent,
      html: htmlContent,
    });

    await client.close();

    return new Response(JSON.stringify({ success: true }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 200,
    })

  } catch (error: any) {
    console.error(error)
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
