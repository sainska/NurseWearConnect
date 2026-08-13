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

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

    // Validate request body
    const body = await req.json()
    const {
        email, amount, orderId, userId, type, phoneNumber, payment_method,
        cart_items, shipping_address, shipping_method, discount_amount, total_amount, final_amount,
        vendor_id, is_fitting_service, digital_receipt_enabled
    } = body

    if (!email || !amount) {
      throw new Error('Missing required fields: email or amount')
    }

    const transactionType = type || (orderId && orderId.toString().startsWith('topup_') ? 'wallet_topup' : 'order_payment');

    // Convert amount to subunits (Paystack expects amount in cents/kobo)
    const amountInSubunits = Math.round(amount * 100)
    const reference = `ps_${crypto.randomUUID()}`

    console.log(`Initializing Paystack: User: ${userId}, Order: ${orderId}, Type: ${transactionType}, Ref: ${reference}`)

    const isTopup = transactionType === 'wallet_topup' || (orderId && orderId.toString().startsWith('topup_'));

    // Prepare Paystack request
    const paystackBody: any = {
      email,
      amount: amountInSubunits,
      currency: 'KES',
      reference,
      callback_url: 'nursewear://checkout',
      metadata: {
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
      },
    }

    // If it's M-Pesa STK, restrict channels
    if (payment_method === 'mpesa_stk') {
        paystackBody.channels = ['mobile_money']
    }

    // 1. Call Paystack to initialize transaction
    const response = await fetch('https://api.paystack.co/transaction/initialize', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${PAYSTACK_SECRET_KEY}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(paystackBody),
    })

    const data = await response.json()
    if (!data.status) {
      console.error(`Paystack Init Error: ${data.message}`)
      throw new Error(data.message || 'Paystack initialization failed')
    }

    // 2. Persist a pending record in the payments table
    // Helper to validate UUID
    const isValidUuid = (id: any) => id && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id.toString());

    const { error: dbError } = await supabase
      .from('payments')
      .insert({
        order_id: isValidUuid(paystackBody.metadata.order_id) ? paystackBody.metadata.order_id : null,
        user_id: userId,
        transaction_id: reference,
        amount: amount,
        payment_method: payment_method || 'paystack',
        status: 'pending',
        provider_response: data.data,
        metadata: paystackBody.metadata
      })

    if (dbError) {
        console.error(`Database Error recording payment: ${dbError.message} (Order ID provided: ${paystackBody.metadata.order_id})`)
    }

    // 3. Return the authorization URL and reference to the app
    return new Response(JSON.stringify({
      authorization_url: data.data.authorization_url,
      reference: reference,
      access_code: data.data.access_code
    }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 200,
    })

  } catch (error: any) {
    console.error(`Paystack Initialization Error: ${error.message}`)
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
