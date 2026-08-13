import { serve } from "https://deno.land/std@0.203.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.38.0'

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: { 'Access-Control-Allow-Origin': '*' } })
  }

  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
    return new Response("Configuration missing", { status: 500 })
  }

  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

  try {
    const body = await req.json()
    const stkCallback = body?.Body?.stkCallback

    if (!stkCallback) {
      throw new Error('Invalid callback payload structure')
    }

    const checkoutRequestId = stkCallback.CheckoutRequestID
    const resultCode = stkCallback.ResultCode
    const resultDesc = stkCallback.ResultDesc

    if (resultCode === 0) {
      const callbackMetadata = stkCallback.CallbackMetadata?.Item || []
      const mpesaReceiptNumber = callbackMetadata.find((i: any) => i.Name === 'MpesaReceiptNumber')?.Value
      const amount = callbackMetadata.find((i: any) => i.Name === 'Amount')?.Value

      // 1. Find the order with this payment_id
      const { data: order, error: findError } = await supabase
        .from('orders')
        .select('id, user_id')
        .eq('payment_id', checkoutRequestId)
        .single()

      if (order) {
        // 2. Update order status
        await supabase
          .from('orders')
          .update({
            payment_status: 'paid',
            status: 'processing',
            updated_at: new Date().toISOString()
          })
          .eq('id', order.id)

        // 3. Insert into payments table (Using 'completed' for consistency)
        await supabase
          .from('payments')
          .insert({
            order_id: order.id,
            amount: amount,
            currency: 'KES',
            payment_method: 'mpesa',
            transaction_id: mpesaReceiptNumber || `MPESA_${checkoutRequestId}`,
            status: 'completed',
            provider_response: stkCallback
          })

        console.log(`Successfully processed M-Pesa payment for Order ${order.id}`)
      }
    } else {
      // Payment failed or cancelled
      await supabase
        .from('orders')
        .update({
          payment_status: 'failed',
          status: 'pending',
          updated_at: new Date().toISOString()
        })
        .eq('payment_id', checkoutRequestId)

      console.log(`M-Pesa payment failed for ${checkoutRequestId}: ${resultDesc}`)
    }

    return new Response(JSON.stringify({ message: "Callback processed" }), {
      headers: { 'Content-Type': 'application/json' },
      status: 200,
    })

  } catch (error: any) {
    console.error('M-Pesa Callback processing error:', error.message)
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
