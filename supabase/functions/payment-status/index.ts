import { serve } from "https://deno.land/std@0.203.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.38.0'

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const url = new URL(req.url)
    const checkoutId = url.searchParams.get('checkoutId')

    if (!checkoutId) {
      throw new Error('Missing checkoutId parameter')
    }

    if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) throw new Error('Supabase configuration missing')
    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

    // 1. Try to find in orders table (Order Payments)
    const { data: order, error: orderError } = await supabase
      .from('orders')
      .select('payment_status, status, id')
      .eq('payment_id', checkoutId)
      .maybeSingle()

    if (order && order.payment_status === 'paid') {
        return new Response(JSON.stringify({
            ResultCode: "0",
            ResultDesc: "Success",
            MpesaReceiptNumber: checkoutId
        }), { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 200 })
    }

    // 2. Try to find in payments table (Wallet Top-ups or fallback)
    const { data: payment, error: payError } = await supabase
      .from('payments')
      .select('status, transaction_id')
      .eq('transaction_id', checkoutId)
      .maybeSingle()

    if (payment && payment.status === 'completed') {
        return new Response(JSON.stringify({
            ResultCode: "0",
            ResultDesc: "Success",
            MpesaReceiptNumber: checkoutId
        }), { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 200 })
    }

    // 3. Fallback to PENDING
    return new Response(JSON.stringify({
      ResultCode: "PENDING",
      ResultDesc: "Waiting for payment confirmation...",
      MpesaReceiptNumber: ""
    }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 200,
    })

  } catch (error: any) {
    console.error('Payment Status Check Error:', error.message)
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
