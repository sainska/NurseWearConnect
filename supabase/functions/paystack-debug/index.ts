import { serve } from "https://deno.land/std@0.203.0/http/server.ts"

const PAYSTACK_SECRET_KEY = Deno.env.get('PAYSTACK_SECRET_KEY')

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })

  const results = {
    step1_env_check: {
      key_found: !!PAYSTACK_SECRET_KEY,
      key_prefix: PAYSTACK_SECRET_KEY ? PAYSTACK_SECRET_KEY.substring(0, 7) + "..." : "NONE"
    },
    step2_api_connectivity: {
      status: "pending",
      http_code: null,
      message: null,
      paystack_timestamp: null
    }
  }

  try {
    if (!PAYSTACK_SECRET_KEY) {
      throw new Error("PAYSTACK_SECRET_KEY is missing from Supabase secrets.")
    }

    // Attempt a simple request to Paystack's "Balance" or "Transactions" endpoint to verify the key
    const response = await fetch('https://api.paystack.co/transaction?perPage=1', {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${PAYSTACK_SECRET_KEY}`,
      },
    })

    const data = await response.json()
    results.step2_api_connectivity.http_code = response.status
    results.step2_api_connectivity.status = response.ok ? "SUCCESS" : "FAILED"
    results.step2_api_connectivity.message = response.ok ? "Successfully authenticated with Paystack" : data.message

  } catch (error) {
    results.step2_api_connectivity.status = "ERROR"
    results.step2_api_connectivity.message = error.message
  }

  return new Response(JSON.stringify(results, null, 2), {
    headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    status: 200,
  })
})
