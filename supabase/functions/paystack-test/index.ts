import { serve } from "https://deno.land/std@0.203.0/http/server.ts"

const PAYSTACK_SECRET_KEY = Deno.env.get('PAYSTACK_SECRET_KEY')
const SUPABASE_URL = Deno.env.get('SUPABASE_URL')
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })

  const report = {
    env: {
      PAYSTACK_SECRET_KEY: PAYSTACK_SECRET_KEY ? "Present (Starts with " + PAYSTACK_SECRET_KEY.substring(0, 7) + "...)" : "MISSING",
      SUPABASE_URL: SUPABASE_URL ? "Present" : "MISSING",
      SUPABASE_SERVICE_ROLE_KEY: SUPABASE_SERVICE_ROLE_KEY ? "Present" : "MISSING",
    },
    connectivity: {
      paystack_api: "Pending",
      details: ""
    }
  }

  try {
    if (!PAYSTACK_SECRET_KEY) throw new Error("Missing Secret Key")

    // Test connection to Paystack by fetching a single transaction (minimal data)
    const startTime = Date.now()
    const response = await fetch('https://api.paystack.co/transaction?perPage=1', {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${PAYSTACK_SECRET_KEY}`,
      },
    })
    const duration = Date.now() - startTime

    if (response.ok) {
      report.connectivity.paystack_api = "SUCCESS"
      report.connectivity.details = `Reached Paystack in ${duration}ms. API key is valid.`
    } else {
      const errorBody = await response.json()
      report.connectivity.paystack_api = "FAILED"
      report.connectivity.details = `Paystack returned ${response.status}: ${errorBody.message}`
    }

  } catch (error) {
    report.connectivity.paystack_api = "ERROR"
    report.connectivity.details = error.message
  }

  return new Response(JSON.stringify(report, null, 2), {
    headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    status: report.connectivity.paystack_api === "SUCCESS" ? 200 : 500,
  })
})
