import { serve } from "https://deno.land/std@0.203.0/http/server.ts"

const PAYSTACK_SECRET_KEY = Deno.env.get('PAYSTACK_SECRET_KEY')

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  // Handle CORS
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })

  const healthReport: any = {
    timestamp: new Date().toISOString(),
    environment: {
      PAYSTACK_SECRET_KEY_EXISTS: !!PAYSTACK_SECRET_KEY,
      KEY_PREFIX: PAYSTACK_SECRET_KEY ? PAYSTACK_SECRET_KEY.substring(0, 7) + "..." : "NONE",
    },
    connectivity: {
      can_reach_paystack: false,
      http_status: null,
      error: null,
      latency_ms: 0
    }
  }

  try {
    if (!PAYSTACK_SECRET_KEY) {
      throw new Error("Secret key is missing. Ensure you ran 'supabase secrets set PAYSTACK_SECRET_KEY=...'")
    }

    const start = Date.now()
    // We call the 'transaction' list with 1 item as a lightweight "ping"
    const response = await fetch('https://api.paystack.co/transaction?perPage=1', {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${PAYSTACK_SECRET_KEY}`,
        'Content-Type': 'application/json',
      },
    })

    healthReport.connectivity.latency_ms = Date.now() - start
    healthReport.connectivity.http_status = response.status

    if (response.ok) {
      healthReport.connectivity.can_reach_paystack = true
    } else {
      const errorData = await response.json()
      healthReport.connectivity.error = errorData.message || "Unknown Paystack Error"
    }

  } catch (err: any) {
    healthReport.connectivity.error = err.message
  }

  return new Response(JSON.stringify(healthReport, null, 2), {
    headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    status: healthReport.connectivity.can_reach_paystack ? 200 : 500
  })
})
