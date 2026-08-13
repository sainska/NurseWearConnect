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
    const url = new URL(req.url)

    // Configuration Check
    if (!PAYSTACK_SECRET_KEY) throw new Error('PAYSTACK_SECRET_KEY is not configured')
    if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) throw new Error('Supabase configuration missing')

    const reference = url.searchParams.get('reference')
    if (!reference) throw new Error('Missing reference parameter')

    console.log(`Verifying Paystack transaction: ${reference}`)

    // 1. Verify transaction with Paystack
    const response = await fetch(`https://api.paystack.co/transaction/verify/${encodeURIComponent(reference)}`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${PAYSTACK_SECRET_KEY}`,
        'Content-Type': 'application/json',
      },
    })

    if (!response.ok) {
        const errorData = await response.json().catch(() => ({ message: "Unknown error" }))
        throw new Error(`Paystack API error (${response.status}): ${errorData.message}`)
    }

    const data = await response.json()
    if (!data.status) throw new Error(data.message || 'Verification request failed')

    const tx = data.data
    const orderId = tx.metadata?.order_id
    const userId = tx.metadata?.user_id
    const type = tx.metadata?.type
    const topupId = tx.metadata?.topup_id

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

    // 2. Business Logic: Handle successful transactions
    if (tx.status === 'success') {
      const paidAmount = tx.amount / 100 // Convert sub-units back to KES
      const isWalletTopup = type === 'wallet_topup' || !!topupId || (orderId && orderId.toString().startsWith('topup_'));

      if (isWalletTopup && userId) {
          console.log(`Processing Wallet Top-up for User: ${userId}, Amount: ${paidAmount}`)
          const { error: walletError } = await supabase.rpc('top_up_wallet', {
            p_user_id: userId,
            p_amount: paidAmount,
            p_reference: reference,
            p_description: 'Paystack Wallet Top-up'
          })
          if (walletError) {
              console.error('Wallet Top-up RPC Error:', walletError);
              throw walletError;
          }

          // Also update payments table
          await supabase.from('payments').update({ status: 'completed', provider_response: tx }).eq('transaction_id', reference)

      } else {
          // Atomic Update for Orders (Handles both existing and deferred creation)
          const { error: rpcError } = await supabase.rpc('finalize_successful_payment', {
            p_order_id: (orderId && orderId.length > 10) ? orderId : null,
            p_transaction_id: reference,
            p_amount: paidAmount,
            p_method: tx.channel || 'paystack',
            p_response: tx,
            p_user_id: userId
          })

          if (rpcError) {
            console.error('Finalize Payment RPC Error:', rpcError)
            throw new Error(`Database update failed: ${rpcError.message}`)
          }
          console.log(`Transaction ${reference} finalized via Verification API (Order: ${orderId || 'Deferred'})`)
      }
    }

    return new Response(JSON.stringify({
      status: tx.status,
      gateway_response: tx.gateway_response,
      reference: reference,
      amount: tx.amount,
      currency: tx.currency
    }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 200,
    })

  } catch (error: any) {
    console.error('Verification Error:', error.message)
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
