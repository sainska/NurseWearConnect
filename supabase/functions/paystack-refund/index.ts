import { serve } from "https://deno.land/std@0.203.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.38.0'

const PAYSTACK_SECRET_KEY = Deno.env.get('PAYSTACK_SECRET_KEY')
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
    if (!PAYSTACK_SECRET_KEY) throw new Error('PAYSTACK_SECRET_KEY is not configured')
    if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) throw new Error('Supabase configuration missing')

    const { orderId, amount, reason } = await req.json()

    if (!orderId) {
      throw new Error('Missing orderId')
    }

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

    // 1. Fetch transaction reference from payments table
    // We look for 'completed' status which is set by the RPC and the updated mpesa-callback
    const { data: payment, error: paymentError } = await supabase
      .from('payments')
      .select('*')
      .eq('order_id', orderId)
      .eq('status', 'completed')
      .eq('payment_method', 'paystack')
      .single()

    if (paymentError || !payment) {
      throw new Error('Completed Paystack payment record not found for this order')
    }

    // 2. Initiate Paystack Refund
    const refundResponse = await fetch('https://api.paystack.co/refund', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${PAYSTACK_SECRET_KEY}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        transaction: payment.transaction_id,
        amount: amount ? Math.round(amount * 100) : undefined, // Partial refund support
        customer_note: reason || "Refund requested by Admin"
      }),
    })

    const refundData = await refundResponse.json()

    if (!refundData.status) {
      throw new Error(refundData.message || 'Paystack refund failed')
    }

    // 3. Update Database
    await supabase
      .from('orders')
      .update({
        payment_status: 'refunded',
        status: 'cancelled',
        updated_at: new Date().toISOString()
      })
      .eq('id', orderId)

    await supabase
      .from('payments')
      .update({
        status: 'refunded',
        provider_response: { ...payment.provider_response, refund: refundData.data },
        updated_at: new Date().toISOString()
      })
      .eq('id', payment.id)

    return new Response(JSON.stringify({ status: 'success', data: refundData.data }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 200,
    })

  } catch (error: any) {
    console.error('Paystack Refund Error:', error.message)
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
