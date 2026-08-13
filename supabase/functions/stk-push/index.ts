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

    const body = await req.json()
    const {
        orderId, phoneNumber, amount, email, userId, type,
        cart_items, shipping_address, shipping_method, discount_amount, total_amount, final_amount,
        vendor_id, is_fitting_service, digital_receipt_enabled
    } = body

    if (!phoneNumber || !amount) {
        throw new Error('Missing required fields: phoneNumber and amount are required')
    }

    const transactionType = type || (orderId && orderId.toString().startsWith('topup_') ? 'wallet_topup' : 'order_payment');

    // 1. Initialize Charge with Paystack (M-Pesa Kenya)
    console.log(`Initiating STK Push: Order/ID: ${orderId}, Phone: ${phoneNumber}, Type: ${transactionType}`)

    const isTopup = transactionType === 'wallet_topup' || (orderId && orderId.toString().startsWith('topup_'));

    const paystackMetadata = {
        order_id: isTopup ? null : (orderId || null),
        topup_id: isTopup ? (orderId || null) : null,
        user_id: userId,
        type: transactionType,
        phone: phoneNumber,
        // Checkout Payload (Deferred Order Creation)
        cart_items,
        shipping_address,
        shipping_method,
        discount_amount,
        total_amount,
        final_amount: final_amount || amount,
        vendor_id,
        is_fitting_service,
        digital_receipt_enabled,
        custom_fields: [
          {
            display_name: "Transaction Type",
            variable_name: "transaction_type",
            value: transactionType
          }
        ]
    }

    const response = await fetch('https://api.paystack.co/charge', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${PAYSTACK_SECRET_KEY}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        email: email || "customer@nursewearconnect.com",
        amount: Math.round(amount * 100),
        currency: "KES",
        metadata: paystackMetadata,
        mobile_money: {
          phone: phoneNumber,
          provider: "mpesa"
        }
      }),
    })

    const data = await response.json()
    console.log('Paystack Charge Response:', JSON.stringify(data))

    if (!data.status) {
      console.error(`Paystack STK Error: ${data.message}`)
      throw new Error(data.message || 'Paystack Charge failed')
    }

    // Paystack might return "send_otp" or "pending"
    // For M-Pesa STK, it usually returns data.data.status === 'send_pin' or 'pay_offline'
    console.log(`STK Push Status: ${data.data.status} for Ref: ${data.data.reference}`)

    // 2. Persist to payments table if we have Supabase config
    if (data.data.reference && SUPABASE_URL && SUPABASE_SERVICE_ROLE_KEY) {
      const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

      const isValidUuid = (id: any) => id && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id.toString());

      // Update order if it's an order payment (legacy flow)
      if (orderId && !isTopup && isValidUuid(orderId)) {
          await supabase
            .from('orders')
            .update({ payment_id: data.data.reference })
            .eq('id', orderId)
      }

      // Record pending payment
      await supabase
        .from('payments')
        .insert({
            order_id: isValidUuid(paystackMetadata.order_id) ? paystackMetadata.order_id : null,
            user_id: userId,
            transaction_id: data.data.reference,
            amount: amount,
            payment_method: 'mpesa_stk',
            status: 'pending',
            metadata: paystackMetadata
        })
    }

    // 3. Return response
    return new Response(JSON.stringify({
      status: data.data.status,
      CheckoutRequestID: data.data.reference,
      display_text: "M-Pesa STK Sent. Please enter PIN on your phone."
    }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 200,
    })

  } catch (error: any) {
    console.error('STK Push Error:', error.message)
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
